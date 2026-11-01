package app.pbbls.android.testing

import app.pbbls.android.core.data.SettingsLinksServicing
import app.pbbls.android.core.model.SettingsLink
import app.pbbls.android.core.model.decodeSettingsLinks
import kotlinx.coroutines.CompletableDeferred

/** The links a database payload fixture (`src/test/resources`, copied verbatim) decodes to. */
fun settingsLinksFixture(name: String = "settings-links.json"): List<SettingsLink> =
    decodeSettingsLinks(requireNotNull(FakeSettingsLinksService::class.java.classLoader?.getResource(name)).readText())

/**
 * In-memory [SettingsLinksServicing] (#1489). No armed failure: the real service swallows its own
 * (a failed refresh answers what is kept), so [published] null stands for "the refresh failed".
 */
class FakeSettingsLinksService(
    /** What [current] answers, and what a failed refresh falls back to. */
    var kept: List<SettingsLink> = emptyList(),
    /** What the next [refresh] reads and keeps; null is a failed refresh. */
    var published: List<SettingsLink>? = null,
) : SettingsLinksServicing {
    var refreshCalls = 0
        private set

    /** When set, [refresh] waits for it, so a test can see the kept list before the fresh one. */
    var refreshGate: CompletableDeferred<Unit>? = null

    override suspend fun current(): List<SettingsLink> = kept

    override suspend fun refresh(): List<SettingsLink> {
        refreshCalls++
        refreshGate?.await()
        published?.let { kept = it }
        return kept
    }
}
