package app.pbbls.android.features.path

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.ui.LocalSharedTransitionScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The loading page a card flies onto (#1135), inside a shared scope as `RootScreen` provides
 * it on one pane: the blank card's shared bounds find their animated scope, the spinner shows,
 * and the blank card says nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CardFlightLoadingTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun `the blank card waits silently under a shared scope`() {
        compose.setContent {
            PebblesTheme {
                SharedTransitionLayout {
                    AnimatedContent(targetState = "m1", label = "flight") { momentId ->
                        CompositionLocalProvider(
                            LocalSharedTransitionScope provides this@SharedTransitionLayout,
                            LocalNavAnimatedContentScope provides this,
                        ) {
                            PebbleDetailContent(
                                uiState = PebbleDetailUiState.Loading,
                                palette = null,
                                onEditMoment = {},
                                onEditPebble = {},
                                onDelete = {},
                                onShare = null,
                                onRetry = {},
                                photo = null,
                                ratio = null,
                                soulsById = emptyMap(),
                                domains = emptyList(),
                                showBack = true,
                                onBack = {},
                                flightMomentId = momentId,
                            )
                        }
                    }
                }
            }
        }

        compose
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertCountEquals(1)
        // Only the top bar speaks or acts: back and the ⋮ menu. The blank card adds nothing.
        compose.onNodeWithContentDescription(context.getString(R.string.profile_back_a11y)).assertExists()
        compose.onAllNodes(hasClickAction()).assertCountEquals(2)
        compose
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription) or
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),
            ).assertCountEquals(2)
    }
}
