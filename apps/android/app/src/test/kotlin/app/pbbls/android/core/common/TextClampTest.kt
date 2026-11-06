package app.pbbls.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TextClampTest {
    private val grin = "😀"

    @Test
    fun `an emoji across the limit is dropped whole`() {
        assertEquals("abc", ("abc" + grin).takeSafely(4))
    }

    @Test
    fun `an emoji that fits is kept`() {
        assertEquals("abc$grin", ("abc" + grin).takeSafely(5))
    }

    @Test
    fun `short text and plain cuts are as take`() {
        assertEquals("ab", "ab".takeSafely(5))
        assertEquals("abc", "abcdef".takeSafely(3))
        assertEquals("", "".takeSafely(3))
    }

    @Test
    fun `text that ends on a lone high surrogate by itself is left alone`() {
        // Not a cut by us: nothing was removed, so nothing is repaired.
        assertEquals("a\uD83D", "a\uD83D".takeSafely(5))
    }
}
