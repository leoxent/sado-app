package my.sado.app.data

import my.sado.app.R

/** Backdrops for the character's card on Home. Not tied to level: anyone can pick any of them, or none. */
object Backgrounds {
    data class Bg(val id: String, val name: String, val day: Int, val night: Int)

    val all = listOf(
        Bg("kampung", "Kampung", R.drawable.bg_kampung, R.drawable.bg_kampung_night),
        Bg("sawah", "Sawah", R.drawable.bg_sawah, R.drawable.bg_sawah_night),
        Bg("padang", "Padang", R.drawable.bg_padang, R.drawable.bg_padang_night),
        Bg("rooftop", "Rooftop", R.drawable.bg_rooftop, R.drawable.bg_rooftop_night),
    )

    /** The picture for [id] in this theme, or null for "Takde" or an id no longer in the list. */
    fun image(id: String?, dark: Boolean): Int? = all.firstOrNull { it.id == id }?.let { if (dark) it.night else it.day }
}
