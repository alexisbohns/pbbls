package app.pbbls.android.core.data

import app.pbbls.android.core.data.MomentWriteService.Companion.wire
import app.pbbls.android.core.model.KarmaReason
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentWriteServiceTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `delete_moment answers with the files the client removes`() {
        val result =
            json.decodeFromString<DeleteMomentResult>(
                """{"storage_paths":["3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"]}""",
            )
        assertEquals(listOf("3f0a9c1e-5b7d-4e21-8c44-0a1b2c3d4e5f/9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d"), result.storagePaths)
        assertEquals(emptyList<String>(), json.decodeFromString<DeleteMomentResult>("""{"storage_paths":[]}""").storagePaths)
    }

    @Test
    fun `karma reasons use the karma_events wire names`() {
        assertEquals("pebble_created", KarmaReason.PEBBLE_CREATED.wire())
        assertEquals("pebble_enriched", KarmaReason.PEBBLE_ENRICHED.wire())
    }
}
