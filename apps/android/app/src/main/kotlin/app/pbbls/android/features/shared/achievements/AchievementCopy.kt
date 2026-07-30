package app.pbbls.android.features.shared.achievements

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.pbbls.android.R
import app.pbbls.android.services.AchievementRecord
import app.pbbls.android.services.LocalEmotionPaletteService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.theme.ReferenceStrings
import app.pbbls.android.theme.ReferenceType
import java.util.Locale

// Display copy for a badge (design D7): the admin's `title_*`/`description_*`
// overrides win by the active locale, else the family-keyed string resources
// with the threshold or the localized emotion/domain name interpolated
// (`ReferenceStrings.referenceName`, never the DB `name`). Unknown families
// degrade to the slug rather than crash — a family can ship server-side
// before this client catches up.

/**
 * Pure override pick, mirrored from web/iOS: `fr` prefers the French override,
 * any other locale the English one; either falls back to its sibling before
 * dropping to the family strings, so a half-filled row still shows copy.
 */
internal fun achievementOverride(
    en: String?,
    fr: String?,
    isFrench: Boolean,
): String? = if (isFrench) fr ?: en else en ?: fr

private val isFrenchLocale: Boolean
    get() = Locale.getDefault().language == "fr"

@Composable
fun achievementTitle(record: AchievementRecord): String {
    achievementOverride(record.titleEn, record.titleFr, isFrenchLocale)?.let { return it }
    val threshold = record.threshold ?: 0
    return when (record.family) {
        "pebble_count" -> pluralStringResource(R.plurals.achievement_family_pebble_count_title, threshold, threshold)
        "emotion_first" -> stringResource(R.string.achievement_family_emotion_first_title, emotionName(record))
        "domain_first" -> stringResource(R.string.achievement_family_domain_first_title, domainName(record))
        "first_collection" -> stringResource(R.string.achievement_family_first_collection_title)
        "first_glyph" -> stringResource(R.string.achievement_family_first_glyph_title)
        "first_soul" -> stringResource(R.string.achievement_family_first_soul_title)
        "glyph_count" -> stringResource(R.string.achievement_family_glyph_count_title, threshold)
        "glyph_sales" -> pluralStringResource(R.plurals.achievement_family_glyph_sales_title, threshold, threshold)
        else -> record.slug
    }
}

@Composable
fun achievementDescription(record: AchievementRecord): String? {
    achievementOverride(record.descriptionEn, record.descriptionFr, isFrenchLocale)?.let { return it }
    val threshold = record.threshold ?: 0
    return when (record.family) {
        "pebble_count" ->
            pluralStringResource(R.plurals.achievement_family_pebble_count_description, threshold, threshold)
        "emotion_first" -> stringResource(R.string.achievement_family_emotion_first_description, emotionName(record))
        "domain_first" -> stringResource(R.string.achievement_family_domain_first_description, domainName(record))
        "first_collection" -> stringResource(R.string.achievement_family_first_collection_description)
        "first_glyph" -> stringResource(R.string.achievement_family_first_glyph_description)
        "first_soul" -> stringResource(R.string.achievement_family_first_soul_description)
        "glyph_count" -> stringResource(R.string.achievement_family_glyph_count_description, threshold)
        "glyph_sales" ->
            pluralStringResource(R.plurals.achievement_family_glyph_sales_description, threshold, threshold)
        else -> null
    }
}

/** Group heading for a family section; unknown families show their raw value. */
@Composable
fun achievementGroupName(family: String): String =
    when (family) {
        "pebble_count" -> stringResource(R.string.achievement_group_pebble_count)
        "emotion_first" -> stringResource(R.string.achievement_group_emotion_first)
        "domain_first" -> stringResource(R.string.achievement_group_domain_first)
        "first_collection" -> stringResource(R.string.achievement_group_first_collection)
        "first_glyph" -> stringResource(R.string.achievement_group_first_glyph)
        "first_soul" -> stringResource(R.string.achievement_group_first_soul)
        "glyph_count" -> stringResource(R.string.achievement_group_glyph_count)
        "glyph_sales" -> stringResource(R.string.achievement_group_glyph_sales)
        else -> family
    }

/** Icon per family from the existing asset set; unknown families get the trophy. */
fun achievementFamilyIcon(family: String): Int =
    when (family) {
        "pebble_count" -> R.drawable.ic_fossil_shell
        "emotion_first" -> R.drawable.ic_pebble_emotion
        "domain_first" -> R.drawable.ic_pebble_domain
        "first_collection" -> R.drawable.ic_stack
        "first_glyph" -> R.drawable.ic_scribble
        "first_soul" -> R.drawable.ic_person_pair
        "glyph_count" -> R.drawable.ic_scribble
        "glyph_sales" -> R.drawable.ic_sparkle
        else -> R.drawable.ic_trophy
    }

@Composable
private fun emotionName(record: AchievementRecord): String {
    val palettes = LocalEmotionPaletteService.current
    val emotion = record.emotionId?.let { palettes.byEmotionId[it] }
    return emotion?.let { ReferenceStrings.referenceName(ReferenceType.EMOTION, it.slug, it.name) }
        ?: record.slug
}

@Composable
private fun domainName(record: AchievementRecord): String {
    val refs = LocalReferenceDataService.current
    val domain = record.domainId?.let { id -> refs.domains.firstOrNull { it.id == id } }
    return domain?.let { ReferenceStrings.referenceName(ReferenceType.DOMAIN, it.slug, it.name) }
        ?: record.slug
}
