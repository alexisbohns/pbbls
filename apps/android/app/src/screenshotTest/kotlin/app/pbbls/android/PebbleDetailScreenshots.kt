package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.DomainRef
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.core.model.MomentSnap
import app.pbbls.android.core.model.PebbleCollection
import app.pbbls.android.core.model.SoulRef
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.model.Valence
import app.pbbls.android.core.model.Visibility
import app.pbbls.android.core.ui.MomentDeleteDialog
import app.pbbls.android.features.path.PebbleDetailContent
import app.pbbls.android.features.path.PebbleDetailUiState
import app.pbbls.android.features.path.read.BannerAspect
import app.pbbls.android.features.path.read.PebbleReadView
import app.pbbls.android.features.path.read.PebbleSnapFrame
import com.android.tools.screenshot.PreviewTest
import java.time.OffsetDateTime

/**
 * Previews for the moment read surface (#540, #1048): the pure [PebbleReadView]
 * across its variants — full (description + souls + domain + collection), glyph
 * souls, minimal and the no-domain fallback — plus the open ⋮ menu and the R8
 * [MomentDeleteDialog] with and without a pebble. The page itself
 * ([PebbleDetailContent]) shows a pebble as the held card and a bare moment as
 * its plain page (#1135). Light and
 * dark, driven with fixture [MomentDetail]s and a fixed souls cache so no
 * services are needed. The stateful `PebbleDetailScreen` (service-backed) is
 * deliberately not previewed.
 *
 * The #599 snap-overlay frame is covered separately via [PebbleSnapFrame] with a
 * flat [ColorPainter] standing in for the decoded snap (a real photo needs a
 * network load the screenshot renderer can't do) — square / landscape / portrait
 * across light and dark.
 *
 * [previewPalette]'s `darkHex` mirrors the design's dark page-background
 * (#19131F, e.g. the "Anxiété" category) so the dark preview reads as dark as
 * production; the real per-category hexes are hand-entered in Supabase Studio.
 */
private val previewPalette: EmotionPalette =
    requireNotNull(
        EmotionPalette.fromHex(
            primaryHex = "#7B5E99FF",
            secondaryHex = "#AE91CCFF",
            lightHex = "#F2EFF5FF",
            surfaceHex = "#7B5E991A",
            darkHex = "#19131FFF",
            shadedHex = "#4A3A5CFF",
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

private fun soulWithGlyph(
    name: String,
    strokes: List<GlyphStroke>,
): SoulWithGlyph =
    SoulWithGlyph(
        id = name,
        name = name,
        glyphId = "g-$name",
        glyph = Glyph(id = "g-$name", strokes = strokes, viewBox = "0 0 200 200"),
    )

/**
 * The souls cache the read page takes soul glyphs from (A-15): `v_moments_full`
 * projects souls as `{id, name}` only. Internal so the list-detail renders
 * provide the same cache.
 */
internal val detailPreviewRefs =
    PreviewReferenceData(
        souls =
            listOf(
                soulWithGlyph("Alex", waveStrokes),
                soulWithGlyph("Sam", crossStrokes),
                soulWithGlyph("Wave", waveStrokes),
                soulWithGlyph("Cross", crossStrokes),
                soulWithGlyph("Peak", peakStrokes),
            ),
    )

private fun soul(name: String): SoulRef = SoulRef(id = name, name = name)

private fun detail(
    name: String,
    description: String?,
    valence: Valence,
    domains: List<DomainRef> = emptyList(),
    souls: List<SoulRef> = emptyList(),
    collections: List<PebbleCollection> = emptyList(),
): MomentDetail =
    MomentPreviewFixtures.momentDetail(
        id = name,
        name = name,
        description = description,
        happenedAt = OffsetDateTime.parse("2026-07-08T14:23:00+00:00"),
        pebble =
            MomentPreviewFixtures.momentPebble(
                valence = valence,
                visibility = Visibility.PRIVATE,
                domains = domains,
                souls = souls,
                collections = collections,
            ),
    )

private val fullDetail: MomentDetail =
    detail(
        name = "Morning walk",
        description = "Long loop around the park before work. The light was unreal.",
        valence = Valence.NEUTRAL_MEDIUM,
        domains = listOf(DomainRef(id = "work", slug = "work", name = "Work")),
        collections = listOf(PebbleCollection(id = "Wins", name = "Wins")),
        souls = listOf(soul("Alex"), soul("Sam")),
    )

private val glyphSoulsDetail: MomentDetail =
    detail(
        name = "Studio session",
        description = "Three of us sketching glyphs for the new set.",
        valence = Valence.HIGHLIGHT_LARGE,
        domains = listOf(DomainRef(id = "hobbies", slug = "hobbies", name = "Hobbies")),
        souls = listOf(soul("Wave"), soul("Cross"), soul("Peak")),
    )

private val minimalDetail: MomentDetail =
    detail(
        name = "Quiet afternoon",
        description = null,
        valence = Valence.HIGHLIGHT_SMALL,
        domains = listOf(DomainRef(id = "friends", slug = "friends", name = "Friends")),
    )

private val noDomainDetail: MomentDetail =
    detail(
        name = "Rough morning",
        description = "Nothing landed the way I wanted it to.",
        valence = Valence.LOWLIGHT_MEDIUM,
    )

/** A photo-only capture without its pebble yet: no name, so the page's title reads "untitled" (A-8). */
private val untitledBareMoment: MomentDetail =
    MomentPreviewFixtures.momentDetail(
        name = null,
        description = "Saved from the bus stop, to finish later.",
        happenedAt = OffsetDateTime.parse("2026-07-08T14:23:00+00:00"),
        snaps = listOf(MomentSnap(id = "snap-bus-stop", storagePath = "fixture/snap-bus-stop")),
        pebble = null,
    )

@Composable
private fun DetailPreview(
    detail: MomentDetail,
    palette: EmotionPalette? = previewPalette,
) {
    // PebbleReadView draws no background of its own (#940): it sits on its
    // host, which in production is the page's or the pane's surface.
    CompositionLocalProvider(LocalReferenceDataService provides detailPreviewRefs) {
        PebbleReadView(
            detail = detail,
            palette = palette,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
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

/**
 * The read page as a phone shows it (#1135): a page of its own, no longer a
 * sheet, with its back arrow, over the real [PebbleDetailContent]. A pebble is
 * the held card (F1 from [PebbleCardFixtures], its photo unsigned so the field
 * shows its tint); a bare moment is its plain page and the place for its card.
 */
@Composable
private fun PagePreview(
    detail: MomentDetail = PebbleCardFixtures.f1,
    initialMenuOpen: Boolean = false,
) {
    CompositionLocalProvider(LocalReferenceDataService provides detailPreviewRefs) {
        PebbleDetailContent(
            uiState = PebbleDetailUiState.Content(detail),
            palette =
                detail.pebble
                    ?.emotion
                    ?.id
                    ?.let(PebbleCardFixtures.palettes::get),
            onEditMoment = {},
            onEditPebble = {},
            onDelete = {},
            onShare = null,
            onRetry = {},
            photo = null,
            ratio = if (detail.snaps.isEmpty()) null else PebbleCardFixtures.ROOFTOP_RATIO,
            soulsById = PebbleCardFixtures.soulsById,
            domains = PebbleCardFixtures.domains,
            showBack = true,
            onBack = {},
            initialMenuOpen = initialMenuOpen,
            zone = PebbleCardFixtures.zone,
        )
    }
}

/** A pebble opens as the card, on its recto (#1135). */
@PreviewTest
@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun PebbleDetailCardLight() {
    PebblesTheme { PagePreview() }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 412, heightDp = 915, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleDetailCardDark() {
    PebblesTheme { PagePreview() }
}

/** F4, a bare moment, as the page shows it: no card, the place its card will print into, and "Make it a pebble" (A-8). */
@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun PebbleDetail_BareMoment() {
    PebblesTheme { PagePreview(detail = PebbleCardFixtures.f4) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleDetail_BareMomentDark() {
    PebblesTheme { PagePreview(detail = PebbleCardFixtures.f4) }
}

/** A photo-only capture: "untitled", its photo matted (unsigned here). */
@PreviewTest
@Preview(showBackground = true)
@Composable
fun PebbleDetail_BareMomentUntitled() {
    PebblesTheme { PagePreview(detail = untitledBareMoment) }
}

/** The ⋮ menu (R7) over the card: Edit moment, Edit pebble because this moment has one, a divider, and Delete. */
@PreviewTest
@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@PreviewFrench
@Composable
fun PebbleDetail_MenuOpenWithDelete() {
    PebblesTheme { PagePreview(initialMenuOpen = true) }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 412, heightDp = 915, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleDetail_MenuOpenWithDeleteDark() {
    PebblesTheme { PagePreview(initialMenuOpen = true) }
}

/** R8 with your pebble: keep the moment, or delete it too. */
@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun MomentDeleteDialog_WithPebble() {
    PebblesTheme { MomentDeleteDialog(hasPebble = true, onConfirm = {}, onDismiss = {}) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun MomentDeleteDialog_WithPebbleDark() {
    PebblesTheme { MomentDeleteDialog(hasPebble = true, onConfirm = {}, onDismiss = {}) }
}

/** R8 for a bare moment: one destructive action. */
@PreviewTest
@Preview(showBackground = true)
@PreviewFrench
@Composable
fun MomentDeleteDialog_BareMoment() {
    PebblesTheme { MomentDeleteDialog(hasPebble = false, onConfirm = {}, onDismiss = {}) }
}

// A neutral fill stands in for the decoded snap — the screenshot renderer has no
// network, so a real rendition can't load in a preview.
private val previewSnap = ColorPainter(Color(0xFF6B7A8F))

@Composable
private fun SnapFramePreview(
    aspect: BannerAspect,
    valence: Valence?,
) {
    PebbleSnapFrame(
        photo = previewSnap,
        aspect = aspect,
        glyph = MomentPreviewFixtures.pebbleGlyph(),
        valence = valence,
        palette = previewPalette,
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 24.dp),
    )
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun PebbleSnapSquareLight() {
    PebblesTheme { SnapFramePreview(BannerAspect.SQUARE, Valence.NEUTRAL_MEDIUM) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleSnapSquareDark() {
    PebblesTheme { SnapFramePreview(BannerAspect.SQUARE, Valence.NEUTRAL_MEDIUM) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun PebbleSnapLandscapeLight() {
    PebblesTheme { SnapFramePreview(BannerAspect.FOUR_THREE, Valence.NEUTRAL_MEDIUM) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun PebbleSnapPortraitLargeLight() {
    PebblesTheme { SnapFramePreview(BannerAspect.THREE_FOUR, Valence.HIGHLIGHT_LARGE) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleSnapPortraitLargeDark() {
    PebblesTheme { SnapFramePreview(BannerAspect.THREE_FOUR, Valence.HIGHLIGHT_LARGE) }
}

/** A moment without its pebble over its photo: the blank stone pokes out of the corner (A-8). */
@PreviewTest
@Preview(showBackground = true)
@Composable
fun PebbleSnap_BlankStone() {
    PebblesTheme { SnapFramePreview(BannerAspect.FOUR_THREE, valence = null) }
}
