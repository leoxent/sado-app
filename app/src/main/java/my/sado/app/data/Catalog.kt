package my.sado.app.data

data class Move(
    val id: String,
    val name: String,
    val group: String, // planner slot: PlanBuilder.groupNames; lats is for real vertical and horizontal pulls only
    val muscles: List<String>,
    val secondary: List<String> = emptyList(),
    val levels: Set<Int> = setOf(0, 1, 2),
    val need: String? = null, // bar, chair, towel, backpack, dumbbell, band
    val avoid: Set<String> = emptySet(),
    val timed: Boolean = false,
    val minPull: Int? = null,
    val maxPull: Int? = null,
    val wger: String? = null, // exact wger name used to pull description and credit
    val tips: List<String> = emptyList(),
    val weighted: Boolean = false, // carries extra kg (a loaded backpack, or a dumbbell); progresses by kg at the top of its range
)

object Catalog {
    val muscleNames = mapOf(
        "chest" to "Dada", "triceps" to "Trisep", "delts" to "Bahu", "lats" to "Belakang", "biceps" to "Bisep",
        "forearms" to "Lengan bawah", "traps" to "Trapezius", "quads" to "Peha depan", "glutes" to "Punggung",
        "hamstrings" to "Peha belakang", "calves" to "Betis", "abs" to "Perut", "obliques" to "Sisi perut", "lowerback" to "Pinggang",
    )

    private val pushTips = listOf(
        "Tangan selebar bahu, badan lurus dari kepala ke tumit.",
        "Turun sampai dada hampir sentuh lantai.",
        "Siku 45° dari badan, hembus nafas masa tolak naik.",
    )

    val moves: List<Move> = listOf(
        // order inside a group = planner preference
        Move("decline", "Decline push-up", "chest", listOf("chest", "delts"), listOf("triceps"), setOf(2), "chair", setOf("shoulder"), tips = listOf("Kaki atas kerusi, tangan di lantai.", "Badan lurus, jangan biar pinggang jatuh.", "Turun perlahan 2 saat.")),
        Move("push", "Push-up", "chest", listOf("chest", "triceps", "delts"), listOf("abs"), setOf(1, 2), wger = "Push-Up", tips = pushTips),
        Move("kneepush", "Knee push-up", "chest", listOf("chest", "triceps"), listOf("delts"), setOf(0), tips = listOf("Lutut di lantai, badan lurus dari kepala ke lutut.", "Turun sampai dada hampir sentuh lantai.", "Tolak naik sambil hembus nafas.")),
        Move("wide", "Wide push-up", "chest", listOf("chest", "delts"), listOf("triceps"), setOf(1, 2), avoid = setOf("shoulder"), tips = pushTips),
        Move("floorpress", "Dumbbell floor press", "chest", listOf("chest", "triceps"), need = "dumbbell", tips = listOf("Baring, siku sentuh lantai perlahan.", "Tolak dumbbell lurus ke atas dada.", "Jangan hentak siku.")),
        Move("archer", "Archer push-up", "chest", listOf("chest", "triceps"), listOf("delts"), setOf(2), avoid = setOf("shoulder"), tips = listOf("Tangan lebar, turun ke satu sisi.", "Tangan sebelah lagi lurus sebagai bantuan.", "Selang-seli kiri dan kanan.")),
        Move("onearm", "One-arm push-up", "chest", listOf("chest", "triceps"), listOf("abs", "delts"), setOf(2), avoid = setOf("shoulder"), tips = listOf("Kaki lebar untuk imbangan.", "Badan jangan berpusing.", "Start dengan tangan atas bench.")),
        Move("wpush", "Weighted push-up (backpack)", "chest", listOf("chest", "triceps"), listOf("delts", "abs"), setOf(2), avoid = setOf("shoulder"), weighted = true, tips = listOf("Galas beg berisi, tali ketat supaya beg tak gelongsor.", "Badan lurus, turun sampai dada hampir sentuh lantai.", "Bila rep atas dah senang, tambah isi beg.")),
        Move("tempopush", "Tempo push-up (3 s turun)", "chest", listOf("chest", "triceps"), listOf("delts", "abs"), setOf(1, 2), avoid = setOf("shoulder"), tips = listOf("Turun perlahan kira 3 saat.", "Tahan sekejap di bawah, jangan rehat atas lantai.", "Tolak naik biasa.")),
        Move("lateral", "Lateral raise", "side_delts", listOf("delts"), listOf("traps"), need = "dumbbell", tips = listOf("Siku sedikit bengkok.", "Angkat sampai paras bahu je.", "Turun perlahan 3 saat.")),
        Move("dbpress", "Dumbbell shoulder press", "front_delts", listOf("delts", "triceps"), listOf("traps"), need = "dumbbell", avoid = setOf("shoulder"), tips = listOf("Duduk tegak, dumbbell paras telinga.", "Tolak lurus ke atas, jangan kunci siku.", "Turun perlahan 2 saat.")),
        Move("bppress", "Backpack overhead press", "front_delts", listOf("delts", "triceps"), listOf("traps"), need = "backpack", avoid = setOf("shoulder"), tips = listOf("Peluk beg depan dada, tali ke atas.", "Tolak beg lurus ke atas kepala.", "Perut kemut, jangan lentik pinggang.")),
        Move("arnold", "Arnold press", "front_delts", listOf("delts"), listOf("triceps"), setOf(1, 2), need = "dumbbell", avoid = setOf("shoulder"), tips = listOf("Start tapak tangan menghadap muka.", "Pusing tangan ke depan sambil tolak naik.", "Turun ikut jalan yang sama.")),
        Move("reardelt", "Rear delt fly", "rear_delts", listOf("delts", "traps"), need = "dumbbell", tips = listOf("Bongkok ke depan, belakang rata.", "Angkat dumbbell ke sisi, siku sedikit bengkok.", "Picit belikat di atas.")),
        Move("bandlat", "Band lateral raise", "side_delts", listOf("delts"), need = "band", tips = listOf("Pijak band, pegang hujung.", "Angkat ke sisi paras bahu.", "Kawal masa turun.")),
        Move("bottlelat", "Water bottle lateral raise", "side_delts", listOf("delts"), listOf("traps"), tips = listOf("Pegang botol air 1.5L penuh di setiap tangan.", "Siku sedikit bengkok, angkat ke sisi sampai paras bahu.", "Turun perlahan 3 saat. Ringan, jadi buat sampai hampir tak larat.")),
        Move("ytw", "Y-T-W raise", "rear_delts", listOf("delts", "traps"), listOf("lats"), tips = listOf("Baring meniarap, dahi atas tuala.", "Angkat tangan bentuk Y, T, kemudian W.", "Picit belikat setiap kali angkat.")),
        Move("pike", "Pike push-up", "front_delts", listOf("delts", "triceps"), listOf("traps"), setOf(1, 2), avoid = setOf("shoulder"), tips = listOf("Punggung tinggi, badan bentuk V terbalik.", "Turunkan kepala di antara tangan.", "Tolak naik guna bahu.")),
        Move("pullapart", "Band pull-apart", "rear_delts",
 listOf("delts", "traps"), need = "band", tips = listOf("Tangan lurus depan dada.", "Tarik band sampai sentuh dada.", "Picit belikat.")),
        Move("pullup", "Pull-up", "lats", listOf("lats", "biceps"), listOf("forearms", "abs"), need = "bar", avoid = setOf("shoulder"), minPull = 1, wger = "Pull-ups", tips = listOf("Genggam lebih lebar dari bahu.", "Tarik dada ke bar, jangan hayun badan.", "Turun sampai tangan lurus penuh.")),
        Move("archerpull", "Archer pull-up", "lats", listOf("lats", "biceps"), listOf("forearms", "abs"), setOf(2), "bar", setOf("shoulder"), minPull = 1, tips = listOf("Genggam lebar, tarik badan ke satu tangan.", "Tangan sebelah lagi lurus sebagai bantuan.", "Selang-seli kiri dan kanan, turun perlahan.")),
        Move("wpullup", "Weighted pull-up (backpack)", "lats", listOf("lats", "biceps"), listOf("forearms", "abs"), setOf(2), "bar", setOf("shoulder"), minPull = 1, weighted = true, tips = listOf("Galas beg berisi depan atau belakang, tali ketat.", "Tarik dada ke bar tanpa hayun.", "Turun sampai tangan lurus penuh.")),
        Move("negative", "Negative pull-up", "lats", listOf("lats", "biceps"), listOf("forearms"), need = "bar", avoid = setOf("shoulder"), maxPull = 0, tips = listOf("Lompat ke posisi atas, dagu atas bar.", "Turun perlahan 4–5 saat.", "Ulang. Ini jalan pintas ke pull-up pertama.")),
        Move("invrow", "Inverted row", "lats", listOf("lats", "biceps"), listOf("traps", "delts"), need = "chair", tips = listOf("Pastikan meja kukuh dan tak terbalik.", "Badan lurus, tarik dada ke tepi meja.", "Bengkok lutut untuk lebih senang.")),
        Move("dbrow", "Dumbbell row", "lats", listOf("lats", "biceps"), listOf("traps"), need = "dumbbell", avoid = setOf("back"), tips = listOf("Satu tangan dan lutut atas kerusi.", "Tarik dumbbell ke pinggul.", "Belakang rata, jangan pusing badan.")),
        Move("dbrow2", "Bent-over row", "lats", listOf("lats", "biceps"), listOf("traps", "lowerback"), setOf(1, 2), need = "dumbbell", avoid = setOf("back"), tips = listOf("Bongkok 45°, belakang rata.", "Tarik dua dumbbell ke pinggang.", "Jangan hayun badan.")),
        Move("bprow", "Backpack row", "lats", listOf("lats", "biceps"), listOf("traps"), need = "backpack", avoid = setOf("back"), tips = listOf("Beg isi buku atau botol air, pegang tali.", "Bongkok 45°, belakang lurus.", "Tarik beg ke perut, kepit belikat.")),
        Move("shrug", "Dumbbell shrug", "traps", listOf("traps"), need = "dumbbell", tips = listOf("Tangan lurus di sisi.", "Angkat bahu ke telinga, tahan 1 saat.", "Jangan pusing bahu.")),
        Move("bpshrug", "Backpack shrug", "traps", listOf("traps"), listOf("forearms"), need = "backpack", tips = listOf("Beg berisi, pegang tali dengan dua tangan depan badan.", "Angkat bahu ke telinga, tahan 1 saat.", "Turun perlahan, jangan pusing bahu.")),
        Move("bandpull", "Band lat pulldown", "lats", listOf("lats"), listOf("biceps"), need = "band", tips = listOf("Ikat band tinggi, contohnya pintu.", "Tarik siku ke rusuk.", "Dada keluar.")),
        Move("towelrow", "Towel door row", "lats", listOf("lats", "biceps"), listOf("traps", "forearms"), need = "towel", tips = listOf("Sangkut tuala pada tombol kedua-dua belah pintu, kunci pintu.", "Pegang hujung tuala, kaki dekat pintu, condong ke belakang.", "Tarik dada ke pintu, kepit belikat, turun perlahan.")),
        Move("doorrow", "Doorframe row", "lats", listOf("lats", "biceps"), listOf("traps", "forearms"), tips = listOf("Pegang bingkai pintu dengan dua tangan, kaki rapat ke bingkai.", "Condong ke belakang sampai tangan lurus.", "Tarik dada ke bingkai, kepit belikat. Lagi condong, lagi susah.")),
        Move("superman", "Superman hold", "lower_back", listOf("lowerback", "glutes"), listOf("lats", "traps"), avoid = setOf("back"), timed = true, tips = listOf("Meniarap, tangan lurus ke depan.", "Angkat tangan, dada dan kaki serentak.", "Tahan, pandang lantai, jangan dongak.")),
        Move("snow", "Reverse snow angel", "rear_delts", listOf("delts", "traps"), listOf("lats"), tips = listOf("Baring meniarap, tangan di sisi.", "Lukis separuh bulatan sampai atas kepala.", "Tangan tak sentuh lantai sepanjang move.")),
        Move("chinup", "Chin-up", "arms", listOf("biceps", "lats"), listOf("forearms"), need = "bar", avoid = setOf("shoulder"), minPull = 1, tips = listOf("Genggam tapak tangan menghadap kau.", "Tarik dagu atas bar.", "Turun penuh.")),
        Move("diamond", "Diamond push-up", "arms", listOf("triceps", "chest"), levels = setOf(2), avoid = setOf("shoulder"), wger = "Diamond push ups", tips = listOf("Tangan rapat bentuk berlian bawah dada.", "Siku rapat ke badan.", "Turun sampai dada sentuh tangan.")),
        Move("dips", "Chair dips", "arms", listOf("triceps"), listOf("chest", "delts"), need = "chair", avoid = setOf("shoulder"), wger = "Dips", tips = listOf("Tangan di hujung kerusi, jari ke depan.", "Turun sampai siku 90°, bahu jauh dari telinga.", "Tolak naik guna trisep, bukan kaki.")),
        Move("closepush", "Close-grip push-up", "arms", listOf("triceps", "chest"), listOf("delts"), setOf(1, 2), avoid = setOf("shoulder"), tips = listOf("Tangan selebar bahu atau rapat sikit.", "Siku rapat ke badan masa turun.", "Tolak guna trisep, badan lurus.")),
        Move("floordip", "Floor tricep dip", "arms", listOf("triceps"), listOf("delts"), avoid = setOf("shoulder"), tips = listOf("Duduk, tangan di lantai belakang punggung, jari ke depan.", "Angkat punggung, bengkok siku ke belakang.", "Luruskan siku untuk naik.")),
        Move("towelcurl", "Towel curl", "arms", listOf("biceps", "forearms"), need = "towel", tips = listOf("Pijak tengah tuala dengan satu kaki, pegang hujungnya.", "Curl ke atas sambil kaki tekan ke bawah sebagai beban.", "Turun perlahan, kawal sepanjang masa.")),
        Move("bpcurl", "Backpack curl", "arms", listOf("biceps", "forearms"), need = "backpack", tips = listOf("Pegang tali beg, tangan ke bawah.", "Curl ke bahu, siku rapat ke badan.", "Turun perlahan.")),
        Move("curl", "Bicep curl", "arms", listOf("biceps", "forearms"), need = "dumbbell", tips = listOf("Siku rapat di sisi badan.", "Angkat tanpa hayun badan.", "Turun perlahan.")),
        Move("hammer", "Hammer curl", "arms", listOf("biceps", "forearms"), need = "dumbbell", tips = listOf("Genggam macam pegang tukul.", "Siku tak bergerak.", "Turun perlahan.")),
        Move("ohext", "Overhead tricep extension", "arms", listOf("triceps"), need = "dumbbell", avoid = setOf("shoulder"), tips = listOf("Pegang satu dumbbell dua tangan atas kepala.", "Turun ke belakang kepala, siku tak kembang.", "Luruskan tangan guna trisep.")),
        Move("bandcurl", "Band curl", "arms", listOf("biceps"), need = "band", tips = listOf("Pijak band, tapak tangan ke depan.", "Siku rapat.", "Kawal masa turun.")),
        Move("kneeraise", "Hanging knee raise", "abs", listOf("abs"), listOf("forearms"), setOf(1, 2), "bar", tips = listOf("Gantung dengan bahu aktif.", "Angkat lutut ke dada tanpa hayun.", "Turun perlahan.")),
        Move("hollow", "Hollow hold", "abs", listOf("abs"), levels = setOf(1, 2), avoid = setOf("back"), timed = true, tips = listOf("Pinggang rapat ke lantai.", "Angkat bahu dan kaki sedikit.", "Bengkok lutut kalau terlalu susah.")),
        Move("plank", "Plank", "abs", listOf("abs", "obliques"), listOf("delts", "glutes"), timed = true, wger = "Plank", tips = listOf("Siku tepat bawah bahu.", "Kemut perut dan punggung, pinggang jangan jatuh.", "Bernafas perlahan.")),
        Move("deadbug", "Dead bug", "abs", listOf("abs"), tips = listOf("Baring, tangan dan lutut ke atas.", "Panjangkan tangan dan kaki bertentangan.", "Pinggang kekal rapat ke lantai.")),
        Move("bicycle", "Bicycle crunch", "abs", listOf("abs", "obliques"), avoid = setOf("back"), tips = listOf("Siku ke lutut bertentangan.", "Jangan tarik leher.", "Perlahan dan terkawal.")),
        Move("legraise", "Leg raise", "abs", listOf("abs"), levels = setOf(1, 2), avoid = setOf("back"), tips = listOf("Tangan bawah punggung.", "Angkat kaki lurus ke 90°.", "Turun perlahan, jangan sentuh lantai.")),
        Move("russian", "Russian twist (dumbbell)", "abs", listOf("obliques", "abs"), levels = setOf(1, 2), need = "dumbbell", avoid = setOf("back"), tips = listOf("Duduk, badan condong ke belakang sikit.", "Pusing dumbbell kiri kanan.", "Kaki di lantai kalau susah.")),
        Move("split", "Bulgarian split squat", "legs", listOf("quads", "glutes"), listOf("hamstrings"), setOf(1, 2), "chair", setOf("knee"), tips = listOf("Kaki belakang atas kerusi.", "Turun lurus ke bawah, lutut depan ikut jari kaki.", "Buat semua rep satu kaki dulu.")),
        Move("squat", "Squat", "legs", listOf("quads", "glutes"), listOf("hamstrings", "calves", "lowerback"), avoid = setOf("knee"), tips = listOf("Kaki selebar bahu, hujung kaki sedikit ke luar.", "Duduk ke belakang macam nak duduk kerusi.", "Lutut ikut arah jari kaki, tumit di lantai.")),
        Move("lunge", "Reverse lunge", "legs", listOf("quads", "glutes"), listOf("hamstrings"), setOf(1, 2), avoid = setOf("knee"), tips = listOf("Langkah ke belakang.", "Lutut belakang hampir sentuh lantai.", "Tolak balik guna kaki depan.")),
        Move("goblet", "Goblet squat", "legs", listOf("quads", "glutes"), listOf("abs"), need = "dumbbell", avoid = setOf("knee"), tips = listOf("Pegang satu dumbbell depan dada.", "Duduk dalam, siku antara lutut.", "Dada tegak, tumit di lantai.")),
        Move("bpsquat", "Backpack squat", "legs", listOf("quads", "glutes"), listOf("hamstrings", "abs"), need = "backpack", avoid = setOf("knee"), tips = listOf("Peluk beg depan dada.", "Turun sampai peha selari lantai.", "Lutut ikut arah jari kaki.")),
        Move("dblunge", "Dumbbell lunge", "legs", listOf("quads", "glutes"), listOf("hamstrings"), setOf(1, 2), need = "dumbbell", avoid = setOf("knee"), tips = listOf("Dumbbell di sisi badan.", "Langkah ke depan, lutut belakang hampir sentuh lantai.", "Tolak balik guna kaki depan.")),
        Move("stepup", "Dumbbell step-up", "legs", listOf("quads", "glutes"), listOf("hamstrings"), setOf(1, 2), need = "dumbbell", avoid = setOf("knee"), tips = listOf("Guna kerusi atau tangga yang kukuh.", "Naik guna kaki atas, jangan tolak kaki bawah.", "Turun perlahan.")),
        Move("shrimp", "Shrimp squat", "legs", listOf("quads", "glutes"), levels = setOf(2), avoid = setOf("knee"), tips = listOf("Pegang kaki belakang.", "Turun sampai lutut belakang sentuh lantai.", "Pegang dinding untuk balance.")),
        Move("pistol", "Pistol squat", "legs", listOf("quads", "glutes"), listOf("abs"), setOf(2), avoid = setOf("knee"), tips = listOf("Satu kaki lurus ke depan.", "Turun perlahan atas satu kaki.", "Start dengan duduk atas kerusi.")),
        Move("wpistol", "Weighted pistol squat (backpack)", "legs", listOf("quads", "glutes"), listOf("abs"), setOf(2), avoid = setOf("knee"), weighted = true, tips = listOf("Peluk beg berisi depan dada, ia bantu imbangan.", "Turun perlahan atas satu kaki.", "Bila rep atas dah senang, tambah isi beg.")),
        Move("wallsit", "Wall sit", "legs", listOf("quads"), timed = true, tips = listOf("Belakang rapat ke dinding.", "Lutut 90°.", "Bernafas biasa.")),
        Move("calf", "Calf raise", "legs", listOf("calves"), tips = listOf("Berdiri atas tangga, tumit tergantung.", "Naik penuh, tahan 1 saat.", "Turun perlahan.")),
        Move("slcalf", "Single-leg calf raise", "legs", listOf("calves"), levels = setOf(1, 2), tips = listOf("Berdiri satu kaki atas tangga, pegang dinding untuk imbangan.", "Naik penuh atas hujung kaki, tahan 1 saat.", "Turun perlahan sampai tumit bawah paras tangga.")),
        Move("slbridge", "Single-leg glute bridge", "glutes", listOf("glutes", "hamstrings"), levels = setOf(1, 2), tips = listOf("Satu kaki di lantai, satu lagi lurus.", "Tolak pinggul ke atas.", "Picit punggung di atas.")),
        Move("bridge", "Glute bridge", "glutes", listOf("glutes", "hamstrings"), tips = listOf("Baring, lutut bengkok.", "Tolak pinggul sampai badan lurus.", "Tahan 1 saat di atas.")),
        Move("rdl", "Dumbbell Romanian deadlift", "glutes", listOf("hamstrings", "glutes"), listOf("lowerback"), need = "dumbbell", avoid = setOf("back"), tips = listOf("Lutut sedikit bengkok, belakang rata.", "Tolak punggung ke belakang, dumbbell ikut peha.", "Berdiri sambil picit punggung.")),
        Move("bprdl", "Backpack Romanian deadlift", "glutes", listOf("hamstrings", "glutes"), listOf("lowerback"), need = "backpack", avoid = setOf("back"), tips = listOf("Pegang beg depan peha.", "Tolak punggung ke belakang, lutut bengkok sikit.", "Turun sampai rasa tarikan peha belakang, belakang kekal lurus.")),
        Move("donkey", "Donkey kick", "glutes", listOf("glutes"), tips = listOf("Posisi merangkak.", "Tendang tumit ke siling.", "Jangan lengkung pinggang.")),
        Move("slidecurl", "Towel hamstring curl", "glutes", listOf("hamstrings"), listOf("glutes"), need = "towel", tips = listOf("Baring, tumit atas tuala di lantai licin.", "Angkat pinggul, tarik tumit ke punggung.", "Tolak perlahan keluar, pinggul kekal tinggi.")),
        Move("nordic", "Nordic curl (negative)", "glutes", listOf("hamstrings"), listOf("glutes", "calves"), setOf(1, 2), avoid = setOf("knee"), tips = listOf("Kunci tumit bawah sofa atau minta orang pegang, lutut atas tuala.", "Badan lurus, jatuh ke depan perlahan 3–5 saat.", "Sambut dengan tangan, tolak balik ke atas.")),
        Move("burpee", "Burpee", "cardio", listOf("quads", "chest", "abs"), levels = setOf(1, 2), avoid = setOf("knee", "back", "impact"), timed = true, tips = listOf("Turun, lompat kaki ke belakang.", "Push-up (opsyenal), lompat kaki ke depan.", "Lompat ke atas.")),
        Move("climber", "Mountain climber", "cardio", listOf("abs", "delts", "quads"), timed = true, tips = listOf("Posisi plank.", "Bawa lutut ke dada bergilir-gilir.", "Pinggul rendah.")),
        Move("jack", "Jumping jack", "cardio", listOf("calves", "delts"), avoid = setOf("knee", "impact"), timed = true, tips = listOf("Lompat buka kaki dan tangan.", "Mendarat lembut.", "Ikut rentak.")),
        Move("highknee", "High knees", "cardio", listOf("quads", "abs"), avoid = setOf("knee", "impact"), timed = true, tips = listOf("Lari setempat.", "Lutut paras pinggang.", "Hayun tangan.")),
        Move("boxing", "Shadow boxing", "cardio", listOf("delts", "abs"), timed = true, tips = listOf("Kaki selebar bahu.", "Tumbuk lurus dan cangkuk.", "Kekal bergerak.")),
    )

    val warmup = listOf(
        // one move only, 30 seconds after a 15-second get-ready: a short warm-up is one people actually do
        Triple("Jumping jack", 30, "Slow dulu, then laju sikit. Naikkan heart rate."),
    )

    // low-impact: same 30 seconds, no jumping, kinder on heavy knees and ankles
    val warmupLow = listOf(
        Triple("March in place", 30, "Angkat lutut, hayun tangan. Tak payah lompat, sendi selamat."),
    )

    // progression ladders: index = rung
    val ladders = mapOf(
        "push" to listOf("kneepush", "push", "decline", "archer", "onearm", "wpush"),
        "squat" to listOf("squat", "split", "shrimp", "pistol", "wpistol"),
        "pull" to listOf("invrow", "negative", "pullup", "archerpull", "wpullup"),
    )

    // what each piece of home equipment is called
    val equipNames = mapOf(
        "bar" to "pull-up bar", "chair" to "kerusi/meja", "towel" to "tuala + pintu", "backpack" to "beg berisi",
        "dumbbell" to "dumbbell", "band" to "band",
    )

    fun ladderOf(id: String): String? = ladders.entries.firstOrNull { id in it.value }?.key

    fun get(id: String): Move = moves.first { it.id == id }
}
