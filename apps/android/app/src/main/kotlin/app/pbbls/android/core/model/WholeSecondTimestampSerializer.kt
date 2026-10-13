package app.pbbls.android.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * A timestamp that crosses a surface boundary: emitted in UTC at whole seconds,
 * the narrowest precision every reader accepts (root CLAUDE.md), and parsed
 * tolerantly (`Z`, `+00:00`, any fraction). `save_moment` truncates to seconds
 * too; emitting them already truncated keeps a client round-trip exact.
 *
 * An explicit pattern rather than `ISO_OFFSET_DATE_TIME`, whose output drops
 * zero seconds and keeps a non-zero fraction.
 */
object WholeSecondTimestampSerializer : KSerializer<OffsetDateTime> {
    private val format: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("app.pbbls.WholeSecondTimestamp", PrimitiveKind.STRING)

    fun encode(value: OffsetDateTime): String = value.withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).format(format)

    fun parse(raw: String): OffsetDateTime = OffsetDateTime.parse(raw)

    override fun serialize(
        encoder: Encoder,
        value: OffsetDateTime,
    ) = encoder.encodeString(encode(value))

    override fun deserialize(decoder: Decoder): OffsetDateTime = parse(decoder.decodeString())
}
