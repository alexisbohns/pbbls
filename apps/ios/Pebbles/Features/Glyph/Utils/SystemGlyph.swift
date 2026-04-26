import Foundation

/// UUIDs for the system glyphs seeded server-side (migration
/// `20260415000001_remote_pebble_engine.sql` and re-asserted by
/// `20260426000000_add_glyph_to_souls.sql`).
///
/// `default` is the canonical fallback used when a soul or domain has no
/// user-carved glyph attached. iOS uses it to seed `SoulDraft.glyphId`
/// when creating a new soul, and the migration uses it as the column default.
enum SystemGlyph {
    static let `default` = UUID(uuidString: "4759c37c-68a6-46a6-b4fc-046bd0316752")!
}
