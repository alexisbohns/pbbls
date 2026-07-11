package app.pbbls.android.features.path.models

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.OffsetDateTime

/**
 * Serializer for the `timestamptz` columns PostgREST emits as ISO-8601 text
 * with an explicit offset (`2026-07-08T14:23:45.123456+00:00`).
 *
 * [OffsetDateTime.parse] accepts the `+00:00` form, a `Z` suffix, and any
 * fractional-second precision. `java.time.Instant.parse` does NOT accept
 * `+00:00` offsets — do not "simplify" the model fields to `Instant`.
 */
object OffsetDateTimeSerializer : KSerializer<OffsetDateTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("java.time.OffsetDateTime", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): OffsetDateTime = OffsetDateTime.parse(decoder.decodeString())

    override fun serialize(
        encoder: Encoder,
        value: OffsetDateTime,
    ) {
        encoder.encodeString(value.toString())
    }
}
