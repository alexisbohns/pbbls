package app.pbbls.android.core.designsystem.glyph

import java.io.File

/** Repository files the glyph tests read in place (Gradle runs unit tests from `apps/android/app`). */
object GlyphTestData {
    fun repoFile(path: String): File = File("../../../$path")

    /** The generated resource, the same bytes the app bundles. */
    val resource: File get() = File("src/main/res/raw/pbbls_glyphs.json")
}
