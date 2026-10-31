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
 * `get_profile_engagement` gains `glyphs_carved` and `glyphs_owned` (#1386). The build already on
 * Play decodes the row with `decodeList<ProfileEngagement>()` through supabase-kt's default
 * serializer (`SupabaseModule` sets none), and [ProfileEngagement] does not declare those keys.
 * This pins that the default serializer ignores unknown keys, so adding a column to the row can
 * never blank the counters of an installed app. If it ever goes red, the client needs
 * `defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true })` before any RPC
 * gains a key.
 */
class ProfileEngagementDecodingTest {
    private val assiduity = List(28) { it % 3 == 0 }

    /** The row with the two keys the shipped model does not know. */
    private val row =
        """[{"days_practiced":112,"assiduity":[${assiduity.joinToString(",")}],"glyphs_carved":12,"glyphs_owned":5}]"""

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

            assertEquals(listOf(ProfileEngagement(daysPracticed = 112, assiduity = assiduity)), rows)
        }

    @Test
    fun `the default serializer is the layer that ignores them`() {
        val rows = client().defaultSerializer.decode<List<ProfileEngagement>>(typeOf<List<ProfileEngagement>>(), row)

        assertEquals(listOf(ProfileEngagement(daysPracticed = 112, assiduity = assiduity)), rows)
    }
}
