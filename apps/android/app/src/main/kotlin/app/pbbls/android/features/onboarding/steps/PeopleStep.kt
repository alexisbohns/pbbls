package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PbblsIcon
import app.pbbls.android.core.designsystem.PbblsIconImage
import app.pbbls.android.core.designsystem.PbblsIconSize
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.designsystem.stone.SplitStone
import app.pbbls.android.core.designsystem.stone.StoneGeometry
import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.ui.GlyphView
import app.pbbls.android.core.ui.GlyphViewCase
import app.pbbls.android.features.onboarding.OnboardingPerson

/** The empty place at [index], for the tests. */
fun onboardingPersonAddTag(index: Int): String = "onboarding_person_add_$index"

/** The name field under place [index], for the tests. */
fun onboardingPersonNameTag(index: Int): String = "onboarding_person_name_$index"

/** The "Change glyph" under place [index], for the tests. */
fun onboardingPersonGlyphTag(index: Int): String = "onboarding_person_glyph_$index"

private const val PLACES = 3

/**
 * Step 5 (#1510 §1, D7): "Who would you call first with good news?" One split stone of three
 * pieces (the Home quick actions seed), no shelf line, no outlines. A named person's piece is
 * `primaryContainer` with their glyph; the name and "Change glyph" sit under it. The empty place is
 * a dashed outline with a plus and "Add one"; tapping it opens the name field in place under the
 * piece, keyboard up (S3). The glyph sheet is S2. Continue needs one committed person; Later is
 * the top bar's.
 */
@Composable
fun PeopleStep(
    people: List<OnboardingPerson>,
    glyphs: List<Glyph>,
    onAdd: () -> Unit,
    onNameChange: (Int, String) -> Unit,
    onCommit: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onChangeGlyph: (Int) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val fills =
        List(PLACES) { index ->
            if (people.getOrNull(index)?.editing == false) colors.primaryContainer else Color.Transparent
        }
    val dashColor = colors.outline
    val dashCorner = MaterialTheme.shapes.extraLarge.topStart
    val canAdd = people.size < PLACES && people.none { it.editing }
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        Text(
            stringResource(R.string.onboarding_people_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.onboarding_people_body), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        SplitStone(n = PLACES, seed = StoneGeometry.QUICK_ACTIONS_SEED, fills = fills, minHeight = PIECE_HEIGHT) { index ->
            val person = people.getOrNull(index)
            val glyph = person?.glyphId?.let { id -> glyphs.firstOrNull { it.id == id } } ?: glyphs.firstOrNull()
            Box(
                Modifier
                    .fillMaxSize()
                    .pieceClip(index),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    person == null ->
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(Spacing.xs)
                                .drawBehind {
                                    val stroke = DASH_WIDTH.toPx()
                                    val inset = stroke / 2f
                                    drawRoundRect(
                                        color = dashColor,
                                        topLeft = Offset(inset, inset),
                                        size = Size(size.width - stroke, size.height - stroke),
                                        cornerRadius = CornerRadius(dashCorner.toPx(size, this) - inset),
                                        style =
                                            Stroke(
                                                width = stroke,
                                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH.toPx(), DASH.toPx())),
                                            ),
                                    )
                                }.clickable(enabled = canAdd, role = Role.Button, onClick = onAdd)
                                .testTag(onboardingPersonAddTag(index)),
                            contentAlignment = Alignment.Center,
                        ) {
                            PbblsIconImage(
                                PbblsIcon.PLUS_CIRCLE,
                                PbblsIconSize.TILE,
                                contentDescription = stringResource(R.string.onboarding_people_add),
                                tint = colors.onSurfaceVariant,
                            )
                        }
                    person.editing ->
                        PbblsIconImage(PbblsIcon.PEBBLE_ROUND, PbblsIconSize.TILE, contentDescription = null, tint = colors.outline)
                    else ->
                        if (glyph != null) {
                            GlyphView(case = GlyphViewCase.SELECTED, strokes = glyph.strokes, viewBox = glyph.viewBox, side = GLYPH_SIDE)
                        }
                }
            }
        }
        // Under the pieces: the name (or the field being typed), and "Change glyph" / "Add one".
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            repeat(PLACES) { index ->
                val person = people.getOrNull(index)
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    when {
                        person == null ->
                            Text(
                                stringResource(R.string.onboarding_people_add),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        person.editing -> PersonNameField(person.name, index, onNameChange, onCommit)
                        else -> {
                            Text(
                                person.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                            TextButton(onClick = { onChangeGlyph(index) }, modifier = Modifier.testTag(onboardingPersonGlyphTag(index))) {
                                Text(stringResource(R.string.onboarding_people_change_glyph), style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(onClick = { onRemove(index) }) {
                                Text(
                                    stringResource(R.string.onboarding_people_remove, person.name),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(
            Modifier
                .weight(1f)
                .height(Spacing.lg),
        )
        PebblesPrimaryButton(
            text = stringResource(R.string.onboarding_continue),
            onClick = onContinue,
            enabled = people.any { !it.editing },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(JourneyTags.ONBOARDING_NEXT),
        )
    }
}

/** S3: the name typed in place under the piece, focused as it appears; Done commits. */
@Composable
private fun PersonNameField(
    name: String,
    index: Int,
    onNameChange: (Int, String) -> Unit,
    onCommit: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    // Focus arrives after the first composition: only a loss after a gain commits (a blank name
    // removes the place), so the field's initial unfocused state never drops it before a keystroke.
    var wasFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = name,
        onValueChange = { onNameChange(index, it) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleSmall.copy(color = colors.onSurface, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(index) }),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (name.isEmpty()) {
                    Text(
                        stringResource(R.string.onboarding_people_name_hint),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                inner()
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .onFocusChanged {
                    if (it.isFocused) {
                        wasFocused = true
                    } else if (wasFocused) {
                        wasFocused = false
                        onCommit(index)
                    }
                }.testTag(onboardingPersonNameTag(index)),
    )
}

private val PIECE_HEIGHT = 104.dp
private val GLYPH_SIDE = 56.dp
private val DASH_WIDTH = 1.5.dp
private val DASH = 6.dp
