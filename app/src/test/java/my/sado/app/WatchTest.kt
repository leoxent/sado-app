package my.sado.app

import my.sado.app.data.Watch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTest {
    @Test fun pairedNamesGiveBrandAndModel() {
        assertEquals(Watch.Found("garmin", "Forerunner 165", "Bluetooth"), Watch.fromName("Forerunner 165"))
        assertEquals("Galaxy Watch6", Watch.fromName("Galaxy Watch6 (A1B2) LE")?.model)
        assertEquals("samsung", Watch.fromName("Galaxy Watch6 (A1B2) LE")?.brand)
        assertEquals("garmin", Watch.fromName("fēnix 7")?.brand)
        assertEquals("fitbit", Watch.fromName("Charge 6")?.brand)
        assertEquals("xiaomi", Watch.fromName("Xiaomi Smart Band 8 1F2E")?.brand)
        assertEquals("Xiaomi Smart Band 8", Watch.fromName("Xiaomi Smart Band 8 1F2E")?.model)
        assertEquals("pixel", Watch.fromName("Pixel Watch 2")?.brand)
        assertEquals("amazfit", Watch.fromName("Amazfit GTR 4")?.brand)
    }

    @Test fun earbudsAndCarsAreNotWatches() {
        assertNull(Watch.fromName("Galaxy Buds2 Pro"))
        assertNull(Watch.fromName("JBL Flip 5"))
        assertNull(Watch.fromName("MYVI"))
    }

    @Test fun companionAppsGiveTheBrandOnly() {
        val f = Watch.fromPackages(setOf("com.garmin.android.apps.connectmobile", "com.whatsapp"))
        assertEquals(listOf(Watch.Found("garmin", null, "app")), f)
        assertEquals(1, Watch.fromPackages(setOf("com.xiaomi.wearable", "com.mi.health")).size)
    }

    @Test fun healthConnectWriterMeansAWatch() {
        val f = Watch.fromOrigins(listOf("com.sec.android.app.shealth" to "SM-R930", "com.sec.android.app.shealth" to null, "com.unknown" to "X"))
        assertEquals(listOf(Watch.Found("samsung", "SM-R930", "Health Connect")), f)
    }

    @Test fun mergePrefersBluetoothAndKeepsAModel() {
        val bt = listOf(Watch.Found("garmin", "Forerunner 165", "Bluetooth"))
        val hc = listOf(Watch.Found("samsung", "SM-R930", "Health Connect"))
        val apps = listOf(Watch.Found("samsung", null, "app"), Watch.Found("garmin", null, "app"))
        val m = Watch.merge(bt, hc, apps)
        assertEquals(listOf("garmin", "samsung"), m.map { it.brand })
        assertEquals("Forerunner 165", m[0].model)
        assertEquals("SM-R930", m[1].model)
        assertEquals("SM-R930", Watch.merge(emptyList(), hc, apps).first().model)
        assertTrue(Watch.merge(emptyList(), emptyList(), emptyList()).isEmpty())
    }

    @Test fun labels() {
        assertEquals("Forerunner 165 (Garmin)", Watch.label("garmin", "Forerunner 165"))
        assertEquals("Galaxy Watch6", Watch.label("samsung", "Galaxy Watch6")) // already says Galaxy Watch
        assertEquals("Garmin", Watch.label("garmin", null))
        assertEquals("Takde jam", Watch.label(Watch.NONE, null))
        assertEquals("Belum pilih", Watch.label(null, null))
    }

    @Test fun everyBrandHasStepsTheGuideCanDraw() {
        Watch.brands.forEach { b ->
            assertTrue(b.id, b.start.isNotEmpty() && b.stop.isNotEmpty())
            (b.start + b.stop).forEach { s -> assertTrue(b.id, s.button == null || s.button in 0 until b.buttons) }
        }
        assertTrue(Watch.brand("garmin")!!.canBroadcast)
        assertTrue(!Watch.brand("samsung")!!.canBroadcast)
    }
}
