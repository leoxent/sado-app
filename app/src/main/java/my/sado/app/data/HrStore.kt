package my.sado.app.data

import java.io.File

/**
 * Heart-rate readings live in their own small file per session (files/hr/<startMs>.txt), not in sado_state.json:
 * an hour of broadcast is ~700 readings, and the state file is rewritten on every change.
 * Each line is "secondsFromStart bpm", which keeps a session to a few kilobytes.
 */
object HrStore {
    @Volatile var dir: File? = null
        private set

    fun init(filesDir: File) { dir = File(filesDir, "hr").apply { mkdirs() } }

    private fun file(startMs: Long) = dir?.let { File(it, "$startMs.txt") }

    fun encode(startMs: Long, pts: List<HrPoint>) = pts.joinToString("\n") { "${(it.t - startMs) / 1000} ${it.bpm}" }

    fun decode(startMs: Long, text: String): List<HrPoint> = text.lineSequence().mapNotNull { line ->
        val (s, b) = line.trim().split(' ').takeIf { it.size == 2 } ?: return@mapNotNull null
        val sec = s.toLongOrNull() ?: return@mapNotNull null
        val bpm = b.toIntOrNull() ?: return@mapNotNull null
        HrPoint(startMs + sec * 1000, bpm)
    }.toList()

    /** The log with its readings stored: in a file when the store is set up, inline otherwise (tests, no context). */
    fun put(log: WorkoutLog, pts: List<HrPoint>): WorkoutLog {
        val start = log.startMs
        val f = start?.let(::file) ?: return log.copy(hr = pts, hrPoints = pts.size)
        if (pts.isEmpty()) return log.copy(hr = emptyList(), hrPoints = 0)
        runCatching { f.writeText(encode(start, pts)) }.onFailure { return log.copy(hr = pts, hrPoints = pts.size) }
        return log.copy(hr = emptyList(), hrPoints = pts.size)
    }

    /** A session's readings, from its file or from the log itself (older saves kept them inline). */
    fun points(log: WorkoutLog): List<HrPoint> {
        if (log.hr.isNotEmpty()) return log.hr
        if (log.hrPoints == 0) return emptyList()
        val start = log.startMs ?: return emptyList()
        return file(start)?.takeIf { it.exists() }?.let { f -> runCatching { decode(start, f.readText()) }.getOrNull() }.orEmpty()
    }

    fun hasHr(log: WorkoutLog) = log.hr.isNotEmpty() || log.hrPoints > 0

    /** Moves readings kept inline by older versions out to their files. */
    fun moveOut(logs: List<WorkoutLog>): List<WorkoutLog> =
        if (dir == null) logs else logs.map { if (it.hr.isNotEmpty() && it.startMs != null) put(it, it.hr) else it }

    /** Every stored session file, for the backup. */
    fun files(): List<File> = dir?.listFiles()?.filter { it.name.endsWith(".txt") }.orEmpty()

    /** Writes one file from a backup; only a plain "<number>.txt" name is accepted. */
    fun restoreFile(name: String, data: ByteArray) {
        val d = dir ?: return
        if (!Regex("\\d+\\.txt").matches(name)) return
        File(d, name).writeBytes(data)
    }
}
