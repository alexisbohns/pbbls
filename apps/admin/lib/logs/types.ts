import type { Database } from "@pbbls/supabase"

export type LogRow = Database["public"]["Tables"]["logs"]["Row"]
export type LogInsert = Database["public"]["Tables"]["logs"]["Insert"]
export type LogUpdate = Database["public"]["Tables"]["logs"]["Update"]

export type LogSpecies = LogRow["species"] // 'announcement' | 'feature'
export type LogStatus = LogRow["status"]   // 'backlog' | 'planned' | 'in_progress' | 'shipped'
export type LogPlatform = LogRow["platform"] // 'web' | 'ios' | 'android' | 'all'
