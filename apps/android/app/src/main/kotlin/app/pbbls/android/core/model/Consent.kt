package app.pbbls.android.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

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

/**
 * The live `health_data` grant Settings shows (#972): when it was given and
 * against which policy version. [grantedAt] goes through
 * [OffsetDateTimeSerializer] because web writes microsecond precision and
 * PostgREST emits `+00:00` offsets, neither of which `Instant.parse` takes.
 */
@Serializable
data class HealthDataConsent(
    @SerialName("document_version") val documentVersion: String,
    @Serializable(with = OffsetDateTimeSerializer::class)
    @SerialName("granted_at")
    val grantedAt: OffsetDateTime,
)
