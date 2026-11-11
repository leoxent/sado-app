package my.sado.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

// Live heart rate from a watch in "Broadcast Heart Rate" mode (Garmin, Polar, Coros, chest straps):
// they all speak the standard Bluetooth LE Heart Rate profile, so no vendor SDK or account is needed.
// The watch is picked once in the system's companion-device dialog; its address is saved and reconnected to after.
@SuppressLint("MissingPermission")
object HeartRate {
    private val SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    enum class Status { Idle, Scanning, Connecting, Connected }
    data class Found(val address: String, val name: String)

    /** What went wrong, in words the user can act on; null when all is well. */
    enum class Problem(val text: String) {
        BluetoothOff("Bluetooth tertutup. Hidupkan Bluetooth dulu."),
        NoPermission("Perlukan kebenaran Bluetooth (Peranti berdekatan) untuk sambung ke jam."),
        NotFound("Jam tak dijumpai. Pastikan Broadcast Heart Rate aktif kat jam, dan jam dekat dengan phone."),
        Lost("Sambungan terputus. Tengah cuba sambung semula…"),
        PickFailed("Tak dapat buka pilihan peranti. Guna Cari manual."),
    }

    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = _bpm
    private val _status = MutableStateFlow(Status.Idle)
    val status: StateFlow<Status> = _status
    private val _found = MutableStateFlow<List<Found>>(emptyList())
    val found: StateFlow<List<Found>> = _found
    private val _problem = MutableStateFlow<Problem?>(null)
    val problem: StateFlow<Problem?> = _problem

    private var gatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private val main = Handler(Looper.getMainLooper())
    private val samples = mutableListOf<Int>()
    private var recording = false

    // the watch the user wants: a drop while it is wanted is retried; Lupakan or a disconnect is not
    private var wanted: String? = null
    private var everConnected = false
    private var retries = 0
    private const val MAX_RETRIES = 5
    private const val CONNECT_TIMEOUT = 15_000L
    private var appContext: Context? = null

    val permissions: Array<String> =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermissions(context: Context) = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    private fun adapter(context: Context) = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun bluetoothOn(context: Context) = adapter(context)?.isEnabled == true

    /** Why a connect can't start right now, or null when it can. */
    fun blocker(context: Context): Problem? = when {
        !bluetoothOn(context) -> Problem.BluetoothOff
        !hasPermissions(context) -> Problem.NoPermission
        else -> null
    }

    fun clearProblem() { _problem.value = null }

    fun denied() { _problem.value = Problem.NoPermission }

    // ── pick the watch first: the system's companion-device dialog, filtered to heart-rate broadcasters ──────
    private fun pickRequest(): AssociationRequest {
        val filter = BluetoothLeDeviceFilter.Builder()
            .setScanFilter(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build())
            .build()
        return AssociationRequest.Builder().addDeviceFilter(filter).setSingleDevice(false).build()
    }

    /** Opens the system's device picker; [launch] shows it, [failed] runs when it can't be shown. */
    fun pick(context: Context, launch: (IntentSender) -> Unit, failed: () -> Unit) {
        if (!bluetoothOn(context)) { _problem.value = Problem.BluetoothOff; return }
        _problem.value = null
        val cdm = context.getSystemService(CompanionDeviceManager::class.java) ?: return failed()
        val cb = object : CompanionDeviceManager.Callback() {
            @Deprecated("Android 12 and below")
            override fun onDeviceFound(sender: IntentSender) = launch(sender)
            override fun onAssociationPending(sender: IntentSender) = launch(sender)
            override fun onFailure(error: CharSequence?) { _problem.value = Problem.NotFound }
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) cdm.associate(pickRequest(), { it.run() }, cb)
            else {
                @Suppress("DEPRECATION")
                cdm.associate(pickRequest(), cb, main)
            }
        }.onFailure { failed() }
    }

    /** The watch the user picked in the system dialog, read from its result. */
    fun picked(data: Intent?): Found? {
        data ?: return null
        @Suppress("DEPRECATION")
        val any: Any? = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE)
        val dev = when (any) {
            is ScanResult -> any.device
            is BluetoothDevice -> any
            else -> null
        }
        if (dev != null) return Found(dev.address, runCatching { dev.name }.getOrNull() ?: (any as? ScanResult)?.scanRecord?.deviceName ?: dev.address)
        if (Build.VERSION.SDK_INT >= 33) {
            val info = data.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)
            val mac = info?.deviceMacAddress?.toString()?.uppercase() ?: return null
            return Found(mac, info.displayName?.toString() ?: mac)
        }
        return null
    }

    // ── manual scan, the fallback when the picker isn't available ──────────────────────────────────────
    fun scan(context: Context) {
        blocker(context)?.let { _problem.value = it; return }
        val scanner = adapter(context)?.bluetoothLeScanner ?: return
        stopScan(context)
        _found.value = emptyList()
        _problem.value = null
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val d = result.device
                if (_found.value.none { it.address == d.address }) _found.value = _found.value + Found(d.address, d.name ?: result.scanRecord?.deviceName ?: d.address)
            }
        }
        scanCallback = cb
        _status.value = Status.Scanning
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()
        scanner.startScan(listOf(filter), ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
        main.postDelayed({
            if (scanCallback === cb) {
                stopScan(context)
                if (_found.value.isEmpty()) _problem.value = Problem.NotFound
            }
        }, 15_000)
    }

    fun stopScan(context: Context) {
        val cb = scanCallback ?: return
        scanCallback = null
        if (hasPermissions(context)) runCatching { adapter(context)?.bluetoothLeScanner?.stopScan(cb) }
        if (_status.value == Status.Scanning) _status.value = Status.Idle
    }

    // ── connection ─────────────────────────────────────────────────────────────────────────────
    fun connect(context: Context, address: String, retry: Boolean = false) {
        appContext = context.applicationContext
        blocker(context)?.let { _problem.value = it; return }
        if (gatt?.device?.address == address && _status.value != Status.Idle) return
        stopScan(context)
        closeGatt()
        if (!retry) { retries = 0; everConnected = false; _problem.value = null }
        wanted = address
        val device = runCatching { adapter(context)?.getRemoteDevice(address) }.getOrNull() ?: run { _problem.value = Problem.NotFound; return }
        _status.value = Status.Connecting
        val g = device.connectGatt(context.applicationContext, false, callback, BluetoothDevice.TRANSPORT_LE)
        gatt = g
        // nothing answers: the watch is off, out of range, or not broadcasting
        main.postDelayed({
            if (gatt === g && _status.value == Status.Connecting) { closeGatt(); _status.value = Status.Idle; onDropped() }
        }, CONNECT_TIMEOUT)
    }

    /** The saved watch, if any: connects quietly, so the pick is done once. */
    fun reconnectSaved(context: Context, address: String?) {
        if (address == null || _status.value != Status.Idle) return
        if (blocker(context) == null) connect(context, address)
    }

    private fun closeGatt() {
        gatt?.let { runCatching { it.disconnect(); it.close() } }
        gatt = null
        _bpm.value = null
    }

    fun disconnect() {
        wanted = null
        closeGatt()
        _status.value = Status.Idle
        _problem.value = null
    }

    // a wanted watch dropped or never answered: try again a few times, a little slower each time
    private fun onDropped() {
        val addr = wanted ?: return
        val ctx = appContext ?: return
        if (retries >= MAX_RETRIES) {
            _problem.value = if (everConnected) Problem.Lost else Problem.NotFound
            wanted = null
            return
        }
        retries++
        _problem.value = if (everConnected) Problem.Lost else Problem.NotFound
        main.postDelayed({ if (wanted == addr && _status.value == Status.Idle) connect(ctx, addr, retry = true) }, 2_000L * retries)
    }

    fun startRecording() { samples.clear(); recording = true }

    // leaving the session for a moment (Info screen) keeps the samples so far
    fun pause() { recording = false }
    fun resume() { recording = true }

    // average and peak over the session; null when no watch was streaming
    fun stopRecording(): Pair<Int, Int>? {
        recording = false
        if (samples.isEmpty()) return null
        return samples.average().toInt() to samples.max()
    }

    private fun onValue(v: ByteArray) {
        if (v.size < 2) return
        val sixteenBit = v[0].toInt() and 1 == 1
        val bpm = if (sixteenBit && v.size >= 3) (v[1].toInt() and 0xff) or ((v[2].toInt() and 0xff) shl 8) else v[1].toInt() and 0xff
        if (bpm <= 0) return
        _bpm.value = bpm
        if (recording) samples += bpm
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) g.discoverServices()
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runCatching { g.close() }
                main.post {
                    if (gatt !== g) return@post // an old connection closing, not the current one
                    gatt = null
                    _bpm.value = null
                    _status.value = Status.Idle
                    onDropped()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(SERVICE)?.getCharacteristic(MEASUREMENT) ?: return
            g.setCharacteristicNotification(ch, true)
            val desc = ch.getDescriptor(CCCD) ?: return
            if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            else {
                @Suppress("DEPRECATION")
                desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(desc)
            }
            main.post {
                _status.value = Status.Connected
                everConnected = true
                retries = 0
                _problem.value = null
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            if (ch.uuid == MEASUREMENT) onValue(value)
        }

        @Deprecated("Android 12 and below")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 33 && ch.uuid == MEASUREMENT) onValue(ch.value ?: return)
        }
    }
}
