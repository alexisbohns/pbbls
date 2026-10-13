package app.pbbls.android.core.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MomentReadServiceTest {
    @Test
    fun `the soul filter is a jsonb containment on the caller's pebble`() {
        assertEquals(
            Json.parseToJsonElement("""{"souls":[{"id":"50000000-0000-4000-8000-000000000004"}]}"""),
            Json.parseToJsonElement(MomentReadService.withSoul("50000000-0000-4000-8000-000000000004")),
        )
    }

    @Test
    fun `the collection filter is a jsonb containment on the caller's pebble`() {
        assertEquals(
            Json.parseToJsonElement("""{"collections":[{"id":"c-1"}]}"""),
            Json.parseToJsonElement(MomentReadService.inCollection("c-1")),
        )
    }

    @Test
    fun `the view is read through an explicit column list`() {
        assertEquals("id, name, description, happened_at, created_at, snaps, pebble", MomentReadService.COLUMNS)
    }
}
