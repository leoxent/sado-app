package my.sado.app.research

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Runs every study and writes research/data/results.js for the dashboard. Skipped in the normal test run:
 * it only runs when research/.run exists (create it, run this test, it deletes nothing).
 */
class ResearchRun {
    private val root = File("../../research").canonicalFile

    @Test fun run() {
        assumeTrue(File(root, ".run").exists())
        val only = File(root, ".run").readText().trim().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        fun wants(k: String) = only.isEmpty() || k in only
        val out = File(root, "data").apply { mkdirs() }
        val timing = mutableMapOf<String, Double>()
        fun <T> timed(name: String, f: () -> T): T { val t = System.nanoTime(); return f().also { timing[name] = (System.nanoTime() - t) / 1e9 } }
        val results = linkedMapOf<String, Any>()
        if (wants("plans")) results["plans"] = timed("plans") { PlanCensus.run() }
        if (wants("progression")) results["progression"] = timed("progression") { ProgressionSim.run() }
        if (wants("engagement")) results["engagement"] = timed("engagement") { EngagementSim.run() }
        if (wants("honesty")) results["honesty"] = timed("honesty") { HonestySim.run(plans = PlanCensus.sample(600, 9).map { it.second }) }
        if (wants("goal")) results["goal"] = timed("goal") { GoalSim.run() }
        // a partial run keeps the other studies' timings
        val metaFile = File(out, "meta.js")
        val old = if (metaFile.exists()) Regex("\"(\\w+)\":([0-9.]+)").findAll(metaFile.readText().substringAfter("\"seconds\":{").substringBefore("}")).associate { it.groupValues[1] to it.groupValues[2].toDouble() } else emptyMap()
        results["meta"] = mapOf("generated" to java.time.LocalDateTime.now().toString(), "seconds" to old + timing, "version" to my.sado.app.BuildConfig.VERSION_NAME)
        results.forEach { (k, v) -> File(out, "$k.js").writeText("window.SADO = window.SADO || {}; window.SADO[\"$k\"] = " + SimKit.json(v) + ";\n") }
    }
}
