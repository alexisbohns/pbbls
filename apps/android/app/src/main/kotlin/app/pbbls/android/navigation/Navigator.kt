package app.pbbls.android.navigation

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * The only writer of navigation state (#852).
 *
 * Part 1 backs this with one flat [NavBackStack], preserving the IA exactly as
 * the two NavHosts had it. Part 2 swaps the internals for four per-tab stacks
 * without touching a single call site — which is why the seam exists now rather
 * than arriving with the tabs.
 *
 * The back stack is typed [NavKey] rather than [PebblesKey]: 1.1.7's Android
 * `rememberNavBackStack(vararg elements: NavKey)` (the reflection-based
 * saveable overload — see its KDoc) returns `NavBackStack<NavKey>`, not a
 * per-call generic `NavBackStack<T>`. [navigate] and [replaceAll] still only
 * accept a [PebblesKey], so callers get the same compile-time safety.
 */
@Stable
class Navigator(
    private val backStack: NavBackStack<NavKey>,
) {
    fun navigate(key: PebblesKey) {
        backStack.add(key)
    }

    fun goBack() {
        // Never pop the last entry: an empty back stack has nothing to render,
        // and NavDisplay throws rather than closing the app. Letting the system
        // handle back at the root is what exits.
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** The key at the bottom of the stack — what the stack is currently rooted at. */
    val rootKey: NavKey?
        get() = backStack.firstOrNull()

    /** Clears the stack and seeds [key]. The auth gate drives this (#852, D8). */
    fun replaceAll(key: PebblesKey) {
        backStack.clear()
        backStack.add(key)
    }

    /**
     * Re-root the stack at [key], but **only if it is not already rooted there**.
     *
     * This is what the auth gate must call rather than [replaceAll] directly,
     * and the distinction is not cosmetic. The gate runs on every resolution of
     * the session, including a **cold restore** — and by then
     * `rememberNavBackStack` has already restored the saved stack. An
     * unconditional `replaceAll(Path)` at that moment throws away the very thing
     * process-death restoration exists to preserve: a user killed on Settings
     * comes back to Path, silently, with every test still green.
     *
     * Rooting is the right signal because it distinguishes the two cases exactly:
     * a genuine sign-in has the stack rooted at `Welcome` (so it re-roots), while
     * a cold restore of an authed session is already rooted at `Path` (so it is
     * left alone, deep entries and all).
     */
    fun rootAt(key: PebblesKey) {
        if (rootKey == key) return
        replaceAll(key)
    }
}
