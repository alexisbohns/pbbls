import { NextResponse } from "next/server"
import { createServerSupabaseClient } from "@/lib/supabase/server"

export async function GET(request: Request) {
  const { searchParams, origin } = new URL(request.url)
  const code = searchParams.get("code")

  if (!code) {
    console.error("[auth/callback] No code parameter in callback URL")
    return NextResponse.redirect(`${origin}/login?error=auth_callback_failed`)
  }

  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.auth.exchangeCodeForSession(code)

  if (error) {
    console.error("[auth/callback] exchangeCodeForSession failed:", error.message)
    return NextResponse.redirect(`${origin}/login?error=auth_callback_failed`)
  }

  const {
    data: { user },
  } = await supabase.auth.getUser()

  if (!user) {
    console.error("[auth/callback] getUser() returned null after successful code exchange")
    return NextResponse.redirect(`${origin}/login?error=auth_callback_failed`)
  }

  const { data: profile } = await supabase
    .from("profiles")
    .select("onboarding_completed")
    .eq("user_id", user.id)
    .maybeSingle()

  if (!profile) {
    // Trigger may not have fired — create the profile row server-side.
    const fullName = user.user_metadata?.full_name as string | undefined
    const { error: insertError } = await supabase
      .from("profiles")
      .insert({
        user_id: user.id,
        display_name: fullName ?? "Pebbler",
      })
    if (insertError) {
      // Log but don't fail — trigger might have created the row between
      // our SELECT and INSERT (race condition)
      console.warn("[auth/callback] profile insert failed:", insertError.message)
    }
  }

  const destination = profile?.onboarding_completed ? "/path" : "/onboarding"
  return NextResponse.redirect(`${origin}${destination}`)
}
