package app.pbbls.android.testing

import app.pbbls.android.core.data.PebbleDraftRecord
import app.pbbls.android.core.data.PebbleDraftsServicing
import app.pbbls.android.core.model.PebbleDraftPayload
import java.util.UUID

/** In-memory [PebbleDraftsServicing] (#849), backed by a mutable list. */
class FakePebbleDraftsService(
    var records: MutableList<PebbleDraftRecord> = mutableListOf(),
    /** Whether [canUseGlyph] approves. */
    var glyphIsUsable: Boolean = true,
) : PebbleDraftsServicing {
    var countCallCount = 0
        private set

    /** How many times [load] (the by-id resume read, #852) was called. */
    var loadCallCount = 0
        private set

    private val armed = ArmedFailure()

    /** Thrown by the next call, then cleared. */
    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    override suspend fun list(): List<PebbleDraftRecord> {
        armed.fire()
        return records.toList()
    }

    override suspend fun load(id: String): PebbleDraftRecord? {
        loadCallCount += 1
        armed.fire()
        return records.firstOrNull { it.id == id }
    }

    override suspend fun save(
        payload: PebbleDraftPayload,
        id: String?,
        userId: String,
    ): String {
        armed.fire()
        val rowId = id ?: UUID.randomUUID().toString()
        records.removeAll { it.id == rowId }
        records.add(PebbleDraftRecord(id = rowId, payload = payload, updatedAt = java.time.OffsetDateTime.now()))
        return rowId
    }

    override suspend fun delete(id: String) {
        armed.fire()
        records.removeAll { it.id == id }
    }

    override suspend fun count(): Int {
        countCallCount += 1
        armed.fire()
        return records.size
    }

    override suspend fun canUseGlyph(
        glyphId: String,
        userId: String,
    ): Boolean {
        armed.fire()
        return glyphIsUsable
    }
}
