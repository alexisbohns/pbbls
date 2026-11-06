package app.pbbls.android.features.path.flow.steps

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.common.takeSafely
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.AttachedSnap
import app.pbbls.android.core.model.FormSnap
import app.pbbls.android.features.path.capture.CaptureDraft
import app.pbbls.android.features.path.capture.CaptureFlowModel
import app.pbbls.android.features.path.capture.WhenSource
import java.time.OffsetDateTime

/** The counter fades in once this many characters are left. */
private const val COUNTDOWN_FROM = 40

/** The thumbnail's side, and the ring over it while the photo uploads. */
private val ThumbSize = 72.dp
private val RingSize = 28.dp
private val RingStroke = 3.dp
private const val RING_SWEEP = 270f
private const val RING_START = -90f
private const val SCRIM_ALPHA = 0.35f

/** A text button whose label sits on the column edge, as the render: the 48 dp target stays, the inset goes. */
private val FlushStart = PaddingValues(end = Spacing.md)

/** The 48 dp touch target. */
private val MinTarget = 48.dp

/**
 * The moment page (record-flow D5, Ledger treatment), ports iOS `RecordMomentStep`: the photo row, then
 * NAME, WHEN and A FEW WORDS, each an uppercase label over a `surfaceContainerLow` block. The buttons live
 * in the scaffold. Stateless: the draft arrives as a parameter and every edit goes up through a callback.
 *
 * Focus lands on NAME only when there is no photo, and the IME's next action moves to the words.
 */
@Composable
fun RecordMomentStep(
    draft: CaptureDraft,
    whenSource: WhenSource,
    snap: FormSnap?,
    @StringRes errorRes: Int?,
    onNameChange: (String) -> Unit,
    onWordsChange: (String) -> Unit,
    onWhenChange: (OffsetDateTime) -> Unit,
    onChangePhoto: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val nameFocus = remember { FocusRequester() }
    val wordsFocus = remember { FocusRequester() }
    // Read once: a photo picked later must not steal focus from wherever the user is typing.
    val hadSnapAtStart = remember { snap != null }
    LaunchedEffect(Unit) { if (!hadSnapAtStart) nameFocus.requestFocus() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        PhotoRow(snap = snap, onChangePhoto = onChangePhoto, onRetry = onRetry, onRemove = onRemove)

        // Under the photo, not at the foot of the page as on iOS: here the page scrolls under the
        // keyboard, and a refused save must be seen without scrolling to the end. There is no line for a
        // photo still uploading: the buttons are disabled and the photo row already says why.
        errorRes?.let {
            Text(text = stringResource(it), style = MaterialTheme.typography.bodyLarge, color = colors.error)
        }

        Field(label = stringResource(R.string.capture_field_name)) {
            ClampedField(
                text = draft.name,
                limit = CaptureFlowModel.NAME_LIMIT,
                onChange = onNameChange,
                textStyle = PebblesTheme.hand.nameInputHand,
                placeholder = stringResource(R.string.record_name_placeholder),
                minLines = 1,
                maxLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { wordsFocus.requestFocus() }),
                focusRequester = nameFocus,
                // A name is one line: a return key (a hardware keyboard sends one) moves on, as the IME's next does.
                onNewline = { wordsFocus.requestFocus() },
                label = stringResource(R.string.capture_field_name),
            )
        }

        Field(label = stringResource(R.string.capture_field_when)) {
            RecordWhenChip(happenedAt = draft.happenedAt, source = whenSource, onChange = onWhenChange)
        }

        Field(label = stringResource(R.string.capture_field_words)) {
            val remaining = (CaptureFlowModel.DESCRIPTION_LIMIT - draft.description.length).coerceAtLeast(0)
            val showsCountdown = remaining <= COUNTDOWN_FROM
            // Always laid out, only faded: the block must not jump when the counter appears mid-typing.
            val countdownAlpha by animateFloatAsState(
                targetValue = if (showsCountdown) 1f else 0f,
                animationSpec = tween(durationMillis = 200),
                label = "momentWordsCountdownAlpha",
            )
            val remainingLabel = stringResource(R.string.record_name_remaining_a11y, remaining)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ClampedField(
                    text = draft.description,
                    limit = CaptureFlowModel.DESCRIPTION_LIMIT,
                    onChange = onWordsChange,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = stringResource(R.string.capture_words_placeholder),
                    minLines = 3,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                    keyboardActions = KeyboardActions.Default,
                    focusRequester = wordsFocus,
                    label = stringResource(R.string.capture_field_words),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PbblsIconImage(
                        icon = PbblsIcon.KARMA_DOT,
                        size = PbblsIconSize.INLINE,
                        contentDescription = null,
                        tint = colors.primary,
                    )
                    Text(
                        text = stringResource(R.string.capture_words_karma),
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.sm).weight(1f),
                    )
                    Text(
                        text = remaining.toString(),
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = colors.onSurfaceVariant,
                        modifier =
                            Modifier.alpha(countdownAlpha).clearAndSetSemantics {
                                if (showsCountdown) contentDescription = remainingLabel
                            },
                    )
                }
            }
        }
    }
}

/** The uppercase label over a rounded block, as the render. */
@Composable
private fun Field(
    label: String,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.surfaceContainerLow)
                    .padding(Spacing.md),
        ) {
            content()
        }
    }
}

/**
 * A text field that owns the text it renders.
 *
 * **The clamp writes into the state the field itself renders.** Handing `BasicTextField` a
 * [TextFieldValue] whose `onValueChange` truncates *without* writing the truncated value back into local
 * state is the bug iOS shipped and fixed: the text control keeps its own buffer, so the extra characters
 * stay on screen and are only trimmed at publish, and the field says one thing while the saved moment says
 * another. Local state is the fix; [onChange] carries the already-clamped value up.
 */
@Composable
private fun ClampedField(
    text: String,
    limit: Int,
    onChange: (String) -> Unit,
    textStyle: TextStyle,
    placeholder: String,
    minLines: Int,
    maxLines: Int,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    focusRequester: FocusRequester,
    onNewline: (() -> Unit)? = null,
    label: String,
) {
    val colors = MaterialTheme.colorScheme
    // [text] only seeds the field. The model's value reaches here through a flow, so it lags the
    // keyboard, and resyncing to it undid keystrokes when text came quickly (seen on a Pixel 8). A
    // restored draft needs no resync: it is in place before the first frame, and nothing else writes.
    var field by remember { mutableStateOf(TextFieldValue(text = text, selection = TextRange(text.length))) }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (field.text.isEmpty()) {
            Text(text = placeholder, style = textStyle, color = colors.onSurfaceVariant)
        }
        BasicTextField(
            value = field,
            onValueChange = { raw ->
                val hadNewline = onNewline != null && raw.text.contains('\n')
                val incoming =
                    if (hadNewline) {
                        raw.copy(
                            text = raw.text.replace("\n", ""),
                            selection = TextRange(raw.text.replace("\n", "").length),
                        )
                    } else {
                        raw
                    }
                val clamped = incoming.text.takeSafely(limit)
                // Assigning state (not just reporting upward) is what actually rewrites the visible field.
                field =
                    if (clamped == incoming.text) {
                        incoming
                    } else {
                        incoming.copy(text = clamped, selection = TextRange(minOf(incoming.selection.end, clamped.length)))
                    }
                onChange(clamped)
                if (hadNewline) onNewline?.invoke()
            },
            textStyle = textStyle.copy(color = colors.onSurface),
            cursorBrush = SolidColor(colors.primary),
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).semantics { contentDescription = label },
        )
    }
}

/** The photo's row: the thumbnail with its state and "Change photo", or "Add a photo" when there is none. */
@Composable
private fun PhotoRow(
    snap: FormSnap?,
    onChangePhoto: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    when (snap) {
        is FormSnap.Pending -> {
            val attached = snap.snap
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Thumbnail(attached)
                Column(modifier = Modifier.weight(1f)) {
                    val (label, color) = stateLabel(attached.state)
                    Text(text = label, style = MaterialTheme.typography.bodyMedium, color = color(colors))
                    TextButton(onClick = onChangePhoto, modifier = Modifier.heightIn(min = MinTarget), contentPadding = FlushStart) {
                        Text(
                            text = stringResource(R.string.capture_change_photo),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                        )
                    }
                }
                if (attached.state == AttachedSnap.UploadState.FAILED) {
                    TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = MinTarget)) {
                        Text(
                            text = stringResource(R.string.record_photo_retry),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                        )
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(MinTarget)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_x_circle),
                        contentDescription = stringResource(R.string.photo_remove_a11y),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        }
        // Unreachable from Capture: a restored photo is re-seeded as an uploaded pending one.
        is FormSnap.Existing -> Unit
        null ->
            TextButton(onClick = onChangePhoto, modifier = Modifier.heightIn(min = MinTarget), contentPadding = FlushStart) {
                PbblsIconImage(
                    icon = PbblsIcon.CAMERA,
                    size = PbblsIconSize.ROW,
                    contentDescription = null,
                    tint = colors.primary,
                )
                Text(
                    text = stringResource(R.string.photo_add),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.primary,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
    }
}

@Composable
private fun stateLabel(
    state: AttachedSnap.UploadState,
): Pair<String, (androidx.compose.material3.ColorScheme) -> androidx.compose.ui.graphics.Color> =
    when (state) {
        AttachedSnap.UploadState.UPLOADING -> stringResource(R.string.capture_photo_uploading) to { it.onSurfaceVariant }
        AttachedSnap.UploadState.UPLOADED -> stringResource(R.string.photo_state_ready) to { it.onSurfaceVariant }
        AttachedSnap.UploadState.FAILED -> stringResource(R.string.photo_state_failed) to { it.error }
    }

/**
 * 72 dp with `shapes.medium`. The upload ring is a static 270 degree arc over the thumbnail (a still
 * picture, no motion to gate). A restored snap has no thumbnail bytes: the slot stays an empty rounded
 * square. Hidden from TalkBack: the state label beside it says everything.
 */
@Composable
private fun Thumbnail(snap: AttachedSnap) {
    val colors = MaterialTheme.colorScheme
    val bitmap =
        remember(snap.localThumb) {
            runCatching {
                android.graphics.BitmapFactory
                    .decodeByteArray(snap.localThumb, 0, snap.localThumb.size)
                    ?.asImageBitmap()
            }.getOrNull()
        }
    Box(
        modifier =
            Modifier
                .size(ThumbSize)
                .clip(MaterialTheme.shapes.medium)
                .background(colors.surfaceContainerHigh)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(ThumbSize))
        }
        if (snap.state == AttachedSnap.UploadState.UPLOADING) {
            val ring = colors.onPrimary
            val scrim = colors.scrim.copy(alpha = SCRIM_ALPHA)
            Canvas(modifier = Modifier.size(RingSize)) {
                drawCircle(color = scrim)
                val stroke = RingStroke.toPx()
                drawArc(
                    color = ring,
                    startAngle = RING_START,
                    sweepAngle = RING_SWEEP,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}
