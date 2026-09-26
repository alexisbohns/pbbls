package app.pbbls.android.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldDefaults
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.DetailPlaceholder

/**
 * The list-detail pairs (#940). Each pair is its own scaffold: the scene key
 * is what stops a soul detail from joining a collections list below it in the
 * flattened back stack.
 */
enum class PanePair { SOULS, COLLECTIONS }

/**
 * List-detail on large screens (#940): the library strategy, with two rules
 * of ours on top.
 *
 * - **Back pops one entry** (`PopLatest`). The default,
 *   `PopUntilScaffoldValueChange`, keeps popping while the scaffold looks the
 *   same — and an idle list with its placeholder looks the same as a list with
 *   a detail, so back from a soul would unwind past the People root.
 * - **A detail with no list under it is not a pair.** `CollectionDetail`
 *   pushed from the You tab has only You beneath it; the library would still
 *   build a one-entry scaffold for it.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun pebblesListDetailStrategy(directive: PaneScaffoldDirective): SceneStrategy<NavKey> {
    val library =
        ListDetailSceneStrategy<NavKey>(
            shouldHandleSinglePaneLayout = false,
            backNavigationBehavior = BackNavigationBehavior.PopLatest,
            directive = directive,
            adaptStrategies = ListDetailPaneScaffoldDefaults.adaptStrategies(),
            paneExpansionDragHandle = null,
            paneExpansionState = null,
        )
    return object : SceneStrategy<NavKey> {
        override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
            val scene = with(library) { calculateScene(entries) } ?: return null
            return if (scene.entries.any(PanePairs::isList)) scene else null
        }
    }
}

/** The strategies `RootScreen`'s `NavDisplay` tries, in order (#940). */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberPebblesSceneStrategies(): List<SceneStrategy<NavKey>> {
    val directive = pebblesPaneDirective(currentWindowAdaptiveInfoV2())
    return remember(directive) { listOf(pebblesListDetailStrategy(directive)) }
}

/**
 * Which entries are list panes and which are detail panes (#940).
 * [metadataFor] is the single source: the entry provider adds it to each
 * entry's transition metadata, and the tests build their entries from it, so
 * the two cannot drift. A detail's metadata does not depend on its id, so the
 * entry provider passes the same throwaway key it gives `NavTransitions`.
 *
 * The library's pane metadata is internal, so a list pane also carries our
 * own marker: that is what the strategy wrapper reads, rather than guessing
 * from content keys.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
object PanePairs {
    private const val IS_LIST = "pebbles.pane.list"

    private fun list(
        pair: PanePair,
        placeholder: @Composable () -> Unit,
    ): Map<String, Any> = ListDetailSceneStrategy.listPane(pair) { placeholder() } + (IS_LIST to true)

    private fun detail(pair: PanePair): Map<String, Any> = ListDetailSceneStrategy.detailPane(pair)

    fun metadataFor(key: PebblesKey): Map<String, Any> =
        when (key) {
            PebblesKey.People -> list(PanePair.SOULS) { SoulsPlaceholder() }
            is PebblesKey.SoulDetail -> detail(PanePair.SOULS)
            PebblesKey.Collections -> list(PanePair.COLLECTIONS) { CollectionsPlaceholder() }
            is PebblesKey.CollectionDetail -> detail(PanePair.COLLECTIONS)
            else -> emptyMap()
        }

    fun isList(entry: NavEntry<*>): Boolean = entry.metadata[IS_LIST] == true
}

@Composable
private fun SoulsPlaceholder() = DetailPlaceholder(iconRes = R.drawable.ic_people, text = stringResource(R.string.souls_detail_placeholder))

@Composable
private fun CollectionsPlaceholder() =
    DetailPlaceholder(iconRes = R.drawable.ic_pebble_collection, text = stringResource(R.string.collections_detail_placeholder))
