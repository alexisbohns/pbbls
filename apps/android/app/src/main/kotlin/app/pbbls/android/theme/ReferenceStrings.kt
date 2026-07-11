package app.pbbls.android.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R

/** Which reference-data table a slug belongs to — see [ReferenceStrings]. */
enum class ReferenceType {
    EMOTION,
    DOMAIN,
    EMOTION_CATEGORY,
}

/**
 * Slug → localized display name for reference data (emotions, domains,
 * emotion categories). Explicit, compile-checked maps (over `getIdentifier`)
 * so a typo or a missing entry fails to compile rather than resolving to
 * nothing at runtime. Mirrors iOS `Emotion+Localized.swift` /
 * `Domain+Localized.swift` (`localizedName`). Never render the DB `name`
 * column directly on a read path — always resolve through [referenceName].
 */
object ReferenceStrings {
    private val emotionNames: Map<String, Int> =
        mapOf(
            "amazed" to R.string.emotion_amazed_name,
            "amused" to R.string.emotion_amused_name,
            "angry" to R.string.emotion_angry_name,
            "annoyed" to R.string.emotion_annoyed_name,
            "anxious" to R.string.emotion_anxious_name,
            "ashamed" to R.string.emotion_ashamed_name,
            "brave" to R.string.emotion_brave_name,
            "calm" to R.string.emotion_calm_name,
            "confident" to R.string.emotion_confident_name,
            "content" to R.string.emotion_content_name,
            "disappointed" to R.string.emotion_disappointed_name,
            "discouraged" to R.string.emotion_discouraged_name,
            "disgusted" to R.string.emotion_disgusted_name,
            "drained" to R.string.emotion_drained_name,
            "embarrassed" to R.string.emotion_embarrassed_name,
            "excited" to R.string.emotion_excited_name,
            "frustrated" to R.string.emotion_frustrated_name,
            "grateful" to R.string.emotion_grateful_name,
            "guilty" to R.string.emotion_guilty_name,
            "happy" to R.string.emotion_happy_name,
            "hopeful" to R.string.emotion_hopeful_name,
            "hopeless" to R.string.emotion_hopeless_name,
            "indifferent" to R.string.emotion_indifferent_name,
            "irritated" to R.string.emotion_irritated_name,
            "jealous" to R.string.emotion_jealous_name,
            "joyful" to R.string.emotion_joyful_name,
            "lonely" to R.string.emotion_lonely_name,
            "overwhelmed" to R.string.emotion_overwhelmed_name,
            "passionate" to R.string.emotion_passionate_name,
            "peaceful" to R.string.emotion_peaceful_name,
            "proud" to R.string.emotion_proud_name,
            "relieved" to R.string.emotion_relieved_name,
            "sad" to R.string.emotion_sad_name,
            "satisfied" to R.string.emotion_satisfied_name,
            "scared" to R.string.emotion_scared_name,
            "stressed" to R.string.emotion_stressed_name,
            "surprised" to R.string.emotion_surprised_name,
            "worried" to R.string.emotion_worried_name,
        )

    private val domainNames: Map<String, Int> =
        mapOf(
            "community" to R.string.domain_community_name,
            "currentevents" to R.string.domain_currentevents_name,
            "dating" to R.string.domain_dating_name,
            "education" to R.string.domain_education_name,
            "family" to R.string.domain_family_name,
            "fitness" to R.string.domain_fitness_name,
            "friends" to R.string.domain_friends_name,
            "health" to R.string.domain_health_name,
            "hobbies" to R.string.domain_hobbies_name,
            "identity" to R.string.domain_identity_name,
            "money" to R.string.domain_money_name,
            "partner" to R.string.domain_partner_name,
            "selfcare" to R.string.domain_selfcare_name,
            "spirituality" to R.string.domain_spirituality_name,
            "tasks" to R.string.domain_tasks_name,
            "travel" to R.string.domain_travel_name,
            "weather" to R.string.domain_weather_name,
            "work" to R.string.domain_work_name,
        )

    private val emotionCategoryNames: Map<String, Int> =
        mapOf(
            "anger" to R.string.emotionCategory_anger_name,
            "fear" to R.string.emotionCategory_fear_name,
            "joy" to R.string.emotionCategory_joy_name,
            "peace" to R.string.emotionCategory_peace_name,
            "pride" to R.string.emotionCategory_pride_name,
            "sadness" to R.string.emotionCategory_sadness_name,
            "shame" to R.string.emotionCategory_shame_name,
        )

    /** Returns the mapped string-resource id for [type]/[slug], or `null` if unmapped. */
    @StringRes
    internal fun resourceId(
        type: ReferenceType,
        slug: String,
    ): Int? =
        when (type) {
            ReferenceType.EMOTION -> emotionNames[slug]
            ReferenceType.DOMAIN -> domainNames[slug]
            ReferenceType.EMOTION_CATEGORY -> emotionCategoryNames[slug]
        }

    /**
     * Localized display name for a reference-data slug, falling back to
     * [fallbackDbName] (the DB `name` column) when no catalog entry exists —
     * safe for new reference rows added server-side before Android catches up.
     */
    @Composable
    fun referenceName(
        type: ReferenceType,
        slug: String,
        fallbackDbName: String,
    ): String {
        val resId = resourceId(type, slug) ?: return fallbackDbName
        return stringResource(resId)
    }
}
