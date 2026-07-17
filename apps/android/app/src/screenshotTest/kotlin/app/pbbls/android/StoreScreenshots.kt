package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.glyph.models.GlyphGridItem
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.glyph.store.GlyphDetailDrawerContent
import app.pbbls.android.features.glyph.store.GlyphTab
import app.pbbls.android.features.glyph.store.GlyphTabBar
import app.pbbls.android.features.glyph.store.SlideToConfirm
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest
import java.time.OffsetDateTime

/**
 * Store previews (M43 #586): the floating tab bar, the slide-to-confirm in
 * enabled/disabled states, and the drawer body in swap and owned states —
 * light and dark. The list screen reads services; these pure pieces are the
 * review surface.
 */
private val previewItem =
    GlyphGridItem(
        glyph =
            Glyph(
                id = "g1",
                name = "Wave",
                strokes =
                    listOf(
                        GlyphStroke(
                            d = "M30,100 Q70,40 100,100 Q130,160 170,100",
                            width = 6.0,
                        ),
                    ),
                viewBox = "0 0 200 200",
                userId = "creator",
            ),
        price = 13,
        owned = false,
        createdAt = OffsetDateTime.parse("2026-07-01T09:00:00+00:00"),
        acquiredAt = null,
    )

@Composable
private fun StoreGallery() {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            Modifier
                .background(system.background)
                .padding(24.dp)
                .width(360.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        GlyphTabBar(selection = GlyphTab.MINE, onSelect = {})
        SlideToConfirm(cost = 13, enabled = true, onConfirm = { true })
        SlideToConfirm(cost = 99, enabled = false, onConfirm = { true })
    }
}

@Composable
private fun DrawerGallery(isOwned: Boolean) {
    val system = PebblesTheme.colors.system
    Column(modifier = Modifier.background(system.background).width(400.dp)) {
        GlyphDetailDrawerContent(
            item = previewItem,
            isOwned = isOwned,
            acquiredAt = if (isOwned) OffsetDateTime.parse("2026-07-16T10:00:00+00:00") else null,
            currentBalance = 21,
            isBuying = false,
            errorRes = null,
            onConfirm = { true },
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun StoreGalleryLight() {
    PebblesTheme { StoreGallery() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun StoreGalleryDark() {
    PebblesTheme { StoreGallery() }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun DrawerSwapLight() {
    PebblesTheme { DrawerGallery(isOwned = false) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun DrawerOwnedDark() {
    PebblesTheme { DrawerGallery(isOwned = true) }
}
