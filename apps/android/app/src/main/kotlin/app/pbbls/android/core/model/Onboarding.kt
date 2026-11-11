package app.pbbls.android.core.model

import androidx.annotation.StringRes
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsGlyph
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** The promise step's letter (#1510 D2), a `promise_letters` row; FR falls back to EN. */
@Serializable
data class PromiseLetter(
    @SerialName("body_md_en") val bodyEn: String,
    @SerialName("body_md_fr") val bodyFr: String? = null,
    @SerialName("signed_by_en") val signedByEn: String,
    @SerialName("signed_by_fr") val signedByFr: String? = null,
) {
    fun body(french: Boolean): String = if (french) bodyFr?.takeIf { it.isNotBlank() } ?: bodyEn else bodyEn

    fun signedBy(french: Boolean): String = if (french) signedByFr?.takeIf { it.isNotBlank() } ?: signedByEn else signedByEn
}

/**
 * Step 3's six tiles (D4), in grid order. [slug] is the wire value in `profiles.onboarding_reasons`.
 * The icons are the nearest drawn ones to the exploration's heart, two people, cloud, sun, winding
 * path and book; the set has no cloud or book, so a hard season is the drizzle and a record is the case.
 */
enum class OnboardingReason(
    val slug: String,
    @StringRes val labelRes: Int,
    val icon: PbblsGlyph,
) {
    NOTICE_FEELINGS("notice-feelings", R.string.onboarding_reason_notice_feelings, PbblsGlyph.HEART),
    KEEP_PEOPLE("keep-people", R.string.onboarding_reason_keep_people, PbblsGlyph.PEOPLE),
    HARD_SEASON("hard-season", R.string.onboarding_reason_hard_season, PbblsGlyph.ABSTRACT_DRIZZLE),
    GOOD_DAYS("good-days", R.string.onboarding_reason_good_days, PbblsGlyph.SUNSHINE),
    OWN_PATTERNS("own-patterns", R.string.onboarding_reason_own_patterns, PbblsGlyph.PATH),
    KEEP_RECORD("keep-record", R.string.onboarding_reason_keep_record, PbblsGlyph.CASE),
}

/** Step 6's four paces (D11), in group order. [slug] is the wire value in `profiles.pace`. */
enum class OnboardingPace(
    val slug: String,
    @StringRes val labelRes: Int,
    @StringRes val detailRes: Int,
    val icon: PbblsGlyph,
) {
    DAILY("daily", R.string.onboarding_pace_daily, R.string.onboarding_pace_daily_detail, PbblsGlyph.SUNSHINE),
    FEW_A_WEEK("few-a-week", R.string.onboarding_pace_few_a_week, R.string.onboarding_pace_few_a_week_detail, PbblsGlyph.PATH),
    WHEN_IT_MATTERS(
        "when-it-matters",
        R.string.onboarding_pace_when_it_matters,
        R.string.onboarding_pace_when_it_matters_detail,
        PbblsGlyph.PEBBLE_ROUND,
    ),
    WEEKLY_WRAP("weekly-wrap", R.string.onboarding_pace_weekly_wrap, R.string.onboarding_pace_weekly_wrap_detail, PbblsGlyph.CALENDAR_DAY),
}

/** One first person (step 5): a name as typed and the glyph on their piece. Always a glyph: `souls.glyph_id` is not null. */
data class OnboardingSoulDraft(
    val name: String,
    val glyphId: String,
)

/**
 * Everything the flow collected, as `complete_onboarding` takes it (D5). A skipped step is null and
 * is left out of the params, so the server sees its default; [souls] is always sent, empty when
 * step 5 was passed with "Later".
 */
data class OnboardingAnswers(
    val displayName: String? = null,
    val glyphId: String? = null,
    val reasons: List<OnboardingReason>? = null,
    val pace: OnboardingPace? = null,
    val souls: List<OnboardingSoulDraft> = emptyList(),
) {
    fun toRpcParams(): JsonObject =
        buildJsonObject {
            displayName?.let { put("p_display_name", it) }
            glyphId?.let { put("p_glyph_id", it) }
            reasons?.let { list -> putJsonArray("p_reasons") { list.forEach { add(it.slug) } } }
            pace?.let { put("p_pace", it.slug) }
            putJsonArray("p_souls") {
                souls.forEach { soul ->
                    addJsonObject {
                        put("name", soul.name)
                        put("glyph_id", soul.glyphId)
                    }
                }
            }
        }
}
