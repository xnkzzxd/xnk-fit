package app.xnkfit.ui.bantuan

import app.xnkfit.data.prefs.WelcomeTab
import app.xnkfit.domain.RepoFiles
import app.xnkfit.domain.rules.toneProblems
import app.xnkfit.ui.nav.Destination
import app.xnkfit.ui.welcomeTabOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reads the help, settings and privacy strings straight from the resource files (R_TONE, R_DISCLAIMER). */
class HelpContentTest {
    private val files = listOf("strings_help.xml", "strings_settings.xml", "strings_privacy.xml")

    private fun strings(): Map<String, String> = files.map { RepoFiles.file("app/src/main/res/values/$it") }.filter { it.isFile }
        .flatMap { f ->
            Regex("<string name=\"([^\"]+)\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(f.readText())
                .map { it.groupValues[1] to it.groupValues[2] }.toList()
        }.toMap()

    @Test fun R_TONE_noBannedWordsInHelpSettingsOrPrivacyText() {
        val bad = strings().filter { toneProblems(it.value, isReminder = false).isNotEmpty() }
        assertTrue("tone problems: $bad", bad.isEmpty())
    }

    @Test fun R_DISCLAIMER_isInBantuan() {
        val s = strings()
        assertTrue(s.getValue("help_disclaimer_title").contains("Bukan pengganti dokter atau ahli gizi"))
    }

    @Test fun everyTabHasOneWelcomeCard_andTheAddActionHasNone() {
        assertEquals(WelcomeTab.entries.toSet(), HelpContent.welcome.keys)
        assertNull(welcomeTabOf(Destination.ADD))
        Destination.entries.filter { it != Destination.ADD }.forEach { assertTrue(welcomeTabOf(it) != null) }
    }

    @Test fun glossaryCoversRirSupersetSetKindsAndMacros() {
        val names = (HelpContent.workoutTerms + HelpContent.foodTerms).size
        assertTrue(names >= 10)
        val s = strings()
        listOf("help_term_rir", "help_term_superset", "help_term_drop", "help_term_failure", "help_term_macro").forEach {
            assertTrue(it, s.containsKey(it) && s.containsKey(it + "_def"))
        }
    }

    @Test fun everySourceHasAReferenceBehindItsMark() {
        val s = strings()
        HelpContent.sources.forEach { src -> assertFalse(src.short == src.full) }
        assertEquals(HelpContent.sources.size, s.keys.count { it.startsWith("help_src_") && it.endsWith("_full") })
    }

    @Test fun feedbackAddressIsNeverGuessed() {
        assertTrue(!FeedbackConfig.available || FeedbackConfig.FORM_URL.startsWith("https://"))
    }
}
