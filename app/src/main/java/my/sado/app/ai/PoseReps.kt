package my.sado.app.ai

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

// Which joint angle drives the rep. Angles are in degrees, 180 = straight.
enum class Joint { Elbow, Knee, Hip, ShoulderAbduction }

// Extra form check that runs every frame alongside the rep count.
enum class FormCheck { None, BodyLine, KneeLine, TorsoUpright, ElbowTucked, WallSitDepth }

/**
 * A rep = leave the [start] zone, reach the [target] zone, come back to [start].
 * start > target means the joint bends (push-up, squat, curl); start < target means it opens (bridge, raise).
 */
data class Motion(
    val joint: Joint,
    val start: Float,
    val target: Float,
    val form: FormCheck = FormCheck.None,
    val view: String = "Letak phone di lantai, tepi badan, 2 meter. Seluruh badan mesti nampak.",
    val shallowCue: String = "Turun lagi sikit!",
)

object Motions {
    // whole-body form corrections, kept here so the voice can pre-generate them
    val formCues = listOf("Pinggang jatuh! Kemut perut.", "Punggung tinggi sangat.", "Dada naik sikit.", "Siku rapat ke badan.", "Turun sampai lutut 90°.")

    private val side = "Letak phone di lantai, tepi badan, 2 meter. Seluruh badan mesti nampak."
    private val front = "Letak phone depan kau, paras pinggang, 2–3 meter. Seluruh badan mesti nampak."

    private val pushUp = Motion(Joint.Elbow, 150f, 100f, FormCheck.BodyLine, side, "Dada turun lagi!")
    private val pull = Motion(Joint.Elbow, 145f, 85f, FormCheck.None, side, "Pull lagi tinggi!")
    private val curl = Motion(Joint.Elbow, 145f, 65f, FormCheck.ElbowTucked, side, "Angkat penuh!")
    private val squat = Motion(Joint.Knee, 155f, 105f, FormCheck.TorsoUpright, side, "Duduk lagi dalam!")
    private val singleLeg = Motion(Joint.Knee, 155f, 95f, FormCheck.None, side, "Turun lagi dalam.")

    // moves not listed here (snow angel, dead bug, calf raise…) stay on tap counting: the pose model can't see them reliably
    val reps: Map<String, Motion> = mapOf(
        "push" to pushUp, "wide" to pushUp, "decline" to pushUp, "archer" to pushUp, "onearm" to pushUp, "diamond" to pushUp,
        "kneepush" to pushUp.copy(form = FormCheck.KneeLine),
        "pike" to Motion(Joint.Elbow, 150f, 100f, view = side, shallowCue = "Kepala turun lagi."),
        "dips" to Motion(Joint.Elbow, 150f, 100f, view = side, shallowCue = "Turun sampai siku 90°."),
        "floorpress" to Motion(Joint.Elbow, 150f, 100f, view = side, shallowCue = "Siku sentuh lantai dulu."),
        "pullup" to pull, "chinup" to pull, "invrow" to pull, "dbrow" to pull, "bandpull" to pull,
        "negative" to Motion(Joint.Elbow, 80f, 145f, view = side, shallowCue = "Turun sampai tangan lurus."),
        "curl" to curl, "hammer" to curl, "bandcurl" to curl,
        "lateral" to Motion(Joint.ShoulderAbduction, 30f, 70f, view = front, shallowCue = "Angkat sampai paras bahu."),
        "bandlat" to Motion(Joint.ShoulderAbduction, 30f, 70f, view = front, shallowCue = "Angkat sampai paras bahu."),
        "squat" to squat,
        "lunge" to singleLeg, "split" to singleLeg,
        "shrimp" to singleLeg.copy(target = 85f), "pistol" to singleLeg.copy(target = 85f),
        "bridge" to Motion(Joint.Hip, 130f, 160f, view = side, shallowCue = "Push hip lagi tinggi!"),
        "slbridge" to Motion(Joint.Hip, 130f, 160f, view = side, shallowCue = "Push hip lagi tinggi!"),
        "legraise" to Motion(Joint.Hip, 160f, 110f, view = side, shallowCue = "Angkat kaki lagi tinggi."),
        "kneeraise" to Motion(Joint.Hip, 160f, 110f, view = side, shallowCue = "Lutut lagi tinggi!"),
        "donkey" to Motion(Joint.Hip, 100f, 145f, view = side, shallowCue = "Tendang lagi tinggi!"),
    )

    // timed moves get form feedback only; the timer stays in charge
    val holds: Map<String, FormCheck> = mapOf("plank" to FormCheck.BodyLine, "wallsit" to FormCheck.WallSitDepth, "climber" to FormCheck.BodyLine)

    fun viewFor(id: String) = reps[id]?.view ?: if (id in holds) side else null
}

private fun angle(a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Float {
    val abx = a.position.x - b.position.x; val aby = a.position.y - b.position.y
    val cbx = c.position.x - b.position.x; val cby = c.position.y - b.position.y
    val dot = abx * cbx + aby * cby
    val m = sqrt(abx * abx + aby * aby) * sqrt(cbx * cbx + cby * cby)
    if (m < 1e-3f) return 180f
    return Math.toDegrees(acos((dot / m).coerceIn(-1f, 1f)).toDouble()).toFloat()
}

/** The body side facing the camera, picked by landmark confidence. */
private class Side(p: Pose) {
    private val left = listOf(PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)
    private val right = listOf(PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)
    private fun score(ids: List<Int>, p: Pose) = ids.sumOf { (p.getPoseLandmark(it)?.inFrameLikelihood ?: 0f).toDouble() }
    private val ids = if (score(left, p) >= score(right, p)) left else right
    private val pts = ids.map { p.getPoseLandmark(it) }
    val shoulder = pts[0]; val elbow = pts[1]; val wrist = pts[2]; val hip = pts[3]; val knee = pts[4]; val ankle = pts[5]
    fun ok(vararg l: PoseLandmark?) = l.all { it != null && it.inFrameLikelihood > 0.5f }
}

/** Frame-by-frame rep counter plus form cues for one move. Not thread-safe; feed it from one thread. */
class RepEngine(private val motion: Motion?, private val hold: FormCheck = FormCheck.None) {
    private val bends = motion != null && motion.start > motion.target
    private var smooth: Float? = null
    private var inStart = true
    private var best: Float? = null // furthest point reached since leaving the start zone
    var visible = false
        private set
    var lastAngle: Float? = null
        private set

    /** Returns events for this frame: a counted rep and/or a cue to show or say. */
    fun feed(p: Pose): Pair<Boolean, String?> {
        val s = Side(p)
        val raw = when (motion?.joint) {
            Joint.Elbow -> if (s.ok(s.shoulder, s.elbow, s.wrist)) angle(s.shoulder!!, s.elbow!!, s.wrist!!) else null
            Joint.Knee -> if (s.ok(s.hip, s.knee, s.ankle)) angle(s.hip!!, s.knee!!, s.ankle!!) else null
            Joint.Hip -> if (s.ok(s.shoulder, s.hip, s.knee)) angle(s.shoulder!!, s.hip!!, s.knee!!) else null
            Joint.ShoulderAbduction -> if (s.ok(s.hip, s.shoulder, s.elbow)) angle(s.hip!!, s.shoulder!!, s.elbow!!) else null
            null -> if (s.ok(s.shoulder, s.hip)) 0f else null
        }
        visible = raw != null
        if (raw == null) return false to "Badan tak nampak penuh"
        val a = smooth?.let { it * 0.6f + raw * 0.4f } ?: raw
        smooth = a
        lastAngle = a
        val cue = formCue(s)
        if (motion == null) return false to cue

        fun beyond(x: Float, limit: Float) = if (bends) x <= limit else x >= limit
        fun before(x: Float, limit: Float) = if (bends) x >= limit else x <= limit
        var rep = false
        var shallow: String? = null
        if (inStart) {
            if (!before(a, motion.start)) { inStart = false; best = a }
        } else {
            best = best?.let { if (bends) minOf(it, a) else maxOf(it, a) } ?: a
            if (before(a, motion.start)) {
                val b = best!!
                if (beyond(b, motion.target)) rep = true
                // came back after going at least a third of the way: a half rep worth flagging
                else if (abs(b - motion.start) > abs(motion.target - motion.start) / 3) shallow = motion.shallowCue
                inStart = true
                best = null
            }
        }
        return rep to (shallow ?: cue)
    }

    private fun formCue(s: Side): String? = when (if (motion != null) motion.form else hold) {
        FormCheck.BodyLine -> lineCue(s, s.ankle)
        FormCheck.KneeLine -> lineCue(s, s.knee)
        FormCheck.TorsoUpright -> if (s.ok(s.shoulder, s.hip)) {
            val dx = s.shoulder!!.position.x - s.hip!!.position.x
            val dy = s.hip.position.y - s.shoulder.position.y
            if (Math.toDegrees(atan2(abs(dx), dy).toDouble()) > 55) "Dada naik sikit." else null
        } else null
        FormCheck.ElbowTucked -> if (s.ok(s.shoulder, s.elbow, s.hip)) {
            if (angle(s.hip!!, s.shoulder!!, s.elbow!!) > 35) "Siku rapat ke badan." else null
        } else null
        FormCheck.WallSitDepth -> if (s.ok(s.hip, s.knee, s.ankle)) {
            if (angle(s.hip!!, s.knee!!, s.ankle!!) > 115) "Turun sampai lutut 90°." else null
        } else null
        FormCheck.None -> null
    }

    // shoulder-hip-end should be a straight line; tells sagging hips from piked hips
    private fun lineCue(s: Side, end: PoseLandmark?): String? {
        if (!s.ok(s.shoulder, s.hip, end)) return null
        val sh = s.shoulder!!; val hp = s.hip!!; val en = end!!
        if (angle(sh, hp, en) > 160) return null
        // where the hip sits relative to the shoulder→end line; image y grows downward
        val t = ((hp.position.x - sh.position.x) / (en.position.x - sh.position.x).let { if (abs(it) < 1f) 1f else it }).coerceIn(0f, 1f)
        val lineY = sh.position.y + t * (en.position.y - sh.position.y)
        return if (hp.position.y > lineY) "Pinggang jatuh! Kemut perut." else "Punggung tinggi sangat."
    }
}
