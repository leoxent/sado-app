package my.sado.app.data

/**
 * Which watch the user wears, found on the phone itself (paired Bluetooth names, installed companion apps, and which
 * app writes heart rate to Health Connect), and how to start and stop a workout on it. Nothing leaves the phone.
 */
object Watch {
    const val NONE = "none"

    /** One watch brand: how it looks in the guide and the steps to start and stop a strength workout on it. */
    data class Brand(
        val id: String,
        val label: String,
        val round: Boolean, // round face, or a rectangular band or watch
        val buttons: Int, // physical buttons drawn on the right side (Garmin also gets its left ones)
        val start: List<Step>,
        val stop: List<Step>,
        val canBroadcast: Boolean = false, // standard Bluetooth heart-rate broadcast
    )

    /** [button] is the physical button pressed (0 = top right), null for a tap on the screen. */
    data class Step(val text: String, val screen: String, val button: Int? = null)

    val brands: List<Brand> = listOf(
        Brand(
            "garmin", "Garmin", round = true, buttons = 2,
            start = listOf(
                Step("Tekan START (butang kanan atas)", "Aktiviti", 0),
                Step("Leret atau tekan DOWN, pilih Strength", "Strength", null),
                Step("Tekan START untuk mula", "00:00", 0),
            ),
            stop = listOf(
                Step("Tekan STOP (butang kanan atas)", "Pause", 0),
                Step("Pilih Save", "Save", null),
            ),
            canBroadcast = true,
        ),
        Brand(
            "samsung", "Galaxy Watch", round = true, buttons = 2,
            start = listOf(
                Step("Tekan butang Home (atas)", "Apps", 0),
                Step("Buka Samsung Health, pilih Workout", "Workout", null),
                Step("Pilih Weight machines atau Circuit training", "Weights", null),
                Step("Tekan Start", "00:00", null),
            ),
            stop = listOf(
                Step("Leret ke kanan, tekan Pause", "Pause", null),
                Step("Tekan End, kemudian simpan", "End", null),
            ),
        ),
        Brand(
            "pixel", "Pixel Watch", round = true, buttons = 1,
            start = listOf(
                Step("Buka Fitbit Exercise", "Exercise", null),
                Step("Pilih Weights", "Weights", null),
                Step("Tekan Start", "00:00", null),
            ),
            stop = listOf(
                Step("Tekan Pause", "Pause", null),
                Step("Tekan End", "End", null),
            ),
        ),
        Brand(
            "fitbit", "Fitbit", round = false, buttons = 1,
            start = listOf(
                Step("Buka app Exercise", "Exercise", null),
                Step("Pilih Weights", "Weights", null),
                Step("Tekan Start", "00:00", null),
            ),
            stop = listOf(
                Step("Tekan butang, kemudian Finish", "Finish", 0),
            ),
        ),
        Brand(
            "huawei", "Huawei", round = true, buttons = 2,
            start = listOf(
                Step("Tekan butang bawah, buka Workout", "Workout", 1),
                Step("Pilih Strength training", "Strength", null),
                Step("Tekan Start", "00:00", null),
            ),
            stop = listOf(
                Step("Tekan butang atas untuk pause", "Pause", 0),
                Step("Tahan Stop untuk simpan", "Stop", null),
            ),
        ),
        Brand(
            "amazfit", "Amazfit", round = true, buttons = 2,
            start = listOf(
                Step("Tekan butang bawah, buka Workout", "Workout", 1),
                Step("Pilih Strength training", "Strength", null),
                Step("Tekan GO", "00:00", null),
            ),
            stop = listOf(
                Step("Tekan butang atas", "Pause", 0),
                Step("Tekan End, kemudian simpan", "End", null),
            ),
        ),
        Brand(
            "xiaomi", "Xiaomi", round = false, buttons = 1,
            start = listOf(
                Step("Buka Workout", "Workout", null),
                Step("Pilih Strength training", "Strength", null),
                Step("Tekan Start", "00:00", null),
            ),
            stop = listOf(
                Step("Leret, tekan Pause", "Pause", null),
                Step("Tekan End", "End", null),
            ),
        ),
        Brand(
            "other", "Jam lain", round = true, buttons = 1,
            start = listOf(
                Step("Buka app senaman kat jam", "Workout", null),
                Step("Pilih Strength atau Weights", "Strength", null),
                Step("Tekan Start", "00:00", null),
            ),
            stop = listOf(
                Step("Stop dan simpan aktiviti", "Save", null),
            ),
        ),
    )

    fun brand(id: String?): Brand? = brands.firstOrNull { it.id == id }

    /** What was found, and how sure: a paired watch's own name beats the app writing its data, which beats an installed app. */
    data class Found(val brand: String, val model: String?, val how: String)

    private val namePatterns = listOf(
        "garmin" to Regex("forerunner|f[eē]nix|venu|vivoactive|vivomove|vivosmart|instinct|epix|enduro|approach s|descent|lily|garmin", RegexOption.IGNORE_CASE),
        "samsung" to Regex("galaxy watch|gear s\\d|galaxy fit", RegexOption.IGNORE_CASE),
        "pixel" to Regex("pixel watch", RegexOption.IGNORE_CASE),
        "fitbit" to Regex("fitbit|charge \\d|versa|inspire|luxe|sense \\d", RegexOption.IGNORE_CASE),
        "huawei" to Regex("huawei (watch|band)|honor (watch|band)", RegexOption.IGNORE_CASE),
        "amazfit" to Regex("amazfit|\\bgtr\\b|\\bgts\\b|\\bbip\\b|t-rex", RegexOption.IGNORE_CASE),
        "xiaomi" to Regex("mi (smart )?band|xiaomi (smart )?band|xiaomi watch|redmi watch|mi watch", RegexOption.IGNORE_CASE),
    )

    // companion apps: present means the brand, not the model
    val packages = mapOf(
        "com.garmin.android.apps.connectmobile" to "garmin",
        "com.samsung.android.app.watchmanager" to "samsung",
        "com.google.android.apps.wearables.maestro.companion" to "pixel",
        "com.fitbit.FitbitMobile" to "fitbit",
        "com.huawei.health" to "huawei",
        "com.huami.watch.hmwatchmanager" to "amazfit",
        "com.xiaomi.wearable" to "xiaomi",
        "com.mi.health" to "xiaomi",
    )

    // apps that write heart rate into Health Connect; a phone has no heart sensor, so these mean a watch
    private val origins = packages + mapOf("com.sec.android.app.shealth" to "samsung")

    /** A paired device's name as a watch: "Forerunner 165" from "Forerunner 165 (A1B2)". */
    fun fromName(name: String): Found? {
        val brand = namePatterns.firstOrNull { it.second.containsMatchIn(name) }?.first ?: return null
        val model = name.trim().replace(Regex("\\s+LE$"), "").replace(Regex("\\s*\\([^)]*\\)\\s*$"), "").replace(Regex("\\s+[0-9A-F]{4}$"), "").trim()
        return Found(brand, model.ifBlank { null }, "Bluetooth")
    }

    fun fromPackages(installed: Set<String>): List<Found> =
        packages.filterKeys { it in installed }.values.distinct().map { Found(it, null, "app") }

    /** From Health Connect heart-rate records: the app that wrote them, and the device model when it said. */
    fun fromOrigins(origins: List<Pair<String, String?>>): List<Found> =
        origins.mapNotNull { (pkg, model) -> this.origins[pkg]?.let { Found(it, model?.takeIf { m -> m.isNotBlank() }, "Health Connect") } }
            .distinctBy { it.brand }

    /** Every watch found, best first: one per brand, its model taken from whichever source named it. */
    fun merge(bluetooth: List<Found>, health: List<Found>, apps: List<Found>): List<Found> {
        val all = bluetooth + health + apps
        return all.map { it.brand }.distinct().map { b ->
            val first = all.first { it.brand == b }
            first.copy(model = first.model ?: all.firstOrNull { it.brand == b && it.model != null }?.model)
        }
    }

    fun label(brand: String?, model: String?): String = when {
        brand == null -> "Belum pilih"
        brand == NONE -> "Takde jam"
        model != null -> model + (brand(brand)?.label?.let { if (model.contains(it, true)) "" else " ($it)" } ?: "")
        else -> brand(brand)?.label ?: brand
    }
}
