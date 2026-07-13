package app.pbbls.android.features.path.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValenceRenderHeightTest {
    @Test
    fun `render height maps small medium large to 180 220 260`() {
        assertEquals(180, ValenceSizeGroup.SMALL.renderHeightDp)
        assertEquals(220, ValenceSizeGroup.MEDIUM.renderHeightDp)
        assertEquals(260, ValenceSizeGroup.LARGE.renderHeightDp)
    }

    @Test
    fun `every size group has a positive render height`() {
        // The `when` in renderHeightDp is exhaustive, so a future 4th entry is a
        // compile error, not a silent gap — the assert documents the intent.
        ValenceSizeGroup.entries.forEach { assertTrue(it.renderHeightDp > 0) }
    }
}
