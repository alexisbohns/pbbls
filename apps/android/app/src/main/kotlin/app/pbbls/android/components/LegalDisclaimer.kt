package app.pbbls.android.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.sp
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * "Read our [Terms] and [Privacy] before creating an account…" disclaimer with
 * two inline tappable links. Ports `LegalDisclaimerText.swift`. The iOS version
 * embeds `pebbles://legal/...` markdown links intercepted by an `OpenURLAction`;
 * here the links are [LinkAnnotation.Clickable]s whose listener calls the tap
 * handler directly — the custom scheme never reaches the OS, same net behavior.
 *
 * The sentence is a single localized template ([R.string.welcome_legal_disclaimer])
 * with `{terms}`/`{privacy}` tokens, so translations control word order and the
 * links stay inline in both en and fr.
 */
@Composable
fun LegalDisclaimer(
    onTermsTap: () -> Unit,
    onPrivacyTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val template = stringResource(R.string.welcome_legal_disclaimer)
    val termsLabel = stringResource(R.string.legal_link_terms)
    val privacyLabel = stringResource(R.string.legal_link_privacy)
    val linkStyles = TextLinkStyles(SpanStyle(color = accent.primary, textDecoration = TextDecoration.Underline))

    val text =
        buildLegalDisclaimer(
            template = template,
            tokens =
                listOf(
                    LegalToken("{terms}", termsLabel, onTermsTap),
                    LegalToken("{privacy}", privacyLabel, onPrivacyTap),
                ),
            linkStyles = linkStyles,
        )

    Text(
        text = text,
        style = PebblesTypography.subhead.copy(fontSize = 12.sp),
        color = system.secondary,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

private data class LegalToken(
    val token: String,
    val label: String,
    val onTap: () -> Unit,
)

/**
 * Assembles the disclaimer by scanning [template] for each token in order of
 * appearance, appending literal text between tokens and a styled link for each.
 * Deterministic — link placement follows the localized template, so word order
 * is respected per locale.
 */
private fun buildLegalDisclaimer(
    template: String,
    tokens: List<LegalToken>,
    linkStyles: TextLinkStyles,
): AnnotatedString =
    buildAnnotatedString {
        var cursor = 0
        while (cursor < template.length) {
            val next =
                tokens
                    .mapNotNull { t ->
                        val idx = template.indexOf(t.token, cursor)
                        if (idx >= 0) idx to t else null
                    }.minByOrNull { it.first }
            if (next == null) {
                append(template.substring(cursor))
                break
            }
            val (idx, matched) = next
            if (idx > cursor) append(template.substring(cursor, idx))
            withLink(LinkAnnotation.Clickable(tag = matched.token, styles = linkStyles) { _ -> matched.onTap() }) {
                append(matched.label)
            }
            cursor = idx + matched.token.length
        }
    }
