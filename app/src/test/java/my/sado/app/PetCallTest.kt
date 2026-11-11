package my.sado.app

import my.sado.app.data.Pets
import my.sado.app.ui.Feedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PetCallTest {
    @Test fun everyAnimalHasACallAtEveryStage() {
        val files = java.io.File("src/main/res/raw").list()!!.toSet()
        for (k in Pets.kinds) for (stage in 0..3) {
            val (key, rate) = Feedback.petCall(k.id, stage)
            assertTrue("missing sfx_$key.wav", "sfx_$key.wav" in files)
            assertTrue(rate in 0.5f..2f) // SoundPool's range
        }
    }

    @Test fun legendsWithTheirOwnRecordingPlayItUnchanged() {
        assertEquals("pet_serigala_legenda" to 1f, Feedback.petCall("serigala", 3))
        assertEquals("pet_harimau_legenda" to 1f, Feedback.petCall("harimau", 3))
        assertEquals("pet_beruang" to 0.84f, Feedback.petCall("beruang", 3))
    }

    @Test fun theMouthKnowsHowLongEveryCallIs() {
        val wavs = java.io.File("src/main/res/raw").listFiles()!!.filter { it.name.startsWith("sfx_pet_") }
        assertEquals(wavs.size, Feedback.PET_CALL_MILLIS.size)
        for (f in wavs) {
            val b = f.readBytes()
            val perSecond = java.nio.ByteBuffer.wrap(b, 28, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
            val millis = (b.size - 44) * 1000L / perSecond
            val key = f.name.removePrefix("sfx_").removeSuffix(".wav")
            assertEquals(key, millis.toDouble(), Feedback.PET_CALL_MILLIS.getValue(key).toDouble(), 20.0)
        }
    }

    @Test fun everyStageHasAnOpenMouthAndOnlyGrownWolvesHowl() {
        for (k in Pets.kinds) {
            assertEquals(4, k.openImages.toSet().size)
            for (stage in 0..3) assertEquals(k.id == "serigala" && stage >= 2, Pets.howlImage(k.id, stage) != null)
        }
    }

    @Test fun onlyTheEagleHasWingsAndEachStageHasItsOwn() {
        for (k in Pets.kinds) for (stage in 0..3) assertEquals(k.id == "helang", Pets.flapImages(k.id, stage) != null)
        val all = (0..3).flatMap { Pets.flapImages("helang", it)!!.toList() }
        assertEquals(8, all.toSet().size)
    }

    @Test fun youngerSoundsHigher() {
        assertTrue(Feedback.petCall("buaya", 0).second > Feedback.petCall("buaya", 2).second)
    }
}
