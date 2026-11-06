package app.pbbls.android.core.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** At most this many recent domains are kept, newest first (the Life screen's RECENT row). */
const val RECENT_DOMAIN_CAP = 5

/**
 * What the record flow remembers on the device, per account — ports iOS `ValenceHelpSeen` and
 * `RecentDomains`. The fake is a pair of maps.
 */
interface RecordFlowMemorying {
    /** True once the valence help sheet has opened by itself for [ownerId]. */
    fun isValenceHelpSeen(ownerId: String): Boolean

    fun markValenceHelpSeen(ownerId: String)

    /** Newest first, [RECENT_DOMAIN_CAP] at most. Unreadable data reads as none. */
    fun recentDomainIds(ownerId: String): List<String>

    /** Forgets every account's recent domains: they say which domains someone tagged (sign-out, GDP-07). The help flag stays. */
    fun clearDomains()

    /** [picked] go first, then the ones already remembered, each id once. */
    fun recordDomains(
        picked: List<String>,
        ownerId: String,
    )
}

/**
 * [RecordFlowMemorying] in `pebbles_prefs`, the file the Complete autosaves use. Keys are per owner
 * and lowercase, as iOS's `valenceHelpSeen.<owner>` and `recentDomains.<owner>`. Device-local on
 * purpose: the Path's summaries carry no domains, and the help flag is about this phone.
 */
@Singleton
class RecordFlowMemory internal constructor(
    private val prefs: SharedPreferences,
) : RecordFlowMemorying {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    override fun isValenceHelpSeen(ownerId: String): Boolean = prefs.getBoolean(helpKey(ownerId), false)

    override fun markValenceHelpSeen(ownerId: String) {
        prefs.edit { putBoolean(helpKey(ownerId), true) }
    }

    override fun recentDomainIds(ownerId: String): List<String> {
        val raw = prefs.getString(domainsKey(ownerId), null) ?: return emptyList()
        return try {
            json.decodeFromString(ids, raw).take(RECENT_DOMAIN_CAP)
        } catch (e: IllegalArgumentException) {
            // A malformed entry is not worth a crash on the Life screen: it reads as empty and the next pick overwrites it.
            Log.w(TAG, "discarding unreadable recent domains", e)
            emptyList()
        }
    }

    override fun recordDomains(
        picked: List<String>,
        ownerId: String,
    ) {
        val ordered = (picked + recentDomainIds(ownerId)).distinct().take(RECENT_DOMAIN_CAP)
        prefs.edit { putString(domainsKey(ownerId), json.encodeToString(ids, ordered)) }
    }

    override fun clearDomains() {
        val keys = prefs.all.keys.filter { it.startsWith(DOMAINS_PREFIX) }
        prefs.edit { keys.forEach { remove(it) } }
    }

    companion object {
        private const val TAG = "record-flow-memory"
        private const val PREFS_NAME = "pebbles_prefs"
        private val json = Json { ignoreUnknownKeys = true }
        private val ids = ListSerializer(String.serializer())

        fun helpKey(ownerId: String): String = "valenceHelpSeen.${ownerId.lowercase()}"

        private const val DOMAINS_PREFIX = "recentDomains."

        fun domainsKey(ownerId: String): String = DOMAINS_PREFIX + ownerId.lowercase()
    }
}
