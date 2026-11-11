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
import android.content.Context
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
@SuppressLint("MissingPermission")
object HeartRate {
    private val SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    enum class Status { Idle, Scanning, Connecting, Connected }
    data class Found(val address: String, val name: String)

    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = _bpm
    private val _status = MutableStateFlow(Status.Idle)
    val status: StateFlow<Status> = _status
    private val _found = MutableStateFlow<List<Found>>(emptyList())
    val found: StateFlow<List<Found>> = _found

    private var gatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private val main = Handler(Looper.getMainLooper())
    private val samples = mutableListOf<Int>()
    private var recording = false

    val permissions: Array<String> =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    fun hasPermissions(context: Context) = permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    private fun adapter(context: Context) = context.getSystemService(BluetoothManager::class.java)?.adapter

    fun bluetoothOn(context: Context) = adapter(context)?.isEnabled == true

    fun scan(context: Context) {
        val scanner = adapter(context)?.bluetoothLeScanner ?: return
        if (!hasPermissions(context)) return
        stopScan(context)
        _found.value = emptyList()
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
        main.postDelayed({ stopScan(context) }, 15_000)
    }

    fun stopScan(context: Context) {
        val cb = scanCallback ?: return
        scanCallback = null
        if (hasPermissions(context)) runCatching { adapter(context)?.bluetoothLeScanner?.stopScan(cb) }
        if (_status.value == Status.Scanning) _status.value = Status.Idle
    }

    fun connect(context: Context, address: String) {
        if (!hasPermissions(context) || !bluetoothOn(context)) return
        if (gatt?.device?.address == address && _status.value != Status.Idle) return
        stopScan(context)
        disconnect()
        val device = runCatching { adapter(context)?.getRemoteDevice(address) }.getOrNull() ?: return
        _status.value = Status.Connecting
        gatt = device.connectGatt(context.applicationContext, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.let { runCatching { it.disconnect(); it.close() } }
        gatt = null
        _bpm.value = null
        _status.value = Status.Idle
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
                _bpm.value = null
                _status.value = Status.Idle
                runCatching { g.close() }
                if (gatt == g) gatt = null
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
            _status.value = Status.Connected
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
