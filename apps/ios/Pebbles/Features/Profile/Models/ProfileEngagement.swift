import Foundation

/// One-row result of `public.get_profile_engagement(p_tz text)`.
///
/// `daysPracticed` is the all-time distinct count of calendar days
/// (in the caller's timezone) on which the user created any pebble.
///
/// `assiduity` is a 28-element bool array: index 0 = 27 days ago,
/// index 27 = today, both bucketed in the caller's timezone.
/// (Postgres serializes 1-indexed; JSON re-indexes to 0.)
///
/// `glyphsCarved` (the caller's rows in `glyphs`) and `glyphsOwned` (their
/// `glyph_entitlements`) arrive with #1386; nil while the server does not send
/// them, or sends null, so You falls back to count-free lines (#1382 D19).
struct ProfileEngagement: Decodable, Equatable {
    let daysPracticed: Int
    let assiduity: [Bool]
    let glyphsCarved: Int?
    let glyphsOwned: Int?

    enum CodingKeys: String, CodingKey {
        case daysPracticed = "days_practiced"
        case assiduity
        case glyphsCarved = "glyphs_carved"
        case glyphsOwned = "glyphs_owned"
    }
}
