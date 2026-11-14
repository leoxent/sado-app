package my.sado.app

import my.sado.app.data.MealTime
import my.sado.app.data.MealWhen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Logging a meal for earlier: today, yesterday or the day before, never the future. */
class MealTimeTest {
    private val today = 20_000L
    private val now = 10 * 60 + 42

    @Test fun defaultIsRightNow() {
        assertEquals(today to now, MealTime.resolve(MealWhen(), today, now))
        assertTrue(MealWhen().isNow)
    }

    @Test fun pastDaysKeepTheirTime() {
        assertEquals(today - 1 to 20 * 60, MealTime.resolve(MealWhen(1, 20 * 60), today, now))
        assertEquals(today - 2 to 8 * 60, MealTime.resolve(MealWhen(2, 8 * 60), today, now))
    }

    @Test fun noFurtherBackThanTwoDays() {
        assertEquals(today - 2, MealTime.resolve(MealWhen(5, 8 * 60), today, now).first)
        assertNull(MealTime.of(today - 3, 600, today))
        assertEquals(MealWhen(1, 600), MealTime.of(today - 1, 600, today))
    }

    @Test fun neverInTheFuture() {
        // 20:00 today picked at 10:42 lands on now
        assertEquals(today to now, MealTime.resolve(MealWhen(0, 20 * 60), today, now))
        assertFalse(MealTime.allowed(0, 13 * 60, now))
        assertTrue(MealTime.allowed(0, 8 * 60, now))
        assertTrue(MealTime.allowed(1, 23 * 60, now))
    }

    @Test fun labelsAndToasts() {
        assertEquals("Sekarang · 10:42", MealTime.label(MealWhen(), now))
        assertEquals("Semalam 20:00", MealTime.label(MealWhen(1, 1200), now))
        assertEquals("", MealTime.toastSuffix(today, 600, today))
        assertEquals(" semalam 20:00", MealTime.toastSuffix(today - 1, 1200, today))
        assertEquals(" kelmarin 8:00", MealTime.toastSuffix(today - 2, 480, today))
    }

    @Test fun thinkingLinesAreMany() {
        assertTrue(my.sado.app.ui.THINKING.size >= 10)
        assertEquals(my.sado.app.ui.THINKING.size, my.sado.app.ui.THINKING.toSet().size)
    }
}
