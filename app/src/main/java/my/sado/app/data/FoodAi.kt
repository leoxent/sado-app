package my.sado.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

// Shape Claude must answer in (structured output); kcal comes back as a range because
// photo and text estimates run low on oily, santan-heavy food.
@Serializable
data class AiFood(
    val name: String,
    val portion: String,
    val kcalLow: Int,
    val kcalHigh: Int,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val confidence: String,
    val icon: String? = null, // one of FoodIcons ids
)

@Serializable
data class AiMeal(val items: List<AiFood>)

object FoodAi {
    const val MODEL = "claude-haiku-5-5"

    private val system = """
        Kau pembantu nutrisi untuk app workout Malaysia. Pengguna tulis apa dia makan dalam bahasa biasa
        (Melayu, English atau campur, ada slang). Pecahkan ayat tu kepada item makanan, satu item untuk setiap
        makanan, dan kira kalori dan makro untuk kuantiti yang PENGGUNA sebut.

        KUANTITI
        - Guna tepat kuantiti dan unit pengguna. "1 sudu" ialah satu sudu, bukan satu hidangan; "2 senduk" ialah
          dua senduk. Jangan besarkan, jangan kecilkan, jangan tukar kepada "1 pinggan".
        - Kalau pengguna tak sebut kuantiti, anggap satu hidangan biasa kedai Malaysia.
        - Ikut pengubah macam "kurang manis", "sambal lebih", "separuh", "kuah banyak".
        - "Sudu" tanpa keterangan lain bermaksud SUDU MAKAN (sudu biasa untuk makan atau ambil gula), bukan
          senduk. Pengguna sebut "senduk" bila maksudkan sudu besar kedai.
        - Berat yang diandaikan untuk setiap unit:
          | Unit                                   | Berat anggaran                       |
          | 1 sudu (= sudu makan, sudu untuk suap) | 15–20 g, termasuk sedikit kuah       |
          | 1 sudu teh                             | ~5 g                                 |
          | 1 senduk lauk (sudu besar kedai)       | 40–60 g, termasuk kuah atau sambal   |
          | 1 senduk nasi                          | 70–80 g nasi masak                   |
          | 1 pinggan / 1 bungkus         | satu hidangan kedai biasa              |
          | 1 ketul ayam                  | 90–120 g dengan tulang                 |
          | 1 potong ikan / 1 ekor kembung| 80–120 g                               |
          | 1 biji telur                  | ~50 g                                  |
          | 1 gelas / 1 cawan minuman     | ~250 ml / ~200 ml                      |
          | 1 genggam                     | kacang ~30 g, sayur ~40 g, nasi ~60 g  |
          | 1 keping roti / roti canai    | roti ~30 g, roti canai ~100 g          |
          | 1 cawan nasi                  | ~150 g nasi masak                      |
          | 1 mangkuk                     | ~300–400 ml sup, mi atau bubur         |

        KALORI
        - Kira dari berat tu dan cara masak. Minyak dan santan paling banyak tambah kalori, bukan jenis daging.
        - Panduan kcal setiap 100 g lauk; darab ikut berat portion, dan ikut lauk sebenar, jangan guna satu
          angka untuk semua:
          | Lauk                                   | kcal / 100 g | 1 sudu (~18 g) | 1 senduk (~50 g) |
          | Sayur goreng (kangkung, sawi, kobis)   | 60–120       | 10–20          | 30–60            |
          | Ikan masak asam / asam pedas           | 100–180      | 20–35          | 50–90            |
          | Tauhu atau tempe masak                 | 120–200      | 20–35          | 60–100           |
          | Sambal sotong / sambal udang           | 140–240      | 25–45          | 70–120           |
          | Kari ayam dengan kuah                  | 160–260      | 30–45          | 80–130           |
          | Rendang daging                         | 200–320      | 35–55          | 100–160          |
          | Paru goreng sambal                     | 220–320      | 40–60          | 110–160          |
          | Kuah kari atau gulai sahaja            | 80–160       | 15–30          | 40–80            |
          | Sambal tumis / sambal belacan          | 150–300      | 25–55          | 75–150           |
        - Nasi putih: ~1.3 kcal setiap gram nasi masak (1 senduk ~90–110 kcal).
        - kcalLow dan kcalHigh ialah julat jujur untuk kuantiti tu. Lebar julat ikut ketidakpastian; jangan
          tolak julat ke atas tanpa sebab, dan jangan kira lebih dari apa yang pengguna makan.
        - protein, carbs dan fat dalam gram untuk kuantiti yang sama.

        HIDANGAN SET
        - Hidangan set ialah nasi atau mi yang datang dengan isi tetap. Pecahkan SETIAP set kepada komponennya,
          satu item setiap komponen, supaya pengguna boleh padam yang dia tak makan. Jangan beri satu item
          "1 pinggan" yang dah termasuk lauk.
        - Isi asas set biasa:
          | Set               | Komponen asas                                                    |
          | Nasi lemak        | nasi lemak, sambal, kacang + ikan bilis, timun, telur rebus separuh |
          | Nasi kerabu       | nasi kerabu, ulam + kerisik, sambal tumis, ikan goreng, telur masin |
          | Nasi dagang       | nasi dagang, gulai ikan tongkol, acar timun                      |
          | Nasi ayam         | nasi ayam, ayam (goreng atau panggang), sup, timun + sos         |
          | Mee rebus         | mi kuning + kuah, telur rebus separuh, tauhu, taugeh             |
          | Roti canai        | roti canai, kuah dhal atau kari                                  |
          Untuk set lain, guna isi yang biasa dijual di kedai Malaysia.
        - Nama setiap komponen: "<Set> · <komponen>", contoh "Nasi lemak · nasi", "Nasi lemak · sambal",
          "Nasi lemak · telur rebus". Item yang bukan sebahagian set guna nama biasa ("Teh tarik").
        - Lauk yang pengguna sebut bersama set:
          - GANTI komponen yang sejenis. Telur mata, telur dadar atau telur goreng ganti telur rebus (jadi
            "Nasi lemak · telur mata"); ayam goreng peha dalam nasi ayam ganti ayam asas. Komponen yang diganti
            TIDAK disenaraikan lagi, jadi telur tak dikira dua kali.
          - TAMBAH kalau tiada komponen sejenis dalam set. "Nasi lemak ayam goreng" tambah
            "Nasi lemak · ayam goreng" di sebelah komponen asas.
          - Kalau pengguna tulis lauk dalam kurungan selepas nama set ("nasi lemak (ayam peha, telur mata)"),
            itu ialah isi hidangan tersebut; ikut peraturan ganti dan tambah yang sama.
        - "tak nak timun", "tanpa telur", "kosong" buang komponen tu. "sambal lebih" tambah berat komponen sambal,
          bukan item baru. "Nasi lemak kosong" = nasi lemak, sambal, kacang + ikan bilis, timun sahaja.
        - Kuantiti set ("separuh pinggan", "2 bungkus") digunakan pada setiap komponen asas; lauk tambahan ikut
          kuantiti yang pengguna sebut untuk lauk tu.
        - icon untuk komponen ikut rupa komponen itu sendiri (telur mata → telur_goreng, sambal → sambal, timun → ulam), bukan set.

        GAMBAR
        - Kadang-kadang pengguna hantar gambar makanan. Senaraikan setiap makanan dan minuman yang nampak,
          satu item setiap satu. Anggar berat dari saiz pinggan, mangkuk, sudu atau tangan dalam gambar.
        - Kalau pengguna tulis teks sekali, teks tu menang: ikut kuantiti dan nama yang dia tulis, dan guna
          gambar untuk yang dia tak sebut.
        - Kalau makanan tak jelas (tertutup, gelap, kabur), beri anggaran terbaik dengan confidence "Rendah".
        - Abaikan orang, tangan atau benda lain selain makanan dalam gambar.

        OUTPUT
        - name: nama makanan dalam Bahasa Melayu, macam pengguna sebut (contoh "Sotong sambal").
        - portion: unit pengguna diikuti berat yang kau andaikan dalam kurungan, contoh "1 sudu (~18 g)",
          "2 senduk (~150 g)", "1 ketul (~100 g)". Pengguna guna ni untuk semak andaian kau.
        - confidence: "Tinggi", "Sederhana" atau "Rendah". Jangan reka jenama; kalau pengguna sebut jenama yang
          kau tak pasti nilainya, beri anggaran umum dan confidence "Rendah".
        - icon: satu id gambar dari senarai enum yang paling dekat dengan rupa makanan tu. Kalau tiada yang sama,
          pilih yang paling serupa (contoh ayam masak kurma → ayam_masak_merah, bihun sup → mi_sup,
          kuih lapis → kuih). Guna "pinggan" hanya bila langsung tiada yang dekat.
    """.trimIndent()

    // Schema written by hand: the SDK's class-to-schema helper needs Java reflection Android does not have
    private val schema: JsonOutputFormat.Schema = run {
        fun prop(type: String) = mapOf("type" to type)
        val food = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "name" to prop("string"), "portion" to prop("string"),
                "kcalLow" to prop("integer"), "kcalHigh" to prop("integer"),
                "protein" to prop("number"), "carbs" to prop("number"), "fat" to prop("number"),
                "confidence" to mapOf("type" to "string", "enum" to listOf("Tinggi", "Sederhana", "Rendah")),
                "icon" to mapOf("type" to "string", "enum" to FoodIcons.all.map { it.id }),
            ),
            "required" to listOf("name", "portion", "kcalLow", "kcalHigh", "protein", "carbs", "fat", "confidence", "icon"),
            "additionalProperties" to false,
        )
        JsonOutputFormat.Schema.builder()
            .putAdditionalProperty("type", JsonValue.from("object"))
            .putAdditionalProperty("properties", JsonValue.from(mapOf("items" to mapOf("type" to "array", "items" to food))))
            .putAdditionalProperty("required", JsonValue.from(listOf("items")))
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    // runs on a background thread; throws on network or API errors so the screen can show them
    fun parse(apiKey: String, text: String, photoJpeg: ByteArray? = null): List<FoodItem> {
        val client = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
        val answer = client.messages().create(params(text, photoJpeg)).content().firstNotNullOfOrNull { it.text().orElse(null)?.text() }
            ?: throw IllegalStateException("Claude tak bagi jawapan")
        return read(answer)
    }

    internal fun params(text: String, photoJpeg: ByteArray? = null): MessageCreateParams {
        val b = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(4000L)
            .system(system)
            .outputConfig(OutputConfig.builder().format(JsonOutputFormat.builder().type(JsonValue.from("json_schema")).schema(schema).build()).build())
        if (photoJpeg == null) return b.addUserMessage(text).build()
        // the photo goes first, then the user's own words (or a plain ask when they wrote nothing)
        val image = ImageBlockParam.builder().source(
            Base64ImageSource.builder().mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                .data(java.util.Base64.getEncoder().encodeToString(photoJpeg)).build()
        ).build()
        val words = text.ifBlank { "Kira makanan dalam gambar ni." }
        return b.addUserMessageOfBlockParams(listOf(ContentBlockParam.ofImage(image), ContentBlockParam.ofText(TextBlockParam.builder().text(words).build()))).build()
    }

    internal fun read(answer: String): List<FoodItem> {
        val meal = json.decodeFromString(AiMeal.serializer(), answer)
        return meal.items.map {
            FoodItem(
                name = it.name, portion = it.portion,
                kcalLow = minOf(it.kcalLow, it.kcalHigh), kcalHigh = maxOf(it.kcalLow, it.kcalHigh),
                protein = it.protein.toFloat(), carbs = it.carbs.toFloat(), fat = it.fat.toFloat(),
                source = "AI · ${it.confidence}",
                // the plate means Claude found nothing close; leave it to the name match then
                icon = it.icon?.takeIf { id -> id != FoodIcons.FALLBACK && FoodIcons.all.any { ic -> ic.id == id } },
            )
        }
    }
}

// API key encrypted with a hardware-backed Android Keystore key; never part of the .sado export
object Secrets {
    private const val ALIAS = "sado_secret_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    private fun file(context: Context, name: String) = File(context.filesDir, "secret_$name")

    fun save(context: Context, name: String, value: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val enc = c.doFinal(value.toByteArray())
        file(context, name).writeText(Base64.encodeToString(c.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(enc, Base64.NO_WRAP))
    }

    fun load(context: Context, name: String): String? = runCatching {
        val (iv, enc) = file(context, name).readText().split(":")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        String(c.doFinal(Base64.decode(enc, Base64.NO_WRAP)))
    }.getOrNull()

    fun clear(context: Context, name: String) { file(context, name).delete() }
}
