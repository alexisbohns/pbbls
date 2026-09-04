import { cache } from "react"
import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { SharedPebbleView } from "@/components/shared-pebble/SharedPebbleView"
import type { SharedPebble } from "@/lib/types"

// The public share-by-link page (M51) — pattern: /u/[handle]. Anonymous-
// friendly by construction: get_shared_pebble is granted to `anon`, so the
// cookie-bound server client works with or without a session. The RPC returns
// null for unknown, secret and private ids alike — all land on notFound(), so
// a revoked share (grade flipped back) dies without revealing it ever existed.

type Params = { params: Promise<{ id: string }> }

// The uuid IS the capability (122 unguessable bits); anything that isn't one
// can't be a pebble. Rejecting it here keeps a mangled link a 404 — passed
// through, the RPC's uuid cast would turn it into a 500.
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

// `cache` dedupes within a single request: generateMetadata and the page both
// need the pebble, and an unauthenticated RPC call is not free.
const fetchSharedPebble = cache(
  async (id: string): Promise<SharedPebble | null> => {
    if (!UUID_RE.test(id)) return null
    const supabase = await createServerSupabaseClient()
    const { data, error } = await supabase.rpc("get_shared_pebble", {
      p_pebble_id: id,
    })
    if (error) {
      console.error("[shared-pebble] get_shared_pebble failed:", error.message)
      return null
    }
    return (data as SharedPebble | null) ?? null
  },
)

export async function generateMetadata({ params }: Params): Promise<Metadata> {
  const { id } = await params
  const pebble = await fetchSharedPebble(id)
  // Not-found pages keep the default app metadata — no per-id signal for ids
  // that are secret, private or unknown.
  if (!pebble) return {}

  const title = pebble.name
  const description = "A moment kept on Pebbles."
  return {
    title,
    description,
    openGraph: {
      title,
      description,
      type: "article",
      url: `/p/${pebble.id}`,
    },
    twitter: {
      card: "summary",
      title,
      description,
    },
  }
}

export default async function SharedPebblePage({ params }: Params) {
  const { id } = await params
  const pebble = await fetchSharedPebble(id)
  if (!pebble) notFound()
  return <SharedPebbleView pebble={pebble} />
}
