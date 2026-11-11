package my.sado.app.data

import kotlin.math.abs

/** What the progress photos say over weeks, not day to day: one photo's posture or light shouldn't swing it. */
object PhotoTrend {
    const val WINDOW_DAYS = 28
    const val COMPARE_DAYS = 28
    const val TIMELAPSE_AT = 8 // front photos before the timelapse unlocks

    /** Front photos measured from the outline, oldest first. */
    fun measured(photos: List<ProgressPhoto>) = photos.filter { it.angle == 0 && it.ratioKind == 1 && it.ratio != null }.sortedBy { it.epochDay }

    /** For each measured front photo's day, the average ratio of the photos in the 4 weeks up to it. */
    fun rolling(photos: List<ProgressPhoto>, days: Int = WINDOW_DAYS): List<Pair<Long, Float>> {
        val m = measured(photos)
        return m.map { it.epochDay }.distinct().map { d -> d to m.filter { it.epochDay in d - days + 1..d }.map { it.ratio!! }.average().toFloat() }
    }

    /** Banding by default: the newest photo against the one closest to four weeks before it. */
    fun compareDefault(list: List<ProgressPhoto>): Pair<ProgressPhoto, ProgressPhoto>? {
        val newest = list.maxByOrNull { it.epochDay } ?: return null
        val older = list.filter { it.epochDay < newest.epochDay }.minByOrNull { abs(it.epochDay - (newest.epochDay - COMPARE_DAYS)) } ?: return null
        return older to newest
    }

    fun frontCount(photos: List<ProgressPhoto>) = photos.count { it.angle == 0 }

    fun timelapseOpen(photos: List<ProgressPhoto>) = frontCount(photos) >= TIMELAPSE_AT
}
