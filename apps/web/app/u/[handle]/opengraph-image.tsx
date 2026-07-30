import { ImageResponse } from "next/og"

// Dynamic OG share card for /u/[handle] — the repo's first generated OG image.
// Refetches through the anon-granted RPC via plain REST (OG requests carry no
// cookies, and the edge-friendly fetch avoids pulling the SSR client in here).
// Colors mirror the brand light theme (app/layout.tsx viewport themeColor).

export const alt = "Pebbles public profile"
export const size = { width: 1200, height: 630 }
export const contentType = "image/png"

type PublicProfileCard = {
  display_name: string
  handle: string
  ripple_level: number
  pebbles_count: number
  days_practiced: number
}

// Every failure degrades to the neutral brand card — a crawler must never get
// a 500 where a card was promised.
async function fetchCard(handle: string): Promise<PublicProfileCard | null> {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY
  if (!url || !key) {
    console.warn("[public-profile:og] Supabase env missing — serving neutral card")
    return null
  }
  try {
    const res = await fetch(`${url}/rest/v1/rpc/get_public_profile`, {
      method: "POST",
      headers: {
        apikey: key,
        Authorization: `Bearer ${key}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ p_handle: handle }),
      cache: "no-store",
    })
    if (!res.ok) {
      console.warn(`[public-profile:og] RPC responded ${res.status}`)
      return null
    }
    return ((await res.json()) as PublicProfileCard | null) ?? null
  } catch (err) {
    console.warn(
      "[public-profile:og] RPC unreachable:",
      err instanceof Error ? err.message : err,
    )
    return null
  }
}

const BG = "#F8F0F0"
const FG = "#2B1F21"
const MUTED = "#8A7A7C"

export default async function Image({
  params,
}: {
  params: Promise<{ handle: string }>
}) {
  const { handle } = await params
  let decoded = handle
  try {
    decoded = decodeURIComponent(handle)
  } catch {
    // Malformed escape — the lookup simply misses and we serve the neutral card.
  }
  const profile = await fetchCard(decoded)

  // Private/unknown handles get the neutral brand card — no existence signal.
  const name = profile?.display_name ?? "Pebbles"
  const sub = profile ? `@${profile.handle}` : "pbbls.app"
  const rippleLevel = profile?.ripple_level ?? 0

  // Concentric ripple rings: ring i reads active when i < level.
  const rings = Array.from({ length: 6 }, (_, i) => ({
    r: 40 + i * 26,
    active: i < rippleLevel,
  }))

  return new ImageResponse(
    (
      <div
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          backgroundColor: BG,
          color: FG,
          padding: "80px 96px",
          fontFamily: "sans-serif",
        }}
      >
        <div style={{ display: "flex", flexDirection: "column", maxWidth: 700 }}>
          {/* No fontWeight anywhere: next/og ships only Geist Regular, so a
              bold declaration would silently render at 400 — size and color
              carry the hierarchy instead. */}
          <div style={{ display: "flex", fontSize: 84, lineHeight: 1.1 }}>{name}</div>
          <div style={{ display: "flex", fontSize: 40, color: MUTED, marginTop: 16 }}>
            {sub}
          </div>
          {profile ? (
            <div style={{ display: "flex", fontSize: 30, color: MUTED, marginTop: 48 }}>
              {`${profile.pebbles_count} pebbles · ${profile.days_practiced} days practiced`}
            </div>
          ) : null}
          <div style={{ display: "flex", fontSize: 28, color: MUTED, marginTop: 12 }}>
            pbbls.app
          </div>
        </div>
        {/* The level digit is a positioned div, not an SVG <text> node: satori
            renders text through its own layout engine, so the digit is laid out
            over the rings the same way RippleBadge does it on the web page. */}
        <div
          style={{
            display: "flex",
            position: "relative",
            width: 380,
            height: 380,
            alignItems: "center",
            justifyContent: "center",
          }}
        >
          <svg width="380" height="380" viewBox="0 0 380 380">
            {rings.map((ring, i) => (
              <circle
                key={i}
                cx="190"
                cy="190"
                r={ring.r}
                fill="none"
                stroke={ring.active ? FG : MUTED}
                strokeOpacity={ring.active ? 0.9 : 0.25}
                strokeWidth={ring.active ? 6 : 4}
              />
            ))}
          </svg>
          <div
            style={{
              display: "flex",
              position: "absolute",
              fontSize: 44,
              color: FG,
            }}
          >
            {String(rippleLevel)}
          </div>
        </div>
      </div>
    ),
    { ...size },
  )
}
