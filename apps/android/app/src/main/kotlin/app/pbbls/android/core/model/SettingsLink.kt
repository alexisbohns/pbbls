package app.pbbls.android.core.model

import android.util.Log
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/** The three sections of Settings' links, in display order; mirrors the column's CHECK (#1489). */
enum class SettingsLinkSection(
    val wire: String,
) {
    LEGAL("legal"),
    ABOUT("about"),
    SUPPORT("support"),
    ;

    companion object {
        fun fromWire(wire: String?): SettingsLinkSection? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * One Settings row from `settings_links` (#1489; iOS `SettingsLink`): admin-edited, read directly
 * (a single-table select). FR falls back to EN, like [app.pbbls.android.core.model.home.Guide].
 */
data class SettingsLink(
    val key: String,
    val section: SettingsLinkSection,
    val titleEn: String,
    val titleFr: String?,
    val url: String,
) {
    /** `https` opens in a Custom Tab; `mailto` goes to the system. */
    val opensInBrowser: Boolean get() = url.startsWith("https://")

    fun title(locale: Locale): String = if (locale.language == "fr") titleFr ?: titleEn else titleEn
}

/** A `settings_links` row as PostgREST sends it; every field optional so one bad row is dropped, not the list. */
@Serializable
private data class SettingsLinkDto(
    val key: String? = null,
    val section: String? = null,
    @SerialName("title_en") val titleEn: String? = null,
    @SerialName("title_fr") val titleFr: String? = null,
    val url: String? = null,
)

private const val TAG = "settings-links"
private val settingsLinkJson = Json { ignoreUnknownKeys = true }
private val allowedUrl = Regex("^(https://|mailto:).+")

/**
 * The rows of a `settings_links` read, in the order sent. Rows this build cannot show (unknown
 * section, another scheme, a blank EN title) are dropped and logged; a body that is not an array
 * of objects throws, so a whole-response failure reaches the caller.
 */
fun decodeSettingsLinks(body: String): List<SettingsLink> =
    settingsLinkJson.decodeFromString<List<SettingsLinkDto>>(body).mapNotNull { row ->
        val key = row.key
        val section = SettingsLinkSection.fromWire(row.section)
        val titleEn = row.titleEn
        val url = row.url
        if (key == null || section == null || titleEn.isNullOrBlank() || url == null || !allowedUrl.matches(url)) {
            Log.w(TAG, "settings link dropped: ${row.key}")
            null
        } else {
            SettingsLink(key, section, titleEn, row.titleFr, url)
        }
    }

/** The non-empty sections in the fixed order legal, about, support, each with its rows in order. */
fun groupSettingsLinks(links: List<SettingsLink>): List<Pair<SettingsLinkSection, List<SettingsLink>>> =
    SettingsLinkSection.entries.mapNotNull { section ->
        links.filter { it.section == section }.takeIf { it.isNotEmpty() }?.let { section to it }
    }
