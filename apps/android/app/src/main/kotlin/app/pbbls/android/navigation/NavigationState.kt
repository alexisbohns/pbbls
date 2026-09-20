package app.pbbls.android.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.runtime.serialization.NavKeySerializer

/**
 * Per-tab back stacks (#852, D4), modelled on the AndroidX `multiplestacks`
 * recipe.
 *
 * Each tab owns a [NavBackStack], which is saveable — so process-death
 * restoration is a property of this container rather than something each screen
 * re-implements. Each tab also owns its own `SaveableStateHolder` decorator, so
 * a tab's scroll position survives a trip to another tab.
 *
 * The stacks are typed [NavKey] rather than [PebblesKey] because 1.1.7's
 * `rememberNavBackStack` is fixed to `NavBackStack<NavKey>` — it restores
 * through a reflection-based serializer and has no per-call generic. [Navigator]
 * is the only writer and takes a [PebblesKey], so every element is one by
 * construction.
 *
 * This class never modifies itself. [Navigator] is the only writer.
 */
@Stable
class NavigationState(
    val startRoute: PebblesKey,
    topLevelRoute: MutableState<PebblesKey>,
    val backStacks: Map<PebblesKey, NavBackStack<NavKey>>,
) {
    var topLevelRoute: PebblesKey by topLevelRoute

    /** The stack the user is currently in. Modals are pushed here (D6). */
    val currentStack: NavBackStack<NavKey>
        get() = backStacks[topLevelRoute] ?: error("No stack for $topLevelRoute")

    /**
     * The key on top of everything — what the bar check reads.
     *
     * Typed [NavKey], not [PebblesKey], and deliberately NOT cast: `topKey is
     * BarKey` and `topKey == PebblesKey.Path` both work on a `NavKey`, because
     * [BarKey] is a marker unrelated to the stack's element type. A cast here
     * would buy nothing and could throw.
     */
    val topKey: NavKey
        get() = currentStack.last()

    /**
     * "Exit through home" (D4): the start route is always first, and at most one
     * other tab is active. Back therefore unwinds the current tab, falls back to
     * Path, then exits — and the back path cannot grow without bound however
     * much the user taps around the bar.
     *
     * A tab that is not in use still RETAINS its stack; it is simply not in the
     * back path.
     */
    fun topLevelRoutesInUse(): List<PebblesKey> = if (topLevelRoute == startRoute) listOf(startRoute) else listOf(startRoute, topLevelRoute)

    @Composable
    fun toDecoratedEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
        val decorated =
            backStacks.mapValues { (_, stack) ->
                rememberDecoratedNavEntries(
                    backStack = stack,
                    entryDecorators =
                        listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                    entryProvider = entryProvider,
                )
            }
        return topLevelRoutesInUse().flatMap { decorated[it].orEmpty() }
    }
}

@Composable
fun rememberNavigationState(
    startRoute: PebblesKey = PebblesKey.Path,
    tabs: List<PebblesKey> = PebblesKey.tabs,
): NavigationState {
    // NB: the `androidx.savedstate.compose.serialization.serializers.MutableStateSerializer`
    // class exists but is the wrong shape here — it serializes a `MutableState<T>` and pairs
    // with a `rememberSerializable(serializer = ...)` overload that returns `T` directly. The
    // overload that actually returns a `MutableState<T>` (what `by` needs below) lives in
    // `androidx.compose.runtime.saveable` and takes the INNER serializer as `stateSerializer`.
    val topLevelRoute =
        rememberSerializable(
            startRoute,
            tabs,
            stateSerializer = NavKeySerializer<PebblesKey>(),
        ) { mutableStateOf(startRoute) }

    val backStacks = tabs.associateWith { key -> rememberNavBackStack(key) }

    return remember(startRoute, tabs) {
        NavigationState(startRoute = startRoute, topLevelRoute = topLevelRoute, backStacks = backStacks)
    }
}
