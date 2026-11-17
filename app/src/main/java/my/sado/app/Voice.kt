package my.sado.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import my.sado.app.data.Secrets
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import my.sado.app.data.Progress
import my.sado.app.data.Store
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale

// Coach voice through the phone's own TTS engine: free and offline, a bit formal. Malay if the
// engine has it, else Indonesian, which is the closest fallback.
object Coach {
    private var tts: TextToSpeech? = null
    private var ready = false
    private lateinit var app: Context

    fun init(context: Context) {
        app = context.applicationContext
        VoiceClips.dropLegacy(app)
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val t = tts ?: return@TextToSpeech
            ready = listOf(Locale("ms", "MY"), Locale("ms"), Locale("id", "ID")).any {
                t.setLanguage(it) >= TextToSpeech.LANG_AVAILABLE
            }
            t.setSpeechRate(1.05f)
        }
    }

    private val ones = listOf("kosong", "satu", "dua", "tiga", "empat", "lima", "enam", "tujuh", "lapan", "sembilan")

    /** 12 → "dua belas", 120 → "seratus dua puluh": spoken Malay so the voice never reads digits. */
    fun malay(n: Int): String = when {
        n < 10 -> ones[n]
        n == 10 -> "sepuluh"
        n == 11 -> "sebelas"
        n < 20 -> "${ones[n % 10]} belas"
        n < 100 -> "${ones[n / 10]} puluh" + if (n % 10 > 0) " ${ones[n % 10]}" else ""
        n < 1000 -> (if (n / 100 == 1) "seratus" else "${ones[n / 100]} ratus") + if (n % 100 > 0) " ${malay(n % 100)}" else ""
        n < 1_000_000 -> (if (n / 1000 == 1) "seribu" else "${malay(n / 1000)} ribu") + if (n % 1000 > 0) " ${malay(n % 1000)}" else ""
        else -> n.toString()
    }

    fun words(text: String): String = text
        .replace(Regex("(\\d+)°")) { "${malay(it.groupValues[1].toInt())} darjah" }
        .replace(Regex("\\d+")) { malay(it.value.toInt()) }

    // AI voice on: the chosen voice. Off: the voice shipped in the APK, whose missing lines can be made too
    private fun activeVoice(st: my.sado.app.data.Settings): String? = when {
        !::app.isInitialized -> null
        st.aiVoice -> st.voiceId
        else -> VoiceClips.bundledVoice(app)
    }

    /** Says one of the variants at random so repeated lines don't sound robotic. */
    fun say(options: List<String>) = say(options.random())

    fun say(line: String) {
        val st = Store.state.value.settings
        if (!st.voice) return
        val text = words(line)
        // AI voice: play the cached clip; first time a line is heard it falls back to TTS and the clip is fetched for next time
        // the clips shipped in the APK need no key, so they play with AI voice off too
        val voice = activeVoice(st)
        if (voice != null) {
            if (VoiceClips.play(app, voice, text)) { tts?.stop(); return }
            // no clip yet: the phone voice speaks and the report chip offers to make the AI one
            VoiceClips.report(app, voice, listOf(text))
        }
        if (!ready) return
        VoiceClips.stop()
        // the phone engine would read the [tags] out loud
        val plain = text.replace(Regex("\\[[^\\]]*]\\s*"), "")
        tts?.speak(plain, TextToSpeech.QUEUE_FLUSH, null, plain.hashCode().toString())
    }

    /**
     * Says a line built from parts ("Next… Push-up!" + "Tiga set." + "Sepuluh rep."), each its own clip.
     * Parts never change when reps climb, so a clip made once stays in use; any missing part means the
     * phone voice says the whole line and the report chip offers to make the missing parts.
     */
    fun sayParts(parts: List<String>) {
        val st = Store.state.value.settings
        if (!st.voice) return
        val texts = parts.map { words(it) }
        val voice = activeVoice(st)
        if (voice != null) {
            if (VoiceClips.playChain(app, voice, texts)) { tts?.stop(); return }
            VoiceClips.report(app, voice, texts.filterNot { VoiceClips.has(app, voice, it) })
        }
        if (!ready) return
        VoiceClips.stop()
        val plain = texts.joinToString(" ") { it.replace(Regex("\\[[^\\]]*]\\s*"), "") }
        tts?.speak(plain, TextToSpeech.QUEUE_FLUSH, null, plain.hashCode().toString())
    }

    /** A move's intro: its name (one of two openings), then sets, then reps or seconds. */
    fun intro(name: String, sets: Int, reps: String) = listOf(Lines.introName(name).random(), Lines.sets(sets), Lines.reps(reps))

    /** Rep labels written for the screen ("8–12", "30s") said the way a person would say them. */
    fun spoken(reps: String): String = reps
        .replace(Regex("(\\d+)–(\\d+)$"), "$1 sampai $2 rep")
        .replace(Regex("(\\d+)s\\b"), "$1 saat")
        .replace(Regex("^(\\d+)$"), "$1 rep")


    // every line and variant, generated up front so the first workout already sounds right
    fun phraseGroups(): List<PhraseGroup> {
        val s = Store.state.value
        val moves = (s.plan?.allSessions.orEmpty().flatMap { it.exercises } + my.sado.app.data.Templates.all.flatMap { my.sado.app.data.Templates.session(it, s.answers, s.ladders) })
            .map { my.sado.app.data.Progress.boosted(it, s.boosts, s.extraSets) }
        // every move in the catalogue, so a new plan or template already has its names
        val intros = my.sado.app.data.Catalog.moves.flatMap { Lines.introName(it.name) }
        // the plan's own labels (ranges, max rep) plus every count a climbing target can reach
        val numbers = (1..my.sado.app.data.Progress.MAX_SETS).map { Lines.sets(it) } +
            ((1..50).map { "$it" } + (5..180 step 5).map { "${it}s" } + moves.map { it.reps }).distinct().map { Lines.reps(it) }
        val cues = (my.sado.app.ai.Motions.reps.values.map { it.shallowCue } + my.sado.app.ai.Motions.formCues).flatMap { Lines.cue(it) }
        val workout = Lines.warmup + Lines.warmupLow + (2..6).flatMap { Lines.set(it, last = false) } + Lines.set(2, last = true) +
            listOf(60, 90, 120).flatMap { Lines.rest(it) } + Lines.restEnding + Lines.effort + Lines.done + Lines.zero + Lines.quit + Lines.cancel
        val photo = listOf(10, 3).flatMap { Lines.countdown(it) } + Lines.photoSaved + Lines.photoPartial + Lines.photoFail
        return listOf(
            PhraseGroup("Workout", workout),
            PhraseGroup("Intro setiap move", intros),
            PhraseGroup("Nombor set & rep", numbers),
            PhraseGroup("Level up", (Progress.stages.drop(1) + Progress.heavyStages.drop(1).take(5)).flatMap { Lines.levelUp(it) }),
            // a drop always lands on tier I or II
            PhraseGroup("Rehat lama", Lines.tired + (Progress.stages + Progress.heavyStages.take(6)).filter { it.tier < 2 }.flatMap { Lines.dropped(it) } + Lines.comeback + Lines.backAtBest),
            PhraseGroup("Gambar progress", photo),
            PhraseGroup("Ujian", Lines.provePass + Lines.proveFail + Lines.testRecord + Lines.testDone),
            // only heard with camera reps on, so not picked by default
            PhraseGroup("Camera AI tegur form", cues, picked = false),
        ).map { g -> g.copy(lines = g.lines.map { words(it) }.distinct()) }
    }

    fun commonPhrases(): List<String> = phraseGroups().flatMap { it.lines }.distinct()

    /** Lines that are pieces of a chained intro; these are made together in one take so their tone matches. */
    fun partLines(): Set<String> = phraseGroups().filter { it.title == "Intro setiap move" || it.title == "Nombor set & rep" }.flatMap { it.lines }.toSet()
}

data class PhraseGroup(val title: String, val lines: List<String>, val picked: Boolean = true)

/**
 * Everything the coach says. Text in [brackets] is an ElevenLabs v3 audio tag: it sets the tone and
 * isn't spoken. Numbers are written as digits here and turned into Malay words before speaking.
 */
object Lines {
    val warmup = listOf("[cheerful] Okay! Warm-up dulu… jumping jack 1 minit je, relax.")
    val warmupLow = listOf("[cheerful] Okay! Warm-up dulu… march in place 1 minit, tak payah lompat.")

    // a chained intro: every part is a whole short sentence with the same tag, so the joins sound like two sentences
    fun introName(name: String) = listOf("[energetic] Next… $name!", "[energetic] Okay, $name pula!")
    fun sets(n: Int) = "[energetic] $n set."
    fun reps(label: String) = "[energetic] ${Coach.spoken(label)}."

    /** Intros from before 0.25, with the numbers inside; they stopped matching once reps climbed. */
    private val oldIntro = Regex("^\\[(energetic|calm)] Next(…|,) .*\\bset\\b.*")
    fun isOldIntro(line: String) = oldIntro.matches(line)

    fun set(n: Int, last: Boolean) = listOf(
        when {
            last -> "[intense] Last set! Bagi semua!"
            n == 2 -> "[pumped] Set 2! Jom!"
            n == 3 -> "[encouraging] Set 3… ko boleh ni."
            n == 4 -> "[hyped] Set 4! Push lagi!"
            n == 5 -> "[determined] Set 5. Fokus, form kena cantik."
            else -> "[hyped] Set $n! Habiskan!"
        },
    )

    fun rest(seconds: Int) = listOf("[relieved] Cun! Rest $seconds saat… ambik nafas.", "[chill] Nice. Rest $seconds saat, minum air sikit.")
    val restEnding = listOf("[alert] 10 saat lagi… ready!")
    val effort = listOf("[curious] Nice! Tadi rasa macam mana… easy, ngam, ke susah?")
    val done = listOf("[proud] Settle! Workout hari ni… DONE!", "[excited] Fuhh! Siap dah! Padu ah ko hari ni.")
    val zero = listOf("[calm] Okay… hari ni semua skip. Takpe, esok kita try lagi.")
    val quit = listOf("[warm] Takpe… satu set pun dikira. Esok sambung.")
    val cancel = listOf("[neutral] Okay, session cancel.")

    // time off: said once on Home when he turns tired or drops a tier, and after a comeback workout
    val tired = listOf("[sleepy] Hmm… dah seminggu lebih ko tak gerak. Badan aku dah rasa lembik ni. Jom, satu workout pun jadi.")
    fun dropped(stage: my.sado.app.data.Stage) = listOf("[sad] Alamak… aku dah turun ke ${stage.spoken}. Takpe, workout sekarang, XP double sampai kita naik balik.")
    val comeback = listOf("[excited] Comeback! XP double hari ni. Sikit lagi kita balik ke level lama.")
    val backAtBest = listOf("[proud] Yes! Dah balik ke tahap terbaik ko. Jangan lepas lagi!")

    // the placement at setup and the 4-week test
    val provePass = listOf("[excited] Terbukti! Ko memang dah biasa workout. Kita mula terus dari level empat.")
    val proveFail = listOf("[warm] Takpe, ni rekod pertama ko. Empat minggu lagi kita uji balik, tengok berapa banyak ko naik.")
    val testRecord = listOf("[excited] Rekod baru! Ni la bukti ko makin kuat.", "[proud] Fuhh! Lagi tinggi dari ujian lepas. Padu!")
    val testDone = listOf("[warm] Ujian siap. Tak naik kali ni pun takpe, empat minggu lagi kita cuba lagi.")

    fun levelUp(stage: my.sado.app.data.Stage) = listOf(
        when {
            stage.body == 4 && stage.tier == 0 -> "[shouting] WEH! Ko dah level up… BEAST MODE!"
            stage.body == 4 -> "[shouting] WEH! ${stage.spoken.uppercase()}!"
            else -> "[excited] WEH! Ko dah level up… ${stage.spoken}!"
        },
    )

    fun cue(text: String) = listOf("[firm] $text")
    fun countdown(seconds: Int) = listOf("[calm] Berdiri diam… countdown $seconds saat.")
    val photoSaved = listOf("[happy] Gambar dah save!")
    val photoPartial = listOf("[apologetic] Gambar dah save… tapi badan tak nampak penuh, so trend tak dikira.")
    val photoFail = listOf("[disappointed] Alamak… tak dapat ambik gambar.")
}

// ElevenLabs text-to-speech, cached as mp3 per (voice, line) in private storage. The API key
// stays in the Keystore-encrypted secret file and only goes to api.elevenlabs.io.
object VoiceClips {
    const val MODEL = "eleven_v4" // reads [audio tags] as expression instead of saying them
    private const val API = "https://api.elevenlabs.io/v1"
    private var player: MediaPlayer? = null
    private val io = Executors.newSingleThreadExecutor()
    private val json = Json { ignoreUnknownKeys = true }

    fun dir(context: Context) = File(context.filesDir, "voice").apply { mkdirs() }

    // clips from before v0.8 sat flat in voice/ with no way to tell voices apart
    fun dropLegacy(context: Context) { dir(context).listFiles()?.filter { it.isFile }?.forEach { it.delete() } }

    // one folder per voice so a voice's clips can be counted and deleted on their own
    private fun voiceDir(context: Context, voiceId: String): File {
        val d = File(dir(context), voiceId.filter { it.isLetterOrDigit() }).apply { mkdirs() }
        // clips from another model sound different and never match a key again, so they go
        val marker = File(d, "model.txt")
        if (marker.takeIf { it.exists() }?.readText() != MODEL) { d.listFiles()?.forEach { it.delete() }; marker.writeText(MODEL) }
        return d
    }

    private fun hash(voiceId: String, text: String) =
        MessageDigest.getInstance("SHA-1").digest("$voiceId|$MODEL|$text".toByteArray()).joinToString("") { "%02x".format(it) }

    internal fun file(context: Context, voiceId: String, text: String): File = File(voiceDir(context, voiceId), hash(voiceId, text) + ".mp3")

    // ── default voice shipped inside the APK ─────────────────────────────────────────────────
    // assets/voice/default/ holds clips exported from Dev mode (same file names: the SHA-1 of voice|model|line)
    // plus voice.txt with the voice id, so a new user hears the AI coach without any API key.
    private const val BUNDLE = "voice/default"
    @Volatile private var bundle: Pair<String, Set<String>>? = null

    private fun bundle(context: Context): Pair<String, Set<String>>? {
        bundle?.let { return it.takeIf { b -> b.first.isNotEmpty() } }
        val loaded = runCatching {
            val names = context.assets.list(BUNDLE).orEmpty().toSet()
            val id = if ("voice.txt" in names) context.assets.open("$BUNDLE/voice.txt").bufferedReader().use { it.readText().trim() } else ""
            id to names.filter { it.endsWith(".mp3") }.toSet()
        }.getOrDefault("" to emptySet())
        bundle = loaded
        return loaded.takeIf { it.first.isNotEmpty() }
    }

    /** The voice the APK ships clips for, or null when it ships none. */
    fun bundledVoice(context: Context): String? = bundle(context)?.first

    private fun asset(context: Context, voiceId: String, text: String): String? {
        val (id, names) = bundle(context) ?: return null
        if (id != voiceId) return null
        val name = hash(id, text) + ".mp3"
        return if (name in names) "$BUNDLE/$name" else null
    }

    fun has(context: Context, voiceId: String, text: String) = file(context, voiceId, text).exists() || asset(context, voiceId, text) != null

    // a clip is either one made on this phone or one shipped in the APK
    private fun MediaPlayer.load(context: Context, voiceId: String, text: String) {
        val f = file(context, voiceId, text)
        if (f.exists()) setDataSource(f.path)
        else context.assets.openFd(asset(context, voiceId, text)!!).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
    }

    fun count(context: Context, voiceId: String) = voiceDir(context, voiceId).listFiles()?.count { it.name.endsWith(".mp3") } ?: 0

    /** Lines that have a clip for this voice, oldest first. */
    fun lines(context: Context, voiceId: String): List<String> {
        val d = voiceDir(context, voiceId)
        val index = File(d, "lines.txt").takeIf { it.exists() }?.readLines().orEmpty()
        return index.mapNotNull { row -> row.split("\t", limit = 2).takeIf { it.size == 2 && File(d, it[0] + ".mp3").exists() }?.get(1) }.distinct()
    }

    fun delete(context: Context, voiceId: String, text: String) { file(context, voiceId, text).delete() }

    fun clear(context: Context, voiceId: String) { voiceDir(context, voiceId).deleteRecursively() }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var chainId = 0

    fun stop() { chainId++; main.removeCallbacksAndMessages(null); player?.let { runCatching { it.stop(); it.release() } }; player = null }

    // the pause between parts of a chained line, about the gap between two spoken sentences
    private const val GAP_MS = 220L

    /** Plays several clips one after another; false (and nothing plays) unless every clip exists. */
    fun playChain(context: Context, voiceId: String, texts: List<String>): Boolean {
        if (texts.isEmpty() || texts.any { !has(context, voiceId, it) }) return false
        stop()
        val id = chainId
        fun next(i: Int) {
            if (id != chainId || i >= texts.size) return
            player = runCatching {
                MediaPlayer().apply {
                    load(context, voiceId, texts[i])
                    setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null; main.postDelayed({ next(i + 1) }, GAP_MS) }
                    prepare(); start()
                }
            }.getOrNull()
        }
        next(0)
        return player != null
    }

    private fun start(source: String): Boolean {
        stop()
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(source)
                setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null }
                prepare()
                start()
            }
        }.getOrNull()
        return player != null
    }

    fun play(context: Context, voiceId: String, text: String): Boolean {
        if (!has(context, voiceId, text)) return false
        stop()
        player = runCatching {
            MediaPlayer().apply {
                load(context, voiceId, text)
                setOnCompletionListener { mp -> mp.release(); if (player === mp) player = null }
                prepare()
                start()
            }
        }.getOrNull()
        return player != null
    }

    /** Streams ElevenLabs' public sample mp3 for a premade voice (no key, no credits). */
    fun preview(url: String) { io.execute { start(url) } }

    /** Last line that played with the phone voice because it had no clip; the report chip watches this. */
    val missing = kotlinx.coroutines.flow.MutableStateFlow<List<String>?>(null)
    /** The voice the missing lines belong to: the chosen AI voice, or the default one shipped in the APK. */
    @Volatile var missingVoice: String? = null

    /** Shows the chip that offers to make [texts] in [voiceId]; needs a key and credit left. */
    fun report(context: Context, voiceId: String, texts: List<String>) {
        if (texts.isEmpty() || creditOut || Secrets.load(context, "elevenlabs") == null) return
        missingVoice = voiceId
        missing.value = texts
    }

    /** Set when ElevenLabs says the credit is used up, so the report chip stays hidden until a generate works again. */
    @Volatile var creditOut = false

    private fun isCredit(code: Int, body: String?) = code == 402 || body?.contains("quota_exceeded", ignoreCase = true) == true

    private fun errorFor(code: Int, body: String?): String = when {
        isCredit(code, body) -> "Credit ElevenLabs dah habis. Top up atau tunggu reset bulan depan."
        code == 401 && body?.contains("voice_clon", ignoreCase = true) == true -> "Clone suara perlukan plan berbayar ElevenLabs (Starter ke atas)."
        code == 401 -> "API key ElevenLabs salah atau expired."
        code == 429 -> "Terlalu banyak request ke ElevenLabs. Cuba lagi kejap lagi."
        code == 403 -> "Akaun ElevenLabs ko tak boleh guna feature ni. Clone suara perlukan plan berbayar."
        code == 404 -> "Voice ID tak jumpa."
        else -> "ElevenLabs balas error $code." + (body?.let { Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(it)?.groupValues?.get(1) }?.let { " $it" } ?: "")
    }

    // v3 sometimes drifts into Indonesian on Malay text: an accent tag up front steers the voice, and
    // language_code pins the language where the model accepts it. Neither changes the cache key.
    private const val ACCENT = "[Malaysian Malay accent, speaking Bahasa Melayu Malaysia] "
    private const val LANGUAGE = "ms"

    /** Blocking HTTP call; returns null on success or an error message. */
    fun generate(context: Context, key: String, voiceId: String, text: String, steady: Boolean = false): String? {
        val out = file(context, voiceId, text)
        if (out.exists()) return null
        return try {
            var (code, body) = request(key, voiceId, text, out, pinLanguage = true, steady = steady)
            // a model that rejects language_code gets the request again without it
            if ((code == 400 || code == 422) && body?.contains("language", ignoreCase = true) == true) {
                val retry = request(key, voiceId, text, out, pinLanguage = false, steady = steady)
                code = retry.first; body = retry.second
            }
            if (code == 200) {
                creditOut = false
                index(out, text)
                null
            } else {
                if (isCredit(code, body)) creditOut = true
                errorFor(code, body)
            }
        } catch (e: Exception) {
            "Tak dapat connect ElevenLabs. Check internet."
        }
    }

    // index so the library can list what each clip says
    private fun index(out: File, text: String) = File(out.parentFile, "lines.txt").appendText(out.nameWithoutExtension + "\t" + text.replace("\n", " ") + "\n")

    // intro parts are made steady: the most stable setting and a fixed seed, so pieces said apart still sound alike
    private const val SEED = 7
    private fun body(text: String, pinLanguage: Boolean, steady: Boolean): String {
        val fields = mutableMapOf<String, kotlinx.serialization.json.JsonElement>("text" to JsonPrimitive(ACCENT + text), "model_id" to JsonPrimitive(MODEL))
        if (pinLanguage) fields["language_code"] = JsonPrimitive(LANGUAGE)
        if (steady) {
            fields["voice_settings"] = JsonObject(mapOf("stability" to JsonPrimitive(1.0)))
            fields["seed"] = JsonPrimitive(SEED)
        }
        return JsonObject(fields).toString()
    }

    /** One text-to-speech call; writes the mp3 on 200 and returns the status with any error body. */
    private fun request(key: String, voiceId: String, text: String, out: File, pinLanguage: Boolean, steady: Boolean = false): Pair<Int, String?> {
        val conn = URL("$API/text-to-speech/$voiceId?output_format=mp3_22050_32").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000; conn.readTimeout = 30000
            conn.doOutput = true
            conn.setRequestProperty("xi-api-key", key)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "audio/mpeg")
            conn.outputStream.use { it.write(body(text, pinLanguage, steady).toByteArray()) }
            val code = conn.responseCode
            if (code != 200) return code to conn.errorStream?.bufferedReader()?.readText()
            val tmp = File(out.parentFile, out.name + ".tmp")
            conn.inputStream.use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
            tmp.renameTo(out)
            return code to null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Several short lines with the same [tag] said in one take, then cut apart at the timestamps ElevenLabs
     * returns, so they share one mood and loudness. Each cut keeps a little air before and after the words.
     * Anything unexpected (no timestamps, a line not found) falls back to one request per line.
     */
    fun generateBatch(context: Context, key: String, voiceId: String, texts: List<String>): String? {
        val todo = texts.filterNot { has(context, voiceId, it) }.distinct()
        if (todo.isEmpty()) return null
        val tag = tagOf(todo.first())
        if (todo.size == 1 || todo.any { tagOf(it) != tag }) return todo.firstNotNullOfOrNull { generate(context, key, voiceId, it, steady = true) }
        val said = todo.map { it.removePrefix(tag).trim() }
        val take = runCatching { takeWithTimes(key, voiceId, tag + " " + said.joinToString(" ")) }.getOrElse { return "Tak dapat connect ElevenLabs. Check internet." }
        val cuts = take.first?.let { (audio, chars) -> cut(context, audio, chars, said) }
        if (cuts == null) {
            // the API said no: report a real error (credit, key), otherwise try line by line
            take.second?.let { if (it.startsWith("!")) return it.drop(1) }
            return todo.firstNotNullOfOrNull { generate(context, key, voiceId, it, steady = true) }
        }
        creditOut = false
        todo.forEachIndexed { i, text -> val out = file(context, voiceId, text); out.writeBytes(cuts[i]); index(out, text) }
        return null
    }

    private fun tagOf(line: String) = Regex("^\\[[^\\]]+]").find(line)?.value.orEmpty()

    /** The take's mp3 and its per-character times (char, start, end), or null with a reason ("!" = show it to the user). */
    private fun takeWithTimes(key: String, voiceId: String, text: String): Pair<Pair<ByteArray, List<Triple<Char, Double, Double>>>?, String?> {
        val first = takeOnce(key, voiceId, text, pinLanguage = true)
        // a model that rejects language_code gets the request again without it
        return if (first.second == "language") takeOnce(key, voiceId, text, pinLanguage = false) else first
    }

    private fun takeOnce(key: String, voiceId: String, text: String, pinLanguage: Boolean): Pair<Pair<ByteArray, List<Triple<Char, Double, Double>>>?, String?> {
        val conn = URL("$API/text-to-speech/$voiceId/with-timestamps?output_format=mp3_22050_32").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000; conn.readTimeout = 60000
            conn.doOutput = true
            conn.setRequestProperty("xi-api-key", key)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.use { it.write(body(text, pinLanguage, true).toByteArray()) }
            val code = conn.responseCode
            if (code != 200) {
                val err = conn.errorStream?.bufferedReader()?.readText()
                if (pinLanguage && (code == 400 || code == 422) && err?.contains("language", ignoreCase = true) == true) return null to "language"
                if (isCredit(code, err)) creditOut = true
                return null to (if (code == 401 || isCredit(code, err)) "!" + errorFor(code, err) else errorFor(code, err))
            }
            return parseTake(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    private fun parseTake(raw: String): Pair<Pair<ByteArray, List<Triple<Char, Double, Double>>>?, String?> {
        val o = json.parseToJsonElement(raw).jsonObject
        val audio = o["audio_base64"]?.jsonPrimitive?.contentOrNull ?: return null to "no audio"
        val a = (o["alignment"] as? JsonObject) ?: return null to "no alignment"
        val ch = a["characters"]?.jsonArray?.map { it.jsonPrimitive.content } ?: return null to "no alignment"
        val s = a["character_start_times_seconds"]?.jsonArray?.map { it.jsonPrimitive.content.toDouble() } ?: return null to "no alignment"
        val e = a["character_end_times_seconds"]?.jsonArray?.map { it.jsonPrimitive.content.toDouble() } ?: return null to "no alignment"
        if (ch.size != s.size || ch.size != e.size) return null to "bad alignment"
        val chars = ch.indices.map { Triple(ch[it].firstOrNull() ?: ' ', s[it], e[it]) }
        return (android.util.Base64.decode(audio, android.util.Base64.DEFAULT) to chars) to null
    }

    /** Cuts one mp3 take into a clip per line, using where each line's first and last letter were said. */
    /**
     * Cuts a take into one clip per line. The v3 timestamps run a little late, so they only say roughly where
     * each line ends; the real cut goes in the quiet gap nearest that guess, found in the decoded audio.
     * Each clip then keeps a little silence before the first word, so the decoder's warm-up never eats it.
     */
    internal fun cut(context: Context, audio: ByteArray, chars: List<Triple<Char, Double, Double>>, said: List<String>): List<ByteArray>? {
        val hints = spans(chars, said) ?: return null
        val frames = Mp3.frames(audio)
        if (frames.isEmpty()) return null
        val energy = Pcm.energy(audio, context.cacheDir) ?: return null
        val ranges = plan(energy, hints) ?: return null
        return ranges.map { (lo, hi) -> Mp3.slice(audio, frames, lo, hi).takeIf { it.isNotEmpty() } ?: return null }
    }

    /** Where ElevenLabs says each line starts and ends, in seconds. */
    internal fun spans(chars: List<Triple<Char, Double, Double>>, said: List<String>): List<Pair<Double, Double>>? {
        val spoken = String(chars.map { it.first }.toCharArray())
        var from = 0
        return said.map { line ->
            val at = spoken.indexOf(line, from)
            if (at < 0) return null
            from = at + line.length
            chars[at].second to chars[at + line.length - 1].third
        }
    }

    const val WINDOW = 0.01 // seconds per energy value
    private const val MIN_GAP = 0.08 // a pause shorter than this is inside a word, not between lines
    private const val LEAD = 0.12 // silence kept before the first word (covers the mp3 decoder warm-up)
    private const val TAIL = 0.12 // and after the last

    /**
     * Picks a start and end time for each line from the loudness of the take (one value per 10 ms) and the
     * timestamp hints. Between two lines it takes the quiet run closest to the hinted boundary; inside each
     * line's stretch it trims to the first and last loud window. Null when a boundary has no quiet run nearby.
     */
    internal fun plan(energy: FloatArray, hints: List<Pair<Double, Double>>): List<Pair<Double, Double>>? {
        if (energy.isEmpty() || hints.isEmpty()) return null
        val loud = (energy.maxOrNull() ?: 0f) * 0.06f
        val quiet = BooleanArray(energy.size) { energy[it] < loud }
        // quiet runs as [first, last] window indexes
        val runs = mutableListOf<IntArray>()
        var i = 0
        while (i < quiet.size) {
            if (!quiet[i]) { i++; continue }
            val s = i
            while (i < quiet.size && quiet[i]) i++
            if ((i - s) * WINDOW >= MIN_GAP) runs += intArrayOf(s, i - 1)
        }
        val cuts = mutableListOf<IntArray>()
        for (k in 0 until hints.size - 1) {
            val guess = (hints[k].second + hints[k + 1].first) / 2 / WINDOW
            val after = cuts.lastOrNull()?.get(1) ?: -1
            // a pause between sentences is longer than a comma pause, so a long run wins over a slightly closer short one
            val best = runs.filter { it[0] > after }.minByOrNull { r -> kotlin.math.abs((r[0] + r[1]) / 2.0 - guess) - 0.5 * (r[1] - r[0] + 1) } ?: return null
            if (kotlin.math.abs((best[0] + best[1]) / 2.0 - guess) * WINDOW > 0.8) return null
            cuts += best
        }
        return hints.indices.map { k ->
            val from = cuts.getOrNull(k - 1)?.get(1)?.plus(1) ?: 0
            val to = cuts.getOrNull(k)?.get(0)?.minus(1) ?: (energy.size - 1)
            val first = (from..to).firstOrNull { !quiet[it] } ?: return null
            val last = (to downTo from).first { !quiet[it] }
            // the air around the words may reach into the pause, up to its middle
            val lo = maxOf(cuts.getOrNull(k - 1)?.let { (it[0] + it[1] + 1) / 2.0 * WINDOW } ?: 0.0, first * WINDOW - LEAD)
            val hi = minOf(cuts.getOrNull(k)?.let { (it[0] + it[1] + 1) / 2.0 * WINDOW } ?: (energy.size * WINDOW), (last + 1) * WINDOW + TAIL)
            lo to hi
        }
    }

    data class Remote(val id: String, val name: String, val about: String, val previewUrl: String?)

    /** Voices on the user's ElevenLabs account (premade + their own). Blocking. */
    fun listVoices(key: String): Pair<List<Remote>, String?> {
        val conn = URL("$API/voices").openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 15000; conn.readTimeout = 30000
            conn.setRequestProperty("xi-api-key", key)
            val code = conn.responseCode
            if (code != 200) return emptyList<Remote>() to errorFor(code, conn.errorStream?.bufferedReader()?.readText())
            val root = json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
            val list = root["voices"]?.jsonArray.orEmpty().mapNotNull { el ->
                val o = el.jsonObject
                val id = o["voice_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val labels = o["labels"] as? JsonObject
                val about = listOfNotNull(
                    labels?.get("gender")?.jsonPrimitive?.contentOrNull,
                    labels?.get("accent")?.jsonPrimitive?.contentOrNull,
                    labels?.get("description")?.jsonPrimitive?.contentOrNull ?: labels?.get("descriptive")?.jsonPrimitive?.contentOrNull,
                    o["category"]?.jsonPrimitive?.contentOrNull,
                ).joinToString(" · ")
                Remote(id, o["name"]?.jsonPrimitive?.contentOrNull ?: id, about, o["preview_url"]?.jsonPrimitive?.contentOrNull)
            }
            list to null
        } catch (e: Exception) {
            emptyList<Remote>() to "Tak dapat connect ElevenLabs. Check internet."
        } finally {
            conn.disconnect()
        }
    }

    /** Instant voice clone from audio samples. Returns (voiceId, null) or (null, error). Blocking. */
    fun clone(key: String, name: String, samples: List<File>): Pair<String?, String?> {
        val boundary = "sado" + System.currentTimeMillis()
        val conn = URL("$API/voices/add").openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000; conn.readTimeout = 120000
            conn.doOutput = true
            conn.setRequestProperty("xi-api-key", key)
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.outputStream.use { out ->
                fun line(s: String) = out.write((s + "\r\n").toByteArray())
                line("--$boundary"); line("Content-Disposition: form-data; name=\"name\""); line(""); line(name)
                line("--$boundary"); line("Content-Disposition: form-data; name=\"remove_background_noise\""); line(""); line("true")
                samples.forEach { f ->
                    val type = if (f.name.endsWith(".mp3")) "audio/mpeg" else if (f.name.endsWith(".wav")) "audio/wav" else "audio/mp4"
                    line("--$boundary"); line("Content-Disposition: form-data; name=\"files\"; filename=\"${f.name}\""); line("Content-Type: $type"); line("")
                    f.inputStream().use { it.copyTo(out) }
                    line("")
                }
                line("--$boundary--")
            }
            when (val code = conn.responseCode) {
                200 -> {
                    val id = json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject["voice_id"]?.jsonPrimitive?.contentOrNull
                    if (id == null) null to "ElevenLabs tak bagi voice ID." else id to null
                }
                else -> null to errorFor(code, conn.errorStream?.bufferedReader()?.readText())
            }
        } catch (e: Exception) {
            null to "Tak dapat connect ElevenLabs. Check internet."
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Walks MPEG audio frames so a take can be cut on frame edges without decoding (a frame is ~26 ms).
 * The Xing/Info header frame carries no sound and is left out of every cut.
 */
/** Decodes an mp3 with the phone's own decoder and measures its loudness every 10 ms. */
internal object Pcm {
    fun energy(mp3: ByteArray, cacheDir: File): FloatArray? {
        val tmp = File(cacheDir, "take-" + System.nanoTime() + ".mp3").apply { writeBytes(mp3) }
        val ex = android.media.MediaExtractor()
        var codec: android.media.MediaCodec? = null
        return try {
            ex.setDataSource(tmp.path)
            val fmt = ex.getTrackFormat(0)
            ex.selectTrack(0)
            val rate = fmt.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE)
            val ch = fmt.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT)
            val win = (rate * VoiceClips.WINDOW).toInt() * ch
            val c = android.media.MediaCodec.createDecoderByType(fmt.getString(android.media.MediaFormat.KEY_MIME)!!)
            codec = c
            c.configure(fmt, null, null, 0); c.start()
            val out = ArrayList<Float>()
            var sum = 0.0; var n = 0
            val info = android.media.MediaCodec.BufferInfo()
            var inDone = false; var outDone = false
            while (!outDone) {
                if (!inDone) {
                    val ii = c.dequeueInputBuffer(10_000)
                    if (ii >= 0) {
                        val size = ex.readSampleData(c.getInputBuffer(ii)!!, 0)
                        if (size < 0) { c.queueInputBuffer(ii, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { c.queueInputBuffer(ii, 0, size, ex.sampleTime, 0); ex.advance() }
                    }
                }
                val oi = c.dequeueOutputBuffer(info, 10_000)
                if (oi >= 0) {
                    val buf = c.getOutputBuffer(oi)!!.order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    buf.position(info.offset); buf.limit(info.offset + info.size)
                    val s = buf.asShortBuffer()
                    while (s.hasRemaining()) {
                        val v = s.get() / 32768.0
                        sum += v * v; n++
                        if (n == win) { out += kotlin.math.sqrt(sum / n).toFloat(); sum = 0.0; n = 0 }
                    }
                    c.releaseOutputBuffer(oi, false)
                    if (info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outDone = true
                }
            }
            if (n > 0) out += kotlin.math.sqrt(sum / n).toFloat()
            out.toFloatArray().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { codec?.stop(); codec?.release() }
            ex.release(); tmp.delete()
        }
    }
}

internal object Mp3 {
    data class Frame(val off: Int, val len: Int, val sec: Double)
    private val br1 = intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
    private val br2 = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)
    private val rates = arrayOf(intArrayOf(44100, 48000, 32000), intArrayOf(22050, 24000, 16000), intArrayOf(11025, 12000, 8000))

    fun frames(b: ByteArray): List<Frame> {
        var i = 0
        if (b.size > 10 && b[0] == 'I'.code.toByte() && b[1] == 'D'.code.toByte() && b[2] == '3'.code.toByte()) {
            i = 10 + ((b[6].toInt() and 0x7f) shl 21 or ((b[7].toInt() and 0x7f) shl 14) or ((b[8].toInt() and 0x7f) shl 7) or (b[9].toInt() and 0x7f))
        }
        val out = mutableListOf<Frame>()
        var t = 0.0
        var first = true
        while (i + 4 <= b.size) {
            val h = (b[i].toInt() and 0xff shl 24) or (b[i + 1].toInt() and 0xff shl 16) or (b[i + 2].toInt() and 0xff shl 8) or (b[i + 3].toInt() and 0xff)
            val ver = (h ushr 19) and 3; val layer = (h ushr 17) and 3
            val bri = (h ushr 12) and 15; val sri = (h ushr 10) and 3; val pad = (h ushr 9) and 1
            if ((h ushr 21) != 0x7FF || layer != 1 || ver == 1 || bri == 0 || bri == 15 || sri == 3) { i++; continue }
            val v1 = ver == 3
            val rate = (if (v1) br1 else br2)[bri] * 1000
            val sr = rates[if (v1) 0 else if (ver == 2) 1 else 2][sri]
            val len = (if (v1) 144 else 72) * rate / sr + pad
            if (len <= 4 || i + len > b.size) break
            val info = first && String(b, i, minOf(len, 64), Charsets.ISO_8859_1).let { "Xing" in it || "Info" in it }
            first = false
            if (!info) { out += Frame(i, len, t); t += (if (v1) 1152.0 else 576.0) / sr }
            i += len
        }
        return out
    }

    /** The frames that start inside [from, to) seconds, back to back. */
    fun slice(b: ByteArray, frames: List<Frame>, from: Double, to: Double): ByteArray {
        val keep = frames.filter { it.sec >= from && it.sec < to }
        val out = java.io.ByteArrayOutputStream(keep.sumOf { it.len })
        keep.forEach { out.write(b, it.off, it.len) }
        return out.toByteArray()
    }
}

object Reminders {
    const val CHANNEL = "sado_reminders"

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(context, 1, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi)
        val st = Store.state.value.settings
        if (!st.reminders) return
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, st.reminderHour); set(Calendar.MINUTE, st.reminderMinute); set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }

    // the character speaks: a nudge on workout days, a sad note after 2+ missed days, quiet on rest days
    fun message(today: LocalDate = LocalDate.now()): Pair<String, String>? {
        val s = Store.state.value
        if (s.plan == null) return null
        val active = Progress.activeDays(s)
        if (today.toEpochDay() in active) return null
        val tag = Progress.todayTag(s, today)
        if (my.sado.app.data.Care.active(s, today.toEpochDay()) != null) return null // rest mode means no nagging
        val lastActive = active.maxOrNull()
        val gap = lastActive?.let { today.toEpochDay() - it } ?: 0
        val name = s.profile?.name?.ifBlank { null } ?: "bro"
        return when {
            gap >= 3 -> "Aku rindu kau, $name..." to "Dah $gap hari. Satu set pun jadilah. Badan aku makin lembik ni."
            tag == "Rest" -> null
            else -> "Jom, $name! Hari ni: $tag" to "${(my.sado.app.data.WeekShift.sessionOn(s, today, today) ?: s.plan.sessionOn(today)).exercises.size} move. Streak ${Progress.streak(s).days} hari, jangan putus."
        }
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Peringatan workout", NotificationManager.IMPORTANCE_DEFAULT))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context.applicationContext)
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) { Reminders.schedule(context); my.sado.app.data.AutoBackup.schedule(context); return }
        val (title, body) = Reminders.message() ?: return
        Reminders.ensureChannel(context)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(7, n) }
    }
}
