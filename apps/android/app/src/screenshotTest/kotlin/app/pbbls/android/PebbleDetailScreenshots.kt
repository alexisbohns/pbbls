package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.data.LocalReferenceDataService
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.CardFace
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.MomentDetail
import app.pbbls.android.core.model.MomentSnap
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.MomentDeleteDialog
import app.pbbls.android.features.path.PebbleDetailContent
import app.pbbls.android.features.path.PebbleDetailUiState
import app.pbbls.android.features.path.card.CardCompletionState
import com.android.tools.screenshot.PreviewTest
import java.time.OffsetDateTime

/**
 * Previews for the moment detail page (#540, #1048, #1135): the real
 * [PebbleDetailContent] as a phone shows it, where a pebble is the held card and
 * a bare moment is its plain page, plus the print into a completed moment, the
 * open ⋮ menu and the R8 [MomentDeleteDialog] with and without a pebble. Light
 * and dark, driven with fixture [MomentDetail]s and a fixed souls cache so no
 * services are needed. The stateful `PebbleDetailScreen` (service-backed) is
 * deliberately not previewed.
 *
 * The strokes below are simple and in range (0..200), so each soul carves a
 * distinct mark.
 */
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
 * The souls cache the detail takes soul glyphs from (A-15): `v_moments_full`
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

/** A photo-only capture without its pebble yet: no name, so the page's title reads "untitled" (A-8). */
private val untitledBareMoment: MomentDetail =
    MomentPreviewFixtures.momentDetail(
        name = null,
        description = "Saved from the bus stop, to finish later.",
        happenedAt = OffsetDateTime.parse("2026-07-08T14:23:00+00:00"),
        snaps = listOf(MomentSnap(id = "snap-bus-stop", storagePath = "fixture/snap-bus-stop")),
        pebble = null,
    )

/**
 * The detail page as a phone shows it (#1135): a page of its own, no longer a
 * sheet, with its back arrow, over the real [PebbleDetailContent]. A pebble is
 * the held card (F1 from [PebbleCardFixtures], its photo unsigned so the field
 * shows its tint); a bare moment is its plain page and the place for its card.
 */
@Composable
private fun PagePreview(
    detail: MomentDetail = PebbleCardFixtures.f1,
    initialMenuOpen: Boolean = false,
    printProgress: Float? = null,
) {
    val completion = remember(printProgress) { printProgress?.let { CardCompletionState(frozenAt = it) } }
    CompositionLocalProvider(LocalReferenceDataService provides detailPreviewRefs) {
        PebbleDetailContent(
            uiState = PebbleDetailUiState.Content(detail, completing = completion != null),
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
            requestedFace = if (completion != null) CardFace.VERSO else CardFace.RECTO,
            completion = completion,
            printed = completion != null,
        )
    }
}

/** F4 come back from Complete with F1's pebble: the moment of the print frames below. */
private val f4Completed = PebbleCardFixtures.f4.copy(pebble = PebbleCardFixtures.f1.pebble)

/**
 * The print (pebble card spec §Completion), a fifth of the way: the card feeds out of the
 * place verso up, the lockup printed and the postmarks coming, the clearing still empty.
 */
@PreviewTest
@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun PebbleDetail_CompletionPrinting() {
    PebblesTheme { PagePreview(detail = f4Completed, printProgress = 0.2f) }
}

/** At the press: the dashed stone has landed in the clearing and the lit stone is in; band and footer printed. */
@PreviewTest
@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun PebbleDetail_CompletionPress() {
    PebblesTheme { PagePreview(detail = f4Completed, printProgress = 0.45f) }
}

/** Rising: the page steps back and fades while the card climbs to its rest. */
@PreviewTest
@Preview(showBackground = true, widthDp = 412, heightDp = 915, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PebbleDetail_CompletionRising() {
    PebblesTheme { PagePreview(detail = f4Completed, printProgress = 0.8f) }
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
