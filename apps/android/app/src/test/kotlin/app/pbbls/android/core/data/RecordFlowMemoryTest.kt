package app.pbbls.android.core.data

import app.pbbls.android.testing.InMemoryPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordFlowMemoryTest {
    private val prefs = InMemoryPrefs()
    private val memory = RecordFlowMemory(prefs)

    @Test
    fun `the help flag is unset until marked, and per account`() {
        assertFalse(memory.isValenceHelpSeen("user-1"))
        memory.markValenceHelpSeen("user-1")
        assertTrue(memory.isValenceHelpSeen("user-1"))
        assertFalse(memory.isValenceHelpSeen("user-2"))
    }

    @Test
    fun `keys are per owner and lowercase, as iOS writes them`() {
        assertEquals("valenceHelpSeen.7d1f6a2e", RecordFlowMemory.helpKey("7D1F6A2E"))
        assertEquals("recentDomains.7d1f6a2e", RecordFlowMemory.domainsKey("7D1F6A2E"))
        memory.markValenceHelpSeen("7D1F6A2E")
        assertTrue(prefs.getBoolean("valenceHelpSeen.7d1f6a2e", false))
    }

    @Test
    fun `nothing is remembered at first`() {
        assertEquals(emptyList<String>(), memory.recentDomainIds("user-1"))
    }

    @Test
    fun `picks go first, each id once, newest first`() {
        memory.recordDomains(listOf("a", "b"), "user-1")
        memory.recordDomains(listOf("c", "a"), "user-1")
        assertEquals(listOf("c", "a", "b"), memory.recentDomainIds("user-1"))
    }

    @Test
    fun `at most five are kept, the oldest go`() {
        memory.recordDomains(listOf("a", "b", "c"), "user-1")
        memory.recordDomains(listOf("d", "e", "f"), "user-1")
        assertEquals(listOf("d", "e", "f", "a", "b"), memory.recentDomainIds("user-1"))
        assertEquals(RECENT_DOMAIN_CAP, memory.recentDomainIds("user-1").size)
    }

    @Test
    fun `clearing forgets every account's domains and keeps the help flag`() {
        memory.recordDomains(listOf("a"), "user-1")
        memory.recordDomains(listOf("b"), "user-2")
        memory.markValenceHelpSeen("user-1")
        memory.clearDomains()
        assertEquals(emptyList<String>(), memory.recentDomainIds("user-1"))
        assertEquals(emptyList<String>(), memory.recentDomainIds("user-2"))
        assertTrue(memory.isValenceHelpSeen("user-1"))
    }

    @Test
    fun `domains are per account`() {
        memory.recordDomains(listOf("a"), "user-1")
        assertEquals(emptyList<String>(), memory.recentDomainIds("user-2"))
    }

    @Test
    fun `corrupt data reads as empty and the next pick overwrites it`() {
        prefs.edit().putString(RecordFlowMemory.domainsKey("user-1"), "{not a list").apply()
        assertEquals(emptyList<String>(), memory.recentDomainIds("user-1"))
        memory.recordDomains(listOf("a"), "user-1")
        assertEquals(listOf("a"), memory.recentDomainIds("user-1"))
    }

    @Test
    fun `a list of the wrong shape reads as empty`() {
        prefs.edit().putString(RecordFlowMemory.domainsKey("user-1"), """[1,2,{"a":1}]""").apply()
        assertEquals(emptyList<String>(), memory.recentDomainIds("user-1"))
    }
}
