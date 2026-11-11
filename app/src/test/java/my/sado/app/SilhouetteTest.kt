package my.sado.app

import my.sado.app.data.Silhouette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// The ratio reads the body outline: wider delts or lats move it, the skeleton doesn't have to.
class SilhouetteTest {
    private val w = 200
    private val h = 400
    private val shoulderY = 100f
    private val hipY = 260f

    /** A torso centred at x=100: [shoulder] wide at the shoulders, narrowing to [waist] by 70% down, an arm off to the side. */
    private fun body(shoulder: Int, waist: Int, armGap: Int = 6): FloatArray {
        val m = FloatArray(w * h)
        for (y in 60 until 380) {
            val t = ((y - shoulderY) / (hipY - shoulderY)).coerceIn(0f, 1f)
            val width = when {
                y < shoulderY -> 40 // head and neck
                t < 0.7f -> shoulder + (waist - shoulder) * (t / 0.7f)
                else -> waist + (t - 0.7f) * 40 // hips flare a little
            }.toInt()
            for (x in 100 - width / 2 until 100 + width / 2) m[y * w + x] = 1f
            // an arm hanging apart from the torso: must not count
            if (y in 110..250) for (x in 100 + width / 2 + armGap until 100 + width / 2 + armGap + 14) if (x < w) m[y * w + x] = 1f
        }
        return m
    }

    @Test fun measuresShoulderAndWaistFromTheOutline() {
        val m = Silhouette.measure(body(120, 80), w, h, shoulderY, hipY, 100f)
        assertNotNull(m)
        assertEquals(120f, m!!.shoulderPx, 4f)
        assertEquals(80f, m.waistPx, 4f)
        assertEquals(1.5f, m.ratio, 0.08f)
    }

    @Test fun widerLatsRaiseTheRatio() {
        val relaxed = Silhouette.measure(body(120, 80), w, h, shoulderY, hipY, 100f)!!.ratio
        val flared = Silhouette.measure(body(132, 80), w, h, shoulderY, hipY, 100f)!!.ratio
        assertTrue("$relaxed → $flared", flared > relaxed * 1.05f)
    }

    @Test fun sameBodyGivesTheSameNumber() {
        val a = Silhouette.measure(body(120, 80), w, h, shoulderY, hipY, 100f)!!.ratio
        val b = Silhouette.measure(body(120, 80), w, h, shoulderY + 3, hipY - 2, 103f)!!.ratio // landmarks a little off
        assertEquals(a, b, a * 0.02f)
    }

    @Test fun nonsenseIsDropped() {
        assertNull(Silhouette.measure(FloatArray(w * h), w, h, shoulderY, hipY, 100f))
        assertNull(Silhouette.measure(body(120, 80), w, h, shoulderY, shoulderY + 10f, 100f)) // torso too short to trust
    }
}
