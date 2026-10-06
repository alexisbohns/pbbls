import { NextResponse } from "next/server"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { isSafeRelativePath } from "@/lib/utils/safe-relative-path"
import { CONSENT_DOCUMENT_VERSION } from "@/lib/config/consent"
import { oauthConsentActs } from "@/lib/auth/oauth-consents"
import { REAUTH_RETURN_PARAM } from "@/lib/auth/pending-reauth"

export async function GET(request: Request) {
  const { searchParams, origin } = new URL(request.url)
  const code = searchParams.get("code")

  // Optional post-auth destination (M49, design D12). Anything that is not
  // strictly relative is ignored — never redirect off-origin from here.
  const nextParam = searchParams.get("next")
  const next = isSafeRelativePath(nextParam) ? nextParam : null

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

  // The four /register acts from the OAuth path (terms, privacy, Art. 9 health
  // data, 16+). The register page put the policy version on the callback URL
  // because no signup metadata survives an OAuth round trip; recording it here
  // rather than client-side means it survives whatever the tab does after the
  // redirect. oauthConsentActs validates the param against the known-good
  // version and says why a mismatch records nothing.
  //
  // record_consent is idempotent, so a replayed callback is a no-op. A failure
  // must never block the sign-in — it is logged loudly instead, and the
  // post-auth consent gate asks for whatever is still missing.
  const consentParam = searchParams.get("consent")
  if (consentParam && consentParam !== CONSENT_DOCUMENT_VERSION) {
    console.error(
      `[auth/callback] ignoring consent param with unexpected version: ${consentParam}`,
    )
  }
  for (const act of oauthConsentActs(consentParam)) {
    const { error: consentError } = await supabase.rpc("record_consent", {
      p_kind: act.kind,
      p_document_version: act.version,
      p_source: "web_oauth",
    })
    if (consentError) {
      console.error(`[auth/callback] record_consent (${act.kind}) failed:`, consentError.message)
    }
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

  // `next` applies to onboarded users only: an un-onboarded user always goes
  // to /onboarding first, and the pending-invite mechanism brings them back.
  //
  // Except a re-auth return (#977): the provider can come back as a different,
  // even brand-new, account, and the settings page is where that mismatch is
  // caught and signed out. Sending it to onboarding would skip the check.
  const isReauthReturn = next !== null && new URL(next, origin).searchParams.has(REAUTH_RETURN_PARAM)
  const destination =
    profile?.onboarding_completed || isReauthReturn ? next ?? "/path" : "/onboarding"
  return NextResponse.redirect(`${origin}${destination}`)
}
