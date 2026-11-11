package my.sado.app.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class HealthToday(
    val steps: Long? = null,
    val totalKcal: Int? = null,
    val activeKcal: Int? = null,
    val avgHr: Int? = null,
    val minHr: Int? = null,
    val maxHr: Int? = null,
    val restingHr: Int? = null,
    val sleepHours: Float? = null,
)

data class HcSession(val id: String, val sport: Progress.Sport, val title: String, val epochDay: Long, val minutes: Int, val kcal: Int?, val app: String)

// Read-only bridge to Health Connect: Garmin Connect, Samsung Health, Fitbit etc. write there,
// Sado only reads. Every call is wrapped so a missing permission just leaves that number blank.
object Health {
    const val PROVIDER = "com.google.android.apps.healthdata"

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )

    fun status(context: Context): Int = HealthConnectClient.getSdkStatus(context, PROVIDER)

    fun available(context: Context) = status(context) == HealthConnectClient.SDK_AVAILABLE

    private fun client(context: Context) = HealthConnectClient.getOrCreate(context)

    suspend fun granted(context: Context): Set<String> =
        if (!available(context)) emptySet() else runCatching { client(context).permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    private fun zone() = ZoneId.systemDefault()
    private fun startOf(day: LocalDate): Instant = day.atStartOfDay(zone()).toInstant()

    suspend fun today(context: Context): HealthToday {
        if (!available(context)) return HealthToday()
        val c = client(context)
        val range = TimeRangeFilter.between(startOf(LocalDate.now()), Instant.now())
        suspend fun <T : Any> metric(m: androidx.health.connect.client.aggregate.AggregateMetric<T>): T? =
            runCatching { c.aggregate(AggregateRequest(setOf(m), range))[m] }.getOrNull()
        return HealthToday(
            steps = metric(StepsRecord.COUNT_TOTAL),
            totalKcal = metric(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories?.toInt(),
            activeKcal = metric(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)?.inKilocalories?.toInt(),
            avgHr = metric(HeartRateRecord.BPM_AVG)?.toInt(),
            minHr = metric(HeartRateRecord.BPM_MIN)?.toInt(),
            maxHr = metric(HeartRateRecord.BPM_MAX)?.toInt(),
            restingHr = metric(RestingHeartRateRecord.BPM_AVG)?.toInt(),
            sleepHours = lastNightSleep(context),
        )
    }

    // "last night" = yesterday 18:00 until today 14:00, which also catches late sleepers and naps after night shifts
    suspend fun lastNightSleep(context: Context): Float? {
        if (!available(context)) return null
        val from = LocalDate.now().minusDays(1).atTime(18, 0).atZone(zone()).toInstant()
        val to = LocalDate.now().atTime(14, 0).atZone(zone()).toInstant().coerceAtMost(Instant.now())
        val d = runCatching {
            client(context).aggregate(AggregateRequest(setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL), TimeRangeFilter.between(from, to)))[SleepSessionRecord.SLEEP_DURATION_TOTAL]
        }.getOrNull() ?: return null
        return if (d.isZero) null else d.toMinutes() / 60f
    }

    suspend fun sessions(context: Context, days: Long = 7): List<HcSession> {
        if (!available(context)) return emptyList()
        val c = client(context)
        val range = TimeRangeFilter.between(startOf(LocalDate.now().minusDays(days)), Instant.now())
        val records = runCatching { c.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range)).records }.getOrDefault(emptyList())
        return records.map { r ->
            val kcal = runCatching {
                c.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), TimeRangeFilter.between(r.startTime, r.endTime)))[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
            }.getOrNull()?.inKilocalories?.toInt()?.takeIf { it > 0 }
            val sport = sportFor(r.exerciseType)
            HcSession(
                id = r.metadata.id,
                sport = sport,
                title = r.title?.ifBlank { null } ?: sport.name,
                epochDay = r.startTime.atZone(zone()).toLocalDate().toEpochDay(),
                minutes = java.time.Duration.between(r.startTime, r.endTime).toMinutes().toInt(),
                kcal = kcal,
                app = appName(r.metadata.dataOrigin.packageName),
            )
        }.filter { it.minutes >= 5 }.sortedByDescending { it.epochDay }
    }

    private fun sportFor(type: Int): Progress.Sport {
        val name = when (type) {
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING, ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "Larian"
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Jalan kaki"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING, ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "Basikal"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL, ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "Renang"
            ExerciseSessionRecord.EXERCISE_TYPE_SOCCER -> "Bola sepak"
            ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> "Badminton"
            ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "Bola keranjang"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING, ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Gym"
            else -> "Lain-lain"
        }
        return Progress.sports.first { it.name == name }
    }

    private fun appName(pkg: String) = when {
        pkg.contains("garmin") -> "Garmin"
        pkg.contains("samsung") -> "Samsung Health"
        pkg.contains("fitbit") -> "Fitbit"
        pkg.contains("strava") -> "Strava"
        pkg.contains("google") -> "Google"
        else -> pkg.substringAfterLast('.')
    }
}
