package app.xnkfit.ui.pengaturan

import app.xnkfit.domain.RepoFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PV-4: the in-app text and docs/Privacy-Policy.md (the Play and Health Connect text) must be the same words. */
class PrivacyTextTest {
    private val strings = Regex("<string name=\"([^\"]+)\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        .findAll(RepoFiles.text("app/src/main/res/values/strings_privacy.xml")).associate { it.groupValues[1] to it.groupValues[2] }

    @Test fun docAndAppSayTheSame() {
        val doc = RepoFiles.text("docs/Privacy-Policy.md")
        for (i in 1..9) {
            assertTrue("title $i", doc.contains("## " + strings.getValue("privacy_${i}_title")))
            assertTrue("body $i", doc.contains(strings.getValue("privacy_${i}_body")))
        }
        assertEquals(9, PrivacyText.sections.size)
    }

    @Test fun theFactsPV1ToPV3AreStated() {
        val all = strings.values.joinToString(" ").lowercase()
        listOf("tidak ada iklan", "analitik", "tidak membawa data anda", "google drive anda sendiri", "13 tahun", "hapus semua data").forEach {
            assertTrue(it, all.contains(it))
        }
    }

    @Test fun noPermissionIsPromisedThatTheManifestLacks() {
        val manifest = RepoFiles.text("app/src/main/AndroidManifest.xml")
        listOf("POST_NOTIFICATIONS", "CAMERA", "READ_STEPS", "INTERNET", "SCHEDULE_EXACT_ALARM").forEach { assertTrue(it, manifest.contains(it)) }
    }
}
