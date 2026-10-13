package app.pbbls.android.core.model

import app.pbbls.android.core.data.CompleteSnapshotStore
import app.pbbls.android.core.data.OwnedCompleteSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteSnapshotTest {
    private val answers =
        PebbleAnswers(
            valence = Valence.LOWLIGHT_SMALL,
            emotionId = "e-1",
            domainId = "d-1",
            soulIds = listOf("s-1", "s-gone"),
            collectionId = "c-gone",
            visibility = Visibility.PUBLIC,
        )

    @Test
    fun `answers round-trip through the snapshot`() {
        assertEquals(answers, CompleteSnapshot.from(answers).toAnswers(known = null))
    }

    @Test
    fun `restoring drops souls and collections that no longer exist`() {
        val restored = CompleteSnapshot.from(answers).toAnswers(KnownDraftIds(soulIds = setOf("s-1"), collectionIds = emptySet()))
        assertEquals(listOf("s-1"), restored.soulIds)
        assertNull(restored.collectionId)
    }

    @Test
    fun `a seeded valence and the default grade are not input`() {
        assertTrue(CompleteSnapshot.from(PebbleAnswers(valence = Valence.NEUTRAL_MEDIUM)).isEmpty)
    }

    @Test
    fun `a snapshot is only restorable for the user who wrote it`() {
        val owned = OwnedCompleteSnapshot(ownerId = "user-1", snapshot = CompleteSnapshot.from(answers))
        assertEquals(owned.snapshot, owned.restorableFor("user-1"))
        assertNull(owned.restorableFor("user-2"))
    }

    @Test
    fun `the stored shape is stable`() {
        val json = Json.encodeToString(OwnedCompleteSnapshot.serializer(), OwnedCompleteSnapshot("user-1", CompleteSnapshot.from(answers)))
        assertEquals(
            Json.parseToJsonElement(
                """
                {"ownerId":"user-1","snapshot":{"intensity":1,"positiveness":-1,"emotion_id":"e-1","domain_id":"d-1",
                 "soul_ids":["s-1","s-gone"],"collection_id":"c-gone","visibility":"public"}}
                """.trimIndent(),
            ),
            Json.parseToJsonElement(json),
        )
    }

    @Test
    fun `keys are per moment and lowercase`() {
        assertEquals("completeSnapshot.7d1f6a2e-0c4b-4e8a-9b51-2f3c4d5e6f70", CompleteSnapshotStore.key("7D1F6A2E-0C4B-4E8A-9B51-2F3C4D5E6F70"))
    }
}
