// UUID of the system default glyph seeded server-side
// (`packages/supabase/supabase/migrations/20260415000001_remote_pebble_engine.sql`,
// re-asserted by `20260426000000_add_glyph_to_souls.sql`).
//
// Souls receive this id when created without a chosen glyph; the row exists
// in `public.glyphs` with `user_id = null` and empty strokes, so it renders
// as a blank pebble outline. Mirrors `SystemGlyph.default` on iOS.
export const DEFAULT_GLYPH_ID = "4759c37c-68a6-46a6-b4fc-046bd0316752"

/**
 * Flat community-glyph price in karma. Mirrors the `price` DEFAULT in
 * `<timestamp>_glyph_marketplace.sql`. Server (`buy_glyph`) is authoritative;
 * this is for display only. Keep both in sync.
 */
export const GLYPH_PRICE_DEFAULT = 25
