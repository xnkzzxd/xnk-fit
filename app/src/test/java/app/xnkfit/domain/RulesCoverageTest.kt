package app.xnkfit.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Design §13 and PR-6, checked from the files themselves:
 * 1. every rule ID in Rules.md has a JUnit test whose name starts with the ID;
 * 2. a ⚠ rule's tests are all `@Ignore("unverified")`; every other rule has at least one test that runs;
 * 3. every constant in `domain` is named after a Rules.md ID (or is a listed unit helper);
 * 4. `domain` code has no number outside those constants (no magic numbers) and no Android import.
 */
class RulesCoverageTest {
    private val rulesText = RepoFiles.text("docs/Rules.md")

    /** ID → true when the row is ⚠ (not final). */
    private val rules: Map<String, Boolean> = rulesText.lines()
        .filter { it.startsWith("| R_") }
        .associate { line ->
            val id = line.removePrefix("| ").substringBefore(" |").trim()
            id to line.contains("⚠")
        }

    private val ids = rules.keys.sortedByDescending { it.length }

    private fun sources(dir: String): List<File> =
        RepoFiles.file(dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private data class TestFun(val name: String, val ignored: Boolean)

    private val testFunctions: List<TestFun> by lazy {
        sources("app/src/test/java").flatMap { f ->
            val lines = f.readLines()
            lines.mapIndexedNotNull { i, line ->
                val m = Regex("""^\s*fun\s+([A-Za-z0-9_]+)\s*\(""").find(line) ?: return@mapIndexedNotNull null
                val before = lines.subList(maxOf(0, i - 3), i)
                if (before.none { it.trim() == "@Test" }) return@mapIndexedNotNull null
                TestFun(m.groupValues[1], before.any { it.trim() == "@Ignore(\"unverified\")" })
            }
        }
    }

    /** The rule ID a test name refers to: the part before the first `_` followed by a lower-case letter. */
    private fun idOf(testName: String): String? {
        val stem = testName.removePrefix("golden_")
        val cut = Regex("_[a-z0-9]").find(stem)?.range?.first ?: return null
        return stem.substring(0, cut).takeIf { it in rules }
    }

    @Test
    fun everyRuleIdHasATest() {
        assertTrue("Rules.md parse found too few IDs: ${rules.size}", rules.size >= 80)
        val tested = testFunctions.mapNotNull { idOf(it.name) }.toSet()
        val missing = rules.keys - tested
        assertTrue("Rules IDs without a test: $missing", missing.isEmpty())
    }

    @Test
    fun unverifiedRulesAreIgnoredAndVerifiedRulesRun() {
        val byId = testFunctions.groupBy { idOf(it.name) }
        val problems = mutableListOf<String>()
        rules.forEach { (id, unverified) ->
            val tests = byId[id].orEmpty()
            if (unverified && tests.any { !it.ignored }) problems += "$id is ⚠ but has a running test"
            if (!unverified && tests.none { !it.ignored }) problems += "$id has no running test"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun ignoredTestsAreOnlyForUnverifiedRulesOrOpenGaps() {
        val wrong = testFunctions.filter { it.ignored }.filterNot { t ->
            val id = idOf(t.name)
            (id != null && rules[id] == true) || Regex("^G[0-9]+_").containsMatchIn(t.name)
        }
        assertTrue("ignored without a ⚠ rule: $wrong", wrong.isEmpty())
    }

    private val unitHelpers = setOf("CM_PER_M", "FLOAT_TOLERANCE", "HALF", "MINUTES_PER_HOUR", "WORD_SPLIT")

    @Test
    fun everyDomainConstantIsNamedAfterARule() {
        val bad = mutableListOf<String>()
        sources("app/src/main/java/app/xnkfit/domain").forEach { f ->
            Regex("""(?:const\s+)?val\s+([A-Z][A-Z0-9_]+)\s*[:=]""").findAll(f.readText()).forEach { m ->
                val name = m.groupValues[1]
                val ok = name in unitHelpers || ids.any { name == it || name.startsWith(it + "_") }
                if (!ok) bad += "${f.name}: $name"
            }
        }
        assertTrue("constants without a Rules ID prefix: $bad", bad.isEmpty())
    }

    @Test
    fun domainHasNoMagicNumbersAndNoAndroid() {
        val bad = mutableListOf<String>()
        sources("app/src/main/java/app/xnkfit/domain").forEach { f ->
            var inBlockComment = false
            f.readLines().forEachIndexed { i, raw ->
                var line = raw
                if (inBlockComment) {
                    if (!line.contains("*/")) return@forEachIndexed
                    line = line.substringAfter("*/")
                    inBlockComment = false
                }
                if (line.contains("/*")) {
                    inBlockComment = !line.contains("*/")
                    line = line.substringBefore("/*")
                }
                line = line.substringBefore("//").replace(Regex("\"[^\"]*\""), "\"\"")
                if (line.contains("import android")) bad += "${f.name}:${i + 1} Android import"
                if (Regex("""^\s*(private\s+|internal\s+)?(const\s+)?val\s+[A-Z][A-Z0-9_]+""").containsMatchIn(line)) {
                    return@forEachIndexed // a named constant: the one place a number may be written
                }
                Regex("""(?<![A-Za-z0-9_.])\d+(\.\d+)?""").findAll(line).forEach { m ->
                    if (m.value !in setOf("0", "1", "0.0", "1.0")) bad += "${f.name}:${i + 1} ${m.value}"
                }
            }
        }
        assertTrue("numbers outside named constants: $bad", bad.isEmpty())
    }
}
