package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.PbblsGlyphImage
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.glyph.GlyphCatalog
import app.pbbls.android.core.designsystem.glyph.GlyphFill
import app.pbbls.android.core.designsystem.glyph.GlyphForm
import com.android.tools.screenshot.PreviewTest
import java.io.File

/** Glyphs per page: about 800dp of rows, under the preview's height cap (as [PER_PAGE] for the icons). */
private const val FORM_PER_PAGE = 240

/** One page of a form group. */
data class GlyphFormSheetPage(
    val group: String,
    val index: Int,
)

private val GROUPS = listOf("regular", "solid", "marked", "other")

/** The form a group draws a glyph in, or null when the glyph is not in the group (iOS `GlyphFormSheetReferences`). */
private fun formOf(
    forms: List<GlyphForm>,
    group: String,
): GlyphForm? =
    when (group) {
        "regular" -> GlyphForm.REGULAR
        "solid" -> GlyphForm.SOLID.takeIf { it in forms }
        "marked" -> GlyphForm.MARKED.takeIf { it in forms }
        else ->
            forms.lastOrNull { it.fill is GlyphFill.Progress && !it.marked }
                ?: forms.firstOrNull { it.fill is GlyphFill.Parts }
                ?: GlyphForm.DOTTED.takeIf { it in forms }
    }

/**
 * Each group's glyphs with their forms, read once from the repository's resource (a provider has no Context).
 * The forms come from [GlyphCatalog.readIndex], not `PbblsGlyph.forms`: the catalog logs through `android.util.Log`,
 * whose natives are not linked yet while the screenshot task discovers its parameters (an UnsatisfiedLinkError).
 */
private val DRAWN: Map<String, List<Pair<PbblsGlyph, GlyphForm>>> by lazy {
    val open = { File("src/main/res/raw/pbbls_glyphs.json").inputStream() }
    GlyphCatalog.installSource(open)
    val index = GlyphCatalog.readIndex(open)
    val formsOf =
        PbblsGlyph.entries.associateWith { glyph ->
            index
                .decode(glyph.manifestName)
                ?.formKeys
                .orEmpty()
                .mapNotNull(GlyphForm::fromKey)
        }
    GROUPS.associateWith { group ->
        PbblsGlyph.entries.mapNotNull { glyph -> formOf(formsOf.getValue(glyph), group)?.let { glyph to it } }
    }
}

/** The form groups' pages, one render each. */
class GlyphFormSheetPages : PreviewParameterProvider<GlyphFormSheetPage> {
    override val values: Sequence<GlyphFormSheetPage> =
        GROUPS
            .flatMap { group ->
                (0 until (DRAWN.getValue(group).size + FORM_PER_PAGE - 1) / FORM_PER_PAGE).map { GlyphFormSheetPage(group, it) }
            }.asSequence()
}

/**
 * The glyph set in its forms (spec §4): regular, solid, marked, and the rest
 * (each stepped glyph done, each parted glyph's first parts form, each dotted
 * drawing), at 24dp, in manifest order, in pages of [FORM_PER_PAGE].
 */
@Composable
private fun GlyphFormSheet(page: GlyphFormSheetPage) {
    FlowRow(
        modifier =
            Modifier
                .width(360.dp)
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DRAWN.getValue(page.group).drop(page.index * FORM_PER_PAGE).take(FORM_PER_PAGE).forEach { (glyph, form) ->
            PbblsGlyphImage(glyph = glyph, contentDescription = null, form = form)
        }
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun GlyphFormSheetLight(
    @PreviewParameter(GlyphFormSheetPages::class) page: GlyphFormSheetPage,
) {
    PebblesTheme { GlyphFormSheet(page) }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun GlyphFormSheetDark(
    @PreviewParameter(GlyphFormSheetPages::class) page: GlyphFormSheetPage,
) {
    PebblesTheme { GlyphFormSheet(page) }
}
