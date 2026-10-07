package app.pbbls.android.core.data

import app.pbbls.android.core.model.ActiveConsent
import app.pbbls.android.core.model.ConsentKind
import app.pbbls.android.core.model.HealthDataConsent
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** The consent-ledger seam `ConsentGateViewModel` and `SettingsViewModel` are tested against. */
interface ConsentServicing {
    /** The signed-in user's active rows (neither withdrawn nor superseded). */
    suspend fun active(): List<ActiveConsent>

    /** The signed-in user's active `health_data` grant, or null when there is none. */
    suspend fun healthData(): HealthDataConsent?

    /** One consent act through `record_consent`, idempotent server-side. */
    suspend fun record(
        kind: ConsentKind,
        documentVersion: String,
        source: String,
    )
}

/**
 * The `user_consents` ledger. Reads are a single-table select that RLS scopes
 * to the owner; writes go through the `record_consent` definer RPC, because
 * the table has no client write policy at all (20260911090000 §2). Errors
 * propagate: the caller owns the gate's state.
 */
@Singleton
class ConsentService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) : ConsentServicing {
        override suspend fun active(): List<ActiveConsent> =
            supabase.client
                .from("user_consents")
                .select(Columns.list("kind", "document_version")) {
                    filter {
                        exact("withdrawn_at", null)
                        exact("superseded_at", null)
                    }
                }.decodeList()

        override suspend fun healthData(): HealthDataConsent? =
            supabase.client
                .from("user_consents")
                .select(Columns.list("document_version", "granted_at")) {
                    filter {
                        eq("kind", ConsentKind.HEALTH_DATA.wire)
                        exact("withdrawn_at", null)
                        exact("superseded_at", null)
                    }
                    // A partial unique index allows one active row per kind;
                    // the order only makes that assumption explicit.
                    order("granted_at", Order.DESCENDING)
                    limit(1)
                }.decodeList<HealthDataConsent>()
                .firstOrNull()

        override suspend fun record(
            kind: ConsentKind,
            documentVersion: String,
            source: String,
        ) {
            supabase.client.postgrest.rpc(
                "record_consent",
                buildJsonObject {
                    put("p_kind", kind.wire)
                    put("p_document_version", documentVersion)
                    put("p_source", source)
                },
            )
        }
    }
