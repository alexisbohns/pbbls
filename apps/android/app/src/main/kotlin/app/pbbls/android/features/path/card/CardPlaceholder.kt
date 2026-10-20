package app.pbbls.android.features.path.card

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import app.pbbls.android.core.designsystem.PebblesTheme
import app.pbbls.android.core.ui.LocalSharedTransitionScope
import app.pbbls.android.core.ui.sharedCardBounds
import kotlinx.coroutines.flow.first

/** The blank card's height to its width: a held recto's usual shape, photo, legend and footer. */
private const val PLACEHOLDER_ASPECT = 1.3f

/**
 * The page while the moment loads, when its card is flying in from the Spine (#1135): a
 * blank card where the held card will lie, carrying its shared bounds, so the flight starts
 * on the open's first frame rather than when the network answers. A warm load lands the
 * real card mid-flight and the bounds carry on to it; a cold one lands the flight on this
 * blank card, which waits with the spinner until the card replaces it.
 *
 * Seen only once a flight has begun: a moment opened from anywhere else (a soul, a
 * collection, two panes) has no card flying in, and shows the spinner alone, as before.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CardPlaceholder(
    momentId: String,
    modifier: Modifier = Modifier,
) {
    val scope = LocalSharedTransitionScope.current
    var flew by remember { mutableStateOf(false) }
    LaunchedEffect(scope) {
        if (scope != null) {
            snapshotFlow { scope.isTransitionActive }.first { it }
            flew = true
        }
    }
    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.TopCenter) {
            val width = min(maxWidth - CardLayout.PAGE_MARGIN * 2, CardLayout.MAX_WIDTH).coerceAtLeast(0.dp)
            Box(
                sharedCardBounds(momentId)
                    .width(width)
                    .height(width * PLACEHOLDER_ASPECT)
                    // Read in the layer: the flight's first frame shows it, before the effect lands.
                    .graphicsLayer { alpha = if (flew || scope?.isTransitionActive == true) 1f else 0f }
                    .background(PebblesTheme.spine.colors.stock, MaterialTheme.shapes.medium),
            )
        }
        // Not while the card flies: the spinner would show through the flying card's fade.
        if (scope?.isTransitionActive != true) LoadingIndicator(Modifier.align(Alignment.Center))
    }
}
