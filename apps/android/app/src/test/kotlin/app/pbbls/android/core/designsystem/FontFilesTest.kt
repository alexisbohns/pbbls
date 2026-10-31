package app.pbbls.android.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Pins the two Inclusive Sans files byte for byte.
 *
 * They are the maintainer's Inclusive Sans 2.004 release build, the same files
 * iOS and web bundle, so all three surfaces render the same font program
 * (spec D4 of `2026-10-31-android-home-and-you-design.md`). The Google Fonts
 * build that shipped before differs in its kerning tables, and a later Theme
 * Builder re-export would silently swap it back: this test is what notices.
 *
 * The Gradle test task's working directory is the module root
 * (`apps/android/app`), so the paths are relative to it, as in
 * `LocalizationParityTest`.
 */
class FontFilesTest {
    private fun sha1(relativePath: String): String {
        val file = File(relativePath)
        check(file.exists()) { "Missing font file: ${file.absolutePath}" }
        val digest = MessageDigest.getInstance("SHA-1").digest(file.readBytes())
        return digest.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun inclusiveSansIsTheSharedReleaseBuild() {
        assertEquals(
            "7c3e44eb5d0cb356fc8b5389578564cc345a1cfa",
            sha1("src/main/res/font/inclusive_sans.ttf"),
        )
    }

    @Test
    fun inclusiveSansItalicIsTheSharedReleaseBuild() {
        assertEquals(
            "c4fdd08a8ffb41239938920bb19b522cc7cb280e",
            sha1("src/main/res/font/inclusive_sans_italic.ttf"),
        )
    }
}
