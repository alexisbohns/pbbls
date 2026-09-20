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

    /** Clears the stack and seeds [key]. Part 5 drives auth with this. */
    fun replaceAll(key: PebblesKey) {
        backStack.clear()
        backStack.add(key)
    }
}
