package app.pbbls.android.core.model

import app.pbbls.android.testing.UiFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the [PebbleDraft.from] edit-prefill factory over a [MomentDetail] —
 * every field maps across, a moment without a pebble keeps only its moment
 * half — and the [PebbleDraft.answers] projection.
 */
class PebbleDraftTest {
    @Test
    fun `from a moment populates every field of a fully-populated pebble`() {
        val source =
            UiFixtures.momentDetail(
                description = "Finally.",
                pebble =
                    UiFixtures.momentPebble(
                        valence = Valence.HIGHLIGHT_LARGE,
                        visibility = Visibility.PUBLIC,
                        souls = listOf(SoulRef("soul-1", "Me")),
                        collections = listOf(PebbleCollection("collection-1", "Wins")),
                    ),
            )

        val draft = PebbleDraft.from(source)

        assertEquals("Morning walk", draft.name)
        assertEquals("Finally.", draft.description)
        assertEquals(source.happenedAt, draft.happenedAt)
        assertEquals(UiFixtures.joy.id, draft.emotionId)
        assertEquals("domain-1", draft.domainId)
        assertEquals(listOf("soul-1"), draft.soulIds)
        assertEquals("collection-1", draft.collectionId)
        assertEquals(Valence.HIGHLIGHT_LARGE, draft.valence)
        assertEquals(Visibility.PUBLIC, draft.visibility)
    }

    @Test
    fun `from a moment maps a null description to an empty string`() {
        assertEquals("", PebbleDraft.from(UiFixtures.momentDetail(description = null)).description)
    }

    @Test
    fun `from a moment preserves every soul in order`() {
        val source =
            UiFixtures.momentDetail(
                pebble = UiFixtures.momentPebble(souls = listOf(SoulRef("soul-1", "Heloise"), SoulRef("soul-2", "Ingrid"))),
            )
        assertEquals(listOf("soul-1", "soul-2"), PebbleDraft.from(source).soulIds)
    }

    @Test
    fun `from a moment leaves collectionId null when there are no collections`() {
        assertNull(PebbleDraft.from(UiFixtures.momentDetail()).collectionId)
    }

    @Test
    fun `from a moment without a pebble keeps the moment half only`() {
        val draft = PebbleDraft.from(UiFixtures.momentDetail(pebble = null))

        assertEquals("Morning walk", draft.name)
        assertNull(draft.emotionId)
        assertNull(draft.domainId)
        assertNull(draft.valence)
        assertNull(draft.collectionId)
        assertTrue(draft.soulIds.isEmpty())
        assertEquals(Visibility.SECRET, draft.visibility)
    }

    @Test
    fun `from a photo-only moment reads an empty name`() {
        assertEquals("", PebbleDraft.from(UiFixtures.momentDetail(name = null)).name)
    }

    @Test
    fun `from a moment derives valence from positiveness and intensity`() {
        val source = UiFixtures.momentDetail(pebble = UiFixtures.momentPebble(valence = Valence.LOWLIGHT_MEDIUM))
        assertEquals(Valence.LOWLIGHT_MEDIUM, PebbleDraft.from(source).valence)
    }

    @Test
    fun `answers carries the pebble half`() {
        val draft =
            PebbleDraft(
                valence = Valence.NEUTRAL_SMALL,
                emotionId = "emotion-1",
                domainId = "domain-1",
                soulIds = listOf("soul-1"),
                collectionId = "collection-1",
                visibility = Visibility.PRIVATE,
            )
        assertEquals(
            PebbleAnswers(Valence.NEUTRAL_SMALL, "emotion-1", "domain-1", listOf("soul-1"), "collection-1", Visibility.PRIVATE),
            draft.answers,
        )
    }
}
