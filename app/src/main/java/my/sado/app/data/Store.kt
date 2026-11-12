package my.sado.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File

// Single-user, local-first: the whole app state lives in one JSON file in private storage,
// which also makes the later ".sado" export a straight copy.
object Store {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var file: File
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state

    fun init(context: Context) {
        file = File(context.filesDir, "sado_state.json")
        if (file.exists()) {
            _state.value = migrate(runCatching { json.decodeFromString<AppState>(file.readText()) }.getOrDefault(AppState()))
            dropTiers()
        }
    }

    // v0.8 renamed the formal day tags to the English gym terms people actually use
    private val renamedTags = mapOf(
        "Rehat" to "Rest", "Tarik" to "Pull", "Tolak" to "Push", "K+T" to "Legs+Push", "K+C" to "Legs+Core",
        "Atas" to "Upper", "Bawah" to "Lower", "Penuh" to "Full body", "Kuasa" to "Power",
        "Tarik (belakang & bahu)" to "Pull day", "Badan atas" to "Upper body",
        "HIIT seluruh badan" to "HIIT full body", "Seluruh badan" to "Full body",
        // session names went back to English
        "Pull day (belakang & bahu)" to "Pull day", "Push day (dada & bahu)" to "Push day", "Kaki + dada & bahu" to "Legs + Push",
        "Kaki + core" to "Legs + Core", "Power (kaki & letupan)" to "Power", "Badan bawah" to "Lower body",
    )

    private fun migrate(state: AppState): AppState {
        // 5 → 15 levels: old Kurus/Berisi/Fit/Sado/Beast map to tier I of the same body
        val s0 = if (state.levelFloor >= 0) state else state.copy(levelFloor = Progress.oldLevel(state) * 3)
        val s = Pets.migrate(s0.copy(peakXp = maxOf(s0.peakXp, s0.xp), answers = s0.answers?.let(::splitDelts)))
        // a plan from an older builder is rebuilt once from the saved answers; ladders and progress stay
        if (s.plan != null && s.answers != null && s.planBuilt < PlanBuilder.VERSION)
            return s.copy(plan = PlanBuilder.build(s.answers, s.ladders), planBuilt = PlanBuilder.VERSION)
        val p = s.plan ?: return s
        val reps = mapOf("maks rep" to "maximum rep", "max rep" to "maximum rep", "5 turun perlahan" to "5 rep turun slow", "40s kerja, 20s rehat" to "40s work, 20s rest")
        return s.copy(plan = p.copy(
            week = p.week.map { renamedTags[it] ?: it },
            dayLabel = renamedTags[p.dayLabel] ?: p.dayLabel,
            exercises = p.exercises.map { e -> reps[e.reps]?.let { e.copy(reps = it) } ?: e },
            // plans from before one-session-per-day-type get their other sessions; the week stays the same
            sessions = p.sessions.map { it.copy(label = PlanBuilder.tagLabels[it.tag] ?: it.label) }.ifEmpty { s.answers?.let { a -> PlanBuilder.build(a, s.ladders).sessions.takeIf { n -> n.map { it.tag }.toSet() == p.week.filter { it != "Rest" }.toSet() } }.orEmpty() },
        ))
    }

    // v0.43 split the planner's one "delts" group into front, side and rear: each keeps the rating the old one had
    internal fun splitDelts(a: PlannerAnswers): PlannerAnswers {
        val parts = listOf("front_delts", "side_delts", "rear_delts")
        val r = a.ratings["delts"]
        return a.copy(
            ratings = if (r == null) a.ratings else a.ratings - "delts" + parts.filter { it !in a.ratings }.associateWith { r },
            priority = if ("delts" in a.priority) (a.priority - "delts" + listOf("front_delts", "side_delts")).distinct() else a.priority,
        )
    }

    fun update(change: (AppState) -> AppState) {
        val next = Pets.settle(change(_state.value)) // a deleted log takes its EXP back
        _state.value = next
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(AppState.serializer(), next))
        tmp.renameTo(file)
    }

    /** applies tier drops from time off; safe to call often, it only writes when one is due */
    fun dropTiers() {
        val next = Progress.applyDecay(_state.value)
        if (next != _state.value) update { next }
    }

    fun reset() = update { AppState() }

    fun exportText(): String = json.encodeToString(AppState.serializer(), _state.value)

    // dev mode simulations: the real state waits in its own file, so it survives the app being closed
    private val realState get() = File(file.parentFile, "sado_state_before_simulation.json")

    fun simulating() = ::file.isInitialized && realState.exists()

    fun startSimulation(change: (AppState) -> AppState) {
        if (simulating()) return
        realState.writeText(exportText())
        update(change)
    }

    fun endSimulation() {
        if (!simulating()) return
        restore(realState.readText())
        realState.delete()
    }

    // used by backup restore; throws if the text isn't a Sado state file
    fun restore(text: String) {
        val restored = migrate(json.decodeFromString<AppState>(text))
        update { restored }
    }
}
