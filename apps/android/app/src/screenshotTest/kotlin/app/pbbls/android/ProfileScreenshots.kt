package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.RippleSummary
import app.pbbls.android.features.profile.components.YouCounters
import app.pbbls.android.features.profile.components.YouGlyphsSection
import app.pbbls.android.features.profile.components.YouHeader
import com.android.tools.screenshot.PreviewTest
import java.time.OffsetDateTime

/**
 * Profile surface previews (#567): banner (glyph + carve-placeholder
 * variants), the Stats card in loaded and loading states, the Collections
 * carousel (filled + empty variants), and the logout pill — light and dark.
 * ProfileScreen/SettingsScreen themselves read services, so the review
 * surface is this pure-component gallery plus the on-device pass.
 */
private val previewStrokes =
    listOf(
        GlyphStroke(
            d = "M40,40 C80,20 120,20 160,40 S180,120 160,160 S80,180 40,160 S20,80 40,40",
            width = 6.0,
        ),
    )

@Composable
private fun ProfileGallery() {
    Column(
        modifier =
            Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        YouHeader(
            displayName = "Alexis",
            memberSince = OffsetDateTime.parse("2026-04-12T09:00:00+00:00"),
            glyphStrokes = previewStrokes,
            ripple = RippleSummary(rippleLevel = 3, pebbles28d = 11, activeToday = true),
            onOpenSettings = {},
        )
        YouHeader(
            displayName = "Pebbler",
            memberSince = OffsetDateTime.parse("2026-07-01T09:00:00+00:00"),
            glyphStrokes = null,
            ripple = null,
            onOpenSettings = {},
        )
        YouCounters(daysPracticed = 112, pebbles = 318, karma = 1240)
        // Loading state: em-dashes.
        YouCounters(daysPracticed = null, pebbles = null, karma = null)
        YouGlyphsSection(glyphsCarved = 12, glyphsOwned = 5, textInset = 4.dp, onOpen = {})
        // Counts not served yet: the lines read without a number.
        YouGlyphsSection(glyphsCarved = null, glyphsOwned = null, textInset = 4.dp, onOpen = {})
    }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@Composable
fun ProfileComponentsLight() {
    PebblesTheme { ProfileGallery() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ProfileComponentsDark() {
    PebblesTheme { ProfileGallery() }
}

// The real ProfileScreen hosts these in PebblesScreen's readable column
// (#855). The gallery wraps its width, so the wide renders place it in that
// column explicitly; the phone renders above stay untouched.
@PreviewTest
@PreviewWide
@Composable
fun ProfileComponentsWide() {
    PebblesTheme {
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            Box(Modifier.readableWidth()) { ProfileGallery() }
        }
    }
}
