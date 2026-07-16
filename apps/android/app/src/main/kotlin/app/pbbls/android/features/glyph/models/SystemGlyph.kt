package app.pbbls.android.features.glyph.models

/**
 * UUIDs for the system glyphs seeded server-side — ports iOS
 * `SystemGlyph.swift`. [DEFAULT] matches `souls.glyph_id`'s column default
 * (`20260426000000_add_glyph_to_souls.sql`), so a name-only soul insert and a
 * form defaulting to it agree with the server.
 */
object SystemGlyph {
    const val DEFAULT = "4759c37c-68a6-46a6-b4fc-046bd0316752"
}
