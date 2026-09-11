import { NextResponse } from "next/server"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { isSafeRelativePath } from "@/lib/utils/safe-relative-path"
import { CONSENT_DOCUMENT_VERSION } from "@/lib/config/consent"

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

  // Art. 9 consent from the OAuth path. The register page put the policy
  // version on the callback URL because no signup metadata survives an OAuth
  // round trip; recording it here rather than client-side means it survives
  // whatever the tab does after the redirect.
  //
  // record_consent is idempotent, so a replayed callback is a no-op. A failure
  // must never block the sign-in — it is logged loudly instead, because a
  // silently missing consent record is the exact defect this change fixes.
  //
  // The only legitimate caller is our own buildCallbackUrl, which always sends
  // the current CONSENT_DOCUMENT_VERSION — there is no flow where an older or
  // different version is a valid value here (unlike a ledger row itself, which
  // may legitimately cite an older version recorded at the time). Anything
  // else is either a stale client bundle or a crafted parameter, and the
  // ledger is an accountability record: a garbage document_version is worse
  // than a missing row, since a missing row is at least visibly absent. So we
  // validate against the known-good value rather than trusting it verbatim.
  const consentVersion = searchParams.get("consent")
  if (consentVersion) {
    if (consentVersion !== CONSENT_DOCUMENT_VERSION) {
      console.error(
        `[auth/callback] ignoring consent param with unexpected version: ${consentVersion}`,
      )
    } else {
      const { error: consentError } = await supabase.rpc("record_consent", {
        p_kind: "health_data",
        p_document_version: consentVersion,
        p_source: "web_oauth",
      })
      if (consentError) {
        console.error("[auth/callback] record_consent failed:", consentError.message)
      }
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
  const destination = profile?.onboarding_completed ? next ?? "/path" : "/onboarding"
  return NextResponse.redirect(`${origin}${destination}`)
}
