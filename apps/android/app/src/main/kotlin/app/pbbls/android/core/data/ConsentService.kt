package app.pbbls.android.core.data

import app.pbbls.android.core.model.ActiveConsent
import app.pbbls.android.core.model.ConsentKind
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** The consent-ledger seam `ConsentGateViewModel` is tested against. */
interface ConsentServicing {
    /** The signed-in user's active rows (neither withdrawn nor superseded). */
    suspend fun active(): List<ActiveConsent>

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
