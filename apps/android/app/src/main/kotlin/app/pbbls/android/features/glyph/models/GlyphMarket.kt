package app.pbbls.android.features.glyph.models

import app.pbbls.android.features.path.models.OffsetDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

/**
 * One store/picker grid cell — ports iOS `GlyphGridItem`: the glyph plus its
 * market face. [price] is 0 when no approved+listed submission exists;
 * [acquiredAt] is the entitlement's creation time (Owned tab only).
 */
data class GlyphGridItem(
    val glyph: Glyph,
    val price: Int,
    val owned: Boolean,
    val createdAt: OffsetDateTime?,
    val acquiredAt: OffsetDateTime?,
) {
    val id: String get() = glyph.id
}

/** `buy_glyph`'s scalar jsonb result — `{entitlement_id, balance}` (buyer's new wallet). */
@Serializable
data class BuyGlyphResult(
    @SerialName("entitlement_id")
    val entitlementId: String,
    val balance: Int,
)

/**
 * Wire row for the Mine query — `glyphs` with the embedded
 * `glyph_submissions(price, status, listed)` the price badge derives from.
 */
@Serializable
data class MineGlyphRow(
    val id: String,
    val name: String? = null,
    val strokes: List<GlyphStroke>,
    @SerialName("view_box")
    val viewBox: String,
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("created_at")
    @Serializable(with = OffsetDateTimeSerializer::class)
    val createdAt: OffsetDateTime? = null,
    @SerialName("glyph_submissions")
    val submissions: List<SubmissionPriceRow> = emptyList(),
) {
    @Serializable
    data class SubmissionPriceRow(
        val price: Int,
        val status: String,
        val listed: Boolean,
    )

    /** iOS `listedPrice`: the first approved+listed submission's price, else 0. */
    val listedPrice: Int
        get() = submissions.firstOrNull { it.status == "approved" && it.listed }?.price ?: 0

    fun toGlyph(): Glyph = Glyph(id = id, name = name, strokes = strokes, viewBox = viewBox, userId = userId)
}

/** Wire row for the Owned query — `glyph_entitlements` with the joined glyph. */
@Serializable
data class OwnedGlyphRow(
    @SerialName("price_paid")
    val pricePaid: Int,
    @SerialName("created_at")
    @Serializable(with = OffsetDateTimeSerializer::class)
    val acquiredAt: OffsetDateTime? = null,
    @SerialName("glyphs")
    val glyph: MineGlyphRow,
)

/** Wire row for the Commu query — `v_glyph_market` (flat, price/owned precomputed). */
@Serializable
data class MarketGlyphRow(
    val id: String,
    @SerialName("user_id")
    val userId: String? = null,
    val name: String? = null,
    val strokes: List<GlyphStroke>,
    @SerialName("view_box")
    val viewBox: String,
    @SerialName("created_at")
    @Serializable(with = OffsetDateTimeSerializer::class)
    val createdAt: OffsetDateTime? = null,
    val price: Int,
    val owned: Boolean,
) {
    fun toGlyph(): Glyph = Glyph(id = id, name = name, strokes = strokes, viewBox = viewBox, userId = userId)
}
