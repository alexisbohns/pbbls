package app.pbbls.android

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.features.consent.ConsentGateContent
import app.pbbls.android.features.consent.ConsentGateLogic
import app.pbbls.android.features.consent.ConsentGateUiState
import com.android.tools.screenshot.PreviewTest

/**
 * The consent gate (#967): a first ask with all four acts, a re-ask after one
 * document's version moved, and the fail-closed state (design D4).
 */
@PreviewTest
@Preview(showBackground = true)
@PreviewLargeFont
@PreviewFrench
@Composable
fun ConsentGateAllFour() {
    PebblesTheme {
        ConsentGateContent(
            uiState = ConsentGateUiState.Required(missing = ConsentGateLogic.REQUIRED, ticked = setOf(ConsentKind.TERMS)),
            onToggle = { _, _ -> },
            onContinue = {},
            onRetry = {},
            onSignOut = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
fun ConsentGateOneOutdated() {
    PebblesTheme {
        ConsentGateContent(
            uiState = ConsentGateUiState.Required(missing = listOf(ConsentKind.TERMS)),
            onToggle = { _, _ -> },
            onContinue = {},
            onRetry = {},
            onSignOut = {},
        )
    }
}

@PreviewTest
@Preview(showBackground = true)
@Composable
fun ConsentGateFailed() {
    PebblesTheme {
        ConsentGateContent(
            uiState = ConsentGateUiState.Failed(R.string.error_offline),
            onToggle = { _, _ -> },
            onContinue = {},
            onRetry = {},
            onSignOut = {},
        )
    }
}
