// apps/admin/lib/pebblestore/types.ts

/** One stroke of a glyph — mirrors the apps/web Mark stroke + DB glyphs.strokes JSON. */
export type GlyphStroke = { d: string; width: number }

export type SubmissionStatus = "pending" | "approved" | "rejected"

/** A row from the admin_list_glyph_submissions RPC. */
export type AdminSubmission = {
  submission_id: string
  glyph_id: string
  status: SubmissionStatus
  price: number
  review_note: string | null
  created_at: string
  reviewed_at: string | null
  submitter_id: string
  submitter_email: string | null
  name: string | null
  shape_id: string | null
  strokes: GlyphStroke[]
  view_box: string
}

/** A pebble shape (reference data) for the shape dropdown + preview clip. */
export type PebbleShape = {
  id: string
  slug: string
  name: string
  path: string
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

/** SVG coordinate-space stroke width used when a source path has none. */
export const DEFAULT_STROKE_WIDTH = 6
