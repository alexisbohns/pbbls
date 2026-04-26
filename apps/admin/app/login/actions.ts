"use server"

import { redirect } from "next/navigation"
import { createServerSupabaseClient } from "@/lib/supabase/server"

export type LoginResult = { error: string } | undefined

export async function signIn(_: LoginResult, formData: FormData): Promise<LoginResult> {
  const email = String(formData.get("email") ?? "").trim()
  const password = String(formData.get("password") ?? "")

  if (!email || !password) {
    return { error: "Email and password are required." }
  }

  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.auth.signInWithPassword({ email, password })

  if (error) {
    console.error("[login/signIn] signInWithPassword failed:", error.message)
    return { error: "Invalid email or password." }
  }

  redirect("/logs")
}
