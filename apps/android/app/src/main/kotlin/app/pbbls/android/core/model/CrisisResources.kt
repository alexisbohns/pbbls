package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Locale

/** A string in both product languages; anything but French reads English. */
@Serializable
data class Localized(
    val en: String,
    val fr: String,
) {
    fun text(locale: Locale): String = if (locale.language == "fr") fr else en
}

@Serializable
data class CrisisLine(
    val label: Localized,
    val number: String,
    val tel: String,
    val note: Localized? = null,
)

@Serializable
data class CrisisCountry(
    val code: String,
    val name: Localized,
    val lines: List<CrisisLine>,
)

@Serializable
data class CrisisWorldwide(
    val label: Localized,
    val url: String,
)

/** The canonical crisis lines list (`packages/shared/crisis-resources.json`); iOS `CrisisResources`. */
@Serializable
data class CrisisResources(
    val version: Int,
    @SerialName("verified_on") val verifiedOn: String,
    val worldwide: CrisisWorldwide,
    val countries: List<CrisisCountry>,
)

private val crisisResourcesJson = Json { ignoreUnknownKeys = true }

fun decodeCrisisResources(body: String): CrisisResources = crisisResourcesJson.decodeFromString(CrisisResources.serializer(), body)

/** The device region when listed, otherwise FR (the home market). */
fun CrisisResources.defaultCountry(region: String?): CrisisCountry? =
    countries.firstOrNull { region != null && it.code == region.uppercase() }
        ?: countries.firstOrNull { it.code == "FR" }
        ?: countries.firstOrNull()
