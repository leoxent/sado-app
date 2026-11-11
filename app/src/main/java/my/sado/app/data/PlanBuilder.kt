package my.sado.app.data

import kotlin.math.roundToInt

// Port of the design canvas planner (Planner.dc.html), tested there against sample profiles.
object PlanBuilder {
    val shapeLabels = mapOf(
        "vshape" to "V-shape", "aesthetic" to "Aesthetic", "bulk" to "Besar & kuat", "athletic" to "Atletik", "lean" to "Kurus & tone", "shredded" to "Shredded",
    )
    val groupNames = mapOf(
        "chest" to "Dada", "front_delts" to "Bahu depan", "side_delts" to "Bahu sisi", "rear_delts" to "Bahu belakang", "lats" to "Belakang", "traps" to "Trapezius",
        "lower_back" to "Pinggang", "arms" to "Lengan", "abs" to "Perut", "legs" to "Kaki", "glutes" to "Punggung", "cardio" to "Kardio",
    )
    // small helpers, not what a shape is built from: without a rating or a shape asking for them they get only a little weight
    private val baseWeight = mapOf("front_delts" to 3.0, "rear_delts" to 3.0, "traps" to 1.5, "lower_back" to 1.5)
    private val profiles = mapOf(
        // side delts are what make the shoulders wider; front delts already get plenty from every press and push-up
        "vshape" to mapOf("lats" to 35.0, "side_delts" to 25.0, "rear_delts" to 5.0, "front_delts" to 3.0, "abs" to 15.0, "legs" to 10.0, "chest" to 10.0),
        "aesthetic" to mapOf("chest" to 18.0, "side_delts" to 11.0, "front_delts" to 4.0, "rear_delts" to 3.0, "abs" to 18.0, "lats" to 16.0, "arms" to 14.0, "legs" to 10.0, "glutes" to 6.0),
        "bulk" to mapOf("legs" to 24.0, "chest" to 22.0, "lats" to 20.0, "arms" to 14.0, "front_delts" to 6.0, "side_delts" to 6.0, "glutes" to 8.0),
        "athletic" to mapOf("legs" to 24.0, "cardio" to 24.0, "abs" to 18.0, "chest" to 12.0, "lats" to 12.0, "glutes" to 10.0),
        "lean" to mapOf("cardio" to 36.0, "legs" to 20.0, "abs" to 18.0, "chest" to 13.0, "lats" to 13.0),
        // muscle that shows: hypertrophy everywhere, abs and arms a little more, some cardio to stay lean
        "shredded" to mapOf("abs" to 18.0, "chest" to 15.0, "side_delts" to 9.0, "front_delts" to 3.0, "rear_delts" to 3.0, "lats" to 15.0, "arms" to 14.0, "legs" to 12.0, "cardio" to 11.0),
    )
    private val hyperShapes = setOf("vshape", "aesthetic", "bulk", "shredded")

    fun allowed(m: Move, a: PlannerAnswers): Boolean {
        val hasBar = a.pull >= 0 || "bar" in a.equip
        val needOk = when (m.need) {
            null -> true
            "bar" -> hasBar
            else -> m.need in a.equip
        }
        return a.level in m.levels && needOk && loadOk(m, a) &&
            (m.minPull == null || a.pull >= m.minPull) &&
            (m.maxPull == null || (a.pull >= 0 && a.pull <= m.maxPull)) &&
            m.avoid.none { it in a.injury }
    }

    /** A weighted move needs something to carry: a backpack to load, or a dumbbell. */
    fun loadOk(m: Move, a: PlannerAnswers) = !m.weighted || a.equip.any { it == "backpack" || it == "dumbbell" }

    // swap a ladder move for the rung the user has actually reached; a rung the gear or an injury rules out
    // falls back to the highest usable rung below it, so passing an unusable top rung never sends the user back to the start
    fun progressed(id: String, a: PlannerAnswers, ladders: Map<String, LadderProgress>): String {
        val key = Catalog.ladderOf(id) ?: return id
        val rung = ladders[key]?.rung ?: return id
        val rungs = Catalog.ladders.getValue(key)
        return (rung.coerceAtMost(rungs.lastIndex) downTo 0).map { rungs[it] }.firstOrNull { fits(Catalog.get(it), a) } ?: id
    }

    /** Whether a move can be done with the user's gear and injuries; the ladder climbs only onto usable rungs. */
    fun usable(id: String, a: PlannerAnswers?) = a == null || fits(Catalog.get(id), a)

    // the saved plan was built once; swap in the rung each ladder has reached since, with that move's own dose
    fun refresh(exercises: List<PlannedExercise>, a: PlannerAnswers, ladders: Map<String, LadderProgress>): List<PlannedExercise> =
        exercises.map { e ->
            val id = progressed(e.id, a, ladders)
            if (id == e.id) e else dose(Catalog.get(id), a).let { (sets, reps, target) -> PlannedExercise(id, sets, reps, target) }
        }

    /** The gear from the quiz, the pull-up bar included. */
    fun gearOf(a: PlannerAnswers) = (a.equip + listOfNotNull("bar".takeIf { a.pull >= 0 })).distinct()

    /** The answers as if only [gear] were at hand; a bar added for the day starts at negatives. */
    fun withGear(a: PlannerAnswers, gear: List<String>) =
        a.copy(equip = gear - "bar", pull = if ("bar" in gear) maxOf(a.pull, 0) else -1)

    private fun fits(m: Move, a: PlannerAnswers) =
        (m.need == null || (m.need == "bar" && a.pull >= 0) || m.need in a.equip) && loadOk(m, a) && m.avoid.none { it in a.injury }

    /**
     * A session redone for the gear at hand: a move that needs something missing becomes the closest move that fits,
     * the most shared main muscles first, then the same group. Sets stay; reps follow the new move. With no stand-in it is dropped.
     */
    fun adapt(list: List<PlannedExercise>, a: PlannerAnswers, ladders: Map<String, LadderProgress>): List<PlannedExercise> {
        val out = mutableListOf<PlannedExercise>()
        list.forEach { e ->
            val m = Catalog.get(e.id)
            if (fits(m, a)) { out += e; return@forEach }
            val taken = (out.map { it.id } + list.map { it.id }).toSet()
            val sub = Catalog.moves.filter { it.group != "warmup" && allowed(it, a) }
                .map { Catalog.get(progressed(it.id, a, ladders)) }.distinctBy { it.id }
                .filter { it.id !in taken && fits(it, a) && (it.group == m.group || it.muscles.any { x -> x in m.muscles }) }
                .maxByOrNull { c -> (if (c.group == m.group) 4 else 0) + c.muscles.count { it in m.muscles } * 5 + c.secondary.count { it in m.muscles } + if (c.timed == m.timed) 1 else 0 }
            if (sub != null) out += dose(sub, a).let { (_, reps, target) -> PlannedExercise(sub.id, e.sets, reps, target) }
        }
        return out
    }

    fun startingLadders(a: PlannerAnswers): Map<String, LadderProgress> = mapOf(
        "push" to LadderProgress(rung = if (a.level == 2 && "chair" in a.equip && "shoulder" !in a.injury) 2 else a.level.coerceAtMost(1)),
        "squat" to LadderProgress(rung = if (a.level == 2 && "chair" in a.equip && "knee" !in a.injury) 1 else 0),
        "pull" to LadderProgress(rung = when { a.pull >= 1 -> 2; a.pull == 0 -> 1; else -> 0 }),
    )

    fun dose(m: Move, a: PlannerAnswers): Triple<Int, String, Int> {
        val sets = listOf(2, 3, 4)[a.intensity]
        val hyper = a.shape.any { it in hyperShapes }
        // intervals are for cardio only; on Kurus & tone the strength moves stay rep-based (12–15) so they keep the muscle
        return when {
            m.group == "cardio" -> Triple(3, "40s work, 20s rest", 40)

            m.id == "negative" -> Triple(sets, "5 rep turun slow", 5)
            m.id == "pullup" || m.id == "chinup" -> if (a.pull == 1) Triple(sets, "maximum rep", 5) else Triple(sets, "6–10", 10)
            m.id == "archerpull" -> Triple(sets, "3–6", 6)
            m.weighted -> Triple(sets, "5–8", 8)

            m.timed -> listOf(20, 30, 45)[a.level].let { Triple(sets, "${it}s", it) }
            hyper && a.shape.filter { it in hyperShapes } == listOf("bulk") -> Triple(sets, "6–10", 10)
            hyper -> if (a.level == 0) Triple(sets, "8–10", 10) else Triple(sets, "8–12", 12)
            else -> Triple(sets, "12–15", 15)
        }
    }

    // muscle groups each day type trains; a tag not listed works the whole body
    private val tagGroups = mapOf(
        "Pull" to listOf("lats", "rear_delts", "side_delts", "arms", "abs", "traps"),
        "Push" to listOf("chest", "front_delts", "side_delts", "arms", "abs"),
        "Legs+Push" to listOf("legs", "glutes", "chest", "front_delts", "side_delts"),
        "Legs+Core" to listOf("legs", "glutes", "abs", "lower_back", "cardio"),
        "Upper" to listOf("chest", "side_delts", "front_delts", "lats", "rear_delts", "arms", "abs", "traps"),

        "Lower" to listOf("legs", "glutes", "abs", "lower_back", "cardio"),
        "Power" to listOf("legs", "glutes", "chest", "lats", "abs"),
    )
    val tagLabels = mapOf(
        "Pull" to "Pull day", "Push" to "Push day",
        "Legs+Push" to "Legs + Push", "Legs+Core" to "Legs + Core",
        "Upper" to "Upper body", "Lower" to "Lower body",
        "HIIT" to "HIIT full body", "Full body" to "Full body", "Power" to "Power", "Circuit" to "Circuit full body",
    )

    /** A group's rating, 1 to 5; plans saved before ratings rate their old priorities 5 and the rest 3. */
    fun rating(a: PlannerAnswers, g: String): Int = (a.ratings[g] ?: if (g in a.priority) 5 else 3).coerceIn(1, 5)
    val ratingNames = listOf("Sikit", "Kurang", "Biasa", "Lebih", "Fokus")
    // the rating sets the weight; the shapes add theirs on top, so V-shape still leans to back and shoulders
    private val ratingScale = listOf(0.3, 0.6, 1.0, 1.5, 2.2)
    // a focus group gets at least this many direct sets a week, the range where muscle reliably grows
    const val FOCUS_SETS = 10

    // split options; every one is built and the one closest to the weekly targets wins (ties keep the first)
    private val pullA = listOf("Pull", "Legs+Push", "Pull", "Legs+Core", "Push")
    private val pullB = listOf("Pull", "Legs+Push", "Push", "Legs+Core", "Pull")
    private val upperLower = listOf("Upper", "Lower", "Upper", "Lower", "Upper")
    private val patterns = mapOf(
        2 to listOf(1, 0, 0, 1, 0, 0, 0), 3 to listOf(1, 0, 1, 0, 1, 0, 0),
        4 to listOf(1, 1, 0, 1, 1, 0, 0), 5 to listOf(1, 1, 1, 0, 1, 1, 0),
    )

    private class Week(val week: List<String>, val sessions: List<PlanSession>, val got: Map<String, Int>, val days: Map<String, Int>, val score: Double)

    /** Direct sets a week per muscle group, each move counted under its own group. */
    fun groupSets(plan: Plan): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        plan.allSessions.forEach { s ->
            val d = plan.daysOf(s).size
            s.exercises.forEach { e -> Catalog.get(e.id).group.let { g -> out[g] = (out[g] ?: 0) + e.sets * d } }
        }
        return out
    }

    /** Days a week each group is trained directly. */
    fun groupDays(plan: Plan): Map<String, Int> {
        val out = mutableMapOf<String, Int>()
        plan.allSessions.forEach { s ->
            val d = plan.daysOf(s).size
            s.exercises.map { Catalog.get(it.id).group }.distinct().forEach { g -> out[g] = (out[g] ?: 0) + d }
        }
        return out
    }

    /** Focus groups that could not get FOCUS_SETS a week within the chosen days and time. */
    fun short(a: PlannerAnswers, plan: Plan): List<String> {
        val got = groupSets(plan)
        return groupNames.keys.filter { it != "cardio" && rating(a, it) == 5 && (got[it] ?: 0) < FOCUS_SETS }
    }

    // One week on a split: a slot per session per round, so sessions fill evenly, each slot going to the group
    // furthest below its weekly target. A slot on a session that runs twice a week counts its sets twice.
    // Moves are compared by the ladder rung they become, and moves another session already has go last.
    private fun assemble(
        tags: List<String>, a: PlannerAnswers, n: Int, targets: Map<String, Double>, focus: Set<String>,
        pool: List<Move>, idOf: (Move) -> String,
    ): Week {
        val week = patterns.getValue(a.days).let { p -> var k = 0; p.map { if (it == 1) tags[k++ % tags.size] else "Rest" } }
        val order = week.filter { it != "Rest" }.distinct()
        val dayCount = order.associateWith { t -> week.count { it == t } }
        val picked = order.associateWith { mutableListOf<Move>() }
        val got = mutableMapOf<String, Int>()
        val used = mutableSetOf<String>()
        repeat(n) {
            order.forEach { tag ->
                val mine = picked.getValue(tag)
                val taken = { m: Move -> mine.any { idOf(it) == idOf(m) } }
                // focus groups still under their floor come first, then whoever is furthest below target
                val groups = (tagGroups[tag] ?: targets.keys.toList()).sortedByDescending { g ->
                    val gap = (targets[g] ?: 0.0) - (got[g] ?: 0)
                    if (g in focus && (got[g] ?: 0) < FOCUS_SETS) 1000.0 + (FOCUS_SETS - (got[g] ?: 0)) + gap / 1000 else gap
                }
                var ex: Move? = null
                for (g in groups) {
                    ex = pool.firstOrNull { it.group == g && !taken(it) && idOf(it) !in used } ?: pool.firstOrNull { it.group == g && !taken(it) }
                    if (ex != null) break
                }
                if (ex == null) ex = pool.firstOrNull { !taken(it) && idOf(it) !in used } ?: pool.firstOrNull { !taken(it) }
                if (ex != null) {
                    mine += ex
                    used += idOf(ex)
                    got[ex.group] = (got[ex.group] ?: 0) + dose(Catalog.get(idOf(ex)), a).first * dayCount.getValue(tag)
                }
            }
        }
        val days = mutableMapOf<String, Int>()
        order.forEach { tag -> picked.getValue(tag).map { it.group }.distinct().forEach { g -> days[g] = (days[g] ?: 0) + dayCount.getValue(tag) } }
        // distance from the targets, with a heavy cost for a focus group under its floor or trained only once a week
        val score = targets.entries.sumOf { (g, t) -> kotlin.math.abs(t - (got[g] ?: 0)) } +
            focus.sumOf { g -> 25.0 * maxOf(0, FOCUS_SETS - (got[g] ?: 0)) } +
            focus.count { g -> a.days >= 2 && (days[g] ?: 0) < 2 } * 15.0
        val sessions = order.map { tag ->
            // the day's own groups in order, so a Push day opens with chest
            val rank = tagGroups[tag] ?: targets.keys.toList()
            PlanSession(tag, tagLabels[tag] ?: tag, picked.getValue(tag).sortedBy { m -> rank.indexOf(m.group).let { if (it < 0) 99 else it } }.map { m ->
                val id = idOf(m)
                val (sets, reps, target) = dose(Catalog.get(id), a)
                PlannedExercise(id, sets, reps, target)
            })
        }
        return Week(week, sessions, got, days, score)
    }

    fun build(a: PlannerAnswers, ladders: Map<String, LadderProgress>): Plan {
        // group weights from the shapes, then scaled by the rating given to each group
        val w = mutableMapOf<String, Double>()
        a.shape.forEach { s -> profiles.getValue(s).forEach { (g, v) -> w[g] = (w[g] ?: 0.0) + v / a.shape.size } }
        if (a.body == "heavy") w["cardio"] = (w["cardio"] ?: 0.0) + 12
        if (a.body == "skinny" && w.containsKey("cardio")) w["cardio"] = maxOf(0.0, w.getValue("cardio") - 12)
        groupNames.keys.filter { it != "cardio" }.forEach { g -> w[g] = ((w[g] ?: 0.0) + (baseWeight[g] ?: 5.0)) * ratingScale[rating(a, g) - 1] }

        // weekly set targets: the week's sets shared by weight, every focus group lifted to its floor first
        val n = mapOf(10 to 3, 20 to 4, 30 to 5, 45 to 6).getValue(a.time)
        val total = (a.days * n * listOf(2, 3, 4)[a.intensity]).toDouble()
        val sumW = w.values.sum().takeIf { it > 0 } ?: 1.0
        val raw = w.filter { it.value > 0 }.mapValues { total * it.value / sumW }
        val focus = groupNames.keys.filter { it != "cardio" && rating(a, it) == 5 }.toSet()
        val fixed = focus.associateWith { maxOf(raw[it] ?: 0.0, FOCUS_SETS.toDouble()) }
        val rest = raw.filterKeys { it !in focus }
        val scale = if (rest.values.sum() > 0) maxOf(0.0, total - fixed.values.sum()) / rest.values.sum() else 0.0
        val targets = (fixed + rest.mapValues { it.value * scale }).entries.sortedByDescending { it.value }.associate { it.key to it.value }

        val has = { s: String -> s in a.shape }
        val hyper = a.shape.any { it in hyperShapes }
        val split = a.days >= 4 && hyper
        val options = when {
            split && has("vshape") -> listOf(pullA, pullB, upperLower)
            split -> listOf(upperLower, pullB, pullA)
            has("lean") -> listOf(listOf("HIIT", "Full body"))
            has("athletic") -> listOf(listOf("Power", "Circuit"))
            else -> listOf(listOf("Full body"))
        }
        // with a weight at home (dumbbell or a loaded backpack), each group alternates: weighted move, bodyweight move…
        val allowedMoves = Catalog.moves.filter { it.group != "warmup" && allowed(it, a) }
        val weights = setOf("dumbbell", "backpack")
        val pool = if (a.equip.none { it in weights }) allowedMoves else allowedMoves.groupBy { it.group }.values.flatMap { g ->
            val (db, bw) = g.partition { it.need in weights }
            (0 until maxOf(db.size, bw.size)).flatMap { i -> listOfNotNull(db.getOrNull(i), bw.getOrNull(i)) }
        }
        val idOf = { m: Move -> progressed(m.id, a, ladders) }
        val built = options.map { assemble(it, a, n, targets, focus, pool, idOf) }
        val best = built.reduce { b, c -> if (c.score < b.score - 0.01) c else b }
        val switched = best !== built.first()
        val tagsUsed = best.week.filter { it != "Rest" }.toSet()

        val why = mutableListOf<String>()
        why += when (a.shape.firstOrNull()) {
            "vshape" -> "V-shape: fokus belakang dan bahu supaya nampak lebar, perut dijaga supaya pinggang nampak kecil."
            "aesthetic" -> "Aesthetic: semua bahagian dilatih seimbang, perut diberi lebih supaya abs nampak."
            "bulk" -> "Besar & kuat: move bodyweight paling susah ikut level ko, rep lebih rendah."
            "athletic" -> "Atletik: kaki, kardio dan core untuk tenaga dan ketangkasan."
            "shredded" -> "Shredded: otot dilatih untuk membesar, perut dan lengan lebih sikit, kardio ringan. Yang buat urat nampak ialah pemakanan, jadi ikut target kalori."
            else -> "Kurus & tone: kardio dan circuit untuk bakar lemak, otot dikekalkan."
        }
        if (a.shape.size > 1) why += "Digabung dengan " + a.shape.drop(1).joinToString(" & ") { shapeLabels.getValue(it) } + ", jadi fokus dikongsi."
        val met = focus.filter { (best.got[it] ?: 0) >= FOCUS_SETS }
        if (met.isNotEmpty()) why += "Fokus: " + met.joinToString(", ") { "${groupNames.getValue(it)} ${best.got[it]} set seminggu (${best.days[it] ?: 0} hari)" } + "."
        if (switched) why += "Susunan hari diubah supaya otot fokus dapat cukup set dan kena 2× seminggu."
        val short = focus.filter { (best.got[it] ?: 0) < FOCUS_SETS }
        if (short.isNotEmpty()) why += "Tak muat: " + short.joinToString(", ") { "${groupNames.getValue(it)} cuma ${best.got[it] ?: 0} set" } +
            " seminggu (sasaran $FOCUS_SETS+). Tambah hari atau masa, atau kurangkan fokus lain."
        if (a.body == "heavy") why += "Kardio ditambah untuk buang lemak perut."
        if (a.body == "skinny") why += "Kardio dikurangkan. Makan lebih sikit dari biasa supaya otot naik."
        val gear = a.equip - "bar"
        if (a.pull < 0 && gear.isEmpty()) {
            why += "Tanpa alat langsung: plan guna berat badan, lantai dan bingkai pintu. Belakang train guna doorframe row; superman untuk pinggang."
            why += "Kalau nak beli satu benda je, ambil pull-up bar pintu (RM40–80): paling banyak buka move untuk belakang dan lengan."
        } else if (a.pull < 0 && (has("vshape") || rating(a, "lats") >= 4)) {
            why += "Takde pull-up bar: belakang train guna " + when {
                "chair" in a.equip -> "inverted row (guna meja)."
                "towel" in a.equip -> "towel door row."
                "backpack" in a.equip -> "backpack row."
                else -> "doorframe row."
            }
            why += "Pull-up bar pintu (RM40–80) paling berbaloi untuk belakang lebar."
        }
        if ("backpack" in a.equip) why += "Beg galas: isi buku atau botol air sebagai beban. Bila rep atas dah senang, tambah isi."
        if ("dumbbell" in a.equip) why += "Ada dumbbell: move dumbbell dimasukkan. Bila rep atas dah senang, naikkan berat."
        if ("Legs+Push" in tagsUsed && "Legs+Core" in tagsUsed) why += "Legs+Push dan Legs+Core, jadi kaki pun kena 2× seminggu."
        if (hyper) why += "Rest 90s antara set, stop bila tinggal 1–2 rep."
        if (a.time == 10 || (a.intensity == 0 && a.days <= 2)) why += "Jujur: sesi ni cukup untuk kekalkan otot. Untuk naik, sasarkan 10+ set seminggu setiap otot."
        if ("impact" in a.injury) why += "Mod low-impact: takde lompat, sendi lutut dan buku lali lebih selamat. Bila berat turun, buat semula plan untuk buka balik."
        if (a.injury.any { it != "impact" }) why += "Move yang risky untuk " + a.injury.filter { it != "impact" }.joinToString(" & ") {
            mapOf("knee" to "lutut", "back" to "belakang", "shoulder" to "bahu").getValue(it)
        } + " dibuang."

        // the focus bar shows what the week actually delivers, not what was asked for
        val sumGot = best.got.values.sum().toDouble().takeIf { it > 0 } ?: 1.0
        val focusBar = best.got.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(5)
            .map { FocusItem(groupNames.getValue(it.key), (it.value / sumGot * 100).roundToInt()) }
        val title = a.shape.joinToString(" + ") { shapeLabels.getValue(it) } + " · ${a.days} hari/minggu"
        return Plan(title, why.joinToString(" "), focusBar, best.week, best.sessions.first().label, best.sessions.first().exercises, best.sessions)
    }
}

data class Template(
    val id: String,
    val name: String,
    val minutes: Int,
    val level: String,
    val primary: List<String>,
    val moves: List<Pair<String, String>>, // move id to default dose
)

object Templates {
    val all = listOf(
        Template("full", "All-around", 30, "Sederhana", listOf("chest", "quads", "glutes", "abs"),
            listOf("squat" to "3×15", "push" to "3×10–12", "snow" to "3×10–12", "bridge" to "3×15", "plank" to "3×40s", "climber" to "3×40s")),
        Template("top", "Upper body", 30, "Sederhana", listOf("chest", "delts", "triceps", "lats"),
            listOf("push" to "3×10–12", "pike" to "3×8–12", "invrow" to "3×8–12", "dips" to "3×10–12", "diamond" to "3×8", "plank" to "3×40s")),
        Template("lower", "Lower body", 30, "Sederhana", listOf("quads", "glutes", "hamstrings", "calves"),
            listOf("squat" to "3×15", "split" to "3×10", "lunge" to "3×12", "bridge" to "3×15", "wallsit" to "3×40s", "calf" to "3×20")),
        Template("abs", "Six-pack core", 15, "Mudah", listOf("abs", "obliques"),
            listOf("deadbug" to "3×12", "plank" to "3×40s", "bicycle" to "3×20", "hollow" to "3×20s", "legraise" to "3×12")),
        Template("dumbbell", "Dumbbell full body", 30, "Sederhana", listOf("quads", "delts", "lats", "hamstrings"),
            listOf("goblet" to "3×10–12", "floorpress" to "3×8–12", "dbrow" to "3×8–12", "dbpress" to "3×8–12", "rdl" to "3×10–12", "curl" to "3×10–12")),
        Template("stamina", "Stamina booster", 15, "Sederhana", listOf("quads", "calves", "abs"),
            listOf("jack" to "3×40s", "highknee" to "3×40s", "burpee" to "3×40s", "climber" to "3×40s", "boxing" to "3×40s")),
    )

    // parse "3×10–12" / "3×40s" into sets, label and target; swap in the user's ladder rung; drop unsafe moves
    fun session(t: Template, a: PlannerAnswers?, ladders: Map<String, LadderProgress>): List<PlannedExercise> =
        t.moves.mapNotNull { (id, dose) ->
            val base = Catalog.get(id)
            if (a != null && (base.avoid.any { it in a.injury } || (base.need != null && base.need != "bar" && base.need !in a.equip))) return@mapNotNull null
            val finalId = if (a != null) PlanBuilder.progressed(id, a, ladders) else id
            val sets = dose.substringBefore("×").toInt()
            val reps = dose.substringAfter("×")
            val target = reps.filter { it.isDigit() || it == '–' }.substringAfterLast('–').toIntOrNull() ?: 10
            PlannedExercise(finalId, sets, reps, target)
        }
}
