package app.pbbls.android.features.onboarding.steps

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.pbbls.android.core.designsystem.PageTypography

/** Every step's title (#1510): the mocks' 30sp light Ysabeau, the app's section head. */
@Composable
internal fun StepTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(text, style = PageTypography.sectionHead, modifier = modifier.semantics { heading() })
}
