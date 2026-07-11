package app.pbbls.android.rive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.pbbls.android.R
import app.rive.runtime.kotlin.RiveAnimationView

/**
 * Wraps the bundled `pbbls_logo_appear_idle.riv` — default artboard, default
 * timeline, autoplay. The file has no named state machine, mirroring iOS
 * `WelcomeView.swift`'s `RiveViewModel(fileName:)` call, which also passes no
 * artboard/state-machine args. Consumed by Welcome in sub-project C; B proves
 * it plays via the debug token-preview screen.
 *
 * `RiveAnimationView`'s constructor loads Rive's native library, which isn't
 * available in the JVM-hosted Layoutlib renderer Compose Preview Screenshot
 * Testing uses (no device/emulator) — swap in a placeholder there via
 * [LocalInspectionMode] so `updateDebugScreenshotTest` doesn't crash; a real
 * device/emulator (`LocalInspectionMode.current == false`) always gets the
 * real animated view.
 */
@Composable
fun RiveLogo(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        Box(
            modifier = modifier.background(Color(0x14000000)).border(1.dp, Color(0x33000000)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Rive logo (native view — unavailable in preview)",
                modifier = Modifier.padding(8.dp),
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            RiveAnimationView(context).apply {
                setRiveResource(resId = R.raw.pbbls_logo_appear_idle, autoplay = true)
            }
        },
    )
}
