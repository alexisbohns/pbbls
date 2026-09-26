package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.readableWidth
import app.pbbls.android.core.model.Collection
import app.pbbls.android.core.model.CollectionMode
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.EmotionRef
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphGridItem
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.Pebble
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.features.glyph.store.GlyphDetailDrawerContent
import app.pbbls.android.features.glyph.store.GlyphDetailSurface
import app.pbbls.android.features.glyph.store.GlyphTab
import app.pbbls.android.features.glyph.store.GlyphsListContent
import app.pbbls.android.features.glyph.store.GlyphsUiState
import app.pbbls.android.features.path.PathContent
import app.pbbls.android.features.path.PebbleDetailContent
import app.pbbls.android.features.path.PebbleDetailUiState
import app.pbbls.android.features.profile.CollectionDetailContent
import app.pbbls.android.features.profile.CollectionDetailUiState
import app.pbbls.android.features.profile.CollectionsListContent
import app.pbbls.android.features.profile.CollectionsListUiState
import app.pbbls.android.features.profile.SoulDetailContent
import app.pbbls.android.features.profile.SoulDetailUiState
import app.pbbls.android.features.profile.SoulsListContent
import app.pbbls.android.features.profile.SoulsListUiState
import app.pbbls.android.navigation.CollectionsPlaceholder
import app.pbbls.android.navigation.GlyphsPlaceholder
import app.pbbls.android.navigation.PebblesKey
import app.pbbls.android.navigation.SoulsPlaceholder
import com.android.tools.screenshot.PreviewTest
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Souls and collections beside their list on large screens (#940): the idle
 * state (list + the production [SoulsPlaceholder] / [CollectionsPlaceholder],
 * also in French) and the open state (list + the real
 * detail content, `showBack = false`) through [ListDetailPreviewFrame], at the
 * 840/1024 dp breakpoints plus a 1024 dp dark render. Fixtures are built the
 * same way [SoulsScreenshots], [CollectionsScreenshots] and
 * [PebbleDetailScreenshots] do — a private palette and small hand-built rows —
 * rather than reusing those files' `private` values.
 *
 * Path's pair is the exception: an open pebble beside Path (there is no idle
 * render, since idle Path stays full width) reuses the Path timeline and the
 * full pebble the Path and detail screenshots already render, so the pane
 * shows exactly what those do.
 *
 * The glyph store's pair renders the store's stateless content in its pane
 * layout (tabs at the bottom of the list pane) beside the production
 * [GlyphsPlaceholder], or beside the drawer body on the same page
 * `GlyphDetailScreen` draws, opened on [StoreScreenshots]' glyph.
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
private val loopStroke =
    GlyphStroke(d = "M30,30 C60,10 140,10 170,30 S190,140 170,170 S60,190 30,170 S10,60 30,30", width = 6.0)
private val waveStroke = GlyphStroke(d = "M 20 120 Q 100 40 180 120", width = 6.0)
private val crossStrokes =
    listOf(
        GlyphStroke(d = "M 50 50 L 150 150", width = 6.0),
        GlyphStroke(d = "M 150 50 L 50 150", width = 6.0),
    )
private val peakStroke = GlyphStroke(d = "M 40 150 L 100 50 L 160 150", width = 6.0)

private fun soulFixture(
    id: String,
    name: String,
    strokes: List<GlyphStroke>,
    pebblesCount: Int,
): SoulWithGlyph =
    SoulWithGlyph(
        id = id,
        name = name,
        glyphId = "g-$id",
        glyph = Glyph(id = "g-$id", name = null, strokes = strokes, viewBox = "0 0 200 200"),
        pebblesCount = pebblesCount,
    )

private val previewSouls =
    listOf(
        soulFixture("s1", "Molly", listOf(loopStroke), pebblesCount = 12),
        soulFixture("s2", "Alex", listOf(waveStroke), pebblesCount = 9),
        soulFixture("s3", "Sam", crossStrokes, pebblesCount = 3),
        soulFixture("s4", "Wren", listOf(peakStroke), pebblesCount = 1),
    )

private fun pebbleFixture(
    id: String,
    name: String,
    daysAgo: Long,
    intensity: Int,
    positiveness: Int,
    renderSvg: String?,
): Pebble {
    val happenedAt = OffsetDateTime.parse("2026-07-08T14:23:00+00:00").minusDays(daysAgo)
    return Pebble(
        id = id,
        name = name,
        happenedAt = happenedAt,
        createdAt = happenedAt,
        intensity = intensity,
        positiveness = positiveness,
        renderSvg = renderSvg,
        emotion = EmotionRef(id = "e1", slug = "joyful", name = "Joyful"),
    )
}

private val soulPebbles =
    listOf(
        pebbleFixture("p1", "Morning walk", daysAgo = 0, intensity = 2, positiveness = 0, PebbleSvgFixtures.mediumNeutral),
        pebbleFixture("p2", "Studio session", daysAgo = 3, intensity = 3, positiveness = 1, PebbleSvgFixtures.largeHighlight),
        pebbleFixture("p3", "Quiet afternoon", daysAgo = 7, intensity = 1, positiveness = 1, null),
    )

private val previewCollections =
    listOf(
        Collection(id = "c1", name = "Wins", mode = CollectionMode.STACK, pebbleCount = 14),
        Collection(id = "c2", name = "Travel", mode = CollectionMode.PACK, pebbleCount = 6),
        Collection(id = "c3", name = "Growth", mode = CollectionMode.TRACK, pebbleCount = 2),
    )

private val collectionPebbles =
    listOf(
        pebbleFixture("p4", "Beach sunset", daysAgo = 1, intensity = 3, positiveness = 1, PebbleSvgFixtures.largeHighlight),
        pebbleFixture("p5", "New job offer", daysAgo = 40, intensity = 2, positiveness = 1, PebbleSvgFixtures.mediumNeutral),
    )

private val previewZone: ZoneId = ZoneId.of("UTC")

@PreviewTest
@PreviewWideTall
// French at 840 dp rather than @PreviewFrench: that one has no width, and a
// phone-width window never gets two panes, so it would only squeeze the pair.
@Preview(name = "fr", showBackground = true, locale = "fr", widthDp = 840, heightDp = 720)
@Composable
fun SoulsListDetailIdle() {
    PebblesTheme {
        ListDetailPreviewFrame(
            tab = PebblesKey.People,
            list = {
                SoulsListContent(
                    uiState = SoulsListUiState.Content(souls = previewSouls),
                    onBack = {},
                    onOpenSoul = {},
                    onCreateSoul = {},
                    onRetry = {},
                    onDeleteSoul = {},
                )
            },
            detail = {
                SoulsPlaceholder()
            },
        )
    }
}

@PreviewTest
@PreviewWideTall
@Composable
fun SoulsListDetailOpen() {
    PebblesTheme {
        ListDetailPreviewFrame(
            tab = PebblesKey.People,
            list = {
                SoulsListContent(
                    uiState = SoulsListUiState.Content(souls = previewSouls),
                    onBack = {},
                    onOpenSoul = {},
                    onCreateSoul = {},
                    onRetry = {},
                    onDeleteSoul = {},
                )
            },
            detail = {
                SoulDetailContent(
                    uiState = SoulDetailUiState.Content(soul = previewSouls.first(), pebbles = soulPebbles),
                    onBack = {},
                    onEditSoul = {},
                    onRetry = {},
                    onOpenPebble = {},
                    onDeletePebble = {},
                    paletteFor = { previewPalette },
                    showBack = false,
                )
            },
        )
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 1024, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SoulsListDetailOpenDark() {
    PebblesTheme {
        ListDetailPreviewFrame(
            tab = PebblesKey.People,
            list = {
                SoulsListContent(
                    uiState = SoulsListUiState.Content(souls = previewSouls),
                    onBack = {},
                    onOpenSoul = {},
                    onCreateSoul = {},
                    onRetry = {},
                    onDeleteSoul = {},
                )
            },
            detail = {
                SoulDetailContent(
                    uiState = SoulDetailUiState.Content(soul = previewSouls.first(), pebbles = soulPebbles),
                    onBack = {},
                    onEditSoul = {},
                    onRetry = {},
                    onOpenPebble = {},
                    onDeletePebble = {},
                    paletteFor = { previewPalette },
                    showBack = false,
                )
            },
        )
    }
}

@PreviewTest
@PreviewWideTall
// French at 840 dp rather than @PreviewFrench: that one has no width, and a
// phone-width window never gets two panes, so it would only squeeze the pair.
@Preview(name = "fr", showBackground = true, locale = "fr", widthDp = 840, heightDp = 720)
@Composable
fun CollectionsListDetailIdle() {
    PebblesTheme {
        ListDetailPreviewFrame(
            tab = PebblesKey.Collections,
            list = {
                CollectionsListContent(
                    uiState = CollectionsListUiState.Content(collections = previewCollections),
                    onBack = {},
                    onOpenCollection = {},
                    onCreateCollection = {},
                    onRetry = {},
                    onRefresh = {},
                    onDeleteCollection = {},
                )
            },
            detail = {
                CollectionsPlaceholder()
            },
        )
    }
}

@PreviewTest
@PreviewWideTall
@Composable
fun CollectionsListDetailOpen() {
    PebblesTheme {
        ListDetailPreviewFrame(
            tab = PebblesKey.Collections,
            list = {
                CollectionsListContent(
                    uiState = CollectionsListUiState.Content(collections = previewCollections),
                    onBack = {},
                    onOpenCollection = {},
                    onCreateCollection = {},
                    onRetry = {},
                    onRefresh = {},
                    onDeleteCollection = {},
                )
            },
            detail = {
                CollectionDetailContent(
                    uiState =
                        CollectionDetailUiState.Content(
                            collection = previewCollections.first(),
                            pebbles = collectionPebbles,
                            zone = previewZone,
                        ),
                    onBack = {},
                    onEditCollection = {},
                    onRetry = {},
                    onOpenPebble = {},
                    onDeletePebble = {},
                    paletteFor = { previewPalette },
                    showBack = false,
                )
            },
        )
    }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 1024, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CollectionsListDetailOpenDark() {
    PebblesTheme {
        ListDetailPreviewFrame(
            tab = PebblesKey.Collections,
            list = {
                CollectionsListContent(
                    uiState = CollectionsListUiState.Content(collections = previewCollections),
                    onBack = {},
                    onOpenCollection = {},
                    onCreateCollection = {},
                    onRetry = {},
                    onRefresh = {},
                    onDeleteCollection = {},
                )
            },
            detail = {
                CollectionDetailContent(
                    uiState =
                        CollectionDetailUiState.Content(
                            collection = previewCollections.first(),
                            pebbles = collectionPebbles,
                            zone = previewZone,
                        ),
                    onBack = {},
                    onEditCollection = {},
                    onRetry = {},
                    onOpenPebble = {},
                    onDeletePebble = {},
                    paletteFor = { previewPalette },
                    showBack = false,
                )
            },
        )
    }
}

@Composable
private fun PathPebblePair() {
    ListDetailPreviewFrame(
        tab = PebblesKey.Path,
        list = {
            val focused = populatedEntries.last().weekStart
            PathContent(
                entries = populatedEntries,
                initialWeekStart = focused,
                focusedWeekStart = focused,
                today = pathPreviewToday,
                onFocusChange = {},
                paletteFor = { previewPalette },
                // Mirrors PathScreen: the timeline is the readable column.
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .readableWidth(),
            )
        },
        detail = {
            PebbleDetailContent(
                uiState = PebbleDetailUiState.Content(fullDetail),
                palette = previewPalette,
                onEdit = {},
                onShare = null,
                onRetry = {},
            )
        },
    )
}

@PreviewTest
@PreviewWideTall
@Composable
fun PathPebbleListDetailOpen() {
    PebblesTheme { PathPebblePair() }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 1024, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun PathPebbleListDetailOpenDark() {
    PebblesTheme { PathPebblePair() }
}

private val storeItems: List<GlyphGridItem> =
    listOf(
        previewItem,
        GlyphGridItem(
            glyph = Glyph(id = "g2", name = "Loop", strokes = listOf(loopStroke), viewBox = "0 0 200 200", userId = "u2"),
            price = 7,
            owned = false,
            createdAt = null,
            acquiredAt = null,
        ),
        GlyphGridItem(
            glyph = Glyph(id = "g3", name = "Cross", strokes = crossStrokes, viewBox = "0 0 200 200", userId = "u3"),
            price = 9,
            owned = false,
            createdAt = null,
            acquiredAt = null,
        ),
        GlyphGridItem(
            glyph = Glyph(id = "g4", name = "Peak", strokes = listOf(peakStroke), viewBox = "0 0 200 200", userId = "u4"),
            price = 5,
            owned = false,
            createdAt = null,
            acquiredAt = null,
        ),
    )

@Composable
private fun GlyphsPair(detail: @Composable () -> Unit) {
    ListDetailPreviewFrame(
        tab = PebblesKey.You,
        list = {
            GlyphsListContent(
                uiState = GlyphsUiState.Content(tab = GlyphTab.COMMU, items = storeItems, karma = 21, isLoadingTab = false),
                didRenameFail = false,
                // A list pane: the tabs sit at the bottom of the pane, not the window's edge.
                toolbarOnEndEdge = false,
                onBack = {},
                onCarve = {},
                onSelectTab = {},
                onOpenGlyph = {},
                onRename = {},
            )
        },
        detail = detail,
    )
}

@Composable
private fun GlyphDetailPane() {
    GlyphDetailSurface {
        GlyphDetailDrawerContent(
            item = previewItem,
            isOwned = false,
            acquiredAt = null,
            currentBalance = 21,
            isBuying = false,
            errorRes = null,
            onConfirm = { true },
        )
    }
}

@PreviewTest
@PreviewWideTall
@Composable
fun GlyphsListDetailIdle() {
    PebblesTheme { GlyphsPair { GlyphsPlaceholder() } }
}

@PreviewTest
@PreviewWideTall
@Composable
fun GlyphsListDetailOpen() {
    PebblesTheme { GlyphsPair { GlyphDetailPane() } }
}

@PreviewTest
@Preview(showBackground = true, widthDp = 1024, heightDp = 720, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun GlyphsListDetailOpenDark() {
    PebblesTheme { GlyphsPair { GlyphDetailPane() } }
}
