package app.pbbls.android.core.model

import app.pbbls.android.core.data.CompleteSnapshotStore
import app.pbbls.android.core.data.OwnedCompleteSnapshot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompleteSnapshotTest {
    private val answers =
        PebbleAnswers(
            valence = Valence.LOWLIGHT_SMALL,
            emotionId = "e-1",
            domainIds = listOf("d-1", "d-2"),
            soulIds = listOf("s-1", "s-gone"),
            collectionId = "c-gone",
            visibility = Visibility.PUBLIC,
        )

    @Test
    fun `answers round-trip through the snapshot`() {
        assertEquals(answers, CompleteSnapshot.from(answers).toAnswers(known = null))
    }

    @Test
    fun `a snapshot stored with the old single domain_id restores it`() {
        val old = """{"emotion_id":"e-1","domain_id":"d1","soul_ids":["s-1"]}"""
        val snapshot = Json.decodeFromString(CompleteSnapshot.serializer(), old)
        assertFalse(snapshot.isEmpty)
        assertEquals(listOf("d1"), snapshot.toAnswers(known = null).domainIds)
        val rewritten = CompleteSnapshot.from(snapshot.toAnswers(known = null))
        assertFalse(Json.encodeToString(CompleteSnapshot.serializer(), rewritten).contains("\"domain_id\""))
    }

    @Test
    fun `restoring drops souls and collections that no longer exist`() {
        val restored = CompleteSnapshot.from(answers).toAnswers(KnownDraftIds(soulIds = setOf("s-1"), collectionIds = emptySet()))
        assertEquals(listOf("s-1"), restored.soulIds)
        assertNull(restored.collectionId)
    }

    @Test
    fun `a valence alone is input, since nothing seeds it any more`() {
        assertFalse(CompleteSnapshot.from(PebbleAnswers(valence = Valence.NEUTRAL_MEDIUM)).isEmpty)
        assertFalse(CompleteSnapshot.from(PebbleAnswers(valence = Valence.LOWLIGHT_SMALL)).isEmpty)
    }

    @Test
    fun `the default grade alone is not input`() {
        assertTrue(CompleteSnapshot.from(PebbleAnswers()).isEmpty)
    }

    @Test
    fun `a snapshot with a valence restores it and one without restores none`() {
        val with = CompleteSnapshot.from(PebbleAnswers(valence = Valence.HIGHLIGHT_LARGE, emotionId = "e-1"))
        assertEquals(Valence.HIGHLIGHT_LARGE, with.toAnswers(known = null).valence)
        val without = CompleteSnapshot.from(PebbleAnswers(emotionId = "e-1"))
        assertNull(without.toAnswers(known = null).valence)
        assertEquals("e-1", without.toAnswers(known = null).emotionId)
    }

    @Test
    fun `a valence alone survives the store's round trip`() {
        val owned = OwnedCompleteSnapshot("user-1", CompleteSnapshot.from(PebbleAnswers(valence = Valence.HIGHLIGHT_MEDIUM)))
        val decoded =
            Json.decodeFromString(
                OwnedCompleteSnapshot.serializer(),
                Json.encodeToString(OwnedCompleteSnapshot.serializer(), owned),
            )
        assertEquals(Valence.HIGHLIGHT_MEDIUM, decoded.restorableFor("user-1")?.toAnswers(known = null)?.valence)
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
                {"ownerId":"user-1","snapshot":{"intensity":1,"positiveness":-1,"emotion_id":"e-1","domain_ids":["d-1","d-2"],
                 "soul_ids":["s-1","s-gone"],"collection_id":"c-gone","visibility":"public"}}
                """.trimIndent(),
            ),
            Json.parseToJsonElement(json),
        )
    }

    @Test
    fun `keys are per moment and lowercase`() {
        assertEquals(
            "completeSnapshot.7d1f6a2e-0c4b-4e8a-9b51-2f3c4d5e6f70",
            CompleteSnapshotStore.key("7D1F6A2E-0C4B-4E8A-9B51-2F3C4D5E6F70"),
        )
    }
}
