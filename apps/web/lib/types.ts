// Re-export static config types for convenience
export type { Emotion, Domain, CardType } from "./config"

export type ColorWorld =
  | "blush-quartz"
  | "stoic-rock"
  | "cave-pigment"
  | "dusk-stone"
  | "moss-pool"

export type PebbleCard = {
  species_id: string
  value: string
}

// One row from `public.snaps`. `storage_path` is the per-snap directory under
// `pebbles-media` (`{userId}/{snapId}`) — actual files live at
// `{storage_path}/original.jpg` and `{storage_path}/thumb.jpg`.
export type PebbleSnap = {
  id: string
  storage_path: string
  sort_order: number
}

export type Pebble = {
  id: string
  name: string
  description?: string
  happened_at: string
  intensity: 1 | 2 | 3
  positiveness: -1 | 0 | 1
  visibility: "private" | "public"
  emotion_id: string
  soul_ids: string[]
  domain_ids: string[]
  collection_ids: string[]
  mark_id?: string
  // Signed `original.jpg` URLs derived from `snaps` at load time. Display-only.
  instants: string[]
  snaps: PebbleSnap[]
  cards: PebbleCard[]
  // Server-rendered pebble. Populated by the compose-pebble / compose-pebble-update
  // edge functions; null for legacy rows or anonymous (LocalProvider) pebbles.
  render_svg: string | null
  render_version: string | null
  created_at: string
  updated_at: string
}

export type Soul = {
  id: string
  name: string
  glyph_id: string
  created_at: string
  updated_at: string
}

export type Collection = {
  id: string
  name: string
  mode?: "stack" | "pack" | "track"
  pebble_ids: string[]
  created_at: string
  updated_at: string
}

export type KarmaEvent = {
  delta: number
  reason: string
  ref_id?: string
  created_at: string
}

export type MarkStroke = {
  d: string
  width: number
}

export type Mark = {
  id: string
  name?: string
  shape_id: string
  strokes: MarkStroke[]
  viewBox: string
  created_at: string
  updated_at: string
}

// ---------------------------------------------------------------------------
// Lab — product-transparency feed (announcements, changelog, backlog)
// ---------------------------------------------------------------------------

export type LogSpecies = "announcement" | "feature"
export type LogStatus = "backlog" | "planned" | "in_progress" | "shipped"
export type LogPlatform = "webapp" | "ios" | "android" | "all" | "project" | "infra"

export type Log = {
  id: string
  species: LogSpecies
  platform: LogPlatform
  status: LogStatus
  title_en: string
  title_fr: string | null
  summary_en: string
  summary_fr: string | null
  body_md_en: string | null
  body_md_fr: string | null
  cover_image_path: string | null
  external_url: string | null
  published: boolean
  published_at: string | null
  released_at: string | null
  created_at: string
  reaction_count: number
}

// ---------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------

export type Account = {
  id: string
  email: string
  created_at: string
}

export type Profile = {
  id: string
  user_id: string
  display_name: string
  onboarding_completed: boolean
  color_world: ColorWorld
  terms_accepted_at: string | null
  privacy_accepted_at: string | null
  created_at: string
  updated_at: string
}

export type RegisterInput = {
  email: string
  password: string
  terms_accepted: boolean
  privacy_accepted: boolean
}
export type LoginInput = { email: string; password: string }
export type UpdateProfileInput = Partial<
  Omit<Profile, "id" | "user_id" | "created_at" | "updated_at">
>
