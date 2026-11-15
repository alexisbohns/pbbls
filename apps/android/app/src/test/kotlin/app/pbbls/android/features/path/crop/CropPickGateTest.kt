package app.pbbls.android.features.path.crop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ported from iOS `CropPickGateTests` (#1659). */
class CropPickGateTest {
    @Test
    fun `a pick counts as coming from its tap`() {
        val gate = CropPickGate()
        assertFalse(gate.isActive)
        gate.begin()
        assertTrue(gate.isActive)
        assertTrue(gate.isLoading)
    }

    @Test
    fun `a load that finishes while current opens the editor and stays active`() {
        val gate = CropPickGate()
        val token = gate.begin()
        assertTrue(gate.finishLoading(token, opened = true))
        assertFalse(gate.isLoading)
        // The editor is open: the photo still counts as coming.
        assertTrue(gate.isActive)
    }

    @Test
    fun `a load that returns nothing releases the gate`() {
        val gate = CropPickGate()
        val token = gate.begin()
        assertTrue(gate.finishLoading(token, opened = false))
        assertFalse(gate.isActive)
    }

    @Test
    fun `a later pick supersedes an earlier one even when the earlier one loads first`() {
        val gate = CropPickGate()
        val first = gate.begin()
        val second = gate.begin()
        assertFalse(gate.finishLoading(first, opened = true))
        // The second is still loading.
        assertTrue(gate.isLoading)
        assertTrue(gate.finishLoading(second, opened = true))
    }

    @Test
    fun `a stale unusable load does not release a newer pick`() {
        val gate = CropPickGate()
        val first = gate.begin()
        val second = gate.begin()
        assertFalse(gate.finishLoading(first, opened = false))
        assertEquals(second, gate.token)
        assertTrue(gate.isActive)
    }

    @Test
    fun `a load landing after clear is dropped`() {
        val gate = CropPickGate()
        val token = gate.begin()
        gate.clear()
        assertFalse(gate.isActive)
        assertFalse(gate.finishLoading(token, opened = true))
        assertFalse(gate.isActive)
    }

    @Test
    fun `release ends the pick it names and leaves a newer one alone`() {
        val gate = CropPickGate()
        val first = gate.begin()
        assertTrue(gate.finishLoading(first, opened = true))
        val second = gate.begin()
        // The first pick's upload finishing late.
        gate.release(first)
        assertEquals(second, gate.token)
        gate.release(second)
        assertFalse(gate.isActive)
    }

    @Test
    fun `a loaded photo opens the editor only when nothing else is on screen`() {
        val gate = CropPickGate()
        val token = gate.begin()
        gate.finishLoading(token, opened = true)
        assertEquals(CropPresentation.WAIT, gate.presentation(token, canPresent = false))
        assertEquals(CropPresentation.PRESENT, gate.presentation(token, canPresent = true))
    }

    @Test
    fun `nothing pending asks for nothing`() {
        val gate = CropPickGate()
        gate.begin()
        assertEquals(CropPresentation.NONE, gate.presentation(null, canPresent = true))
    }

    @Test
    fun `a pending photo is dropped once a later pick or a discard took over`() {
        val gate = CropPickGate()
        val first = gate.begin()
        gate.finishLoading(first, opened = true)
        gate.begin()
        assertEquals(CropPresentation.DROP, gate.presentation(first, canPresent = true))
        assertEquals(CropPresentation.DROP, gate.presentation(first, canPresent = false))

        val closed = CropPickGate()
        val token = closed.begin()
        closed.finishLoading(token, opened = true)
        closed.clear()
        assertEquals(CropPresentation.DROP, closed.presentation(token, canPresent = true))
    }
}
