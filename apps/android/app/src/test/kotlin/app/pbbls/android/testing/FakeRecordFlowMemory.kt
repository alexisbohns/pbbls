package app.pbbls.android.testing

import app.pbbls.android.core.data.RECENT_DOMAIN_CAP
import app.pbbls.android.core.data.RecordFlowMemorying

class FakeRecordFlowMemory : RecordFlowMemorying {
    val helpSeen = mutableSetOf<String>()
    val domains = mutableMapOf<String, List<String>>()

    override fun isValenceHelpSeen(ownerId: String): Boolean = ownerId in helpSeen

    override fun markValenceHelpSeen(ownerId: String) {
        helpSeen += ownerId
    }

    override fun recentDomainIds(ownerId: String): List<String> = domains[ownerId].orEmpty()

    override fun recordDomains(
        picked: List<String>,
        ownerId: String,
    ) {
        domains[ownerId] = (picked + recentDomainIds(ownerId)).distinct().take(RECENT_DOMAIN_CAP)
    }
}
