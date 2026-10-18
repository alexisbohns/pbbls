package app.pbbls.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UuidHashTest {
    @Test
    fun `fnv1a matches the web seed`() {
        assertEquals(3118864542L, UuidHash.fnv1a("0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b"))
        assertEquals(3303075303L, UuidHash.fnv1a("8d1f6c3e-2b7a-4c1e-9f0a-5e6d7c8b9a01"))
    }

    @Test
    fun `is stable and unsigned`() {
        val h = UuidHash.fnv1a("anything")
        assertEquals(h, UuidHash.fnv1a("anything"))
        assertTrue(h in 0L..0xffffffffL)
    }
}
