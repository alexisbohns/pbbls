import type { Database } from "@pbbls/supabase"

export type SettingsLinkRow = Database["public"]["Tables"]["settings_links"]["Row"]
export type SettingsLinkInsert = Database["public"]["Tables"]["settings_links"]["Insert"]
