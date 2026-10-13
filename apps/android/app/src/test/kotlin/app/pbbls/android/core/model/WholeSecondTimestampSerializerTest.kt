package app.pbbls.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.OffsetDateTime

class WholeSecondTimestampSerializerTest {
    @Test
    fun `emits UTC at whole seconds, dropping the fraction`() {
        val local = OffsetDateTime.parse("2026-10-12T08:40:00.987654+02:00")
        assertEquals("2026-10-12T06:40:00Z", WholeSecondTimestampSerializer.encode(local))
    }

    @Test
    fun `emits seconds even when they are zero`() {
        assertEquals("2026-10-12T06:40:00Z", WholeSecondTimestampSerializer.encode(OffsetDateTime.parse("2026-10-12T06:40Z")))
    }

    @Test
    fun `parses every precision the other surfaces emit`() {
        val instant = OffsetDateTime.parse("2026-10-12T06:40:00Z").toInstant()
        listOf(
            "2026-10-12T06:40:00Z",
            "2026-10-12T06:40:00+00:00",
            "2026-10-12T08:40:00+02:00",
        ).forEach { assertEquals(it, instant, WholeSecondTimestampSerializer.parse(it).toInstant()) }
        assertEquals(
            OffsetDateTime.parse("2026-10-12T06:40:00.123456+00:00").toInstant(),
            WholeSecondTimestampSerializer.parse("2026-10-12T06:40:00.123456+00:00").toInstant(),
        )
    }
}
