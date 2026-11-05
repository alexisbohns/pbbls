package app.pbbls.android.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The complete_onboarding payload against the bytes the harness sent and the server accepted
 * (`scripts/oracles/onboarding/payloads/complete_onboarding.json`): a same-surface round-trip
 * cannot catch a same-surface encoder bug (the standing cross-surface rule).
 */
class OnboardingPayloadTest {
    private val oracle: JsonObject =
        Json
            .parseToJsonElement(
                checkNotNull(javaClass.getResourceAsStream("/onboarding/complete_onboarding_payload.json")).bufferedReader().readText(),
            ).jsonObject

    private val answers =
        OnboardingAnswers(
            displayName = "  Maya  ",
            glyphId = oracle["p_glyph_id"]!!.jsonPrimitive.content,
            reasons = listOf(OnboardingReason.NOTICE_FEELINGS, OnboardingReason.KEEP_PEOPLE, OnboardingReason.KEEP_RECORD),
            pace = OnboardingPace.WEEKLY_WRAP,
            souls =
                oracle["p_souls"]!!.jsonArray.map {
                    OnboardingSoulDraft(
                        name = it.jsonObject["name"]!!.jsonPrimitive.content,
                        glyphId = it.jsonObject["glyph_id"]!!.jsonPrimitive.content,
                    )
                },
        )

    @Test
    fun `encodes the harness payload verbatim`() {
        assertEquals(oracle, answers.toRpcParams())
    }

    @Test
    fun `a skipped step is an absent key, not null`() {
        val params = OnboardingAnswers().toRpcParams()
        assertNull(params["p_display_name"])
        assertNull(params["p_glyph_id"])
        assertNull(params["p_reasons"])
        assertNull(params["p_pace"])
        assertEquals("[]", params["p_souls"].toString())
    }

    @Test
    fun `reason and pace slugs are the spec's`() {
        assertEquals(
            listOf("notice-feelings", "keep-people", "hard-season", "good-days", "own-patterns", "keep-record"),
            OnboardingReason.entries.map { it.slug },
        )
        assertEquals(listOf("daily", "few-a-week", "when-it-matters", "weekly-wrap"), OnboardingPace.entries.map { it.slug })
    }

    @Test
    fun `the letter falls back to english`() {
        val letter = PromiseLetter(bodyEn = "en", bodyFr = null, signedByEn = "The Pebbles team", signedByFr = null)
        assertEquals("en", letter.body(french = true))
        assertEquals("The Pebbles team", letter.signedBy(french = true))
        val both = letter.copy(bodyFr = "fr", signedByFr = "L'équipe")
        assertEquals("fr", both.body(french = true))
        assertEquals("L'équipe", both.signedBy(french = true))
    }
}
