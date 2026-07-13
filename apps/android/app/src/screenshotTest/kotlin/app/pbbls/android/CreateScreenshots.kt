package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.features.glyph.models.Glyph
import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.karma.KarmaEarnedCapsule
import app.pbbls.android.features.karma.KarmaEarnedContent
import app.pbbls.android.features.karma.KarmaReason
import app.pbbls.android.features.path.create.CategoryGroup
import app.pbbls.android.features.path.create.PebbleForm
import app.pbbls.android.features.path.create.pickers.CreateSoulDialog
import app.pbbls.android.features.path.create.pickers.EmotionPickerBody
import app.pbbls.android.features.path.create.pickers.GlyphPickerBody
import app.pbbls.android.features.path.create.pickers.SoulPickerBody
import app.pbbls.android.features.path.create.pickers.ValencePickerBody
import app.pbbls.android.features.path.models.Domain
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.EmotionWithPalette
import app.pbbls.android.features.path.models.PebbleCollection
import app.pbbls.android.features.path.models.PebbleDraft
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.profile.models.SoulWithGlyph
import app.pbbls.android.theme.PebblesTheme
import com.android.tools.screenshot.PreviewTest
import java.time.OffsetDateTime

/**
 * Previews for the create funnel (#541): the pure [PebbleForm] (empty, filled,
 * and error states), the four picker bodies ([ValencePickerBody],
 * [EmotionPickerBody], [SoulPickerBody], [GlyphPickerBody]), the inline
 * [CreateSoulDialog], and the [KarmaEarnedCapsule] flash — light and dark,
 * driven with fixtures so no services are needed. The service-backed sheets and
 * `CreatePebbleScreen` are deliberately not previewed. Glyph and valence shapes
 * render through the same AndroidSVG path the read previews already exercise.
 */
private fun palette(
    primary: String,
    secondary: String,
    light: String,
    surface: String,
): EmotionPalette =
    requireNotNull(
        EmotionPalette.fromHex(
            primaryHex = primary,
            secondaryHex = secondary,
            lightHex = light,
            surfaceHex = surface,
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

private val domains =
    listOf(
        Domain(id = "d-work", slug = "work", name = "Work", label = "Work"),
        Domain(id = "d-health", slug = "health", name = "Health", label = "Health"),
        Domain(id = "d-family", slug = "family", name = "Family", label = "Family"),
    )

private val collections =
    listOf(
        PebbleCollection(id = "c-wins", name = "Wins"),
        PebbleCollection(id = "c-travel", name = "Travel"),
    )

private val joyEmotion =
    EmotionWithPalette(
        id = "e-joy",
        slug = "joyful",
        name = "Joyful",
        emoji = "😊",
        categoryId = "cat-joy",
        categorySlug = "joy",
        categoryName = "Joy",
        palette = joyPalette,
    )

private val sampleGlyph = glyphOf("glyph-1", "Spiral", listOf(sampleStroke))

private val fixedWhen: OffsetDateTime = OffsetDateTime.parse("2026-07-08T14:23:00Z")

private val emptyDraft = PebbleDraft(happenedAt = fixedWhen)

private val filledDraft =
    PebbleDraft(
        happenedAt = fixedWhen,
        name = "Shipped the Android create flow",
        description = "Long day, but it all came together in the end.",
        emotionId = "e-joy",
        domainId = "d-work",
        valence = Valence.HIGHLIGHT_MEDIUM,
        soulIds = listOf("s1", "s2"),
        collectionId = "c-wins",
        glyphId = "glyph-1",
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
private fun FormPreview(
    draft: PebbleDraft,
    selectedEmotion: EmotionWithPalette?,
    selectedGlyph: Glyph?,
    saveError: String?,
) {
    val system = PebblesTheme.colors.system
    PebbleForm(
        draft = draft,
        onDraftChange = {},
        domains = domains,
        souls = souls,
        collections = collections,
        selectedEmotion = selectedEmotion,
        selectedGlyph = selectedGlyph,
        onGlyphPicked = {},
        saveError = saveError,
        modifier = Modifier.fillMaxSize().background(system.background),
    )
}

@Composable
private fun ValenceBodyPreview(current: Valence?) {
    val system = PebblesTheme.colors.system
    ValencePickerBody(
        current = current,
        onSelected = {},
        modifier = Modifier.fillMaxSize().background(system.background),
    )
}

@Composable
private fun EmotionBodyPreview() {
    val system = PebblesTheme.colors.system
    EmotionPickerBody(
        groups = emotionGroups,
        stagedId = "e-joy",
        onToggle = {},
        modifier = Modifier.fillMaxSize().background(system.background),
    )
}

@Composable
private fun SoulBodyPreview() {
    val system = PebblesTheme.colors.system
    SoulPickerBody(
        souls = souls,
        selection = setOf("s1", "s3"),
        onToggle = {},
        onCreateTap = {},
        modifier = Modifier.fillMaxSize().background(system.background),
    )
}

@Composable
private fun GlyphBodyPreview() {
    val system = PebblesTheme.colors.system
    GlyphPickerBody(
        glyphs = glyphs,
        currentGlyphId = "glyph-1",
        onSelect = {},
        modifier = Modifier.fillMaxSize().background(system.background),
    )
}

@Composable
private fun KarmaCapsulePreview(content: KarmaEarnedContent) {
    val system = PebblesTheme.colors.system
    val gradient = Brush.verticalGradient(listOf(system.background, system.muted))
    Box(
        modifier = Modifier.fillMaxSize().background(gradient),
        contentAlignment = Alignment.Center,
    ) {
        KarmaEarnedCapsule(content = content, onTap = {})
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun CreateFormEmptyLight() {
    PebblesTheme { FormPreview(emptyDraft, null, null, null) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CreateFormEmptyDark() {
    PebblesTheme { FormPreview(emptyDraft, null, null, null) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun CreateFormFilledLight() {
    PebblesTheme { FormPreview(filledDraft, joyEmotion, sampleGlyph, null) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CreateFormFilledDark() {
    PebblesTheme { FormPreview(filledDraft, joyEmotion, sampleGlyph, null) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun CreateFormErrorLight() {
    PebblesTheme {
        FormPreview(filledDraft, joyEmotion, sampleGlyph, "Couldn't save your pebble. Please try again.")
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun CreateFormErrorDark() {
    PebblesTheme {
        FormPreview(filledDraft, joyEmotion, sampleGlyph, "Couldn't save your pebble. Please try again.")
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ValencePickerEmptyLight() {
    PebblesTheme { ValenceBodyPreview(null) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ValencePickerEmptyDark() {
    PebblesTheme { ValenceBodyPreview(null) }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ValencePickerSelectedLight() {
    PebblesTheme { ValenceBodyPreview(Valence.HIGHLIGHT_MEDIUM) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ValencePickerSelectedDark() {
    PebblesTheme { ValenceBodyPreview(Valence.HIGHLIGHT_MEDIUM) }
}

@PreviewTest
@Preview(showBackground = true)
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
