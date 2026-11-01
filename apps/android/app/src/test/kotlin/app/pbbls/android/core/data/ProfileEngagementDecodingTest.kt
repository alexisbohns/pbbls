package app.pbbls.android.core.data

import app.pbbls.android.core.model.ProfileEngagement
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
import org.junit.Test
import kotlin.reflect.typeOf

/**
 * `get_profile_engagement` gained `glyphs_carved` and `glyphs_owned` (#1386) while the build on
 * Play declared neither. The app decodes the row with `decodeList<ProfileEngagement>()` through
 * supabase-kt's default serializer (`SupabaseModule` sets none), and that serializer ignores
 * unknown keys, so adding a column to the row never blanks the counters of an installed app.
 * The row here carries a key no build declares, standing in for the next column. If this ever
 * goes red, the client needs `defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys =
 * true })` before any RPC gains a key.
 */
class ProfileEngagementDecodingTest {
    private val assiduity = List(28) { it % 3 == 0 }
    private val assiduityJson = assiduity.joinToString(",", prefix = "[", postfix = "]")

    /** The row as served today, plus a key no build declares. */
    private val row =
        """[{"days_practiced":112,"assiduity":$assiduityJson,"glyphs_carved":12,"glyphs_owned":5,"a_column_from_tomorrow":true}]"""

    private val expected =
        listOf(ProfileEngagement(daysPracticed = 112, assiduity = assiduity, glyphsCarved = 12, glyphsOwned = 5))

    /** Built like `SupabaseModule.provideSupabaseClient`: no serializer is set. */
    @OptIn(SupabaseInternal::class)
    private fun client(): SupabaseClient =
        createSupabaseClient(supabaseUrl = "https://fake.supabase.test", supabaseKey = "not-a-real-key") {
            httpEngine =
                MockEngine {
                    respond(row, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            install(Postgrest)
        }

    @Test
    fun `the rpc decode ignores keys the model does not declare`() =
        runTest {
            val rows =
                client()
                    .postgrest
                    .rpc("get_profile_engagement", buildJsonObject { put("p_tz", "Europe/Paris") })
                    .decodeList<ProfileEngagement>()

            assertEquals(expected, rows)
        }

    @Test
    fun `the default serializer is the layer that ignores them`() {
        val rows = client().defaultSerializer.decode<List<ProfileEngagement>>(typeOf<List<ProfileEngagement>>(), row)

        assertEquals(expected, rows)
    }
}
