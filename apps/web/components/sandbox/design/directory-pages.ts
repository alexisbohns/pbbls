import { Game, PebbleDottedGlyph, Radar, Stack, Sunshine } from "@pbbls/icons"
import type { PbblsIconComponent } from "@/lib/icons/pbbls-icon"

/** The directory's pages, in rail order; `canvas` pages draw screens and get the Screens and Zoom controls. */
export const DIRECTORY_PAGES: readonly { path: string; label: string; icon: PbblsIconComponent; canvas: boolean }[] = [
  { path: "", label: "Overview", icon: Radar, canvas: false },
  { path: "/colors", label: "Colors & type", icon: Sunshine, canvas: false },
  { path: "/icons", label: "Icons", icon: PebbleDottedGlyph, canvas: false },
  { path: "/components", label: "Components", icon: Stack, canvas: true },
  { path: "/views", label: "Views", icon: Game, canvas: true },
]

export function currentPage(pathname: string) {
  return DIRECTORY_PAGES.find((p) => pathname === `/sandbox/design${p.path}`)
}
