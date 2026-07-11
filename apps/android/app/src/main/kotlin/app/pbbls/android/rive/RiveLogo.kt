package app.pbbls.android.rive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.pbbls.android.R
import app.rive.runtime.kotlin.RiveAnimationView

/**
 * Wraps the bundled `pbbls_logo_appear_idle.riv` — default artboard, default
 * timeline, autoplay. The file has no named state machine, mirroring iOS
 * `WelcomeView.swift`'s `RiveViewModel(fileName:)` call, which also passes no
 * artboard/state-machine args. Consumed by Welcome in sub-project C; B proves
 * it plays via the debug token-preview screen.
 */
@Composable
fun RiveLogo(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            RiveAnimationView(context).apply {
                setRiveResource(resId = R.raw.pbbls_logo_appear_idle, autoplay = true)
            }
        },
    )
}
