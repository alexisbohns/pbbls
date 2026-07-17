package app.pbbls.android.features.lab.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import app.pbbls.android.features.lab.models.LabMarkdown
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * Renders [LabMarkdown] blocks (M44 design D5). Headings map to the `title`
 * token at 28/22/20sp (no title2/title3 tokens exist — the iOS sizes are
 * applied to the one display face); paragraphs are `body` with inline spans,
 * links underlined in accent and opened externally by the platform handler
 * (`LinkAnnotation.Url` → `LocalUriHandler`).
 */
@Composable
fun LabMarkdownBody(
    blocks: List<LabMarkdown.Block>,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
    ) {
        blocks.forEach { block ->
            when (block) {
                is LabMarkdown.Block.Heading -> {
                    val style =
                        when (block.level) {
                            1 -> PebblesTypography.title
                            2 -> PebblesTypography.title.copy(fontSize = 22.sp)
                            else -> PebblesTypography.title.copy(fontSize = 20.sp)
                        }
                    PebblesText(
                        text = block.text,
                        style = style,
                        color = system.foreground,
                    )
                }

                is LabMarkdown.Block.Paragraph ->
                    Text(
                        text = annotated(block.spans, accent.primary),
                        style = PebblesTypography.body,
                        color = system.foreground,
                    )
            }
        }
    }
}

private fun annotated(
    spans: List<LabMarkdown.Span>,
    linkColor: Color,
): AnnotatedString =
    buildAnnotatedString {
        spans.forEach { span ->
            val style =
                SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    fontFamily = if (span.code) FontFamily.Monospace else null,
                    textDecoration =
                        when {
                            span.strikethrough -> TextDecoration.LineThrough
                            span.linkUrl != null -> TextDecoration.Underline
                            else -> null
                        },
                    color = if (span.linkUrl != null) linkColor else Color.Unspecified,
                )
            val url = span.linkUrl
            if (url != null) {
                withLink(LinkAnnotation.Url(url, TextLinkStyles(style = style))) { append(span.text) }
            } else {
                withStyle(style) { append(span.text) }
            }
        }
    }
