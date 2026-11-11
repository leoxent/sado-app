package my.sado.app

import com.anthropic.core.jsonMapper
import my.sado.app.data.FoodAi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodAiTest {
    // the request carries a json_schema output format built without reflection
    @Test fun requestHasSchema() {
        val body = jsonMapper().writeValueAsString(FoodAi.params("nasi lemak")._body())
        assertTrue(body, body.contains("\"output_config\":{\"format\":{\"schema\":{\"type\":\"object\""))
        assertTrue(body, body.contains("},\"type\":\"json_schema\"}}"))
        assertTrue(body, body.contains("\"kcalLow\":{\"type\":\"integer\"}"))
    }

    // Claude picks the thumbnail; "pinggan" (nothing close) falls back to the name match
    // "1 sudu" goes to Haiku word for word, and the system prompt says a spoon is a spoon
    @Test fun spoonStaysSpoon() {
        val body = FoodAi.params("nasi putih 2 senduk, sotong sambal 1 sudu").toString()
        assertTrue(body, body.contains("nasi putih 2 senduk, sotong sambal 1 sudu"))
        assertTrue(body, body.contains("\"1 sudu\" ialah satu sudu, bukan satu hidangan"))
        assertTrue(body, body.contains("1 sudu (~18 g)"))
        assertTrue(body, body.contains("bermaksud SUDU MAKAN"))
    }

    @Test fun photoGoesAsImage() {
        val body = FoodAi.params("", byteArrayOf(1, 2, 3)).toString()
        assertTrue(body, body.contains("image/jpeg"))
        assertTrue(body, body.contains("AQID"))
        assertTrue(body, body.contains("Kira makanan dalam gambar ni."))
    }

    @Test fun answerPicksIcon() {
        val body = FoodAi.params("ayam").toString()
        assertTrue(body, body.contains("ayam_masak_merah"))
        val items = FoodAi.read("""{"items":[{"name":"Ayam masak kurma","portion":"1 ketul","kcalLow":250,"kcalHigh":320,"protein":22,"carbs":8,"fat":15,"confidence":"Sederhana","icon":"ayam_masak_merah"},{"name":"Nasi putih","portion":"1 pinggan","kcalLow":250,"kcalHigh":300,"protein":5,"carbs":60,"fat":1,"confidence":"Tinggi","icon":"pinggan"}]}""")
        assertEquals("ayam_masak_merah", items[0].icon)
        assertEquals(null, items[1].icon)
        assertEquals("nasi_putih", my.sado.app.data.FoodIcons.of(items[1]))
    }

    @Test fun answerParses() {
        val items = FoodAi.read("""{"items":[{"name":"Kuey teow goreng","portion":"1 pinggan","kcalLow":600,"kcalHigh":750,"protein":18,"carbs":80.5,"fat":25,"confidence":"Sederhana"}]}""")
        assertEquals(1, items.size)
        assertEquals("AI · Sederhana", items[0].source)
        assertEquals(750, items[0].kcalHigh)
    }
}
