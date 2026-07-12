package app.pbbls.android.features.path.create.pickers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pbbls.android.R
import app.pbbls.android.features.path.create.valencePolarityLabelRes
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.models.ValencePolarity
import app.pbbls.android.features.path.models.ValenceSizeGroup
import app.pbbls.android.features.path.render.ValenceGlyph
import app.pbbls.android.theme.PebblesText
import app.pbbls.android.theme.PebblesTheme
import app.pbbls.android.theme.PebblesTypography

/**
 * The valence picker (D5) — ports iOS `ValencePickerSheet`. A single
 * `ModalBottomSheet` with three size sections (day/week/month), each offering
 * the three polarities (lowlight/neutral/highlight) as tappable [ValenceGlyph]
 * shapes. Tapping commits the [Valence] and dismisses. Pure [ValencePickerBody]
 * is split out for screenshot previews.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValencePickerSheet(
    current: Valence?,
    onDismiss: () -> Unit,
    onSelected: (Valence) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        ValencePickerBody(current = current, onSelected = onSelected)
    }
}

@Composable
fun ValencePickerBody(
    current: Valence?,
    onSelected: (Valence) -> Unit,
    modifier: Modifier = Modifier,
) {
    val system = PebblesTheme.colors.system
    val accent = PebblesTheme.colors.accent
    Column(
        modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ValenceSizeGroup.entries.forEach { size ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PebblesText(
                    text = stringResource(valenceGroupNameRes(size)),
                    style = PebblesTypography.headline,
                    color = system.secondary,
                )
                PebblesText(
                    text = stringResource(valenceGroupDescRes(size)),
                    style = PebblesTypography.subhead,
                    color = system.secondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ValencePolarity.entries.forEach { polarity ->
                        val option = Valence.entries.first { it.sizeGroup == size && it.polarity == polarity }
                        val active = option == current
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (active) accent.primary else system.muted)
                                    .clickable { onSelected(option) }
                                    .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ValenceGlyph(
                                size = size,
                                polarity = polarity,
                                tintColor = if (active) system.background else system.secondary,
                                modifier = Modifier.size(56.dp),
                            )
                            PebblesText(
                                text = stringResource(valencePolarityLabelRes(polarity)),
                                style = PebblesTypography.captionEmphasized,
                                color = if (active) system.background else system.secondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun valenceGroupNameRes(size: ValenceSizeGroup): Int =
    when (size) {
        ValenceSizeGroup.SMALL -> R.string.valence_group_small_name
        ValenceSizeGroup.MEDIUM -> R.string.valence_group_medium_name
        ValenceSizeGroup.LARGE -> R.string.valence_group_large_name
    }

private fun valenceGroupDescRes(size: ValenceSizeGroup): Int =
    when (size) {
        ValenceSizeGroup.SMALL -> R.string.valence_group_small_desc
        ValenceSizeGroup.MEDIUM -> R.string.valence_group_medium_desc
        ValenceSizeGroup.LARGE -> R.string.valence_group_large_desc
    }
