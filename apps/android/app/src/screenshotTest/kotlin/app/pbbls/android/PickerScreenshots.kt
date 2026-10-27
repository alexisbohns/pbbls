package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.data.KarmaEarnedContent
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.EmotionPalette
import app.pbbls.android.core.model.EmotionWithPalette
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.GlyphGridItem
import app.pbbls.android.core.model.GlyphStroke
import app.pbbls.android.core.model.KarmaReason
import app.pbbls.android.core.model.SoulWithGlyph
import app.pbbls.android.core.ui.KarmaEarnedCapsule
import app.pbbls.android.features.glyph.picker.GlyphPickerGrid
import app.pbbls.android.features.path.create.CategoryGroup
import app.pbbls.android.features.path.create.pickers.CreateSoulDialog
import app.pbbls.android.features.path.create.pickers.EmotionPickerBody
import app.pbbls.android.features.path.create.pickers.SoulPickerBody
import com.android.tools.screenshot.PreviewTest

/**
 * Previews for the pickers the edit form and the Complete steps still use, kept
 * from the retired create funnel's gallery (#541) under their original names:
 * the picker bodies ([EmotionPickerBody], [SoulPickerBody], [GlyphPickerGrid]),
 * the inline [CreateSoulDialog], and the [KarmaEarnedCapsule] flash — light and
 * dark, driven with fixtures so no services are needed. The valence fan and the
 * edit form itself are previewed in `CompleteFlowScreenshots` and
 * `EditPebbleScreenshots`.
 */
private fun palette(
    primary: String,
    secondary: String,
    light: String,
    surface: String,
    dark: String = "#2A2138FF",
    shaded: String = "#4A3A5CFF",
): EmotionPalette =
    requireNotNull(
        EmotionPalette.fromHex(
            primaryHex = primary,
            secondaryHex = secondary,
            lightHex = light,
            surfaceHex = surface,
            darkHex = dark,
            shadedHex = shaded,
        ),
    )

private val joyPalette = palette("#7B5E99FF", "#AE91CCFF", "#F2EFF5FF", "#7B5E991A")
private val peacePalette = palette("#3E7C8FFF", "#7FB3C4FF", "#EAF3F6FF", "#3E7C8F1A")
private val pridePalette = palette("#B5703CFF", "#D8A878FF", "#F7EFE6FF", "#B5703C1A")

// In-range (0..200) strokes so each glyph cell carves a distinct mark.
private val sampleStroke =
    GlyphStroke(
        d = "M30,30 C60,10 140,10 170,30 S190,140 170,170 S60,190 30,170 S10,60 30,30",
        width = 6.0,
    )
private val waveStroke = GlyphStroke(d = "M 20 120 Q 100 40 180 120", width = 6.0)
private val crossStrokes =
    listOf(
        GlyphStroke(d = "M 50 50 L 150 150", width = 6.0),
        GlyphStroke(d = "M 150 50 L 50 150", width = 6.0),
    )
private val peakStroke = GlyphStroke(d = "M 40 150 L 100 50 L 160 150", width = 6.0)

private fun glyphOf(
    id: String,
    name: String?,
    strokes: List<GlyphStroke>,
): Glyph = Glyph(id = id, name = name, strokes = strokes, viewBox = "0 0 200 200")

private fun soul(
    id: String,
    name: String,
    strokes: List<GlyphStroke>,
    count: Int,
): SoulWithGlyph =
    SoulWithGlyph(
        id = id,
        name = name,
        glyphId = "g-$id",
        glyph = glyphOf("g-$id", null, strokes),
        pebblesCount = count,
    )

private val souls =
    listOf(
        soul("s1", "Alex", listOf(waveStroke), 12),
        soul("s2", "Sam", crossStrokes, 5),
        soul("s3", "Robin", listOf(peakStroke), 3),
    )

private val glyphs =
    listOf(
        glyphOf("glyph-1", "Spiral", listOf(sampleStroke)),
        glyphOf("glyph-2", "Wave", listOf(waveStroke)),
        glyphOf("glyph-3", "Cross", crossStrokes),
        glyphOf("glyph-4", "Peak", listOf(peakStroke)),
    )

private fun emotionRow(
    id: String,
    slug: String,
    name: String,
    categorySlug: String,
    categoryName: String,
    rowPalette: EmotionPalette,
): EmotionWithPalette =
    EmotionWithPalette(
        id = id,
        slug = slug,
        name = name,
        emoji = "🙂",
        categoryId = "cat-$categorySlug",
        categorySlug = categorySlug,
        categoryName = categoryName,
        palette = rowPalette,
    )

private val emotionGroups =
    listOf(
        CategoryGroup(
            categorySlug = "joy",
            categoryName = "Joy",
            palette = joyPalette,
            rows =
                listOf(
                    emotionRow("e-joy", "joyful", "Joyful", "joy", "Joy", joyPalette),
                    emotionRow("e-content", "content", "Content", "joy", "Joy", joyPalette),
                ),
        ),
        CategoryGroup(
            categorySlug = "peace",
            categoryName = "Peace",
            palette = peacePalette,
            rows = listOf(emotionRow("e-calm", "calm", "Calm", "peace", "Peace", peacePalette)),
        ),
        CategoryGroup(
            categorySlug = "pride",
            categoryName = "Pride",
            palette = pridePalette,
            rows = listOf(emotionRow("e-proud", "proud", "Proud", "pride", "Pride", pridePalette)),
        ),
    )

@Composable
private fun EmotionBodyPreview() {
    EmotionPickerBody(
        groups = emotionGroups,
        stagedId = "e-joy",
        onToggle = {},
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun SoulBodyPreview() {
    SoulPickerBody(
        souls = souls,
        selection = setOf("s1", "s3"),
        onToggle = {},
        onCreateTap = {},
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun GlyphBodyPreview() {
    GlyphPickerGrid(
        items =
            glyphs.mapIndexed { index, glyph ->
                GlyphGridItem(glyph = glyph, price = index * 5, owned = false, createdAt = null, acquiredAt = null)
            },
        currentGlyphId = "glyph-1",
        showCarveRow = true,
        onCarve = {},
        onSelect = {},
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun KarmaCapsulePreview(content: KarmaEarnedContent) {
    val gradient = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceContainerHighest))
    Box(
        modifier = Modifier.fillMaxSize().background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        KarmaEarnedCapsule(haptics = true, content = content, onTap = {})
    }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@Composable
fun EmotionPickerLight() {
    PebblesTheme { EmotionBodyPreview() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun EmotionPickerDark() {
    PebblesTheme { EmotionBodyPreview() }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@Composable
fun SoulPickerLight() {
    PebblesTheme { SoulBodyPreview() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun SoulPickerDark() {
    PebblesTheme { SoulBodyPreview() }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@Composable
fun GlyphPickerLight() {
    PebblesTheme { GlyphBodyPreview() }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun GlyphPickerDark() {
    PebblesTheme { GlyphBodyPreview() }
}

@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@Composable
fun CreateSoulDialogLight() {
    PebblesTheme { CreateSoulDialog(onDismiss = {}, onCreate = {}) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CreateSoulDialogDark() {
    PebblesTheme { CreateSoulDialog(onDismiss = {}, onCreate = {}) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun KarmaFlashCreatedLight() {
    PebblesTheme { KarmaCapsulePreview(KarmaEarnedContent(5, KarmaReason.PEBBLE_CREATED)) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun KarmaFlashCreatedDark() {
    PebblesTheme { KarmaCapsulePreview(KarmaEarnedContent(5, KarmaReason.PEBBLE_CREATED)) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun KarmaFlashEnrichedLight() {
    PebblesTheme { KarmaCapsulePreview(KarmaEarnedContent(12, KarmaReason.PEBBLE_ENRICHED)) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun KarmaFlashEnrichedDark() {
    PebblesTheme { KarmaCapsulePreview(KarmaEarnedContent(12, KarmaReason.PEBBLE_ENRICHED)) }
}
