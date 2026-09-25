package app.pbbls.android.core.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.pbbls.android.core.model.AuthMode

/**
 * Login / Sign-up switcher — an M3 Expressive connected button group: one
 * `ToggleButton` per [AuthMode] with the connected leading / trailing shapes,
 * in a `selectableGroup` with the radio-button role, so TalkBack reads
 * "Log In, selected, 1 of 2" rather than a tab with no state (#854).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PebblesAuthSwitcher(
    mode: AuthMode,
    onModeChange: (AuthMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = AuthMode.entries
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        modes.forEachIndexed { index, entry ->
            ToggleButton(
                checked = entry == mode,
                onCheckedChange = { onModeChange(entry) },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
            ) {
                Text(text = stringResource(entry.labelRes))
            }
        }
    }
}
