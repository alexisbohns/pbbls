package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.path.DeleteConfirmDialog
import app.pbbls.android.features.path.models.DomainRef
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.EmotionRef
import app.pbbls.android.features.path.models.PebbleCollection
import app.pbbls.android.features.path.models.PebbleDetail
import app.pbbls.android.features.path.models.Visibility
import app.pbbls.android.features.path.read.PebbleReadView
import app.pbbls.android.features.profile.models.SoulRow
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest
import java.time.OffsetDateTime

/**
 * Previews for the pebble detail read surface (#540): the pure [PebbleReadView]
 * across its variants — full (description + souls + domain + collection), glyph
 * souls, minimal, and the no-domain / no-render fallback — plus the destructive
 * [DeleteConfirmDialog]. Light and dark, driven with fixture [PebbleDetail]s so
 * no services are needed. The stateful `PebbleDetailScreen` (service-backed) is
 * deliberately not previewed.
 */
private val previewPalette: EmotionPalette =
    requireNotNull(
        EmotionPalette.fromHex(
            primaryHex = "#7B5E99FF",
            secondaryHex = "#AE91CCFF",
            lightHex = "#F2EFF5FF",
            surfaceHex = "#7B5E991A",
        ),
    )

// Simple, in-range (0..200) glyph strokes so each soul cell carves a distinct mark.
private val waveStrokes: List<GlyphStroke> =
    listOf(
        GlyphStroke(d = "M 20 120 Q 100 40 180 120", width = 6.0),
    )

private val crossStrokes: List<GlyphStroke> =
    listOf(
        GlyphStroke(d = "M 50 50 L 150 150", width = 6.0),
        GlyphStroke(d = "M 150 50 L 50 150", width = 6.0),
    )

private val peakStrokes: List<GlyphStroke> =
    listOf(
        GlyphStroke(d = "M 40 150 L 100 50 L 160 150", width = 6.0),
    )

private fun soul(
    name: String,
    strokes: List<GlyphStroke>,
): PebbleDetail.SoulWrapper =
    PebbleDetail.SoulWrapper(
        SoulRow(
            id = name,
            name = name,
            glyphId = "g-$name",
            glyph = Glyph(id = "g-$name", strokes = strokes, viewBox = "0 0 200 200"),
        ),
    )

private fun detail(
    name: String,
    description: String?,
    intensity: Int,
    positiveness: Int,
    renderSvg: String?,
    domains: List<PebbleDetail.DomainWrapper> = emptyList(),
    souls: List<PebbleDetail.SoulWrapper> = emptyList(),
    collections: List<PebbleDetail.CollectionWrapper> = emptyList(),
): PebbleDetail =
    PebbleDetail(
        id = name,
        name = name,
        description = description,
        happenedAt = OffsetDateTime.parse("2026-07-08T14:23:00+00:00"),
        intensity = intensity,
        positiveness = positiveness,
        visibility = Visibility.PRIVATE,
        renderSvg = renderSvg,
        emotion = EmotionRef(id = "e1", slug = "joyful", name = "Joyful"),
        pebbleDomains = domains,
        pebbleSouls = souls,
        collectionPebbles = collections,
    )

private val fullDetail: PebbleDetail =
    detail(
        name = "Morning walk",
        description = "Long loop around the park before work. The light was unreal.",
        intensity = 2,
        positiveness = 0,
        renderSvg = PebbleSvgFixtures.mediumNeutral,
        domains =
            listOf(
                PebbleDetail.DomainWrapper(DomainRef(id = "work", slug = "work", name = "Work")),
            ),
        collections =
            listOf(
                PebbleDetail.CollectionWrapper(PebbleCollection(id = "Wins", name = "Wins")),
            ),
        souls =
            listOf(
                soul("Alex", waveStrokes),
                soul("Sam", crossStrokes),
            ),
    )

private val glyphSoulsDetail: PebbleDetail =
    detail(
        name = "Studio session",
        description = "Three of us sketching glyphs for the new set.",
        intensity = 3,
        positiveness = 1,
        renderSvg = PebbleSvgFixtures.largeHighlight,
        domains =
            listOf(
                PebbleDetail.DomainWrapper(DomainRef(id = "hobbies", slug = "hobbies", name = "Hobbies")),
            ),
        souls =
            listOf(
                soul("Wave", waveStrokes),
                soul("Cross", crossStrokes),
                soul("Peak", peakStrokes),
            ),
    )

private val minimalDetail: PebbleDetail =
    detail(
        name = "Quiet afternoon",
        description = null,
        intensity = 1,
        positiveness = 1,
        renderSvg = PebbleSvgFixtures.smallHighlight,
        domains =
            listOf(
                PebbleDetail.DomainWrapper(DomainRef(id = "friends", slug = "friends", name = "Friends")),
            ),
    )

private val noDomainDetail: PebbleDetail =
    detail(
        name = "Rough morning",
        description = "Nothing landed the way I wanted it to.",
        intensity = 2,
        positiveness = -1,
        renderSvg = null,
    )

@Composable
private fun DetailPreview(detail: PebbleDetail) {
    val system = PebblesTheme.colors.system
    PebbleReadView(
        detail = detail,
        palette = previewPalette,
        modifier = Modifier.fillMaxSize().background(system.background),
    )
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun PebbleDetailFullLight() {
    PebblesTheme { DetailPreview(fullDetail) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleDetailFullDark() {
    PebblesTheme { DetailPreview(fullDetail) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun PebbleDetailGlyphSoulsLight() {
    PebblesTheme { DetailPreview(glyphSoulsDetail) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleDetailGlyphSoulsDark() {
    PebblesTheme { DetailPreview(glyphSoulsDetail) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun PebbleDetailMinimalLight() {
    PebblesTheme { DetailPreview(minimalDetail) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleDetailMinimalDark() {
    PebblesTheme { DetailPreview(minimalDetail) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun PebbleDetailNoDomainLight() {
    PebblesTheme { DetailPreview(noDomainDetail) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleDetailNoDomainDark() {
    PebblesTheme { DetailPreview(noDomainDetail) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun PebbleDeleteDialogLight() {
    PebblesTheme {
        DeleteConfirmDialog(
            pebbleName = "Morning walk",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleDeleteDialogDark() {
    PebblesTheme {
        DeleteConfirmDialog(
            pebbleName = "Morning walk",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
