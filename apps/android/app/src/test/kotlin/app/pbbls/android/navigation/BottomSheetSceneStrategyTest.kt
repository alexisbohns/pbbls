package app.pbbls.android.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.SceneStrategyScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The docked sheet on compact windows (#940). */
class BottomSheetSceneStrategyTest {
    private val strategy = BottomSheetSceneStrategy<NavKey>()

    private fun entry(
        key: PebblesKey,
        sheet: Boolean,
    ) = NavEntry<NavKey>(key, metadata = if (sheet) BottomSheetSceneStrategy.bottomSheet() else emptyMap()) {}

    private fun sceneFor(entries: List<NavEntry<NavKey>>) =
        with(strategy) { with(SceneStrategyScope<NavKey>()) { calculateScene(entries) } }

    @Test
    fun `the marker is read through its typed key`() {
        assertTrue(BottomSheetSceneStrategy.BottomSheetKey in BottomSheetSceneStrategy.bottomSheet())
    }

    @Test
    fun `a sheet entry on top becomes an overlay over what is below`() {
        val below = entry(PebblesKey.Path, sheet = false)
        val sheet = entry(PebblesKey.PebbleDetail("p1"), sheet = true)

        val scene = sceneFor(listOf(below, sheet))

        assertTrue(scene is OverlayScene<*>)
        assertEquals(listOf(sheet), scene!!.entries)
        assertEquals(listOf(below), (scene as OverlayScene<NavKey>).overlaidEntries)
        assertEquals(listOf(below), scene.previousEntries)
    }

    @Test
    fun `anything else is left to the next strategy`() {
        assertNull(sceneFor(listOf(entry(PebblesKey.Path, sheet = false))))
    }

    @Test
    fun `a sheet entry covered by a modal is not a sheet`() {
        val scene =
            sceneFor(
                listOf(
                    entry(PebblesKey.Path, sheet = false),
                    entry(PebblesKey.PebbleDetail("p1"), sheet = true),
                    entry(PebblesKey.EditPebble("p1"), sheet = false),
                ),
            )
        assertNull(scene)
    }
}
