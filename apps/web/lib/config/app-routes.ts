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
  /**
   * A detail (#1384, two-area spec D2): its page lives in the `@detail` slot,
   * beside its tab from 1024 px and over it below.
   */
  detail: boolean
  /** The tab a detail belongs to (its left area, the navigation's current item); a tab root's own href. */
  parentTab: string | null
  /** A detail that opens over whichever tab is showing (the pebble); `parentTab` is its hard load's tab. */
  overAnyTab: boolean
}

/** Two areas from here (two-area spec D1): the tab page on the left, the Path's line or a detail on the right. */
export const PANES_QUERY = "(width >= 1024px)"

/** The tab roots, from the navigation's own items. */
export const TAB_ROOTS: readonly string[] = NAV_ITEMS.map((item) => item.href)

/**
 * The details that open in the `@detail` slot, by prefix, with the tab whose
 * page stays beside them (two-area spec D2). A route joins this table in the
 * part that moves its page into the slot.
 */
export const DETAILS: readonly {
  prefix: string
  tab: string
  /** Only the routes below the prefix (a tab root's own details). */
  below?: boolean
  /** Only one segment below the prefix (the pebble, not its edit flows). */
  one?: boolean
  /** Opens over whichever tab is showing (the pebble); `tab` is the hard load's. */
  overAnyTab?: boolean
}[] = [
  { prefix: "/guides", tab: "/home" },
  { prefix: "/wallet", tab: "/path" },
  { prefix: "/souls", tab: "/souls", below: true },
  { prefix: "/collections", tab: "/collections", below: true },
  { prefix: "/connections", tab: "/souls" },
  { prefix: "/settings", tab: "/profile" },
  { prefix: "/glyphs", tab: "/profile" },
  { prefix: "/achievements", tab: "/profile" },
  { prefix: "/lab", tab: "/profile" },
  { prefix: "/pebble", tab: "/path", one: true, overAnyTab: true },
]

/** Beyond the tabs and the details, the routes on the M3 ground. Deeper pages keep the colour world for now. */
// The docs site (/docs) wears the M3 roles and Inclusive Sans too (#1483).
const M3_PREFIXES: readonly string[] = ["/docs"]

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
  const detailOf = DETAILS.find((d) =>
    d.one
      ? pathname.startsWith(d.prefix + "/") && !pathname.slice(d.prefix.length + 1).includes("/")
      : d.below
        ? pathname.startsWith(d.prefix + "/")
        : underPrefix(pathname, d.prefix),
  )
  return {
    tab,
    detail: detailOf !== undefined,
    parentTab: tab ? pathname : (detailOf?.tab ?? null),
    overAnyTab: detailOf?.overAnyTab ?? false,
    m3: tab || detailOf !== undefined || M3_PREFIXES.some((p) => underPrefix(pathname, p)),
    path,
    fullScreen: FULL_SCREEN_EXACT.some((p) => pathname === p) || FULL_SCREEN_PREFIXES.some((p) => underPrefix(pathname, p)),
    immersive: IMMERSIVE_PREFIXES.some((p) => underPrefix(pathname, p)),
    gated: !UNGATED_EXACT.some((p) => pathname === p) && !UNGATED_PREFIXES.some((p) => underPrefix(pathname, p)),
  }
}
