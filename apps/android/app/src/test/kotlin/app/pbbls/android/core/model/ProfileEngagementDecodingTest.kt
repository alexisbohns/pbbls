package app.pbbls.android.core.model

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `get_profile_engagement` rows carry `glyphs_carved` and `glyphs_owned` since #1386 (spec D17).
 * Decoded through the same path `PathStatsService` uses: supabase-kt's default serializer behind
 * `postgrest.rpc(...).decodeList<ProfileEngagement>()`, over a mock engine.
 */
class ProfileEngagementDecodingTest {
    @OptIn(SupabaseInternal::class)
    private fun client(body: String): SupabaseClient =
        createSupabaseClient(supabaseUrl = "https://fake.supabase.test", supabaseKey = "not-a-real-key") {
            httpEngine =
                MockEngine {
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            install(Postgrest)
        }

    private suspend fun decode(body: String): List<ProfileEngagement> =
        client(body)
            .postgrest
            .rpc("get_profile_engagement", buildJsonObject { put("p_tz", "Europe/Paris") })
            .decodeList<ProfileEngagement>()

    @Test
    fun `the live row decodes with both glyph counts`() =
        runTest {
            val rows = decode(LIVE_ROW)

            val row = rows.single()
            assertEquals(1, row.daysPracticed)
            assertEquals(List(27) { false } + true, row.assiduity)
            assertEquals(1, row.glyphsCarved)
            assertEquals(0, row.glyphsOwned)
        }

    @Test
    fun `an older server's row without the counts decodes them as null`() =
        runTest {
            val rows = decode("""[{"days_practiced":112,"assiduity":[${List(28) { "true" }.joinToString(",")}]}]""")

            val row = rows.single()
            assertEquals(112, row.daysPracticed)
            assertNull(row.glyphsCarved)
            assertNull(row.glyphsOwned)
        }

    private companion object {
        /**
         * The live project's row, minified from `packages/supabase/scripts/oracles/home/payloads/
         * profile-engagement.json` on the back-end branch `feat/1386-glyph-counts-db` (not on main
         * yet). Values and key order unchanged; only the whitespace went.
         */
        const val LIVE_ROW =
            """[{"days_practiced":1,"assiduity":[false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,false,true],"glyphs_carved":1,"glyphs_owned":0}]"""
    }
}
