package app.pbbls.android.testing

import app.pbbls.android.core.model.PebbleDetail
import app.pbbls.android.services.PebbleDetailServicing

/** In-memory [PebbleDetailServicing] (#849) — the detail/edit load path. */
class FakePebbleDetailService(
    var detail: PebbleDetail? = null,
) : PebbleDetailServicing {
    /** Every pebble id passed to [load], oldest first. */
    val loadCalls = mutableListOf<String>()

    private val armed = ArmedFailure()

    /** Thrown by the next call, then cleared. */
    var failNext: Exception?
        get() = armed.next
        set(value) {
            armed.next = value
        }

    override suspend fun load(pebbleId: String): PebbleDetail {
        loadCalls += pebbleId
        armed.fire()
        return detail ?: error("FakePebbleDetailService.detail was not seeded")
    }
}
