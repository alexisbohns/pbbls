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
  positiveness: -2 | -1 | 0 | 1 | 2
  emotion_id: string
  soul_ids: string[]
  domain_ids: string[]
  cards: PebbleCard[]
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
