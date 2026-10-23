package app.pbbls.android.testing

import app.pbbls.android.core.data.WrapDiskCaching
import java.time.ZoneId

/** In-memory [WrapDiskCaching] (#1207): bodies by owner, zone and page. */
class FakeWrapDiskCache : WrapDiskCaching {
    val bodies = mutableMapOf<Triple<String, String, String>, String>()

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
    }

    override fun clearAll() {
        clearAllCount += 1
        bodies.clear()
    }
}
