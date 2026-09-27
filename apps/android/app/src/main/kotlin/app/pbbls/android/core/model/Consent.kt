package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The consent acts the Android gate asks for. [wire] is `user_consents.kind`. */
enum class ConsentKind(
    val wire: String,
) {
    TERMS("terms"),
    PRIVACY("privacy"),
    HEALTH_DATA("health_data"),
    AGE_ASSURANCE("age_assurance"),
}

/**
 * One active (neither withdrawn nor superseded) `user_consents` row, projected
 * to the two columns the gate reads. [kind] stays a String: the ledger holds
 * kinds this app does not ask for (`public_profile`), and those must decode.
 * No timestamps, on purpose — web writes microsecond `granted_at`, and a
 * column the gate never reads is a cross-surface decoder it does not need.
 */
@Serializable
data class ActiveConsent(
    val kind: String,
    @SerialName("document_version") val documentVersion: String,
)
