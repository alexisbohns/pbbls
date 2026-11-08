import { Game, PebbleDottedGlyph, Radar, Stack, Sunshine } from "@pbbls/icons"
import type { PbblsIconComponent } from "@/lib/icons/pbbls-icon"

export type DirectoryPage = {
  path: string
  label: string
  /** One line under the page's title in the side area (from 1024 px). */
  summary: string
  icon: PbblsIconComponent
  /** Canvas pages draw screens and get the Screens and Zoom controls. */
  canvas: boolean
}

/** The directory's pages, in rail order. */
export const DIRECTORY_PAGES: readonly DirectoryPage[] = [
  { path: "", label: "Overview", summary: "Every part of the Pebbles web design system in one place, and what the source does outside it.", icon: Radar, canvas: false },
  { path: "/colors", label: "Colors & type", summary: "Live colors, type and shapes, read from the page under the theme you pick.", icon: Sunshine, canvas: false },
  { path: "/icons", label: "Icons", summary: "Every glyph of @pbbls/icons, base and twin alike, at real size. Hover a tile for where it has migrated: web, iOS, Android.", icon: PebbleDottedGlyph, canvas: false },
  { path: "/components", label: "Components", summary: "The components, drawn on real-size mobile and desktop screens scaled to the page.", icon: Stack, canvas: true },
  { path: "/views", label: "Views", summary: "Whole views, drawn on real-size mobile and desktop screens scaled to the page.", icon: Game, canvas: true },
]

export function currentPage(pathname: string) {
  return DIRECTORY_PAGES.find((p) => pathname === `/sandbox/design${p.path}`)
}
