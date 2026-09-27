package app.pbbls.android.testing

import app.pbbls.android.core.data.ConsentServicing
import app.pbbls.android.core.model.ActiveConsent
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.features.consent.ConsentGateLogic

/**
 * In-memory [ConsentServicing]. Defaults to a ledger that already satisfies the
 * gate, so every whole-app test that signs in lands where it did before the
 * gate existed; a gate test clears [rows] first.
 *
 * [record] behaves like `record_consent`: it replaces the active row of that
 * kind, so a follow-up [active] sees the new version.
 */
class FakeConsentService(
    val rows: MutableList<ActiveConsent> = satisfied(),
) : ConsentServicing {
    /** Every (kind, version, source) passed to [record], oldest first. */
    val recordCalls = mutableListOf<Triple<ConsentKind, String, String>>()

    var activeCalls = 0
        private set

    /** Thrown by every [active] call until cleared. */
    var activeFailure: Exception? = null

    /** Thrown by every [record] call until cleared. */
    var recordFailure: Exception? = null

    override suspend fun active(): List<ActiveConsent> {
        activeCalls += 1
        activeFailure?.let { throw it }
        return rows.toList()
    }

    override suspend fun record(
        kind: ConsentKind,
        documentVersion: String,
        source: String,
    ) {
        recordFailure?.let { throw it }
        recordCalls += Triple(kind, documentVersion, source)
        rows.removeAll { it.kind == kind.wire }
        rows += ActiveConsent(kind.wire, documentVersion)
    }

    companion object {
        fun satisfied(): MutableList<ActiveConsent> =
            ConsentGateLogic.REQUIRED
                .map { ActiveConsent(it.wire, ConsentGateLogic.versionFor(it)) }
                .toMutableList()
    }
}
