package app.pbbls.android.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale

/**
 * Renders [text] in a [PebblesTypography] token. Compose's `TextStyle` has no
 * text-case property, so uppercase tokens (`meta`, `metaEmphasized`,
 * `cardHeading`, `cardHeadingEmphasized`) are uppercased here — bundled with
 * the style so call sites cannot forget it, mirroring iOS `pebblesFont(_:)`.
 */
@Composable
fun PebblesText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val displayText = if (style in PebblesTypography.uppercaseTokens) text.uppercase(Locale.getDefault()) else text
    Text(
        text = displayText,
        style = style,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = overflow,
    )
}
