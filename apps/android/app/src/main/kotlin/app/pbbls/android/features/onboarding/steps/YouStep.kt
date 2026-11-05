package app.pbbls.android.features.onboarding.steps

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.pbbls.android.R
import app.pbbls.android.core.common.JourneyTags
import app.pbbls.android.core.designsystem.PageTypography
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.Glyph

/** The name field's tag, for the tests. */
const val ONBOARDING_YOU_NAME_TAG = "onboarding_you_name"

/**
 * Step 4 (#1510 §1): a large bare name field on a rule (a `TextField` with transparent containers
 * and only its indicator line), the 3×3 glyph stone under it; with the keyboard up the stone folds
 * into one row of four. Continue needs a name or a glyph; Skip is the top bar's.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun YouStep(
    name: String,
    glyphs: List<Glyph>,
    selectedGlyphId: String?,
    onNameChange: (String) -> Unit,
    onGlyphSelect: (Glyph) -> Unit,
    onDraw: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val imeUp = WindowInsets.isImeVisible
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        StepTitle(stringResource(R.string.onboarding_you_title))
        val nameLabel = stringResource(R.string.onboarding_you_name_label)
        val nameSource = remember { MutableInteractionSource() }
        val nameFocused by nameSource.collectIsFocusedAsState()
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            BasicTextField(
                value = name,
                onValueChange = onNameChange,
                textStyle = PageTypography.greeting.copy(color = colors.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(colors.primary),
                interactionSource = nameSource,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = nameLabel }
                        .testTag(ONBOARDING_YOU_NAME_TAG),
            )
            HorizontalDivider(
                thickness = if (nameFocused) 2.dp else 1.dp,
                color = if (nameFocused) colors.primary else colors.outline,
            )
            Text(
                nameLabel,
                style = MaterialTheme.typography.bodySmall,
                color = if (nameFocused) colors.primary else colors.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.onboarding_you_glyph_label).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.14.em),
            color = colors.onSurfaceVariant,
        )
        GlyphStoneGrid(glyphs = glyphs, selectedId = selectedGlyphId, onSelect = onGlyphSelect, onDraw = onDraw, folded = imeUp)
        Spacer(
            Modifier
                .weight(1f)
                .height(Spacing.lg),
        )
        PebblesPrimaryButton(
            text = stringResource(R.string.onboarding_you_cta),
            onClick = onContinue,
            enabled = name.isNotBlank() || selectedGlyphId != null,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(JourneyTags.ONBOARDING_NEXT),
        )
    }
}
