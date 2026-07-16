package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.profile.components.ProfileBanner
import app.pbbls.android.features.profile.components.ProfileCollectionsCard
import app.pbbls.android.features.profile.components.ProfileLogoutButton
import app.pbbls.android.features.profile.components.ProfileStatsCard
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.features.profile.models.CollectionMode
import app.pbbls.android.features.shared.ripples.RippleSummary
import app.pbbls.android.theme.PebblesTheme
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

private val previewCollections =
    listOf(
        Collection(id = "c1", name = "Reading list", mode = CollectionMode.PACK, pebbleCount = 7),
        Collection(id = "c2", name = "Trips", mode = null, pebbleCount = 1),
        Collection(id = "c3", name = "Wins", mode = CollectionMode.TRACK, pebbleCount = 23),
    )

@Composable
private fun ProfileGallery() {
    val system = PebblesTheme.colors.system
    Column(
        modifier =
            Modifier
                .background(system.background)
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ProfileBanner(
            displayName = "Alexis",
            memberSince = OffsetDateTime.parse("2026-04-12T09:00:00+00:00"),
            glyphStrokes = previewStrokes,
        )
        ProfileBanner(
            displayName = "Pebbler",
            memberSince = OffsetDateTime.parse("2026-07-01T09:00:00+00:00"),
            glyphStrokes = null,
        )
        ProfileStatsCard(
            ripple = RippleSummary(rippleLevel = 3, pebbles28d = 11, activeToday = true),
            assiduity = List(28) { it % 3 != 0 },
            daysPracticed = 42,
            pebbles = 137,
            karma = 1200,
        )
        // Loading state: em-dashes, level-0 badge, empty grid.
        ProfileStatsCard(
            ripple = null,
            assiduity = null,
            daysPracticed = null,
            pebbles = null,
            karma = null,
        )
        ProfileCollectionsCard(collections = previewCollections, hasLoaded = true)
        ProfileCollectionsCard(collections = emptyList(), hasLoaded = true)
        ProfileLogoutButton(onClick = {}, modifier = Modifier.fillMaxWidth())
    }
}

@PreviewTest
@Preview(showBackground = true)
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
