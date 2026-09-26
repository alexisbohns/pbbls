package app.pbbls.android.navigation

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Which back stacks become a list-detail scene (#940). */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class PebblesSceneStrategiesTest {
    private fun directive(partitions: Int) =
        PaneScaffoldDirective.Default.copy(
            maxHorizontalPartitions = partitions,
            horizontalPartitionSpacerSize = if (partitions > 1) 24.dp else 0.dp,
        )

    private fun entry(key: PebblesKey) = NavEntry<NavKey>(key, metadata = PanePairs.metadataFor(key)) {}

    private fun SceneStrategy<NavKey>.sceneFor(vararg keys: PebblesKey) =
        with(SceneStrategyScope<NavKey>()) { calculateScene(keys.map(::entry)) }

    @Test
    fun `two panes put a soul beside the souls list`() {
        val scene = pebblesListDetailStrategy(directive(2)).sceneFor(PebblesKey.People, PebblesKey.SoulDetail("s1"))

        assertNotNull(scene)
        assertEquals(2, scene!!.entries.size)
    }

    @Test
    fun `back from a soul beside its list pops only the soul`() {
        // The real input: NavigationState flattens the start tab under the
        // current one, so Path sits below People. PopLatest is what keeps back
        // on the pair; the library default would unwind straight to Path,
        // because an idle list with its placeholder looks the same.
        val entries = listOf(PebblesKey.Path, PebblesKey.People, PebblesKey.SoulDetail("s1")).map(::entry)
        val scene = with(SceneStrategyScope<NavKey>()) { with(pebblesListDetailStrategy(directive(2))) { calculateScene(entries) } }

        assertEquals(entries.drop(1), scene!!.entries)
        assertEquals(2, scene.previousEntries.size)
    }

    @Test
    fun `back from an idle list leaves the pair`() {
        val scene = pebblesListDetailStrategy(directive(2)).sceneFor(PebblesKey.Path, PebblesKey.People)

        assertEquals(1, scene!!.previousEntries.size)
    }

    @Test
    fun `two panes show an idle souls list with its placeholder`() {
        assertNotNull(pebblesListDetailStrategy(directive(2)).sceneFor(PebblesKey.People))
    }

    @Test
    fun `one pane leaves the push to the default scene`() {
        assertNull(pebblesListDetailStrategy(directive(1)).sceneFor(PebblesKey.People, PebblesKey.SoulDetail("s1")))
    }

    @Test
    fun `a modal over the pair is not part of it`() {
        val scene =
            pebblesListDetailStrategy(directive(2))
                .sceneFor(PebblesKey.People, PebblesKey.SoulDetail("s1"), PebblesKey.SoulForm("s1"))
        assertNull(scene)
    }

    @Test
    fun `a collection opened from the You tab has no list beside it`() {
        // CollectionDetail with only You beneath it: the scaffold would hold
        // one entry, so it must not claim the stack.
        val scene = pebblesListDetailStrategy(directive(2)).sceneFor(PebblesKey.You, PebblesKey.CollectionDetail("c1"))
        assertNull(scene)
    }
}
