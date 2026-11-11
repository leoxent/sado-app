package my.sado.app

import my.sado.app.data.PhotoTrend
import my.sado.app.data.ProgressPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoTrendTest {
    private fun p(day: Long, ratio: Float?, angle: Int = 0, kind: Int = 1) = ProgressPhoto(day, angle, "f$day-$angle.jpg", ratio, ratioKind = kind)

    @Test fun rollingAverageOverFourWeeks() {
        val photos = listOf(p(0, 1.40f), p(7, 1.50f), p(40, 1.60f), p(41, 1.62f, kind = 0), p(42, null), p(43, 1.9f, angle = 2))
        val r = PhotoTrend.rolling(photos)
        assertEquals(listOf(0L, 7L, 40L), r.map { it.first })
        assertEquals(1.45f, r[1].second, 0.001f) // day 7: days 0 and 7
        assertEquals(1.60f, r[2].second, 0.001f) // day 40: only itself inside the window
    }

    @Test fun compareDefaultsToAboutFourWeeksBack() {
        val list = listOf(p(0, 1f), p(20, 1f), p(31, 1f), p(50, 1f))
        val (a, b) = PhotoTrend.compareDefault(list)!!
        assertEquals(50L, b.epochDay)
        assertEquals(20L, a.epochDay) // 22 is the target: 20 is closest
    }

    @Test fun timelapseOpensAtEightFrontPhotos() {
        assertFalse(PhotoTrend.timelapseOpen((1..7).map { p(it.toLong(), 1f) } + p(9, null, angle = 1)))
        assertTrue(PhotoTrend.timelapseOpen((1..8).map { p(it.toLong(), null) }))
    }
}
