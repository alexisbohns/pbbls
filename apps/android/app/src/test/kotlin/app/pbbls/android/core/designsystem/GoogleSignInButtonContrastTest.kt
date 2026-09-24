package app.pbbls.android.core.designsystem

import app.pbbls.android.testing.AA_TEXT
import app.pbbls.android.testing.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Google capsule is a pinned light surface, so its label must not follow the
 * theme. Asserted numerically because no screenshot covers every scheme.
 */
class GoogleSignInButtonContrastTest {
    @Test
    fun labelIsLegibleOnTheCapsule() {
        val ratio = contrastRatio(GoogleCapsuleInk, GoogleButtonSurface)
        assertTrue("$ratio:1 fails WCAG AA (4.5:1)", ratio >= AA_TEXT)
    }
}
