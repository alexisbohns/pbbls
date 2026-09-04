import { cache } from "react"
import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { PublicProfileView } from "@/components/public-profile/PublicProfileView"
import type { PublicProfile } from "@/lib/types"

// The first server-rendered data route in the app (M50). Anonymous-friendly by
// construction: get_public_profile is granted to `anon`, so the cookie-bound
// server client works with or without a session. The RPC returns null for
// unknown AND known-but-private handles alike — both land on notFound().

type Params = { params: Promise<{ handle: string }> }

// `cache` dedupes within a single request: generateMetadata and the page both
// need the profile, and an unauthenticated RPC call is not free.
const fetchPublicProfile = cache(
  async (handle: string): Promise<PublicProfile | null> => {
    const supabase = await createServerSupabaseClient()
    const { data, error } = await supabase.rpc("get_public_profile", {
      p_handle: handle,
    })
    if (error) {
      console.error("[public-profile] get_public_profile failed:", error.message)
      return null
    }
    return (data as PublicProfile | null) ?? null
  },
)

/**
 * A malformed percent-escape in the URL makes decodeURIComponent throw; a bad
 * handle is a 404, never a 500.
 */
function safeDecode(value: string): string {
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}

export async function generateMetadata({ params }: Params): Promise<Metadata> {
  const { handle } = await params
  const profile = await fetchPublicProfile(safeDecode(handle))
  // Not-found pages keep the default app metadata — no per-handle signal in
  // the title for handles that are private or unknown.
  if (!profile) return {}

  const title = `${profile.display_name} (@${profile.handle})`
  const description = `${profile.display_name} collects meaningful moments on Pebbles, one pebble at a time.`
  return {
    title,
    description,
    openGraph: {
      title,
      description,
      type: "profile",
      url: `/u/${profile.handle}`,
    },
    twitter: {
      card: "summary_large_image",
      title,
      description,
    },
  }
}

export default async function PublicProfilePage({ params }: Params) {
  const { handle } = await params
  const profile = await fetchPublicProfile(safeDecode(handle))
  if (!profile) notFound()
  return <PublicProfileView profile={profile} />
}
