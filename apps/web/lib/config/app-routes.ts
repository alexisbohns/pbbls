import { NAV_ITEMS } from "./navigation"

/**
 * How the app shell treats a route (#1384): one table that `MainContent`,
 * `AppNavigation` and the sandbox read, instead of a flag per route in each.
 */
export type RouteShell = {
  /** A tab root: the navigation shows, and the page leaves it room. */
  tab: boolean
  /** On the M3 ground (`.m3` on <body>): the five tabs, Settings and the guides. */
  m3: boolean
  /** The Path: its own scroll, on the Spine paper (`path-route` on <body>). */
  path: boolean
  /** Edge to edge, no page padding: the landing, onboarding, sign-in, the Path, the blog. */
  fullScreen: boolean
  /** Record and Carve: padded on every side, no navigation. */
  immersive: boolean
  /** The onboarding and pending-invite redirects run (not on public or pre-sign-in pages). */
  gated: boolean
}

/** The tab roots, from the navigation's own items. */
export const TAB_ROOTS: readonly string[] = NAV_ITEMS.map((item) => item.href)

/** Beyond the tabs, the routes on the M3 ground. Deeper pages keep the colour world for now. */
const M3_PREFIXES = ["/settings", "/guides"] as const

const FULL_SCREEN_EXACT = ["/", "/login", "/register"] as const
const FULL_SCREEN_PREFIXES = ["/onboarding", "/path", "/blog"] as const
const IMMERSIVE_PREFIXES = ["/record", "/carve"] as const
/** Public reading and the way in: never bounced by onboarding or an invite. */
const UNGATED_EXACT = ["/", "/login", "/register"] as const
const UNGATED_PREFIXES = ["/docs", "/u", "/blog"] as const

/** The route itself or below it, by whole segment ("/pathological" is not "/path"). */
export function underPrefix(pathname: string, prefix: string): boolean {
  return pathname === prefix || pathname.startsWith(prefix + "/")
}

export function routeShell(pathname: string): RouteShell {
  const tab = TAB_ROOTS.includes(pathname)
  const path = underPrefix(pathname, "/path")
  return {
    tab,
    m3: tab || M3_PREFIXES.some((p) => underPrefix(pathname, p)),
    path,
    fullScreen: FULL_SCREEN_EXACT.some((p) => pathname === p) || FULL_SCREEN_PREFIXES.some((p) => underPrefix(pathname, p)),
    immersive: IMMERSIVE_PREFIXES.some((p) => underPrefix(pathname, p)),
    gated: !UNGATED_EXACT.some((p) => pathname === p) && !UNGATED_PREFIXES.some((p) => underPrefix(pathname, p)),
  }
}
