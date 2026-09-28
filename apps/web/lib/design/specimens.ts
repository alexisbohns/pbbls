/**
 * Everything /sandbox/design renders, and how far its M3 migration has come
 * (#986).
 * - `bridged`: renders M3 only through the shadcn-token bridge in globals.css.
 * - `tuned`: reads M3 roles explicitly (`m3:` classes) and has been reviewed
 *   on the page.
 * Flip an entry to `tuned` in the same PR that tunes the component.
 */

export type M3Status = "bridged" | "tuned"
export type SpecimenSection = "primitives" | "components" | "views"

export interface SpecimenEntry {
  readonly id: string
  readonly name: string
  /** Relative to apps/web. */
  readonly source: string
  readonly section: SpecimenSection
  readonly m3: M3Status
}

export const SPECIMENS = [
  // primitives: every file in components/ui (the test enforces it)
  { id: "button", name: "Button", source: "components/ui/button.tsx", section: "primitives", m3: "bridged" },
  { id: "badge", name: "Badge", source: "components/ui/badge.tsx", section: "primitives", m3: "bridged" },
  { id: "card", name: "Card", source: "components/ui/card.tsx", section: "primitives", m3: "bridged" },
  { id: "checkbox", name: "Checkbox", source: "components/ui/checkbox.tsx", section: "primitives", m3: "bridged" },
  { id: "input", name: "Input", source: "components/ui/input.tsx", section: "primitives", m3: "bridged" },
  { id: "calendar", name: "Calendar", source: "components/ui/calendar.tsx", section: "primitives", m3: "bridged" },
  { id: "dialog", name: "Dialog", source: "components/ui/dialog.tsx", section: "primitives", m3: "bridged" },
  { id: "alert-dialog", name: "Alert dialog", source: "components/ui/alert-dialog.tsx", section: "primitives", m3: "bridged" },
  { id: "sheet", name: "Sheet", source: "components/ui/sheet.tsx", section: "primitives", m3: "bridged" },
  { id: "popover", name: "Popover", source: "components/ui/popover.tsx", section: "primitives", m3: "bridged" },
  { id: "dropdown-menu", name: "Dropdown menu", source: "components/ui/dropdown-menu.tsx", section: "primitives", m3: "bridged" },
  { id: "toast", name: "Toast", source: "components/ui/sonner.tsx", section: "primitives", m3: "bridged" },
  { id: "confirm-dialog", name: "ConfirmDialog", source: "components/ui/ConfirmDialog.tsx", section: "primitives", m3: "bridged" },
  { id: "emotion-badge", name: "EmotionBadge", source: "components/ui/EmotionBadge.tsx", section: "primitives", m3: "bridged" },
  { id: "picker-sheet", name: "PickerSheet", source: "components/ui/PickerSheet.tsx", section: "primitives", m3: "bridged" },
  { id: "searchable-list", name: "SearchableList", source: "components/ui/SearchableList.tsx", section: "primitives", m3: "bridged" },
  { id: "section-label", name: "SectionLabel", source: "components/ui/SectionLabel.tsx", section: "primitives", m3: "bridged" },
  { id: "selectable-item", name: "SelectableItem", source: "components/ui/SelectableItem.tsx", section: "primitives", m3: "bridged" },
  { id: "tag-list", name: "TagList", source: "components/ui/TagList.tsx", section: "primitives", m3: "bridged" },
  // components: curated feature components that render from props or the fixture store
  { id: "pebble-card", name: "PebbleCard", source: "components/path/PebbleCard.tsx", section: "components", m3: "bridged" },
  { id: "path-pebble-row", name: "PathPebbleRow", source: "components/path/PathPebbleRow.tsx", section: "components", m3: "bridged" },
  { id: "path-empty-state", name: "PathEmptyState", source: "components/path/PathEmptyState.tsx", section: "components", m3: "bridged" },
  { id: "gamification-block", name: "GamificationBlock", source: "components/path/GamificationBlock.tsx", section: "components", m3: "bridged" },
  { id: "pebble-visual", name: "PebbleVisual", source: "components/pebble/PebbleVisual.tsx", section: "components", m3: "bridged" },
  { id: "pebble-indicators", name: "IntensityDots · PositivenessIndicator", source: "components/pebble/PebbleIndicators.tsx", section: "components", m3: "bridged" },
  { id: "soul-card", name: "SoulCard", source: "components/souls/SoulCard.tsx", section: "components", m3: "bridged" },
  { id: "souls-empty-state", name: "SoulsEmptyState", source: "components/souls/SoulsEmptyState.tsx", section: "components", m3: "bridged" },
  { id: "collection-card", name: "CollectionCard", source: "components/collections/CollectionCard.tsx", section: "components", m3: "bridged" },
  { id: "mode-badge", name: "ModeBadge", source: "components/collections/ModeBadge.tsx", section: "components", m3: "bridged" },
  { id: "profile-banner", name: "ProfileBanner", source: "components/profile/ProfileBanner.tsx", section: "components", m3: "bridged" },
  { id: "stats-card", name: "StatsCard", source: "components/profile/StatsCard.tsx", section: "components", m3: "bridged" },
  { id: "data-tile", name: "DataTile", source: "components/profile/DataTile.tsx", section: "components", m3: "bridged" },
  { id: "shortcut-tile", name: "ShortcutTile", source: "components/profile/ShortcutTile.tsx", section: "components", m3: "bridged" },
  { id: "section-card", name: "SectionCard", source: "components/profile/SectionCard.tsx", section: "components", m3: "bridged" },
  { id: "lab-card", name: "LabCard", source: "components/profile/LabCard.tsx", section: "components", m3: "bridged" },
  { id: "settings-row", name: "SettingsGroup · SettingsRow", source: "components/settings/SettingsRow.tsx", section: "components", m3: "bridged" },
  { id: "page-header", name: "PageHeader", source: "components/layout/PageHeader.tsx", section: "components", m3: "bridged" },
  { id: "empty-state", name: "EmptyState", source: "components/layout/EmptyState.tsx", section: "components", m3: "bridged" },
  // views: composed from their feature components, fed by the fixture store
  { id: "view-path", name: "Path", source: "app/path/page.tsx", section: "views", m3: "bridged" },
  { id: "view-pebble", name: "Pebble detail", source: "app/pebble/[id]/page.tsx", section: "views", m3: "bridged" },
  { id: "view-profile", name: "Profile", source: "app/profile/page.tsx", section: "views", m3: "bridged" },
  { id: "view-settings", name: "Settings", source: "app/settings/page.tsx", section: "views", m3: "bridged" },
] as const satisfies readonly SpecimenEntry[]

export type SpecimenId = (typeof SPECIMENS)[number]["id"]

export function getSpecimen(id: SpecimenId): SpecimenEntry {
  const entry = SPECIMENS.find((s) => s.id === id)
  if (!entry) throw new Error(`[specimens] unknown specimen "${id}"`)
  return entry
}

export function tuningProgress(entries: readonly SpecimenEntry[] = SPECIMENS): { tuned: number; total: number } {
  return { tuned: entries.filter((e) => e.m3 === "tuned").length, total: entries.length }
}
