package my.sado.app.data

import kotlin.math.roundToInt

/**
 * Reads and rewrites a portion line like "2 senduk (~150 g)" so the quantity can change on its own:
 * "1 keping" becomes "2 keping", and the weight in brackets follows. Lines with no leading number
 * ("Satu hidangan") get a "×2 · " prefix instead, the way older logs were written.
 */
object Portion {
    data class Parts(val qty: Float, val unit: String, val counted: Boolean)

    private val times = Regex("""^×(\d+(?:[.,]\d+)?) · (.*)$""")
    private val lead = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*(.*)$""")
    private val grams = Regex("""~(\d+(?:\.\d+)?)\s*(g|ml)\b""")

    fun parse(p: String): Parts {
        times.find(p)?.let { m ->
            val k = m.groupValues[1].replace(',', '.').toFloat()
            val inner = parse(m.groupValues[2])
            return inner.copy(qty = inner.qty * k, unit = scaleWeight(inner.unit, k))
        }
        lead.find(p)?.let { m -> return Parts(m.groupValues[1].replace(',', '.').toFloat(), m.groupValues[2].trim(), true) }
        return Parts(1f, p.trim(), false)
    }

    fun format(parts: Parts): String = when {
        parts.counted -> "${num(parts.qty)} ${parts.unit}".trim()
        parts.qty == 1f -> parts.unit
        else -> "×${num(parts.qty)} · ${parts.unit}"
    }

    /** The same portion k times over, weight in brackets included. */
    fun times(p: String, k: Float): String {
        if (k == 1f) return p
        val parts = parse(p)
        return format(parts.copy(qty = parts.qty * k, unit = scaleWeight(parts.unit, k)))
    }

    fun scaleWeight(unit: String, k: Float): String =
        grams.replace(unit) { m -> "~${(m.groupValues[1].toFloat() * k).roundToInt()} ${m.groupValues[2]}" }

    /** Bigger numbers move in bigger steps: half a spoon, one keping, ten grams. */
    fun step(qty: Float): Float = when {
        qty < 5f -> 0.5f
        qty < 20f -> 1f
        else -> 10f
    }

    fun num(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(java.util.Locale.US, v)
}

/** What each word means, shown under the text box and in the editor. */
object Measures {
    val guide = listOf(
        "Sudu" to "Sudu makan, macam sudu gula. Lauk ~15–20 g.",
        "Sudu teh" to "Sudu kecil kacau kopi. ~5 g.",
        "Senduk" to "Sudu besar kedai. Lauk 40–60 g, nasi 70–80 g.",
        "Genggam" to "Satu genggam tangan. Kacang ~30 g, sayur ~40 g.",
        "Biji" to "Satu benda bulat: telur, kuih, karipap, buah.",
        "Ketul" to "Satu potong ayam atau daging. Ayam ~100 g.",
        "Keping" to "Benda nipis: roti, roti canai, tempe, ikan.",
        "Cawan" to "Cawan 250 ml. Nasi ~150 g.",
        "Mangkuk" to "Mangkuk sup atau mi, ~300–400 ml.",
        "Pinggan / bungkus" to "Satu hidangan penuh macam kedai.",
        "Gelas" to "Air ~250 ml. Gelas besar kedai ~350 ml.",
    )
}
