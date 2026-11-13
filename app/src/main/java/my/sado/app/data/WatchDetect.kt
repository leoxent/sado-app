package my.sado.app.data

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant

/** Looks on the phone for the user's watch; see [Watch]. Each source is skipped quietly when it isn't allowed. */
object WatchDetect {
    fun bluetooth(context: Context): List<Watch.Found> {
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return runCatching {
            @Suppress("MissingPermission")
            adapter.bondedDevices.orEmpty().mapNotNull { d -> runCatching { d.name }.getOrNull()?.let(Watch::fromName) }
        }.getOrDefault(emptyList())
    }

    fun apps(context: Context): List<Watch.Found> {
        val pm = context.packageManager
        val installed = Watch.packages.keys.filter { pkg -> runCatching { pm.getPackageInfo(pkg, 0); true }.getOrDefault(false) }.toSet()
        return Watch.fromPackages(installed)
    }

    suspend fun health(context: Context): List<Watch.Found> = runCatching {
        if (!Health.available(context)) return emptyList()
        val c = HealthConnectClient.getOrCreate(context)
        val now = Instant.now()
        val recs = c.readRecords(ReadRecordsRequest(HeartRateRecord::class, TimeRangeFilter.between(now.minusSeconds(7 * 86_400L), now), pageSize = 50)).records
        Watch.fromOrigins(recs.map { it.metadata.dataOrigin.packageName to it.metadata.device?.model })
    }.getOrDefault(emptyList())

    suspend fun all(context: Context): List<Watch.Found> = Watch.merge(bluetooth(context), health(context), apps(context))

    /**
     * Fills in the watch once, when the user hasn't picked one yet: the best match is saved but not marked confirmed,
     * so "Jam & kesihatan" can ask "Betul?". Never overrides a choice the user made.
     */
    suspend fun autoDetect(context: Context) {
        val st = Store.state.value.settings
        if (st.watch != null) return
        val best = all(context).firstOrNull() ?: return
        Store.update { s -> if (s.settings.watch != null) s else s.copy(settings = s.settings.copy(watch = best.brand, watchModel = best.model, watchConfirmed = false)) }
    }
}
