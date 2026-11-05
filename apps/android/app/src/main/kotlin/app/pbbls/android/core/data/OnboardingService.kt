package app.pbbls.android.core.data

import app.pbbls.android.core.model.Glyph
import app.pbbls.android.core.model.OnboardingAnswers
import app.pbbls.android.core.model.PromiseLetter
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val SESSION_WAIT_MS = 10_000L

/** The onboarding's reads and its one write (#1510), a seam so `OnboardingViewModelTest` fakes it. */
interface OnboardingServicing {
    /**
     * Returns once the restored session is live, or after a bounded wait. Until then a request goes
     * out with the anon key, and RLS answers it with an empty 200 instead of an error: the glyphs
     * and the consent ledger would both read as "nothing there".
     */
    suspend fun awaitSession()

    /** The most recently updated published letter, or null when none is published. */
    suspend fun promiseLetter(): PromiseLetter?

    /** The system glyphs, for the starter grid (D7): eight are shown. */
    suspend fun systemGlyphs(): List<Glyph>

    /** `complete_onboarding`: one transaction for everything collected (D5). Throws on refusal. */
    suspend fun complete(answers: OnboardingAnswers)
}

@Singleton
class OnboardingService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) : OnboardingServicing {
        override suspend fun awaitSession() {
            withTimeoutOrNull(SESSION_WAIT_MS) {
                supabase.client.auth.sessionStatus
                    .first { it is SessionStatus.Authenticated }
            }
        }

        override suspend fun promiseLetter(): PromiseLetter? =
            supabase.client
                .from("promise_letters")
                .select(Columns.raw("body_md_en, body_md_fr, signed_by_en, signed_by_fr")) {
                    filter { eq("published", true) }
                    order("updated_at", Order.DESCENDING)
                    limit(1)
                }.decodeList<PromiseLetter>()
                .firstOrNull()

        override suspend fun systemGlyphs(): List<Glyph> =
            supabase.client
                .from("glyphs")
                .select(Columns.raw("id, name, strokes, view_box, user_id, is_system")) {
                    filter { eq("is_system", true) }
                    order("created_at", Order.ASCENDING)
                    limit(8)
                }.decodeList<Glyph>()

        override suspend fun complete(answers: OnboardingAnswers) {
            supabase.client.postgrest.rpc("complete_onboarding", answers.toRpcParams())
        }
    }
