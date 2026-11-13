package app.pbbls.android.core.model

import androidx.annotation.StringRes
import app.pbbls.android.R
import kotlinx.serialization.Serializable

/**
 * Why something is reported (#839, mirrors iOS `ReportReason`). [slug] is the
 * `content_reports.reason` CHECK value, so it never changes; declaration order
 * is the order the report screen lists them in.
 */
enum class ReportReason(
    val slug: String,
    @StringRes val label: Int,
) {
    HARASSMENT("harassment", R.string.report_reason_harassment),
    HATE("hate", R.string.report_reason_hate),
    IMPERSONATION("impersonation", R.string.report_reason_impersonation),
    SEXUAL("sexual", R.string.report_reason_sexual),
    VIOLENCE("violence", R.string.report_reason_violence),
    SELF_HARM("self_harm", R.string.report_reason_self_harm),
    ILLEGAL("illegal", R.string.report_reason_illegal),
    SPAM("spam", R.string.report_reason_spam),
    OTHER("other", R.string.report_reason_other),
}

/**
 * What a report is about. A person is addressed by the route the reporter met
 * them by (their connection, or the invite they hold), never by user id: the
 * app never has one (standing rule), and `report_person` resolves it.
 * `@Serializable` because it rides in `PebblesKey.Report`.
 */
@Serializable
sealed interface ReportTarget {
    @Serializable
    data class Glyph(
        val glyphId: String,
    ) : ReportTarget

    @Serializable
    data class Connection(
        val connectionId: String,
        val name: String?,
    ) : ReportTarget

    @Serializable
    data class Invite(
        val token: String,
        val name: String?,
    ) : ReportTarget

    /** Only a connection can be removed and blocked in the same step. */
    val offersBlock: Boolean get() = this is Connection

    val personName: String?
        get() =
            when (this) {
                is Glyph -> null
                is Connection -> name?.takeIf { it.isNotEmpty() }
                is Invite -> name?.takeIf { it.isNotEmpty() }
            }
}
