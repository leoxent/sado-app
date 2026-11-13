package my.sado.app

import my.sado.app.data.ExerciseLog
import my.sado.app.data.HrPoint
import my.sado.app.data.HrStore
import my.sado.app.data.SessionSummary
import my.sado.app.data.SetLog
import my.sado.app.data.WorkoutLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HrStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val start = 1_700_000_000_000L
    private val pts = (0 until 50).map { HrPoint(start + it * 5_000L, 90 + it % 30) }
    private fun log(hr: List<HrPoint> = emptyList(), startMs: Long? = start) =
        WorkoutLog(1, "Push day", 20, listOf(ExerciseLog("push", listOf(SetLog(10)))), 10, false, startMs = startMs, endMs = startMs?.plus(1_200_000), hr = hr)

    @Test fun encodeDecodeRoundTrip() {
        val text = HrStore.encode(start, pts)
        assertEquals("0 90", text.lines().first())
        assertEquals(pts, HrStore.decode(start, text))
    }

    @Test fun decodeSkipsBrokenLines() {
        assertEquals(listOf(HrPoint(start + 5_000, 100)), HrStore.decode(start, "x y\n5 100\n\n7"))
    }

    @Test fun putWritesAFileAndKeepsTheStateSmall() {
        HrStore.init(tmp.root)
        val saved = HrStore.put(log(), pts)
        assertTrue(saved.hr.isEmpty())
        assertEquals(50, saved.hrPoints)
        assertTrue(File(tmp.root, "hr/$start.txt").exists())
        assertEquals(pts, HrStore.points(saved))
        assertTrue(HrStore.hasHr(saved))
    }

    @Test fun inlineReadingsFromOlderSavesStillRead() {
        HrStore.init(tmp.root)
        assertEquals(pts, HrStore.points(log(hr = pts)))
    }

    @Test fun moveOutTakesInlineReadingsToFiles() {
        HrStore.init(tmp.root)
        val moved = HrStore.moveOut(listOf(log(hr = pts), log(hr = pts, startMs = null)))
        assertTrue(moved[0].hr.isEmpty())
        assertEquals(50, moved[0].hrPoints)
        assertEquals(pts, HrStore.points(moved[0]))
        assertEquals(pts, moved[1].hr) // no start time, no file name: left inline
    }

    @Test fun missingFileMeansNoReadings() {
        HrStore.init(tmp.root)
        val gone = log().copy(hrPoints = 40, startMs = 123L)
        assertTrue(HrStore.points(gone).isEmpty())
        assertFalse(HrStore.hasHr(log()))
    }

    @Test fun restoreAcceptsOnlyPlainNames() {
        HrStore.init(tmp.root)
        HrStore.restoreFile("../evil.txt", byteArrayOf(1))
        HrStore.restoreFile("42.txt", "0 80".toByteArray())
        assertFalse(File(tmp.root, "evil.txt").exists())
        assertEquals(listOf("42.txt"), HrStore.files().map { it.name })
    }

    @Test fun recordCountsPerSession() {
        fun w(vararg r: Int) = WorkoutLog(1, "t", 10, listOf(ExerciseLog("push", r.map { SetLog(it) })), 5, false)
        val logs = listOf(w(10), w(12), w(11), w(13, 9), WorkoutLog(1, "t", 10, listOf(ExerciseLog("push", emptyList(), true)), 0, false))
        assertEquals(listOf(0, 1, 0, 1, 0), SessionSummary.recordCounts(logs))
    }
}
