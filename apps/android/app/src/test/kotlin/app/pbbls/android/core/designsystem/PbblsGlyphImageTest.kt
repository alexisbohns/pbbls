package app.pbbls.android.core.designsystem

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.core.designsystem.glyph.GlyphCatalog
import app.pbbls.android.core.designsystem.glyph.GlyphForm
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PbblsGlyphImageTest {
    @Test
    fun theRawResourceLoadsThroughTheContext() {
        GlyphCatalog.installSource {
            ApplicationProvider.getApplicationContext<Context>().resources.openRawResource(app.pbbls.android.R.raw.pbbls_glyphs)
        }
        assertTrue(GlyphCatalog.glyph(PbblsGlyph.HOME).paths.isNotEmpty())
        assertTrue(GlyphCatalog.vector(PbblsGlyph.HOME, GlyphForm.SOLID).root.size > 0)
    }
}
