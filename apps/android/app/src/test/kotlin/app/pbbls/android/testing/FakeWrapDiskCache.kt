package app.pbbls.android.testing

import app.pbbls.android.core.data.WrapDiskCaching
import java.time.Instant
import java.time.ZoneId

/** In-memory [WrapDiskCaching] (#1207): bodies by owner, zone and page. */
class FakeWrapDiskCache : WrapDiskCaching {
    val bodies = mutableMapOf<Triple<String, String, String>, String>()

    /** When each body was kept, by [now]. */
    val keptTimes = mutableMapOf<Triple<String, String, String>, Instant>()

    var now: () -> Instant = { Instant.EPOCH }

    var clearAllCount = 0
        private set

    override suspend fun read(
        ownerId: String,
        zone: ZoneId,
        page: String,
    ): String? = bodies[Triple(ownerId, zone.id, page)]

    override suspend fun write(
        ownerId: String,
        zone: ZoneId,
        page: String,
        body: String,
    ) {
        bodies[Triple(ownerId, zone.id, page)] = body
        keptTimes[Triple(ownerId, zone.id, page)] = now()
    }

    override suspend fun keptAt(
        ownerId: String,
        zone: ZoneId,
        page: String,
    ): Instant? = keptTimes[Triple(ownerId, zone.id, page)]

    override fun clearAll() {
        clearAllCount += 1
        bodies.clear()
        keptTimes.clear()
    }
}
