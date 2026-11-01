package app.pbbls.android.core.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.model.SettingsLink
import app.pbbls.android.core.model.decodeSettingsLinks
import app.pbbls.android.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings' links (#1489; iOS `SettingsLinksStore` + `SettingsLinksService`): the last good
 * `settings_links` payload, kept in the app's one Preferences DataStore ([SettingsDataStores.of],
 * the instance [SettingsStore] reads, since two DataStores on one file crash), else the seed
 * bundled in `assets/settings-links-default.json`. A single-table select of the published rows,
 * so no RPC.
 */
@Singleton
class SettingsLinksService internal constructor(
    private val supabase: SupabaseService,
    private val store: DataStore<Preferences>,
    private val io: CoroutineDispatcher,
    private val builtIn: () -> String,
) {
    @Inject
    constructor(
        supabase: SupabaseService,
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ) : this(
        supabase = supabase,
        store = SettingsDataStores.of(context, io),
        io = io,
        builtIn = {
            context.assets
                .open(BUILT_IN)
                .bufferedReader()
                .use { it.readText() }
        },
    )

    /** The kept links, else the built-in ones. */
    suspend fun current(): List<SettingsLink> {
        val kept =
            runCatchingCancellable { store.data.first()[PAYLOAD] }
                .onFailure { Log.e(TAG, "kept settings links unreadable", it) }
                .getOrNull()
        val links =
            kept?.let { payload ->
                runCatchingCancellable { decodeSettingsLinks(payload) }
                    .onFailure { Log.e(TAG, "kept settings links undecodable", it) }
                    .getOrNull()
            }
        return links ?: builtInLinks()
    }

    /** Fetches, keeps and returns the published links; on failure returns [current]. */
    suspend fun refresh(): List<SettingsLink> {
        runCatchingCancellable {
            val body =
                supabase.client
                    .from("settings_links")
                    .select(Columns.raw("key,section,sort_order,title_en,title_fr,url")) {
                        filter { eq("published", true) }
                        order("section", Order.ASCENDING)
                        order("sort_order", Order.ASCENDING)
                    }.data
            // Kept only if this build can read it, so a bad payload never replaces a good one.
            decodeSettingsLinks(body)
            store.edit { it[PAYLOAD] = body }
        }.onFailure { Log.e(TAG, "settings links refresh failed", it) }
        return current()
    }

    private suspend fun builtInLinks(): List<SettingsLink> =
        withContext(io) {
            runCatchingCancellable { decodeSettingsLinks(builtIn()) }
                .onFailure { Log.e(TAG, "built-in settings links missing or unreadable", it) }
                .getOrDefault(emptyList())
        }

    private companion object {
        const val TAG = "settings-links"
        const val BUILT_IN = "settings-links-default.json"
        val PAYLOAD = stringPreferencesKey("settings_links_payload")
    }
}
