import type { Database } from "@pbbls/supabase"

export type PromiseLetterRow = Database["public"]["Tables"]["promise_letters"]["Row"]
export type PromiseLetterInsert = Database["public"]["Tables"]["promise_letters"]["Insert"]
