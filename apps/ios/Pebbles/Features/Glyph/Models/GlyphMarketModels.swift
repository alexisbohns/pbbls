import Foundation

// MARK: - Public models

/// The three tabs on the glyph page.
enum GlyphTab: String, CaseIterable, Identifiable {
    case mine, owned, commu
    var id: String { rawValue }
}

/// One cell in any tab's grid, plus the fields the detail drawer needs.
/// Fields are populated per source: `owned`/`price` matter for Commu,
/// `acquiredAt` only for Owned, etc.
struct GlyphGridItem: Identifiable, Hashable {
    let glyph: Glyph
    /// Swap cost in karma. `0` when the glyph has no approved+listed submission.
    let price: Int
    /// Caller already owns this glyph (drives Commu → OWNED state).
    let owned: Bool
    /// When the glyph was created.
    let createdAt: Date?
    /// When the caller acquired it (Owned tab / OWNED drawer state).
    let acquiredAt: Date?

    var id: UUID { glyph.id }
}

/// Result of the `buy_glyph` RPC.
struct BuyGlyphResult: Decodable, Equatable {
    let entitlementId: UUID
    let balance: Int

    enum CodingKeys: String, CodingKey {
        case entitlementId = "entitlement_id"
        case balance
    }
}

// MARK: - Raw rows (one per query shape)

/// Row from `v_glyph_market` (Commu tab).
struct MarketGlyphRow: Decodable {
    let id: UUID
    let userId: UUID?
    let name: String?
    let strokes: [GlyphStroke]
    let viewBox: String
    let createdAt: String
    let price: Int
    let owned: Bool

    enum CodingKeys: String, CodingKey {
        case id, name, strokes, price, owned
        case userId = "user_id"
        case viewBox = "view_box"
        case createdAt = "created_at"
    }

    func toGridItem() -> GlyphGridItem {
        GlyphGridItem(
            glyph: Glyph(id: id, name: name, strokes: strokes, viewBox: viewBox, userId: userId),
            price: price,
            owned: owned,
            createdAt: GlyphTimestamp.parse(createdAt),
            acquiredAt: nil
        )
    }
}

/// A glyph's submission price rows (embedded in the Mine query).
struct SubmissionPriceRow: Decodable {
    let price: Int
    let status: String
    let listed: Bool
}

/// Row from `glyphs` with embedded submissions (Mine tab).
struct MineGlyphRow: Decodable {
    let id: UUID
    let userId: UUID?
    let name: String?
    let strokes: [GlyphStroke]
    let viewBox: String
    let createdAt: String
    let submissions: [SubmissionPriceRow]

    enum CodingKeys: String, CodingKey {
        case id, name, strokes
        case userId = "user_id"
        case viewBox = "view_box"
        case createdAt = "created_at"
        case submissions = "glyph_submissions"
    }

    /// The live market price = first approved+listed submission, else 0.
    var listedPrice: Int {
        submissions.first { $0.status == "approved" && $0.listed }?.price ?? 0
    }

    func toGridItem() -> GlyphGridItem {
        GlyphGridItem(
            glyph: Glyph(id: id, name: name, strokes: strokes, viewBox: viewBox, userId: userId),
            price: listedPrice,
            owned: false,
            createdAt: GlyphTimestamp.parse(createdAt),
            acquiredAt: nil
        )
    }
}

/// A glyph embedded inside an entitlement row (Owned tab).
struct EntitlementGlyphRow: Decodable {
    let id: UUID
    let userId: UUID?
    let name: String?
    let strokes: [GlyphStroke]
    let viewBox: String
    let createdAt: String

    enum CodingKeys: String, CodingKey {
        case id, name, strokes
        case userId = "user_id"
        case viewBox = "view_box"
        case createdAt = "created_at"
    }
}

/// Row from `glyph_entitlements` with embedded glyph (Owned tab).
struct EntitlementRow: Decodable {
    let pricePaid: Int
    let acquiredAt: String
    let glyph: EntitlementGlyphRow

    enum CodingKeys: String, CodingKey {
        case pricePaid = "price_paid"
        case acquiredAt = "created_at"
        case glyph = "glyphs"
    }

    func toGridItem() -> GlyphGridItem {
        GlyphGridItem(
            glyph: Glyph(id: glyph.id, name: glyph.name, strokes: glyph.strokes, viewBox: glyph.viewBox, userId: glyph.userId),
            price: pricePaid,
            owned: true,
            createdAt: GlyphTimestamp.parse(glyph.createdAt),
            acquiredAt: GlyphTimestamp.parse(acquiredAt)
        )
    }
}

// MARK: - Timestamp parsing

/// Parses PostgREST timestamptz strings into `Date`. Postgres emits variable
/// fractional-second precision (0–6 digits); `ISO8601DateFormatter` only tolerates
/// milliseconds, so we normalize the fraction to 3 digits before parsing.
enum GlyphTimestamp {
    private static let withFraction: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    private static let plain: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    static func parse(_ raw: String?) -> Date? {
        guard let raw else { return nil }
        let normalized = normalizeFraction(raw)
        return withFraction.date(from: normalized) ?? plain.date(from: normalized)
    }

    /// Truncates any `.dddddd` fractional-seconds group to 3 digits.
    private static func normalizeFraction(_ s: String) -> String {
        guard let dot = s.firstIndex(of: ".") else { return s }
        let after = s.index(after: dot)
        var end = after
        while end < s.endIndex, s[end].isNumber { end = s.index(after: end) }
        let digits = s[after..<end]
        guard digits.count > 3 else { return s }
        let keep = s.index(after: s.index(after: s.index(after: after))) // after + 3
        return String(s[s.startIndex..<keep]) + String(s[end...])
    }
}
