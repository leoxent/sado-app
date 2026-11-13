package my.sado.app

import my.sado.app.data.Catalog
import my.sado.app.ui.MoveImages
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveImageTest {
    @Test fun everyMoveHasAPicture() {
        assertEquals(emptyList<String>(), Catalog.moves.map { it.id }.filterNot { MoveImages.has(it) })
    }
}
