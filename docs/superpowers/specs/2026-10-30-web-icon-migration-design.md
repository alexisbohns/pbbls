# Web icon migration — design

**Date:** 2026-10-30
**Issue:** #1313 (follow-up of #1311, the icons package)
**Milestone:** M66 · Brand icons
**Package spec:** `docs/superpowers/specs/2026-10-28-icon-package-design.md` (D1, D4, D5, D10)
**Sibling:** `docs/superpowers/specs/2026-10-30-ios-icon-migration-design.md` (D1–D10), whose mapping this spec mirrors site for site
**Inventory:** 98 files under `apps/web` (85) and `apps/admin` (13) outside `components/ui/` import `lucide-react`: 204 file-and-symbol sites, 85 distinct icon symbols, plus the `LucideIcon` type in 8 files. Counted on `docs/1313-web-icon-migration-spec` at `ffbc1aff` (today's `origin/main`) on 2026-10-30.

## Summary

Web and admin draw 85 lucide symbols outside the shadcn primitives. This spec maps every site to a Pebbles icon, to the chrome wave brief, or to a closed list that stays lucide on purpose; fixes five named sizes in rem; settles the accessibility contract of the generated components; rewrites the web form of the migration lint, which as written would fail on correct code; and splits the work into one stack of six parts.

## Goal

After the stack merges:

- no file under `apps/web` or `apps/admin` outside `components/ui/` imports from `lucide-react` a name that `replaces.web` lists, in any spelling (`Trash2`, `Trash2Icon`, `LucideTrash2`, `Trash2 as Bin`), and `npm run lint:replaces --workspace=@pbbls/icons` proves it with `replaces: 27 rows, 77 names, 0 hits`;
- every icon that carries meaning in the product (a pebble, a moment, a soul, a collection, karma, a glyph, a date, an achievement, a photo, create, edit, delete) is a Pebbles drawing, the same one iOS draws at the same place;
- the 60 files that still import lucide do so only for the D1 chrome, the D2 wave figures, admin's own tools (D9) and three sandbox placeholders, and for the `LucideIcon` type that types those.

## Non-goals

- Android (#1315). It reuses the iOS tables.
- `components/ui/` in either app: shadcn primitives keep `iconLibrary: lucide` (package spec, non-goals). The three custom primitives living there (`PickerSheet` `X`, `SearchableList` `Search`, `SelectableItem` `Check`) are chrome by D1, so the lint's directory-wide skip costs nothing.
- PWA and app icons: `apps/web/app/favicon.ico`, `public/manifest.webmanifest` and `public/icons/*.png` are not manifest rows. Out of scope.
- Redrawing anything. Where the library has no drawing that means the thing, lucide stays and the concept goes to the wave brief (D2).
- Deleting dead code (D12).

## Decisions

**D1. Chrome stays lucide; meaning becomes Pebbles.** The iOS closed list, in lucide names: `ChevronLeft`, `ChevronRight`, `ChevronDown`, `X` (close, and the × that drops a chip), `MoreHorizontal`, `Share2`, `Copy`, `Undo2`, `ArrowRight` (the Lab's "See all"). Five web additions, each platform grammar with no iOS twin because SwiftUI draws it: `ArrowUpRight` / `ArrowDownLeft` (the wallet's out and in), `Check` as a selection mark in a menu (`ColorWorldSwitcher`, `LocaleSwitcher`: shadcn's own `DropdownMenuCheckboxItem` draws the same lucide tick), `Search` (the field glyph) and `Loader2` (nine spinners: nothing in the library spins, iOS uses `ProgressView`). Two brand slots stay as well: admin's `Apple` platform mark, and web's `Link2` on a linked sign-in provider, where iOS draws the provider's own logo (iOS D7). The list is closed and lives here and in the decisions log (Part 6). *Rejected:* migrating the selection tick. A Pebbles tick on two menus beside shadcn's lucide tick on every other menu is two vocabularies in one component family.

**D2. No drawing, no replacement; the gap joins the chrome wave.** 29 web sites mean something the library does not draw. They keep lucide and are appended to `packages/icons/waves/2026-11-chrome/brief.md` under a `## Web (#1313)` heading in Part 1. Fifteen sites reuse eleven figures the brief already has: *again* (`RotateCcw` new invite link, `RefreshCw` retry), *upvote* (`ArrowUpCircle`), *something went wrong* (`AlertCircle` upload failed), *document* (`FileText` docs index, `Scale` legal), *privacy* (`HeartHandshake` consent), *secret* and *locked* (`Lock`, two sites), *private* (`UsersRound`, D3), *public* (`Globe` in the visibility menu), *invite* (`UserRoundPlus` connections shortcut), *life domain* (`Compass` domain sheet, `Tag` ×2 domain tiles). Twelve sites need five new figures, one line each in the brief format: *block* (`Ban`), *language* (`Globe` in Appearance and the locale switcher; must read apart from *public*), *appearance* (`Sun`, `Moon`, `Monitor`: light, dark, system, a trio drawn together or not at all), *colour world* (`Palette`), *bounce*, regularity over 28 days (`CirclePile`, `Waves`). Two sites are not briefed: haptics (`Vibrate`, `VibrateOff`, only in a dead file, D12); nor are admin's own tools (D9).

**D3. The visibility trio stays one vocabulary: its `Users` becomes lucide `UsersRound`.** `record/VisibilityMenu.tsx` draws secret `Lock`, private `Users`, public `Globe`, three wave figures that move together (iOS D2). `Users` is also the people icon at four other sites, and the lint matches by name, so the trio cannot keep it while `people` lists it. The private grade switches to lucide `UsersRound` (the same two figures, rounded), a name no row lists, and the brief names `UsersRound` for *private*. This is iOS's `person.2.fill` / `person.2` split in lucide spelling. *Rejected:* dropping `Users` from `replaces.web`, which unpolices the commonest people icon on web; giving *private* `people` now, which puts two vocabularies in one picker.

**D4. Per-site picks follow the iOS tables.** The manifest holds one primary row per name (iOS D8c); a name whose sites mean different things takes the iOS choice at each site. `ImagePlus` is `camera` on the edit photo slot and `camera-focus` on the Record photo step's hero (iOS `RecordPhotoStep`). `Sparkles` is `karma-dot` on the karma counter, `sparkle` beside "date from photo" and `sparkles` on the landing's "enrich" and admin's Features. `Trophy` is `pebble-glow-lg` on the celebration and `pebble-glow-md` everywhere else. `CircleDot` is `pebble-round.lit-1`: the Lab's in-progress entry, the lit stone. A favourited glyph draws `heart.active`, the lit seal, never `fill-current` on `heart` (its strokes are open; a fill would flood the paths). Unlisted names migrate by site where iOS did: a *done* `Check` (Carve save, the souls sheet's done button) is `checkmark-dot`; `X` that removes a pebble from a collection is `remove` (iOS "remove photo"); the time stepper's `Minus` is `remove`, beside its `Plus` as `plus-circle`; `Shapes` is `pebble-dotted-glyph` for `glyph_count` and `pebble-round` for "Edit pebble" (iOS `HeldCardMenu`); `Compass` and `Store` in the achievement family maps take the family default `pebble-glow-md` (iOS `familySymbol` for `domain_first`, `glyph_sales`) while the domain sheet keeps `Compass` (D2). The sandbox (`components/sandbox/design/`) migrates the listed names only (`Gem`, `Sparkles`, `Settings`, `Plus`) and keeps its three placeholders (`Flame`, `Leaf`, `UserRound`): a specimen shows a component, not a meaning.

**D5. Manifest: 21 rows carry 28 web names.** Part 1 edits `replaces.web` as text, a line per row by name (`icons.json` is not round-trip stable; iOS plan, Lessons learned). Two removals: `Check` leaves `checkmark-dot` (the selection mark stays, D1, so the name cannot be listed) and `CirclePlus` moves from `plus-circle-dot` to `plus-circle` (iOS D3: `plus-circle` is the plain plus, `plus-circle-dot` stays unused). Final `replaces.web`, new names in bold: `check-circle` CircleCheckBig · `plus-circle` **Plus**, **CirclePlus** · `remove` **UserRoundMinus** · `camera` ImagePlus · `path` **Route** · `people` Users, **MessagesSquare** · `collection` Layers, **FolderOpen** · `stack` **Gem** · `person` CircleUser · `slider` Settings · `pebble-dotted-glyph` **Fingerprint**, **PenTool** · `draw-dotted` **Pencil** · `pebble-round` Stone, **Shell**, **CircleDashed**, **CircleDot** · `pebble-flat` **Clock** · `karma-dot` **Sparkle** · `sparkles` **Sparkles** · `calendar-date` CalendarDays · `trash` Trash2 · `pebble-glow-md` **Trophy** · `sparkle` **FlaskConical** · `heart` Heart. Every row is one iOS already migrated, so web adopts no drawing iOS did not. None of today's twelve `replaces.web` names is stale: all twelve appear in the code.

**D6. The web lint matches the lucide import, not the file.** Today's web form (`replaces.ts`, `TREES.web`) reads a file only if it imports `lucide-react`, then hits `\bName\b` on every line. After the migration 60 files still import lucide (D1, D2), and in them the pattern hits correct code: the Pebbles `Heart` in `achievements/AchievementBadge.tsx` (keeps `Lock`), `pebble/MomentReadTiles.tsx` and `pebble/PebbleDetailTiles.tsx` (keep `Tag`); the Pebbles `Sparkles` in admin `components/layout/Sidebar.tsx` and the sandbox; the doc comment "Settings "Public profile" section" in `settings/PublicProfileSection.tsx`; `label="Settings"` in the sandbox. It also misses `Trash2Icon`, `LucideTrash2` and a deep `lucide-react/icons/…` import. The web form becomes: in each `.ts`/`.tsx` of the tree, find every `import … from` / `export … from` declaration whose source is `lucide-react` or starts with `lucide-react/`; a deep or dynamic source is a hit by itself (none exists today); for `lucide-react`, each specifier's imported name (left of `as`, `type` dropped), stripped of a leading `Lucide` and a trailing `Icon`, is a hit when a migrated row lists it, reported at the specifier's line. It is not a weaker pattern: a TypeScript identifier reaches a file only through an import, so the import clause is the whole evidence, where a Swift string literal can appear anywhere (which is why iOS greps bare literals and counts comments). *Rejected:* ESLint `no-restricted-imports` with `importNames`, a second copy of the manifest that drifts.

**D7. Five sizes, named, in rem, as Tailwind classes.** `apps/web/lib/icons/pbbls-icon.ts` (non-generated) exports `PBBLS_ICON_SIZE = { inline: "size-4", row: "size-5", toolbar: "size-5.5", tile: "size-7", hero: "size-12" } as const` (16, 20, 22, 28, 48 px at the default root, the iOS `PbblsIconSize` values), `type PbblsIconSize = keyof typeof PBBLS_ICON_SIZE` and `type PbblsIconComponent = ComponentType<PbblsIconProps>`. A call site writes `<PebbleRound className={cn(PBBLS_ICON_SIZE.inline, "text-primary")} />`.
- *Classes, not the `size` prop.* The generated `size` writes `width`/`height` attributes. Web and admin `Button`, web `DropdownMenuItem` and `AlertDialogMedia` carry `[&_svg:not([class*='size-'])]:size-*`, and CSS beats an SVG presentation attribute, so `size={20}` inside a button draws at 16. A `size-*` class both sizes the icon and opts it out of the slot's default. The `size` prop is not used in either app.
- *rem, not px or em.* rem follows the reader's browser font-size setting, as Dynamic Type does on iOS, and scales with the Tailwind spacing around the icon. em would follow the local text and drop below the floor beside `text-xs`. So *inline* does not track its line: it is 16 beside `text-xs` and beside `text-base` alike.
- *Places.* *inline*: on a line of text (pills, counters, captions, labelled buttons, menu items, the Lab timeline), and in icon-only controls of 24–32 px. *row*: the leading icon of a list or settings row, a card header, a tile trigger, the bottom nav, a shortcut tile, icon-only controls of 36 px, and beside display text (`text-xl` and up). *toolbar*: icon-only controls of 40 px and more. *tile*: an icon in a disc or slot of 48 px and more that is not an empty state (the achievement badge, the empty photo slot). *hero*: empty states and placeholders in a 96 px slot, the celebration, the Record photo hero.
- *The floor is 16.* Off-scale sizes move: `size-3` and `size-3.5` (12, 14) to *inline*, `size-[18px]` to *row*, `size-6` in the badge disc to *tile*, `size-8` and `size-10` to *hero*. An icon may sit in a shadcn slot without a class only where the slot gives 16 or more (`Button` default, `lg`, `icon`, `icon-sm`, `icon-lg`; menu items; `AlertDialogMedia`'s 24); under `xs`, `sm` and `icon-xs` (12, 14, 12) it carries `PBBLS_ICON_SIZE.inline`. After the moves no Pebbles icon sits in a px-sized frame. Admin keeps its existing classes (D9).

**D8. Accessibility: the component decides, the call site only labels.** Each generated component sets `aria-hidden="true"` and no role unless it receives `aria-label` or `aria-labelledby`, then `role="img"`; `focusable="false"` always (`packages/icons/react/Home.tsx`). Props spread last, so a passed `aria-hidden` overrides that logic.
- *Decorative is the default:* beside visible text or inside a control that has its own name, pass no aria prop. The migration drops the `aria-hidden` lucide sites carry (lucide hides by default too, so nothing changes for a screen reader).
- *An icon-only control is named on the control,* never on the icon (one name, no double announcement). Every migrating icon-only control already does: the profile settings link, the three rename buttons, Carve clear, connection remove, collection remove, the stepper, the bottom-bar profile link, the favourite toggle, the add-soul row, the sheet's done button, the sandbox's settings link and button specimens.
- *A standalone icon is labelled.* One site: `profile/AchievementShelfBadge.tsx`, whose `<li>` holds only the icon and a `title`; the icon takes `aria-label={title}` and so `role="img"`.
- *State is never the icon alone:* `heart.active` pairs with the toggle's existing `aria-pressed`.
- *Colour only through `text-*`* (`currentColor`). Never `fill-*`, `stroke-*` or `strokeWidth` on a Pebbles icon: the 1.75 stroke is the DNA, the filled state is a twin.
- Icons never take focus; the visible focus ring is the control's.

**D9. Admin: the same rules, a thinner scope.** Admin is a one-maintainer back-office at desktop density whose icons sit in shadcn slots. It migrates exactly what the shared lint forces: `Plus` (two "New" buttons), `Trophy` and `Sparkles` in `components/layout/Sidebar.tsx`, `CircleCheckBig` in `app/(authed)/logs/_components/LogSection.tsx`. That last one moves the status set it belongs to, mirroring web's `LogTimeline`: in progress `CircleDotDashed` → `pebble-round.lit-1`, planned `CircleFadingArrowUp` → `hourglass-end` (not yet), backlog `CirclePile` → `pebble-round`. Its other 26 non-chrome sites (trend arrows, the platform marks, the sidebar's tools, the error block) stay lucide with no wave line: they name tools, not the product. No size map: every admin site is already on the scale or in a slot. D7's floor, D8 and the `fill`/`stroke` rule apply unchanged.

**D10. Types and names.** A map or helper whose every value becomes a Pebbles icon is typed `PbblsIconComponent` (admin: `ComponentType<PbblsIconProps>` inline): `FAMILY_ICONS` in both badge files, `MODE_ICON` in `LogTimeline`, `FEATURE_ICONS` in `LandingPage`, admin `VARIANTS`, and `NavItem.icon` in `lib/config/navigation.ts` (today `typeof Route`). `path/GamificationBlock.tsx` takes the structural `ComponentType<{ className?: string; "aria-hidden"?: boolean }>` that `DataTile`, `ShortcutTile` and `SettingsRow` already use, because the sandbox hands it lucide `Flame`. `LucideIcon` stays only where every value stays lucide: `VisibilityMenu`, admin `PLATFORM_ICONS`, and the dead `CustomizationTile`. A Pebbles name that clashes with a local one is imported as `<Name>Icon`, and only then: `Collection` against the `Collection` type in `record/CollectionSheet.tsx`, `pebble/PebbleDetailTiles.tsx` and `pebble/MomentReadTiles.tsx`. (Package spec D5 expected `Settings` to collide; the package exports no `Settings`.)

**D11. The nav does not light its entry.** `path`, `collection` and `people` have an `active` twin; `plus-circle`, `pebble-dotted-glyph` and `sparkle` do not. `BottomNav` already marks the current entry by colour and `aria-current="page"`. Lighting three of six entries would read as a bug. *Rejected:* swapping twins where they exist (iOS D5 lights all five because all five have one).

**D12. Dead files migrate in place.** Seven files nothing renders: `layout/MobileHeader.tsx`, `layout/Sidebar.tsx`, `path/GamificationBlocks.tsx`, `record/CustomizationTile.tsx`, `record/SoulPicker.tsx` (no importer) and `layout/ResetDataButton.tsx`, `layout/HapticsToggle.tsx` (imported only by the dead `Sidebar`). Five import listed names, so the lint needs them clean: they take the name change and keep their size classes. `CustomizationTile` imports only the type and is not touched. Deleting the seven is a follow-up issue, not this stack (repo rule: no refactor without approval).

**D13. The web runtime needs nothing new.** The components are plain functions without hooks or `"use client"`, so they render in Server and Client Components alike; no new client boundary appears. A component reference does not cross a server-to-client prop, as with lucide today; if a boundary appears, pass the rendered element (the `ReadTile icon={<Heart …/>}` form). `@pbbls/icons` is already a dependency of both apps (`"*"`) and Turbopack transpiles it with no `next.config.ts` change (package D1); `pbbls-icons.test.ts` proves the Vitest resolution, and Part 2's `next build` is the first build that bundles it. `"sideEffects": false` and named imports tree-shake the 221-export barrel; no `optimizePackageImports`. `lucide-react` stays a dependency of both apps, so no bundle win is expected; a Pebbles icon is one small module of path data.

## The mapping

Sizes are D7 names. Web paths are under `apps/web/components/` unless written `app/…` or `lib/…` (under `apps/web/`); admin paths are written `admin/…` (under `apps/admin/`). "badges" is `achievements/AchievementBadge` (tile) and `profile/AchievementShelfBadge` (inline). † is a dead file (D12).

### Navigation and shell

| Lucide | Sites (role) | Pebbles | Size |
|---|---|---|---|
| `Route` ×2 | `lib/config/navigation` path entry; `landing/LandingPage` "grow" feature | `path` | row |
| `CirclePlus` ×1 | `lib/config/navigation` record entry | `plus-circle` | row |
| `FolderOpen` ×1 | `lib/config/navigation` collections entry | `collection` | row |
| `CircleUser` ×3 | `path/PathBottomBar` profile fallback (40 px link); `layout/MobileHeader`†, `layout/Sidebar`† | `person` | toolbar; † as today |
| `Settings` ×3 | `app/profile/page` settings link (32 px); `sandbox/design/views/ProfileView` same; `sandbox/design/ComponentsSection` shortcut specimen | `slider` | inline (slot) / row |

Nav entries draw at *row* in `layout/BottomNav`; the dead `layout/Sidebar` keeps `size-4`.

### Pebbles, moments, done

| Lucide | Sites (role) | Pebbles | Size |
|---|---|---|---|
| `Stone` ×2 | `souls/SoulCard` pebble count beside `text-xs`; `path/GamificationBlocks`† | `pebble-round` | inline |
| `Shell` ×2 | `profile/StatsCard`, `public-profile/PublicProfileView` pebbles counter | `pebble-round` | inline |
| `Shapes` (1 of 4) | `pebble/MomentReadToolbar` "Edit pebble" menu item | `pebble-round` | inline (slot) |
| `Clock` ×1 | `landing/LandingPage` "record" feature | `pebble-flat` | row |
| `CircleCheckBig` ×2 | `lab/LogTimeline` shipped; `admin/app/(authed)/logs/_components/LogSection` shipped header | `check-circle` | inline / row |
| `CircleDot` ×1 | `lab/LogTimeline` in progress | `pebble-round.lit-1` | inline |
| `CircleDashed` ×1 | `lab/LogTimeline` backlog | `pebble-round` | inline |
| `CircleDotDashed` ×1 | admin `LogSection` in progress (D9) | `pebble-round.lit-1` | row |
| `CircleFadingArrowUp` ×1 | admin `LogSection` planned (D9) | `hourglass-end` | row |
| `CirclePile` (1 of 3) | admin `LogSection` backlog (D9) | `pebble-round` | row |
| `Check` (2 of 4) | `carve/CarveToolbar` save (labelled button); `record/SoulsSheet` done (36 px `SheetClose`) | `checkmark-dot` | inline / row |

### Create, edit, remove

| Lucide | Sites (role) | Pebbles | Size |
|---|---|---|---|
| `Plus` ×10 | `app/collections/page`, `souls/AddSoulForm` (labelled buttons); `record/SoulPickerContent` create row; `record/SoulPicker`†; `profile/CollectionTile` new tile; `pebble/PebbleEditSoulsGrid` add-soul row; `record/TimeStepPicker` +15 (32 px); `sandbox/design/primitives/PrimitivesSection` button specimens; `admin/app/(authed)/achievements/page`, `admin/app/(authed)/logs/_components/NewLogButton` | `plus-circle` | inline; row in `PebbleEditSoulsGrid`; slot in the specimens |
| `Minus` (1 of 5) | `record/TimeStepPicker` −15 (32 px) | `remove` | inline |
| `UserRoundMinus` ×1 | `connections/ConnectionRow` remove connection (32 px) | `remove` | inline |
| `X` (1 of 5) | `collections/CollectionPebbleList` remove from collection (`icon-xs`) | `remove` | inline |
| `Pencil` ×4 | `collections/CollectionDetailHeader`, `souls/SoulDetailHeader`, `glyphs/GlyphDetail` rename (`icon-xs`); `pebble/MomentReadToolbar` "Edit moment" menu item | `draw-dotted` | inline |
| `Trash2` ×5 | `app/souls/[id]/page`, `glyphs/GlyphDetail` delete (labelled `sm` button); `carve/CarveToolbar` clear (32 px); `pebble/MomentReadToolbar` delete menu item; `settings/DeleteAccountSection` row lead | `trash` | inline; row in `DeleteAccountSection` |
| `ImagePlus` ×2 | `pebble/PebbleEditPicture` empty photo slot; `record/flow/steps/RecordPhotoStep` hero | `camera` / `camera-focus` | tile / hero |

### Souls, collections, you

| Lucide | Sites (role) | Pebbles | Size |
|---|---|---|---|
| `Users` ×5 | badges `first_soul`; `profile/ShortcutsRow` souls tile; `lib/config/navigation` souls entry; `record/VisibilityMenu` private → lucide `UsersRound` (D3) | `people` | badges / row / row |
| `MessagesSquare` ×1 | `lab/FeaturedCommunityCard` card lead | `people` | row |
| `Layers` ×6 | badges `first_collection`; `pebble/MomentReadTiles`, `pebble/PebbleDetailTiles` collections tile; `record/CollectionSheet` pill and empty tile; `profile/CollectionTile` | `collection` (`CollectionIcon` in three files, D10) | badges / row / inline + row / inline |

### Glyphs, karma, dates, achievements, ideas

| Lucide | Sites (role) | Pebbles | Size |
|---|---|---|---|
| `Fingerprint` ×5 | `profile/ProfileBanner`, `settings/GlyphHeader`, `public-profile/PublicProfileView` no-glyph placeholder (96 px slot); `profile/ShortcutsRow` glyphs tile; `lib/config/navigation` glyphs entry | `pebble-dotted-glyph` | hero / row / row |
| `PenTool` ×2 | badges `first_glyph` | `pebble-dotted-glyph` | badges |
| `Shapes` (2 of 4) | badges `glyph_count` | `pebble-dotted-glyph` | badges |
| `Gem` ×3 | badges `pebble_count`; `sandbox/design/ComponentsSection` gamification specimen | `stack` | badges / inline |
| `Sparkle` ×6 | `activity/GlyphPurchasePill`, `activity/KarmaActivityPill`, `record/complete/CompleteSuccessStep` karma line; `path/PathBottomBar` karma stat; `path/GamificationBlocks`†; `wallet/WalletView` balance beside `text-3xl` | `karma-dot` | inline; row in `WalletView` |
| `Sparkles` ×5 | `profile/StatsCard` karma counter → `karma-dot`; `record/flow/steps/RecordWhenStep` date from photo → `sparkle`; `landing/LandingPage` "enrich", `admin/components/layout/Sidebar` Features, `sandbox/design/ComponentsSection` data-tile specimen → `sparkles` | per site (D4) | inline / inline / row, slot, inline |
| `CalendarDays` ×2 | `profile/StatsCard`, `public-profile/PublicProfileView` days counter | `calendar-date` | inline |
| `Trophy` ×6 | badges fallback; `profile/AchievementsShelf`, `public-profile/PublicAchievementsShelf` card header; `admin/components/layout/Sidebar` Achievements; `activity/AchievementMomentHost` celebration → `pebble-glow-lg` | `pebble-glow-md` / `pebble-glow-lg` | badges / row / slot / hero |
| `Compass` (2 of 3), `Store` (2 of 3) | badges `domain_first`, `glyph_sales` (family default until the wave draws *life domain*, *market*) | `pebble-glow-md` | badges |
| `Heart` ×5 | badges `emotion_first`; `pebble/MomentReadTiles`, `pebble/PebbleDetailTiles` emotion tile with no emotion; `glyphs/MarketGlyphCard` favourite toggle | `heart` (`heart.active` when favourited) | badges / row / inline |
| `FlaskConical` ×2 | `profile/LabCard` card header; `lib/config/navigation` lab entry | `sparkle` | row |

### Kept: chrome and brand (D1), 43 sites

`ChevronLeft` ×10 (back links: `admin/app/(authed)/achievements/[id]/page`, `…/achievements/new/page`, `…/domains/[id]/page`, `app/lab/announcements/[id]/page`, `app/lab/backlog/page`, `app/lab/changelog/page`, `connections/ConnectionDetail`, `docs/DocsSidebar`, `layout/PageHeader`, `record/flow/RecordFlowChrome`), `ChevronRight` ×4 (row disclosure: `profile/AchievementsShelf`, `profile/CollectionsCard`, `profile/LabCard`, `settings/LegalSection`), `ChevronDown` ×1 (`admin/components/layout/TopBar`), `X` ×4 (`pebble/MomentReadToolbar` and `record/flow/RecordFlowChrome` close; `record/SoulPickerContent`, `record/SoulPicker`† chip), `MoreHorizontal` ×1, `Share2` ×3, `Copy` ×2, `Undo2` ×1, `ArrowRight` ×1 (`lab/LabFeed`), `ArrowUpRight` and `ArrowDownLeft` ×1 each (`wallet/WalletHistoryItem`), `Check` ×2 (`settings/ColorWorldSwitcher`, `settings/LocaleSwitcher`), `Search` ×1 (`record/SoulPicker`†), `Loader2` ×9; brand `Link2` (`settings/ProvidersSection`) and `Apple` (admin `LogSection`).

### Kept: wave brief (D2), 29 sites

`AlertCircle` (`pebble/PebbleEditPicture`), `ArrowUpCircle` (`lab/ReactionButton`), `Ban` (`connections/ConnectionRow`), `Compass` (`record/DomainSheet`), `Tag` ×2 (`pebble/MomentReadTiles`, `pebble/PebbleDetailTiles`), `FileText` (`docs/DocsIndex`), `Scale` (`settings/LegalSection`), `HeartHandshake` (`settings/ConsentSection`), `Lock` ×2 (badges' locked caption in `achievements/AchievementBadge`, `record/VisibilityMenu`), `Globe` ×3 (`record/VisibilityMenu`, `settings/AppearanceSection`, `settings/LocaleSwitcher`), `UserRoundPlus` (`profile/ShortcutsRow`), `RotateCcw` ×2 (`connections/InviteSection`, `layout/ResetDataButton`†), `RefreshCw` (`layout/StoreLoadError`), `Sun` ×2 (`layout/ThemeToggle`, `settings/AppearanceSection`), `Moon`, `Monitor` (`layout/ThemeToggle`), `Palette` ×2 (`settings/AppearanceSection`, `settings/ColorWorldSwitcher`), `CirclePile` ×2 (`path/PathBottomBar`, `path/GamificationBlocks`†), `Waves` (`public-profile/PublicProfileView`), `Vibrate`, `VibrateOff` (`layout/HapticsToggle`†). Plus the new `UsersRound` (D3).

### Kept: admin (D9), 26 sites, and sandbox placeholders (D4), 3 sites

Admin: `ArrowUp`, `ArrowDown`, `Minus` ×4 each (trend in `components/analytics/DomainShare`, `KpiCard`, `QualitySignalsTable`, `UserAverages`), `AlertTriangle` (`components/analytics/ErrorBlock`), `BarChart3`, `BookOpen`, `Megaphone`, `Palette`, `Shapes`, `ShieldAlert`, `Smile`, `Store` (`components/layout/Sidebar`), `Bot`, `Monitor`, `MonitorSmartphone`, `DatabaseZap`, `Telescope` (`LogSection` platforms). Sandbox: `Flame`, `Leaf`, `UserRound` (`sandbox/design/ComponentsSection`).

Tally: 204 sites = 102 to a Pebbles icon (88 through a listed name, 14 by site) + 1 to lucide `UsersRound` + 101 kept (43 chrome and brand, 29 wave, 26 admin, 3 sandbox). 85 symbols = 30 that leave lucide entirely + 7 split by site (`Check`, `X`, `Minus`, `Shapes`, `Compass`, `Store`, `CirclePile`) + 48 that stay. Pebbles rows drawn: 25, all among iOS's 28 (iOS alone uses `home`, `check-circle-dot`, `chart-double-grow`), with the twins `pebble-round.lit-1` and `heart.active`. 58 files change, plus `layout/BottomNav`, `profile/ShortcutTile` and `profile/DataTile` for their size classes.

## The manifest and the lint

`replaces.ts` keeps `TREES.web.roots`, `extensions` and `skip`; its `pattern` gives way to an import scan used for `web` only (iOS and Android unchanged):

```
web, for each file in apps/{web,admin}/**/*.{ts,tsx} minus components/ui, node_modules, .next:
  for each declaration matching (import|export) <clause> from "<source>" (multi-line):
    source starts with "lucide-react/"      -> hit at the declaration's line
    source is "lucide-react":
      for each specifier in <clause>:        "Trash2", "type LucideIcon", "Trash2 as Bin"
        name = imported name, minus a leading "Lucide" and a trailing "Icon"
        name listed by a row whose migrated has "web"  -> hit at the specifier's line
```

`replaces.test.ts` keeps its four web expectations (`x.tsx:1`, multi-line `y.tsx:2`, `components/ui` skipped, a local `const Trash2` ignored) and adds: a Pebbles `Heart` in a file importing lucide `Tag` is clean; `label="Settings"` and a comment naming `Trash2` are clean; `Trash2Icon`, `LucideTrash2`, `Trash2 as Bin`, `export { Trash2 } from "lucide-react"` and a `lucide-react/icons/trash-2` import each hit. `packages/icons/CLAUDE.md` gains the web form in its `lint:replaces` row and a web line under "When `Icons` is red" (fix the import, never the pattern).

Part 1 lands it with no row migrated for web: the summary stays `replaces: 27 rows, 49 names, 0 hits`. Part 6 sets `"migrated": ["ios", "web"]` on the 21 rows of D5 in the same commit that deletes the last listed import, so the lint goes live with the code it polices; the summary becomes `replaces: 27 rows, 77 names, 0 hits`. Before that commit the implementer also runs

```
grep -rlE "from ['\"]lucide-react" apps/web apps/admin --include='*.ts' --include='*.tsx' \
  | grep -v -e /components/ui/ -e node_modules -e /.next/ | wc -l
```

which prints 98 today and must print 60.

## Parts (one stack, `chore/1313-…`)

| Part | Branch | Content | Check in the browser |
|---|---|---|---|
| 1 | `chore/1313-web-replaces-lint` | `replaces.ts` web form + tests (D6), `icons.json` `replaces.web` (D5), `packages/icons/CLAUDE.md` web lines, wave brief `## Web (#1313)` (D2) | none |
| 2 | `chore/1313-web-shell` | `lib/icons/pbbls-icon.ts` + test (D7), a sizes specimen in `sandbox/design/ComponentsSection`, `pbbls-icons.test.ts` additions; `lib/config/navigation`, `layout/BottomNav`, `path/PathBottomBar`, `path/GamificationBlock` (type) and `GamificationBlocks`†, `app/profile/page`, `landing/LandingPage`, `layout/MobileHeader`†, `layout/Sidebar`†, the three sandbox files; first `next build` with the package (D13) | `/sandbox/design`, `/path`, `/profile`, `/` |
| 3 | `chore/1313-web-moments` | `pebble/` `MomentReadTiles`, `MomentReadToolbar`, `PebbleDetailTiles`, `PebbleEditPicture`, `PebbleEditSoulsGrid`; `record/` `CollectionSheet`, `SoulsSheet`, `SoulPicker`†, `SoulPickerContent`, `TimeStepPicker`, `VisibilityMenu` (→ `UsersRound`, D3), `complete/CompleteSuccessStep`, `flow/steps/RecordPhotoStep`, `flow/steps/RecordWhenStep`; `carve/CarveToolbar` | a moment, the record flow, carve |
| 4 | `chore/1313-web-profile` | `profile/` `AchievementShelfBadge` (labelled, D8), `AchievementsShelf`, `CollectionTile`, `LabCard`, `ProfileBanner`, `ShortcutsRow`, `StatsCard`, plus the `ShortcutTile` and `DataTile` classes; `public-profile/PublicAchievementsShelf`, `public-profile/PublicProfileView`, `achievements/AchievementBadge`, `activity/` (two karma pills and the celebration host), `wallet/WalletView`, `settings/DeleteAccountSection`, `settings/GlyphHeader` | `/profile`, `/u/…`, `/achievements`, `/wallet`, `/settings` |
| 5 | `chore/1313-web-people-lab` | `souls/AddSoulForm`, `souls/SoulCard`, `souls/SoulDetailHeader`, `app/souls/[id]/page`, `collections/CollectionDetailHeader`, `collections/CollectionPebbleList`, `app/collections/page`, `glyphs/GlyphDetail`, `glyphs/MarketGlyphCard`, `connections/ConnectionRow`, `lab/LogTimeline`, `lab/FeaturedCommunityCard` | `/souls`, `/collections`, `/glyphs`, `/connections`, `/lab` |
| 6 | `chore/1313-admin-arm-lint` | admin `achievements/page`, `NewLogButton`, `layout/Sidebar`, `LogSection` (D9); then `migrated: ["ios", "web"]` on the 21 rows; decisions-log entry (D1 list, D3, D6, D7 sizes, D9); plan "Lessons learned" | admin sidebar, `/logs/*`, `/achievements` |

Each part passes alone: `npm run lint`, `npm run test` and `npm run build --workspace=apps/web` (`web.yml` lints and tests but does not typecheck, so the local build is the type gate); Part 1 runs the five `@pbbls/icons` scripts (`lint`, `build`, `test`, `check`, `lint:replaces`); Part 6 adds `npm run lint`, `test` and `build --workspace=apps/admin` (`admin.yml`). Parts 2–5 are web-only and reviewable route by route; kept lucide sites in a touched file (D1, D2) are not a reason to touch their lines. Each PR inherits #1313's labels and milestone (`chore`, M66 · Brand icons), so no Lab Note: `no-lab-note`.

## Testing

- `apps/web/lib/icons/pbbls-icon.test.ts`: the five names, and that each class is `size-<n>` with `n × 4` equal to the iOS value (16, 20, 22, 28, 48), so the two scales cannot drift.
- `apps/web/lib/icons/pbbls-icons.test.ts` gains: a labelled render has `role="img"` and no `aria-hidden`; a `className` reaches the `<svg>` (what opts it out of the shadcn slot default, D7).
- `packages/icons/scripts/lib/replaces.test.ts`: the D6 cases.
- Visual, by the maintainer, before each part is opened: `npm run dev --workspace=apps/web -- --port 3100` from the worktree, after copying `apps/web/.env.local` from the main checkout. `/sandbox/design` first (the Part 2 sizes specimen draws four icons at the five sizes beside `text-xs`, `text-sm` and `text-base`, and the existing `Button` specimens show the slot sizes), then the routes in the parts table, in light and dark, and once with the browser's font size at 20 px to see rem scaling. Admin: `npx next dev --port 3101` from `apps/admin`.
- Accessibility: the repo has no Lighthouse or axe gate. Per part, the Chrome DevTools accessibility tree on one icon-only control (the name comes from the control, the icon is hidden) and on the shelf badge in Part 4 (an image named by its title).
- Bundle, once in Part 2: after `npm run build --workspace=apps/web`, a path string of an icon the app never imports (`Watermelon`'s first `d`) is absent from `apps/web/.next/static/chunks`.

## Risks

- **R1. The line gets lighter.** Lucide strokes 2 on 24, Pebbles 1.75: at 16 px the line goes from 1.33 to 1.17 px, in every button and menu at once. The eye of the maintainer on `/sandbox/design` is the gate; if *inline* reads faint, it becomes 18 (`size-4.5`), one line in `pbbls-icon.ts` and its test.
- **R2. 16 beside `text-xs` is big.** `SoulCard`'s count and the `DataTile` counters go from 12 and 14 to 16 beside 12 px text (iOS R2; the iOS plan's lessons list `DataTile` among the sizes owed a second look). Accepted at the floor; revisited with iOS's answer.
- **R3. A shadcn slot silently resizes an unclassed icon.** Under `xs`, `sm` and `icon-xs` an icon without a class draws at 12 or 14. D7 names the rule; review checks the parent's `size` on every unclassed icon.
- **R4. Two vocabularies on some screens until the wave lands.** Settings (theme, language, colour world, consent, legal), the moment tiles (`Tag` beside the Pebbles heart and collection) and the visibility picker stay lucide. The brief is written in Part 1 so the wave can start before the stack ends.
- **R5. The web lint now differs in kind from iOS's.** Web reads import clauses, iOS greps literals. Deliberate (D6); a future surface states which kind it needs.
