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
  mark_id?: string
  instants: string[]
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
