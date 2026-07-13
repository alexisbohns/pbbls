package app.pbbls.android.services

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.pbbls.android.features.path.models.Domain
import app.pbbls.android.features.path.models.PebbleCollection
import app.pbbls.android.features.profile.models.SoulRow
import app.pbbls.android.features.profile.models.SoulWithGlyph
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Session cache of the three reference lists the pebble form needs (domains,
 * souls, collections) — ports ReferenceDataService.swift. `load()` is kicked from
 * RootScreen once a user id is present (souls/collections are RLS-scoped). No
 * retry: a failed load leaves empty lists (empty pickers), recovers next launch.
 * `refreshSouls()` closes iOS's stale-list gap after inline soul creation (D11).
 */
class ReferenceDataService(
    private val supabase: SupabaseService,
) {
    var domains: List<Domain> by mutableStateOf(emptyList())
        private set

    var souls: List<SoulWithGlyph> by mutableStateOf(emptyList())
        private set

    var collections: List<PebbleCollection> by mutableStateOf(emptyList())
        private set

    var hasLoaded: Boolean by mutableStateOf(false)
        private set

    suspend fun load() {
        try {
            coroutineScope {
                val domainsDeferred = async { fetchDomains() }
                val soulsDeferred = async { fetchSouls() }
                val collectionsDeferred = async { fetchCollections() }
                domains = domainsDeferred.await()
                souls = soulsDeferred.await()
                collections = collectionsDeferred.await()
                hasLoaded = true
            }
            Log.i(TAG, "loaded ${domains.size} domains, ${souls.size} souls, ${collections.size} collections")
        } catch (e: Exception) {
            Log.e(TAG, "reference data load failed — pickers empty until next launch", e)
        }
    }

    suspend fun refreshSouls() {
        try {
            souls = fetchSouls()
        } catch (e: Exception) {
            Log.e(TAG, "souls refresh failed", e)
        }
    }

    suspend fun refreshCollections() {
        try {
            collections = fetchCollections()
        } catch (e: Exception) {
            Log.e(TAG, "collections refresh failed", e)
        }
    }

    /**
     * Inserts a name-only soul (`souls.glyph_id` has a system-glyph default) and
     * returns the persisted row, updating the [souls] cache in place so the
     * form's selected pill renders immediately — closing iOS's stale-list gap
     * deterministically (D11/D12). Ports `CreateSoulSheet.save()`; mirrors
     * [fetchSouls]'s embedded select. Returns null on failure (logged) so the
     * inline dialog can stay open.
     */
    suspend fun createSoul(name: String): SoulWithGlyph? {
        val userId = supabase.session?.user?.id
        if (userId == null) {
            Log.e(TAG, "createSoul: no session")
            return null
        }
        return try {
            val payload =
                buildJsonObject {
                    put("user_id", userId)
                    put("name", name)
                }
            val inserted =
                supabase.client
                    .from("souls")
                    .insert(payload) {
                        select(Columns.raw("id, name, glyph_id, glyphs(id, name, strokes, view_box)"))
                    }.decodeSingle<SoulRow>()
                    .toSoulWithGlyph()
            souls = (souls + inserted).sortedBy { it.name }
            inserted
        } catch (e: Exception) {
            Log.e(TAG, "createSoul failed", e)
            null
        }
    }

    private suspend fun fetchDomains(): List<Domain> =
        supabase.client
            .from("domains")
            .select {
                order("name", Order.ASCENDING)
            }.decodeList<Domain>()

    private suspend fun fetchSouls(): List<SoulWithGlyph> =
        supabase.client
            .from("souls")
            .select(Columns.raw("id, name, glyph_id, glyphs(id, name, strokes, view_box)")) {
                order("name", Order.ASCENDING)
            }.decodeList<SoulRow>()
            .map { it.toSoulWithGlyph() }

    private suspend fun fetchCollections(): List<PebbleCollection> =
        supabase.client
            .from("collections")
            .select(Columns.list("id", "name")) {
                order("name", Order.ASCENDING)
            }.decodeList<PebbleCollection>()

    companion object {
        private const val TAG = "reference-data"
    }
}

val LocalReferenceDataService =
    staticCompositionLocalOf<ReferenceDataService> {
        error("LocalReferenceDataService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
