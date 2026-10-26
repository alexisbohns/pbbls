import type { Database } from "@pbbls/supabase"

export type GuideRow = Database["public"]["Tables"]["guides"]["Row"]
export type GuideInsert = Database["public"]["Tables"]["guides"]["Insert"]

// The column is a text CHECK, which the generator types as `string | null`;
// the union lives here and ILLUSTRATION_OPTIONS mirrors the CHECK list. The
// two must move together: a stored value the union does not know is shown raw
// in the form and cleared to null on the next save.
export type GuideIllustration =
  | "pebbles" | "stones" | "emotions" | "valence" | "domains"
  | "collections" | "souls" | "glyphs" | "karma"
