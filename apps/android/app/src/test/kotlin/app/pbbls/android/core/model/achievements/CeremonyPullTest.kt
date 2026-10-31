package app.pbbls.android.core.model.achievements

import org.junit.Assert.assertEquals
import org.junit.Test

/** `pull_ceremony`'s answer (#1305 §3.4): one row is a pull; no row is "already pulled", never success. */
class CeremonyPullTest {
    @Test
    fun `one row is a pull with the karma the server paid`() {
        val rows = AchievementsJson.decodeFromString<List<CeremonyRowDto>>("""[{"stack": "emotions", "karma_granted": 25}]""")
        assertEquals(CeremonyPull.Pulled(CompletionStack.EMOTIONS, 25), ceremonyPullOf(CompletionStack.EMOTIONS, rows))
    }

    @Test
    fun `an inactive set's pull is still a pull, with no karma`() {
        val rows = AchievementsJson.decodeFromString<List<CeremonyRowDto>>("""[{"stack": "six_firsts", "karma_granted": 0}]""")
        assertEquals(CeremonyPull.Pulled(CompletionStack.SIX_FIRSTS, 0), ceremonyPullOf(CompletionStack.SIX_FIRSTS, rows))
    }

    @Test
    fun `no row is already pulled`() {
        val rows = AchievementsJson.decodeFromString<List<CeremonyRowDto>>("[]")
        assertEquals(CeremonyPull.AlreadyPulled, ceremonyPullOf(CompletionStack.DOMAINS, rows))
    }

    @Test
    fun `the stacks name the server's p_stack values and their set_complete slugs`() {
        assertEquals(listOf("emotions", "domains", "six_firsts"), CompletionStack.entries.map { it.slug })
        assertEquals(
            listOf("complete-emotions", "complete-domains", "complete-six-firsts"),
            CompletionStack.entries.map { it.catalogSlug },
        )
        assertEquals(listOf("explorer", "emotions", "domains", "pebbles", "year"), AchievementCard.entries.map { it.slug })
    }
}
