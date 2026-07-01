// apps/admin/lib/pebblestore/types.ts

/** One stroke of a glyph — mirrors the apps/web Mark stroke + DB glyphs.strokes JSON. */
export type GlyphStroke = { d: string; width: number }

export type SubmissionStatus = "pending" | "approved" | "rejected"

/** A row from the admin_list_glyph_submissions RPC. */
export type AdminSubmission = {
  submission_id: string
  glyph_id: string
  status: SubmissionStatus
  listed: boolean
  price: number
  review_note: string | null
  created_at: string
  reviewed_at: string | null
  submitter_id: string
  submitter_email: string | null
  owner_id: string | null // the glyph's current owner (creator); payouts route here
  owner_email: string | null
  name: string | null
  strokes: GlyphStroke[]
  view_box: string
}

/** Adjust controls for the upload flow. scale=1, no offset, no flip is identity. */
export type Adjust = {
  scale: number
  offsetX: number
  offsetY: number
  flipH: boolean
  flipV: boolean
}

export const IDENTITY_ADJUST: Adjust = {
  scale: 1,
  offsetX: 0,
  offsetY: 0,
  flipH: false,
  flipV: false,
}

/** Display/sanity mirror of the SQL default; the server is authoritative. */
export const GLYPH_PRICE_DEFAULT = 25

/** Stroke width in glyph coordinate space — constant for every glyph. */
export const DEFAULT_STROKE_WIDTH = 6

/** Side of the canonical square viewBox every glyph is normalized into (#278). */
export const GLYPH_CANVAS = 200

/** The canonical square viewBox string (`"0 0 100 100"`). */
export const GLYPH_CANVAS_VIEWBOX = `0 0 ${GLYPH_CANVAS} ${GLYPH_CANVAS}`
