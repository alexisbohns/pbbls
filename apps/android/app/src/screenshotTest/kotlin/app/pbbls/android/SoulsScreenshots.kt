package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.glyph.models.SystemGlyph
import app.pbbls.android.features.profile.components.ProfileShortcutsRow
import app.pbbls.android.features.profile.models.SoulWithGlyph
import app.pbbls.android.features.shared.SoulItem
import app.pbbls.android.features.shared.SoulItemCase
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest

/**
 * Souls-management previews (#568): the shared [SoulItem] in all four #459
 * cases (the same gallery iOS previews) and the Profile shortcuts row —
 * light and dark. The souls screens themselves read services, so the review
 * surface is this pure-component gallery plus the on-device pass.
 */
private val previewSoul =
    SoulWithGlyph(
        id = "s1",
        name = "Molly",
        glyphId = SystemGlyph.DEFAULT,
        glyph =
            Glyph(
                id = SystemGlyph.DEFAULT,
                name = null,
                strokes =
                    listOf(
                        GlyphStroke(
                            d = "M30,30 C60,10 140,10 170,30 S190,140 170,170 S60,190 30,170 S10,60 30,30",
                            width = 6.0,
                        ),
                    ),
                viewBox = "0 0 200 200",
            ),
        pebblesCount = 12,
    )

@Composable
private fun SoulsGallery() {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            Modifier
                .background(system.background)
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SoulItem(case = SoulItemCase.SELECTED, soul = previewSoul, count = 12)
            SoulItem(case = SoulItemCase.UNSELECTED, soul = previewSoul, count = 9)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SoulItem(case = SoulItemCase.DEFAULT, soul = previewSoul, count = 3)
            SoulItem(case = SoulItemCase.CREATE, soul = null, count = null)
        }
        ProfileShortcutsRow(onOpenSouls = {})
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun SoulsGalleryLight() {
    PebblesTheme { SoulsGallery() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SoulsGalleryDark() {
    PebblesTheme { SoulsGallery() }
}
