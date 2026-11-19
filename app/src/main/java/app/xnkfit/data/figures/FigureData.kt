package app.xnkfit.data.figures

import app.xnkfit.domain.training.MuscleGroup
import org.json.JSONArray
import org.json.JSONObject

// Figure data of the XNK app, read from the bundled assets (Content §5.1): the muscle map, the rig patterns, and the mapping
// of the 16 muscle codes to the figure's regions (content/figures/muscle-map.json). Nothing here is drawn; see ui/latihan.

/**
 * One side of the body ([view] "front" or "back"): outline, base shapes and the shape lists of each region (SVG path data).
 * Split regions of muscle-map.json (schema 2, e.g. `obliques` out of `abs`) are already separate parts here.
 */
data class BodySide(val view: String, val viewBox: List<Float>, val outline: String, val base: List<String>, val parts: Map<String, List<String>>) {
    /** Level key of a region on this side, as [MuscleMapFile.regionsOf] names it. */
    fun key(region: String) = "$view:$region"
}

data class BodyFigure(val front: BodySide, val back: BodySide)

/** A split region (muscle-map.json `split`): these path indices of [from] in [view] form the region [name]. */
data class RegionSplit(val name: String, val from: String, val view: String, val paths: Map<String, List<Int>>)

/**
 * Muscle code to region keys "front:region" / "back:region" (muscle-map.json schema 2: a bare region means both views,
 * a "front:" or "back:" prefix one view, a `split` name the listed paths of another region; Content §5.2).
 */
data class MuscleMapFile(val regionsOf: Map<MuscleGroup, List<String>>, val splits: List<RegionSplit>)

/** Segment transform of one frame: x, y, rotation in degrees, optional horizontal scale. */
class SegState(val x: Float, val y: Float, val rot: Float, val sx: Float)

class RigFrame(val segments: Map<String, SegState>, val points: Map<String, FloatArray>, val cables: List<FloatArray>)

class RigEquip(val d: String?, val follows: String?, val rot: Float, val cable: Boolean, val line: Boolean, val soft: Boolean, val thick: Boolean)

/** A muscle mark on a segment: segment, region, along (0..1 of length), side (±1), rx, ry. */
class RigMark(val segment: String, val region: String, val along: Float, val side: Float, val rx: Float, val ry: Float)

class RigPattern(
    val id: String,
    val name: String,
    val front: Boolean,
    val order: List<String>,
    val helper: Set<String>,
    val main: Set<String>,
    val equip: List<RigEquip>,
    val frames: List<RigFrame>,
    val frontMarks: List<RigMark>,
)

class RigData(
    val lengths: Map<String, Float>,
    val widths: Map<String, Map<String, Float>>,
    val widthsFront: Map<String, Map<String, Float>>,
    val marks: List<RigMark>,
    val patterns: Map<String, RigPattern>,
)

object FigureParser {
    private fun JSONObject.floats(key: String): Map<String, Float> = getJSONObject(key).let { o -> o.keys().asSequence().associateWith { o.getDouble(it).toFloat() } }

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else List(a.length()) { a.getString(it) }

    private val VIEWS = listOf("front", "back")

    fun muscleMap(json: String): MuscleMapFile {
        val root = JSONObject(json)
        val m = root.getJSONObject("map")
        val sp = root.optJSONObject("split")
        val splits = if (sp == null) emptyList() else sp.keys().asSequence().map { name ->
            val o = sp.getJSONObject(name)
            val paths = o.getJSONObject("paths").let { p -> p.keys().asSequence().associateWith { k -> p.getJSONArray(k).let { a -> List(a.length()) { a.getInt(it) } } } }
            RegionSplit(name, o.getString("from"), o.getString("view"), paths)
        }.toList()
        fun keys(ref: String): List<String> {
            val view = VIEWS.firstOrNull { ref.startsWith("$it:") }
            val region = if (view == null) ref else ref.removePrefix("$view:")
            val split = splits.firstOrNull { it.name == region }
            return when {
                split != null -> listOf("${split.view}:$region")
                view != null -> listOf("$view:$region")
                else -> VIEWS.map { "$it:$region" }
            }
        }
        val out = HashMap<MuscleGroup, List<String>>()
        MuscleGroup.entries.forEach { g -> if (m.has(g.name)) out[g] = strings(m.getJSONArray(g.name)).flatMap(::keys).distinct() }
        return MuscleMapFile(out, splits)
    }

    private fun side(o: JSONObject, view: String, sex: String, splits: List<RegionSplit>): BodySide {
        val vb = o.getString("vb").trim().split(Regex("\\s+")).map { it.toFloat() }
        val p = o.getJSONObject("parts")
        val parts = LinkedHashMap(p.keys().asSequence().associateWith { strings(p.getJSONArray(it)) })
        splits.filter { it.view == view }.forEach { s ->
            val all = parts[s.from] ?: return@forEach
            val take = s.paths[sex].orEmpty().toSet()
            parts[s.from] = all.filterIndexed { i, _ -> i !in take }
            parts[s.name] = all.filterIndexed { i, _ -> i in take }
        }
        return BodySide(view, vb, o.getString("outline"), strings(o.getJSONArray("base")), parts)
    }

    /** `sex` is "m" or "f" (figure.min.json `views`); [splits] from [muscleMap]. */
    fun body(json: String, sex: String, splits: List<RegionSplit> = emptyList()): BodyFigure {
        val v = JSONObject(json).getJSONObject("views")
        val key = if (v.has(sex)) sex else "f"
        val s = v.getJSONObject(key)
        return BodyFigure(side(s.getJSONObject("front"), "front", key, splits), side(s.getJSONObject("back"), "back", key, splits))
    }

    private fun mark(a: JSONArray) = RigMark(a.getString(0), a.getString(1), a.getDouble(2).toFloat(), a.getDouble(3).toFloat(), a.getDouble(4).toFloat(), a.getDouble(5).toFloat())

    private fun widths(o: JSONObject?): Map<String, Map<String, Float>> =
        if (o == null) emptyMap() else o.keys().asSequence().associateWith { k -> o.floats(k) }

    private fun frame(o: JSONObject): RigFrame {
        val s = o.getJSONObject("s")
        val segs = s.keys().asSequence().associateWith { k ->
            val a = s.getJSONArray(k)
            SegState(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat(), if (a.length() > 3 && !a.isNull(3)) a.getDouble(3).toFloat() else 1f)
        }
        val p = o.optJSONObject("p")
        val points = if (p == null) emptyMap() else p.keys().asSequence().associateWith { k -> p.getJSONArray(k).let { a -> floatArrayOf(a.getDouble(0).toFloat(), a.getDouble(1).toFloat()) } }
        val c = o.optJSONArray("c")
        val cables = if (c == null) emptyList() else List(c.length()) { i -> c.getJSONArray(i).let { a -> FloatArray(4) { a.getDouble(it).toFloat() } } }
        return RigFrame(segs, points, cables)
    }

    private fun equip(o: JSONObject) = RigEquip(
        d = o.optString("d").takeIf { it.isNotEmpty() }, follows = o.optString("f").takeIf { it.isNotEmpty() }, rot = o.optDouble("rot", 0.0).toFloat(),
        cable = o.optBoolean("cable", false), line = o.optBoolean("line", false), soft = o.optBoolean("soft", false), thick = o.optBoolean("thick", false),
    )

    /** [localJson] = content/figures/rig-local.json (C-08b): more patterns in the same format, drawn with the XNK lengths and marks. */
    fun rig(json: String, localJson: String? = null): RigData {
        val r = JSONObject(json)
        val marks = r.getJSONArray("muscles").let { a -> List(a.length()) { mark(a.getJSONArray(it)) } }
        val pats = r.getJSONObject("pats")
        localJson?.let { JSONObject(it).getJSONObject("pats") }?.let { lp -> lp.keys().forEach { k -> if (!pats.has(k)) pats.put(k, lp.getJSONObject(k)) } }
        val patterns = pats.keys().asSequence().associateWith { id ->
            val p = pats.getJSONObject(id)
            val mus = p.getJSONArray("muscles")
            val frames = p.getJSONArray("frames")
            val eq = p.getJSONArray("eq")
            val fm = p.optJSONArray("fm")
            RigPattern(
                id, p.optString("name", id), p.optString("view") == "front",
                p.optJSONArray("order")?.let { strings(it) } ?: listOf("thigh", "shin", "foot", "torso", "head", "ua", "fa"),
                strings(mus.getJSONArray(1)).toSet(), strings(mus.getJSONArray(0)).toSet(),
                List(eq.length()) { equip(eq.getJSONObject(it)) }, List(frames.length()) { frame(frames.getJSONObject(it)) },
                if (fm == null) emptyList() else List(fm.length()) { mark(fm.getJSONArray(it)) },
            )
        }
        return RigData(r.floats("len"), widths(r.getJSONObject("w")), widths(r.optJSONObject("wf")), marks, patterns)
    }

    /** `rig` of each exercise in `content/exercises.json` (null = no rig; Content §5.1). */
    fun rigIds(exercisesJson: String): Map<String, String> {
        val a = JSONObject(exercisesJson).getJSONArray("exercises")
        val out = HashMap<String, String>()
        for (i in 0 until a.length()) {
            val e = a.getJSONObject(i)
            if (!e.isNull("rig")) out[e.getString("id")] = e.getString("rig")
        }
        return out
    }
}

/** Where the bytes come from: assets in the app, files in tests. */
fun interface FigureSource { fun text(path: String): String }

/** Loads each figure file once, on first use, off the main thread (the caller picks the thread). */
class FigureRepository(private val source: FigureSource) {
    private val muscleMapFile by lazy { FigureParser.muscleMap(source.text(MAP)) }
    private val bodies = HashMap<String, BodyFigure>()
    private val rigData by lazy { FigureParser.rig(source.text(RIG), source.text(RIG_LOCAL)) }
    private val rigIds by lazy { FigureParser.rigIds(source.text(EXERCISES)) }

    val regionsOf: Map<MuscleGroup, List<String>> get() = muscleMapFile.regionsOf

    @Synchronized fun body(sex: String): BodyFigure = bodies.getOrPut(sex) { FigureParser.body(source.text(FIGURE), sex, muscleMapFile.splits) }

    /** The rig of an exercise, only when the exercise has one (`rig` in exercises.json: an XNK or local pattern); otherwise null (map and cues only). */
    fun rigFor(exerciseId: String): RigPattern? = rigIds[exerciseId]?.let { rigData.patterns[it] }
    fun rigData(): RigData = rigData

    companion object {
        const val MAP = "figures/muscle-map.json"
        const val FIGURE = "figures/xnk-source/figure.min.json"
        const val RIG = "figures/xnk-source/rig.min.json"
        const val RIG_LOCAL = "figures/rig-local.json"
        const val EXERCISES = "content/exercises.json"
    }
}
