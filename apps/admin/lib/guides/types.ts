import type { Database } from "@pbbls/supabase"

export type GuideRow = Database["public"]["Tables"]["guides"]["Row"]
export type GuideInsert = Database["public"]["Tables"]["guides"]["Insert"]
export type GuideUpdate = Database["public"]["Tables"]["guides"]["Update"]

// The column is a text CHECK, which the generator types as `string | null`;
// the union lives here and ILLUSTRATION_OPTIONS mirrors the CHECK list.
export type GuideIllustration =
  | "pebbles" | "stones" | "emotions" | "valence" | "domains"
  | "collections" | "souls" | "glyphs" | "karma"
