import { createServerSupabaseClient } from "@/lib/supabase/server"
import { previewConnectionInvite } from "@/lib/data/invite-api"
import { AcceptInvite } from "@/components/connections/AcceptInvite"

// The app's first server-rendered data page (new pattern, sanctioned by the
// M49 design D12): the anon-callable preview must render for signed-out
// visitors, link unfurlers and the sign-up-first funnel alike.
//
// force-dynamic is REQUIRED: the build environment has no Supabase env vars,
// so a build-time prerender attempt would throw inside
// createServerSupabaseClient and fail `next build`. The page is inherently
// per-request anyway (cookies + a live token lookup).
export const dynamic = "force-dynamic"

export default async function InvitePage({
  params,
}: {
  params: Promise<{ token: string }>
}) {
  const { token } = await params
  const supabase = await createServerSupabaseClient()
  const preview = await previewConnectionInvite(supabase, token).catch((err: unknown) => {
    // Transport/config failure, not a dead token (the RPC never raises) —
    // log server-side and let the error boundary render.
    console.error("[invite] preview failed", err)
    throw err
  })
  return <AcceptInvite preview={preview} token={token} />
}
