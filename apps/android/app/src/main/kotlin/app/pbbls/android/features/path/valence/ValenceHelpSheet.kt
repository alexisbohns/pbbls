package app.pbbls.android.features.path.valence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesPrimaryButton
import app.pbbls.android.core.designsystem.Spacing
import app.pbbls.android.core.model.Valence
import kotlinx.coroutines.launch

/** The fan at this fraction of its reference canvas: small enough that fan, four lines and Got it sit together. */
private const val HELP_FAN_SCALE = 0.62f

/**
 * "Two questions, one stone." (D8): opens by itself once over the first valence step of an account
 * with no completed pebble, then lives behind "How to read this". An annotated fan with a lit stone,
 * four lines, Got it — ports iOS `ValenceHelpSheet`.
 *
 * It sizes to its content (`skipPartiallyExpanded`): a half-anchored sheet is laid out at full height
 * and offset down, which would hide Got it under the screen edge. The content scrolls, so a large
 * font reaches it too. The sheet is its own window with its own back dispatcher, so back is handled
 * inside the content and closes it through [ModalBottomSheet]'s own animation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValenceHelpSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val hide: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
        NavigationBackHandler(state = backState) { hide() }
        ValenceHelpContent(onGotIt = hide)
    }
}

/** The sheet's body, stateless so a preview can render it without a window. */
@Composable
fun ValenceHelpContent(
    onGotIt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ValenceFan(
            selected = Valence.HIGHLIGHT_MEDIUM,
            onSelect = {},
            showsCaption = false,
            maxScale = HELP_FAN_SCALE,
            interactive = false,
        )
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            listOf(
                R.string.valence_help_line_1,
                R.string.valence_help_line_2,
                R.string.valence_help_line_3,
                R.string.valence_help_line_4,
            ).forEach { line ->
                Text(text = stringResource(line), style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            }
        }
        PebblesPrimaryButton(text = stringResource(R.string.valence_help_got_it), onClick = onGotIt)
    }
}
