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

/**
 * How the Components canvas groups specimens: one pair of screens (mobile,
 * desktop) per group, so related parts sit side by side and drift shows.
 */
export const SPECIMEN_GROUPS = [
  { id: "actions", title: "Actions & badges", section: "primitives" },
  { id: "inputs", title: "Inputs & lists", section: "primitives" },
  { id: "containers", title: "Containers", section: "primitives" },
  // Overlays open over the whole screen, so their frames keep the device height.
  { id: "overlays", title: "Overlays", section: "primitives", fullHeight: true },
  { id: "pebbles", title: "Pebbles & stones", section: "components" },
  { id: "path", title: "Path & achievements", section: "components" },
  { id: "people", title: "Souls & collections", section: "components" },
  { id: "profile", title: "Profile & settings", section: "components" },
  { id: "layout", title: "Layout & icons", section: "components" },
] as const satisfies readonly {
  id: string
  title: string
  section: Exclude<SpecimenSection, "views">
  fullHeight?: boolean
}[]

export type SpecimenGroupId = (typeof SPECIMEN_GROUPS)[number]["id"]

export interface SpecimenEntry {
  readonly id: string
  readonly name: string
  /** Relative to apps/web. */
  readonly source: string
  readonly section: SpecimenSection
  readonly m3: M3Status
  /** Primitives and components: their group on the Components canvas. Views have none. */
  readonly group?: SpecimenGroupId
  /** A known limitation of rendering it from fixtures, shown beside it. */
  readonly note?: string
}

export const SPECIMENS = [
  // primitives: every file in components/ui (the test enforces it)
  { id: "button", name: "Button", source: "components/ui/button.tsx", section: "primitives", m3: "bridged", group: "actions" },
  { id: "badge", name: "Badge", source: "components/ui/badge.tsx", section: "primitives", m3: "bridged", group: "actions" },
  { id: "card", name: "Card", source: "components/ui/card.tsx", section: "primitives", m3: "bridged", group: "containers" },
  { id: "checkbox", name: "Checkbox", source: "components/ui/checkbox.tsx", section: "primitives", m3: "bridged", group: "inputs" },
  { id: "collapsible", name: "Collapsible", source: "components/ui/collapsible.tsx", section: "primitives", m3: "bridged", group: "containers" },
  { id: "input", name: "Input", source: "components/ui/input.tsx", section: "primitives", m3: "bridged", group: "inputs" },
  { id: "calendar", name: "Calendar", source: "components/ui/calendar.tsx", section: "primitives", m3: "bridged", group: "inputs" },
  { id: "dialog", name: "Dialog", source: "components/ui/dialog.tsx", section: "primitives", m3: "bridged", group: "overlays" },
  { id: "alert-dialog", name: "Alert dialog", source: "components/ui/alert-dialog.tsx", section: "primitives", m3: "bridged", group: "overlays" },
  { id: "sheet", name: "Sheet", source: "components/ui/sheet.tsx", section: "primitives", m3: "bridged", group: "overlays" },
  { id: "popover", name: "Popover", source: "components/ui/popover.tsx", section: "primitives", m3: "bridged", group: "overlays" },
  { id: "select", name: "Select", source: "components/ui/select.tsx", section: "primitives", m3: "bridged", group: "overlays" },
  { id: "dropdown-menu", name: "Dropdown menu", source: "components/ui/dropdown-menu.tsx", section: "primitives", m3: "bridged", group: "overlays" },
  { id: "toast", name: "Toast", source: "components/ui/sonner.tsx", section: "primitives", m3: "bridged", group: "overlays" },
  { id: "confirm-dialog", name: "ConfirmDialog", source: "components/ui/ConfirmDialog.tsx", section: "primitives", m3: "bridged", group: "overlays" },
  { id: "emotion-badge", name: "EmotionBadge", source: "components/ui/EmotionBadge.tsx", section: "primitives", m3: "bridged", group: "actions" },
  { id: "picker-sheet", name: "PickerSheet", source: "components/ui/PickerSheet.tsx", section: "primitives", m3: "bridged", group: "overlays" },
  { id: "searchable-list", name: "SearchableList", source: "components/ui/SearchableList.tsx", section: "primitives", m3: "bridged", group: "inputs" },
  { id: "section-label", name: "SectionLabel", source: "components/ui/SectionLabel.tsx", section: "primitives", m3: "bridged", group: "actions" },
  { id: "selectable-item", name: "SelectableItem", source: "components/ui/SelectableItem.tsx", section: "primitives", m3: "bridged", group: "inputs" },
  { id: "tag-list", name: "TagList", source: "components/ui/TagList.tsx", section: "primitives", m3: "bridged", group: "actions" },
  // components: curated feature components that render from props or the fixture store
  { id: "pebble-card", name: "PebbleCard", source: "components/path/PebbleCard.tsx", section: "components", m3: "bridged", group: "pebbles" },
  { id: "path-empty-state", name: "PathEmptyState", source: "components/path/PathEmptyState.tsx", section: "components", m3: "bridged", group: "path" },
  { id: "gamification-block", name: "GamificationBlock", source: "components/path/GamificationBlock.tsx", section: "components", m3: "bridged", group: "path" },
  { id: "pebble-visual", name: "PebbleVisual", source: "components/pebble/PebbleVisual.tsx", section: "components", m3: "bridged", group: "pebbles" },
  { id: "lit-stone", name: "LitStone", source: "components/pebble/stone/LitStone.tsx", section: "components", m3: "bridged", group: "pebbles" },
  { id: "stone-seal", name: "StoneSeal", source: "components/pebble/stone/StoneSeal.tsx", section: "components", m3: "bridged", group: "pebbles" },
  { id: "spine-week", name: "SpineWeek", source: "components/path/spine/SpineWeek.tsx", section: "components", m3: "bridged", group: "path" },
  { id: "achievement-cards", name: "Achievements page", source: "components/achievements/page/AchievementsPageContent.tsx", section: "components", m3: "bridged", group: "path" },
  { id: "pebble-indicators", name: "IntensityDots · PositivenessIndicator", source: "components/pebble/PebbleIndicators.tsx", section: "components", m3: "bridged", group: "pebbles" },
  { id: "soul-card", name: "SoulCard", source: "components/souls/SoulCard.tsx", section: "components", m3: "bridged", group: "people" },
  { id: "souls-empty-state", name: "SoulsEmptyState", source: "components/souls/SoulsEmptyState.tsx", section: "components", m3: "bridged", group: "people" },
  { id: "collection-card", name: "CollectionCard", source: "components/collections/CollectionCard.tsx", section: "components", m3: "bridged", group: "people" },
  { id: "mode-badge", name: "ModeBadge", source: "components/collections/ModeBadge.tsx", section: "components", m3: "bridged", group: "people" },
  { id: "profile-banner", name: "ProfileBanner", source: "components/profile/ProfileBanner.tsx", section: "components", m3: "bridged", group: "profile" },
  { id: "stats-card", name: "StatsCard", source: "components/profile/StatsCard.tsx", section: "components", m3: "bridged", group: "profile" },
  { id: "data-tile", name: "DataTile", source: "components/profile/DataTile.tsx", section: "components", m3: "bridged", group: "profile" },
  { id: "shortcut-tile", name: "ShortcutTile", source: "components/profile/ShortcutTile.tsx", section: "components", m3: "bridged", group: "profile" },
  { id: "section-card", name: "SectionCard", source: "components/profile/SectionCard.tsx", section: "components", m3: "bridged", group: "profile" },
  { id: "lab-card", name: "LabCard", source: "components/profile/LabCard.tsx", section: "components", m3: "bridged", group: "profile" },
  { id: "settings-row", name: "SettingsGroup · SettingsRow", source: "components/settings/SettingsRow.tsx", section: "components", m3: "bridged", group: "profile" },
  { id: "page-header", name: "PageHeader", source: "components/layout/PageHeader.tsx", section: "components", m3: "bridged", group: "layout" },
  { id: "empty-state", name: "EmptyState", source: "components/layout/EmptyState.tsx", section: "components", m3: "bridged", group: "layout" },
  { id: "pbbls-icon-sizes", name: "Pebbles icon sizes", source: "lib/icons/pbbls-icon.ts", section: "components", m3: "bridged", group: "layout" },
  // views: composed from their feature components, fed by the fixture store
  { id: "view-home", name: "Home", source: "app/(home)/home/page.tsx", section: "views", m3: "tuned",
    note: "Fixtures stand in for the month, the wraps and the guides (the mock's October 2026). Camera and Quick moment cannot save here: there is no session.",
  },
  { id: "view-you", name: "You", source: "app/(you)/profile/page.tsx", section: "views", m3: "tuned",
    note: "Fixtures stand in for the served counts, the achievements shelf and the Lab. Settings and every row link to the real routes.",
  },
  { id: "view-path", name: "Path", source: "app/(path)/path/page.tsx", section: "views", m3: "tuned",
    note: "Tapping a pebble opens the peek; its emotion and domain tiles share the Pebble detail limitation, and its Edit button navigates to the real (empty) edit route rather than through a link.",
  },
  { id: "view-people", name: "People", source: "app/(people)/souls/page.tsx", section: "views", m3: "tuned",
    note: "The tab root on the M3 ground, with the app navigation. Fed by the fixture store.",
  },
  { id: "view-collections", name: "Collections", source: "app/(collections)/collections/page.tsx", section: "views", m3: "tuned",
    note: "The tab root on the M3 ground, with the app navigation. Fed by the fixture store.",
  },
  { id: "view-guide-open", name: "Guide open", source: "app/@detail/(pane)/guides/[slug]/page.tsx", section: "views", m3: "tuned",
    note: "A detail beside its tab: on desktop it covers the right area; on mobile, it is the overlay. Close does nothing here.",
  },
  { id: "view-soul-open", name: "Soul open", source: "app/@detail/(pane)/souls/[id]/page.tsx", section: "views", m3: "tuned",
    note: "A soul beside People, from the fixture store. Writes do nothing.",
  },
  { id: "view-collection-open", name: "Collection open", source: "app/@detail/(pane)/collections/[id]/page.tsx", section: "views", m3: "tuned",
    note: "A collection beside Collections, from the fixture store. Writes do nothing.",
  },
  { id: "view-glyphs-open", name: "Glyphs open", source: "app/@detail/(pane)/glyphs/page.tsx", section: "views", m3: "tuned",
    note: "Glyphs beside You, from the fixture store.",
  },
  { id: "view-achievements-open", name: "Achievements open", source: "app/@detail/(pane)/achievements/page.tsx", section: "views", m3: "tuned",
    note: "Achievements beside You. They read the network, so they show their loading or empty state here.",
  },
  { id: "view-achievement-sheet-open", name: "Achievement sheet open", source: "app/@detail/(pane)/achievements/[sheet]/page.tsx", section: "views", m3: "tuned",
    note: "An achievements sheet (Explorer) open as a nested detail, from the design fixture: /achievements/explorer.",
  },
  { id: "view-achievement-drill", name: "Achievement drill-down", source: "app/@detail/(pane)/achievements/[sheet]/page.tsx", section: "views", m3: "tuned",
    note: "Two levels down: Domains, opened from Explorer's row (/achievements → /achievements/explorer → /achievements/domains), from the design fixture.",
  },
  { id: "view-lab-open", name: "Lab open", source: "app/@detail/(pane)/lab/page.tsx", section: "views", m3: "tuned",
    note: "The Lab beside You. It reads the network, so it shows its loading or error state here.",
  },
  { id: "view-wallet-open", name: "Wallet open", source: "app/@detail/(pane)/wallet/page.tsx", section: "views", m3: "tuned",
    note: "The wallet over the Path's line. It reads the network, so it shows its empty state here.",
  },
  { id: "view-pebble", name: "Pebble detail", source: "app/@detail/(pane)/pebble/[id]/page.tsx", section: "views", m3: "tuned",
    note: "The emotion and domain tiles read reference views from Supabase that cannot be seeded, so here they show their generic fallback.",
  },
  { id: "view-profile", name: "Profile (before You)", source: "components/sandbox/design/views/ProfileView.tsx", section: "views", m3: "bridged",
    note: "The profile before You (#1384): its components stay for the public profile and this sandbox. The achievements shelf reads through provider methods, not the store, so it shows its empty state.",
  },
  { id: "view-settings", name: "Settings", source: "app/@detail/(pane)/settings/page.tsx", section: "views", m3: "tuned",
    note: "Appearance is left out: its controls would change your real theme, locale and color world.",
  },
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
