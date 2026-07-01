// UUID of the system default glyph seeded server-side
// (`packages/supabase/supabase/migrations/20260415000001_remote_pebble_engine.sql`,
// re-asserted by `20260426000000_add_glyph_to_souls.sql`).
//
// Souls receive this id when created without a chosen glyph; the row exists
// in `public.glyphs` with `user_id = null` and empty strokes, so it renders
// as a blank pebble outline. Mirrors `SystemGlyph.default` on iOS.
export const DEFAULT_GLYPH_ID = "4759c37c-68a6-46a6-b4fc-046bd0316752"

/**
 * Canonical glyph coordinate space (#278): every glyph is a shapeless square in
 * a `0 0 200 200` viewBox with a constant 6-unit stroke. Matches iOS carve, the
 * system seeds, and the admin uploader. Rendered by scaling this square into the
 * pebble slot (`renderGlyphPaths` fits it into the 140/150/160 template zone).
 */
export const GLYPH_VIEWBOX = "0 0 200 200"
export const GLYPH_CANVAS = 200
export const GLYPH_STROKE_WIDTH = 6

/**
 * Flat community-glyph price in karma. Mirrors the `price` DEFAULT in
 * `<timestamp>_glyph_marketplace.sql`. Server (`buy_glyph`) is authoritative;
 * this is for display only. Keep both in sync.
 */
export const GLYPH_PRICE_DEFAULT = 25
