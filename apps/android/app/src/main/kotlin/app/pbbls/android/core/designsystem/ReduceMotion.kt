package app.pbbls.android.core.designsystem

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * True when the user turned animations off (Developer options or the
 * accessibility "Remove animations" switch — both set the animator scale to 0).
 * Previews report true so screenshot references capture end states, not frames.
 * Lifted from the two private copies in WelcomeScreen and ValenceFan (#853).
 */
@Composable
fun rememberReduceMotion(): Boolean {
    if (LocalInspectionMode.current) return true
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}
