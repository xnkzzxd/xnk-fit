package app.xnkfit.domain.training

import app.xnkfit.domain.RepoFiles
import app.xnkfit.domain.rules.ActivityLevel
import app.xnkfit.domain.rules.GateResult
import app.xnkfit.domain.rules.Goal
import app.xnkfit.domain.rules.Profile
import app.xnkfit.domain.rules.Screening
import app.xnkfit.domain.rules.Sex
import app.xnkfit.domain.rules.evaluateTargets
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import java.io.File
import java.util.Locale

/** Reads `content/exercises.json` on the plain JVM (no org.json there) and builds the generator inputs. */
internal object TrainingTestSupport {
    val library: List<Exercise> by lazy { parseLibrary(RepoFiles.text("content/exercises.json")) }

    const val YEAR = 2026
    const val TODAY = 20_000L // an epoch day; the generator has no clock

    private val man30 = Profile(Sex.LAKI_LAKI, 1996, 172.0, 70.0, ActivityLevel.JARANG, Goal.JAGA)
    private val clear = Screening(false, false, false, false, false, null, false)

    val readyGate: GateResult = evaluateTargets(man30, clear, YEAR)
    val riskGate: GateResult = evaluateTargets(man30, clear.copy(ongoingInjury = true), YEAR)
    val under18Gate: GateResult = evaluateTargets(man30.copy(birthYear = 2010), clear, YEAR)
    val screeningGate: GateResult = evaluateTargets(man30, null, YEAR)

    fun profile(gate: GateResult = readyGate, experience: Experience = Experience.BARU_MULAI, days: Int = 3, minutes: Int = 45) =
        TrainingProfile(gate, experience, days, minutes)

    fun generated(
        profile: TrainingProfile,
        equipment: Equipment,
        focus: GoalFocus = GoalFocus.OTOT,
        history: TrainingHistory = TrainingHistory(),
    ): Program = when (val r = generate(profile, equipment, focus, history, library, TODAY)) {
        is GenerateResult.Generated -> r.program
        is GenerateResult.Blocked -> error("blocked: ${r.gate}")
    }

    /** Stable text form of a program for golden files. */
    fun render(p: Program): String = buildString {
        appendLine("setting=${p.setting.code} level=${p.level} focus=${p.focus} source=${p.source} rulesVersion=${p.rulesVersion}")
        p.days.forEach { d ->
            appendLine("day ${d.position + 1} ${d.name} ~${d.estMinutes} min")
            d.items.forEachIndexed { i, it ->
                val load = if (it.usesBand) "band" else it.loadKind.name.lowercase(Locale.ROOT)
                val fill = it.fillFor?.let { g -> " for=$g" } ?: ""
                appendLine("  ${i + 1}. ${it.exerciseId} ${it.pattern} ${it.role}$fill ${it.sets}x${it.repMin}-${it.repMax} rest=${it.restSec}s $load")
            }
        }
        appendLine("missing: " + p.missing.joinToString { "${it.pattern} needs ${it.needs.joinToString("+")}" }.ifEmpty { "-" })
        appendLine("under minimum: " + p.underMinimum.joinToString().ifEmpty { "-" })
        appendLine("weekly sets:")
        p.weeklySets.forEach { (g, v) -> appendLine("  $g ${String.format(Locale.ROOT, "%.1f", v)} ${volumeStatus(g, p.level, v)}") }
    }

    /** Compares with `app/src/test/resources/golden/training/<name>.txt`; a missing file is written and the test fails. */
    fun assertGolden(name: String, actual: String) {
        val f = File(RepoFiles.file("app/src/test/resources/golden/training"), "$name.txt")
        if (!f.isFile) {
            f.parentFile.mkdirs()
            f.writeText(actual)
            fail("golden file written, check it and run again: ${f.path}")
        }
        assertEquals("golden ${f.name} (delete the file to rewrite it)", f.readText(), actual)
    }

    // ------------------------------------------------------------ minimal JSON reader (test only)

    fun parseLibrary(text: String): List<Exercise> {
        @Suppress("UNCHECKED_CAST")
        val root = Json(text).value() as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val rows = root["exercises"] as List<Map<String, Any?>>
        return rows.filter { it["active"] != false }.map { o ->
            @Suppress("UNCHECKED_CAST")
            val muscles = o["muscles"] as Map<String, List<String>>
            @Suppress("UNCHECKED_CAST")
            fun strings(key: String) = (o[key] as List<String>).toSet()
            Exercise(
                id = o["id"] as String,
                pattern = Pattern.valueOf(o["pattern"] as String),
                level = (o["level"] as Number).toInt(),
                settings = strings("settings"),
                equipment = strings("equipment"),
                main = muscles.getValue("main").map { MuscleGroup.valueOf(it) }.toSet(),
                helper = muscles.getValue("helper").map { MuscleGroup.valueOf(it) }.toSet(),
                variantOf = o["variantOf"] as String?,
                hintId = o["hint_id"] as String?,
            )
        }
    }

    private class Json(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> if (c == '-' || c.isDigit()) num() else error("bad json at $i")
            }
        }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>(); i++; ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws(); val k = str(); ws(); i++ // ':'
                m[k] = value(); ws()
                if (s[i++] == '}') return m
            }
        }
        fun arr(): List<Any?> {
            val l = ArrayList<Any?>(); i++; ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value(); ws()
                if (s[i++] == ']') return l
            }
        }
        fun str(): String {
            val b = StringBuilder(); i++
            while (s[i] != '"') {
                if (s[i] == '\\') {
                    i++
                    when (val e = s[i]) {
                        'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> b.append('\r')
                        'b' -> b.append('\b'); 'f' -> b.append('\u000c')
                        'u' -> { b.append(s.substring(i + 1, i + 5).toInt(16).toChar()); i += 4 }
                        else -> b.append(e)
                    }
                } else b.append(s[i])
                i++
            }
            i++
            return b.toString()
        }
        fun num(): Number {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val t = s.substring(start, i)
            return if (t.any { it in ".eE" }) t.toDouble() else t.toLong()
        }
    }
}
