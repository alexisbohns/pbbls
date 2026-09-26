package app.pbbls.android.navigation

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The only writer of [NavigationState] (#852).
 *
 * Part 1 backed this with one flat stack; the call sites written then are
 * unchanged, which is what the seam was for.
 */
@Stable
class Navigator(
    val state: NavigationState,
) {
    private val _reselectEvents = MutableSharedFlow<PebblesKey>(extraBufferCapacity = 1)

    /** Emitted when a tab is reselected, for screens that also reset scroll. */
    val reselectEvents = _reselectEvents.asSharedFlow()

    /**
     * A [TopLevelKey] switches tab; anything else pushes onto the current tab's
     * own stack (D6).
     */
    fun navigate(key: PebblesKey) {
        if (key is TopLevelKey) {
            state.topLevelRoute = key
        } else {
            state.currentStack.add(key)
        }
    }

    /**
     * Opens a detail, replacing the one on top if it is the same kind (#940).
     *
     * Beside a list pane the list stays tappable, and pushing each pick would
     * make back walk through every soul looked at. On a phone the detail covers
     * its list, so only the push branch is reachable there. A tab root is
     * never replaced, whatever its type.
     */
    fun navigateToDetail(key: PebblesKey) {
        val stack = state.currentStack
        if (stack.size > 1 && stack.last()::class == key::class) {
            stack[stack.lastIndex] = key
        } else {
            navigate(key)
        }
    }

    /**
     * Pops [key] if it is the detail on top of the current tab (#940).
     *
     * Beside a list pane the list's long-press delete can remove the very item
     * whose detail is open, which would leave it showing a deleted soul or
     * collection. On a phone that cannot happen: the detail covers its list.
     * Anything else on top (another detail, a form, the tab root) is left
     * alone.
     */
    fun closeDetail(key: PebblesKey) {
        val stack = state.currentStack
        if (stack.size > 1 && stack.last() == key) {
            stack.removeAt(stack.lastIndex)
        }
    }

    /**
     * Unwind the current tab, then fall back to the start route, then let the
     * system exit (D4). Popping the start route's last entry is deliberately a
     * no-op: an empty stack has nothing to render.
     */
    fun goBack() {
        val stack = state.currentStack
        if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        } else if (state.topLevelRoute != state.startRoute) {
            state.topLevelRoute = state.startRoute
        }
    }

    /** Reselecting a tab pops it to its root and signals anyone resetting scroll. */
    fun onReselect(key: PebblesKey) {
        val stack = state.backStacks[key] ?: return
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        state.topLevelRoute = key
        _reselectEvents.tryEmit(key)
    }

    /**
     * What the app is currently rooted at — the bottom of the **start tab's**
     * stack.
     *
     * The start tab is the right one to read because [replaceAll] is what seeds
     * a root, and it always seeds into the start tab. The other three tabs are
     * rooted at their own tab key by construction and say nothing about whether
     * the user is signed in.
     */
    val rootKey: PebblesKey?
        get() = state.backStacks[state.startRoute]?.firstOrNull() as? PebblesKey

    /**
     * Resets every tab to its root, returns to the start tab, and seeds [key]
     * there. Part 5 drives the auth switch with this: sign-out must not leave a
     * signed-in user's stack sitting under the Welcome screen.
     */
    fun replaceAll(key: PebblesKey) {
        state.backStacks.forEach { (tab, stack) ->
            while (stack.size > 1) stack.removeAt(stack.lastIndex)
            if (stack.isNotEmpty()) stack[0] = tab
        }
        state.topLevelRoute = state.startRoute
        val start = state.backStacks.getValue(state.startRoute)
        start[0] = key
    }

    /**
     * Re-root at [key], but **only if it is not already rooted there**.
     *
     * This is what the auth gate must call rather than [replaceAll] directly,
     * and the distinction is not cosmetic. The gate runs on every resolution of
     * the session, including a **cold restore** — and by then the saveable
     * per-tab stacks have already restored themselves. An unconditional
     * `replaceAll(Path)` at that moment throws away the very thing process-death
     * restoration exists to preserve: a user killed on `People › SoulDetail`
     * comes back to Path, silently, with every test still green.
     *
     * Rooting is the right signal because it distinguishes the two cases exactly:
     * a genuine sign-in has the start tab rooted at `Welcome` (so it re-roots),
     * while a cold restore of an authed session is already rooted at `Path` (so
     * it is left alone — every tab's stack, and the selected tab, intact).
     */
    fun rootAt(key: PebblesKey) {
        if (rootKey == key) return
        replaceAll(key)
    }
}
