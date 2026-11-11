package my.sado.app

import my.sado.app.data.Pets
import my.sado.app.data.Progress
import my.sado.app.ui.Blink
import org.junit.Assert.assertEquals
import org.junit.Test

class BlinkTest {
    @Test fun everyFreshPictureCanBlink() {
        assertEquals((Progress.levelImages + Progress.heavyImages + Pets.kinds.flatMap { it.images }).toSet(), Blink.overlays.keys)
    }
}
