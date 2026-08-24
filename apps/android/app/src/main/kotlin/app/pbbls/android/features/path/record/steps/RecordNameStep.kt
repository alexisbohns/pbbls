package app.pbbls.android.features.path.record.steps

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import app.pbbls.android.R
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography
import app.pbbls.android.theme.Spacing

/**
 * Step 2 — what to call it — ports iOS `RecordNameStep`.
 *
 * A bare field on a bare page: no input background, the handwritten Caveat
 * face, centered, with a countdown that fades in near the limit instead of a
 * permanent counter.
 *
 * **The clamp writes into the state the field itself renders.** Handing
 * `BasicTextField` a [TextFieldValue] whose `onValueChange` truncates *without*
 * writing the truncated value back into local state is the same bug iOS shipped
 * and fixed: the text control keeps its own buffer, so the extra characters stay
 * on screen and are only trimmed at publish — the field says one thing and the
 * saved pebble says another. Local state is the fix; [onChange] carries the
 * already-clamped value up.
 */
@Composable
fun RecordNameStep(
    name: String,
    limit: Int,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    val focusRequester = remember { FocusRequester() }

    var field by remember {
        mutableStateOf(TextFieldValue(text = name, selection = TextRange(name.length)))
    }

    // Resync if the model's value moves on its own (a resumed or reset draft).
    // A no-op while typing, since the model holds exactly what we just sent it.
    LaunchedEffect(name) {
        if (name != field.text) {
            field = field.copy(text = name, selection = TextRange(name.length))
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val remaining = (limit - field.text.length).coerceAtLeast(0)
    val showsCountdown = remaining <= COUNTDOWN_FROM
    // Always laid out, only faded — otherwise the field jumps when the counter
    // appears mid-typing.
    val countdownAlpha by animateFloatAsState(
        targetValue = if (showsCountdown) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "recordNameCountdownAlpha",
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (field.text.isEmpty()) {
                PebblesText(
                    text = stringResource(R.string.record_name_placeholder),
                    style = PebblesTypography.nameInputHand,
                    color = system.muted,
                    textAlign = TextAlign.Center,
                )
            }
            BasicTextField(
                value = field,
                onValueChange = { incoming ->
                    val clamped = incoming.text.take(limit)
                    // Assigning state (not just reporting upward) is what
                    // actually rewrites the visible field.
                    field =
                        if (clamped == incoming.text) {
                            incoming
                        } else {
                            incoming.copy(
                                text = clamped,
                                selection = TextRange(minOf(incoming.selection.end, clamped.length)),
                            )
                        }
                    onChange(clamped)
                },
                textStyle =
                    PebblesTypography.nameInputHand.copy(
                        color = system.foreground,
                        textAlign = TextAlign.Center,
                    ),
                cursorBrush = SolidColor(accent.primary),
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(Spacing.md)
                        .focusRequester(focusRequester),
            )
        }

        val remainingLabel = stringResource(R.string.record_name_remaining_a11y, remaining)
        PebblesText(
            text = remaining.toString(),
            style = PebblesTypography.subhead,
            color = if (remaining == 0) accent.primary else system.secondary,
            modifier =
                Modifier
                    .alpha(countdownAlpha)
                    .clearAndSetSemantics {
                        if (showsCountdown) contentDescription = remainingLabel
                    },
        )
    }
}

/** The counter stays out of the way until the end is in sight. */
private const val COUNTDOWN_FROM = 15
