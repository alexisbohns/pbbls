package app.pbbls.android.features.lab.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
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
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.LabMarkdown

/**
 * Renders [LabMarkdown] blocks (M44 design D5). Level-1 headings are
 * `headlineMedium`, deeper ones `titleLarge` (iOS's title2/title3 both land on
 * it); paragraphs are `bodyLarge` with inline spans, links underlined in
 * `primary` and opened externally by the platform handler
 * (`LinkAnnotation.Url` → `LocalUriHandler`).
 */
@Composable
fun LabMarkdownBody(
    blocks: List<LabMarkdown.Block>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PebblesTheme.spacing.lg),
    ) {
        blocks.forEach { block ->
            when (block) {
                is LabMarkdown.Block.Heading -> {
                    val style =
                        when (block.level) {
                            1 -> MaterialTheme.typography.headlineMedium
                            else -> MaterialTheme.typography.titleLarge
                        }
                    Text(
                        text = block.text,
                        style = style,
                        color = colors.onSurface,
                    )
                }

                is LabMarkdown.Block.Paragraph ->
                    Text(
                        text = annotated(block.spans, colors.primary),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface,
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
