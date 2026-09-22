package app.pbbls.android.core.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * One-shot effects out of a ViewModel: navigate, show a snackbar, buzz (#849).
 *
 * A `StateFlow` is the wrong shape for these. State is a value the UI re-reads
 * whenever it recomposes; an effect must happen exactly once, and a `StateFlow`
 * replays its latest value to every new collector — which is every
 * configuration change. Put "navigate back" in state and the screen navigates
 * back again after a rotation.
 *
 * A [Channel] has the opposite semantics, and they are the ones wanted: each
 * element is delivered to exactly one collector, and an effect emitted while
 * nobody is collecting waits in the buffer rather than being dropped. That last
 * part is what makes the pair with [ObserveUiEffects] safe — the collector stops
 * below `STARTED`, so an effect emitted while the screen is backgrounded is
 * delivered when it comes back, not thrown away and not delivered to a
 * destroyed view.
 *
 * The [scope] is the owner's — `viewModelScope` at every call site — so [emit]
 * is callable from a plain (non-suspend) handler and never has to drop an
 * element the way `trySend` does against a full buffer:
 *
 * ```
 * @HiltViewModel
 * class XViewModel @Inject constructor(...) : ViewModel() {
 *     private val _effects = UiEffects<XEffect>(viewModelScope)
 *     val effects: Flow<XEffect> = _effects.flow
 *
 *     fun onSaved() = _effects.emit(XEffect.NavigateBack)
 * }
 * ```
 */
class UiEffects<T>(
    private val scope: CoroutineScope,
) {
    private val channel = Channel<T>(Channel.BUFFERED)

    /** Collect through [ObserveUiEffects], never with a bare `collect`. */
    val flow: Flow<T> = channel.receiveAsFlow()

    /**
     * Queue [effect] for the collector.
     *
     * Ordering holds: `viewModelScope` dispatches on `Main.immediate`, so a call
     * already on the main thread runs the `send` inline, and a buffered channel
     * with room does not suspend.
     */
    fun emit(effect: T) {
        scope.launch { channel.send(effect) }
    }
}

/**
 * Collects [effects] under `repeatOnLifecycle(STARTED)` and runs [onEffect] for
 * each one.
 *
 * `STARTED` rather than a bare `LaunchedEffect` because the whole point is to
 * not act on an effect while the screen is off-screen: a `navigate` dispatched
 * to a stopped `NavController` throws, and a backgrounded screen that consumes
 * its snackbar shows the user nothing. The collector is torn down on `STOP` and
 * restarted on `START`, and [UiEffects]'s buffer holds whatever arrived in
 * between.
 *
 * [onEffect] is read through [rememberUpdatedState] so a recomposition with a
 * fresh lambda (a new `navController` capture, say) does not restart the
 * collection — restarting it is how a buffered effect gets consumed twice.
 */
@Composable
fun <T> ObserveUiEffects(
    effects: Flow<T>,
    onEffect: (T) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnEffect by rememberUpdatedState(onEffect)
    LaunchedEffect(effects, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effects.collect { currentOnEffect(it) }
        }
    }
}
