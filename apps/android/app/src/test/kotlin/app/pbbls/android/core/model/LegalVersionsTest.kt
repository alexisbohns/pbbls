package app.pbbls.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The versions Android records must be the versions of the documents it links
 * to. The documents live in the web workspace (`apps/web/docs`), so a policy
 * bump that forgets Android fails this test rather than recording the wrong
 * version into an accountability ledger. Gradle runs unit tests with the
 * module directory (`apps/android/app`) as the working directory.
 */
class LegalVersionsTest {
    private fun frontmatterVersion(path: String): String {
        val file = File("../../web/docs/$path")
        check(file.exists()) { "expected the legal document at ${file.absolutePath}" }
        return file
            .readLines()
            .dropWhile { it.trim() != "---" }
            .drop(1)
            .takeWhile { it.trim() != "---" }
            .first { it.startsWith("version:") }
            .substringAfter("version:")
            .trim()
    }

    @Test
    fun `terms version matches the published Terms of Service`() {
        assertEquals(frontmatterVersion("terms/en.md"), LegalVersions.TERMS)
    }

    @Test
    fun `privacy version matches the published Privacy Policy`() {
        assertEquals(frontmatterVersion("privacy/en.md"), LegalVersions.PRIVACY)
    }

    @Test
    fun `the French documents carry the same versions`() {
        assertEquals(frontmatterVersion("terms/fr.md"), LegalVersions.TERMS)
        assertEquals(frontmatterVersion("privacy/fr.md"), LegalVersions.PRIVACY)
    }
}
