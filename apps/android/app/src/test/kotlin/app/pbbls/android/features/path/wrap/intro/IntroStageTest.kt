package app.pbbls.android.features.path.wrap.intro

import androidx.compose.ui.Modifier
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test

/** The reveal modifier adds nothing to a settled render (the plan's "settled is the default"). */
class IntroStageTest {
    @Test
    fun `a settled reveal, or a part with no key, returns the modifier as it is`() {
        assertSame(Modifier, Modifier.introReveal(IntroReveal.settled, IntroKey.Header))
        assertSame(Modifier, Modifier.introReveal(IntroReveal.hidden, null))
    }

    @Test
    fun `a reveal in play adds a layer`() {
        assertNotSame(Modifier, Modifier.introReveal(IntroReveal.hidden, IntroKey.Header))
    }
}
