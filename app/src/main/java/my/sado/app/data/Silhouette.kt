package my.sado.app.data

/**
 * Shoulder:waist from a body outline (a selfie-segmentation mask), so it moves when delts and lats grow or the waist
 * shrinks. Joint-to-joint widths from pose landmarks don't: the skeleton doesn't get wider. Pose landmarks are only
 * used to find the rows: the shoulder joints, and the hips that set the torso length.
 *
 * Shoulder width: the widest outline in a thin band at shoulder height (the delts sit at and just below the joint).
 * Waist width: the narrowest outline between the bottom of the ribcage (~55% of shoulder-to-hip) and the navel (~85%).
 * Each row counts only the run of body pixels joined to the middle of the body, so an arm held away from the side is
 * left out; an arm pressed against the waist is not, which is why the camera tip asks for arms a little out.
 */
object Silhouette {
    const val THRESHOLD = 0.5f
    const val SHOULDER_FROM = -0.03f
    const val SHOULDER_TO = 0.08f
    const val WAIST_FROM = 0.55f
    const val WAIST_TO = 0.85f

    data class Measure(val shoulderPx: Float, val waistPx: Float) {
        val ratio get() = shoulderPx / waistPx
    }

    /** Width of the body run through [cx] on row [y]; if [cx] itself is background, the nearest body pixel close by. */
    fun runWidth(mask: FloatArray, w: Int, h: Int, y: Int, cx: Int): Int {
        if (y !in 0 until h) return 0
        val row = y * w
        fun body(x: Int) = x in 0 until w && mask[row + x] >= THRESHOLD
        val reach = w / 20
        val start = (0..reach).flatMap { listOf(cx - it, cx + it) }.firstOrNull { body(it) } ?: return 0
        var l = start
        var r = start
        while (body(l - 1)) l--
        while (body(r + 1)) r++
        return r - l + 1
    }

    // a 3-row average, so one ragged row of the mask doesn't decide the number
    private fun smooth(mask: FloatArray, w: Int, h: Int, y: Int, cx: Int) =
        (-1..1).map { runWidth(mask, w, h, y + it, cx) }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.toFloat() ?: 0f

    fun measure(mask: FloatArray, w: Int, h: Int, shoulderY: Float, hipY: Float, centerX: Float): Measure? {
        val torso = hipY - shoulderY
        if (torso < h * 0.08f || mask.size < w * h) return null
        val cx = centerX.toInt().coerceIn(0, w - 1)
        fun rows(from: Float, to: Float) = (shoulderY + from * torso).toInt()..(shoulderY + to * torso).toInt()
        val shoulder = rows(SHOULDER_FROM, SHOULDER_TO).maxOfOrNull { smooth(mask, w, h, it, cx) } ?: return null
        val waist = rows(WAIST_FROM, WAIST_TO).map { smooth(mask, w, h, it, cx) }.filter { it > 0f }.minOrNull() ?: return null
        // nonsense outlines (background merged in, body cut off) are dropped rather than trusted
        if (waist < w * 0.03f || shoulder <= waist * 0.8f || shoulder > w * 0.98f) return null
        return Measure(shoulder, waist)
    }
}
