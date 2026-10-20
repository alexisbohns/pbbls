package app.pbbls.android.features.path

import android.app.Application
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The read page's held scroll ([rememberHeldScrollState]) on its own: a viewport that grows
 * at the foot (the bar snapping away under a pushed entry) pins the reader's position and
 * gives it back when the viewport shrinks again, at once or over a transition's frames; content that really got shorter does not,
 * so a longer page later never moves the reader; a scroll drops a pin; and a restored
 * position comes back once a page laid out short reaches it. Legacy graphics: nothing here
 * reads a pixel.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w390dp-h800dp-xhdpi")
class HeldScrollStateTest {
    @get:Rule
    val compose = createComposeRule()

    private var viewport by mutableStateOf(VIEWPORT)
    private var content by mutableStateOf(CONTENT)
    private var shown by mutableStateOf(true)
    private lateinit var scroll: ScrollState

    private fun px(dp: Dp): Int = with(compose.density) { dp.roundToPx() }

    private fun page() {
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            if (shown) {
                holder.SaveableStateProvider("page") {
                    Box(Modifier.height(viewport)) {
                        scroll = rememberHeldScrollState()
                        Column(Modifier.fillMaxSize().verticalScroll(scroll)) { Spacer(Modifier.height(content)) }
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    /** Reads the page to its foot, as a reader's drag would. */
    private fun readToTheFoot(): Int {
        compose.runOnIdle { scroll.dispatchRawDelta(Float.MAX_VALUE) }
        compose.waitForIdle()
        assertEquals(px(CONTENT - VIEWPORT), scroll.value)
        return scroll.value
    }

    private fun set(block: () -> Unit) {
        compose.runOnIdle(block)
        compose.waitForIdle()
    }

    @Test
    fun `a viewport grown at the foot gives the position back when it shrinks`() {
        page()
        val foot = readToTheFoot()

        set { viewport = VIEWPORT + BAR }
        assertEquals("clamped meanwhile", foot - px(BAR), scroll.value)
        set { viewport = VIEWPORT }

        assertEquals(foot, scroll.value)
    }

    /** As a transition resizes the page: the viewport shrinks back over several frames. */
    @Test
    fun `a viewport shrinking back by steps gives the position back`() {
        page()
        val foot = readToTheFoot()

        set { viewport = VIEWPORT + BAR }
        repeat(STEPS) { step -> set { viewport = VIEWPORT + BAR * (STEPS - step - 1) / STEPS } }

        assertEquals(foot, scroll.value)
    }

    @Test
    fun `content shortened at the foot does not jump the reader when it grows again`() {
        page()
        val foot = readToTheFoot()

        set { content = CONTENT - BAR }
        val shortened = scroll.value
        assertEquals(foot - px(BAR), shortened)
        set { content = CONTENT }

        assertEquals(shortened, scroll.value)
    }

    @Test
    fun `a scroll after a clamp drops the pin`() {
        page()
        val foot = readToTheFoot()

        set { viewport = VIEWPORT + BAR }
        set { scroll.dispatchRawDelta(-SCROLL_UP_PX) }
        val read = scroll.value
        assertEquals(foot - px(BAR) - SCROLL_UP_PX.toInt(), read)
        set { viewport = VIEWPORT }

        assertEquals(read, scroll.value)
    }

    @Test
    fun `a restored position comes back once a page laid out short reaches it`() {
        page()
        val foot = readToTheFoot()

        set { shown = false }
        set {
            content = CONTENT - BAR
            shown = true
        }
        assertEquals("laid out short", px(CONTENT - BAR - VIEWPORT), scroll.value)
        set { content = CONTENT }

        assertEquals(foot, scroll.value)
    }

    private companion object {
        val VIEWPORT = 400.dp
        val CONTENT = 1_000.dp
        val BAR = 80.dp
        const val SCROLL_UP_PX = 100f
        const val STEPS = 4
    }
}
