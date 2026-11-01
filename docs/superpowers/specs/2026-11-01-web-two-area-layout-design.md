# Web two-area layout — design

**Date:** 2026-11-01
**Issue:** #1384 (web Home and You), continued. It builds on Part 7 (#1440: one `AppNavigation`, one route table, the five tabs on the M3 ground).
**Milestone:** M67 · Home & You
**Maintainer's brief (2026-11-01):** "a two area layout for any page in desktop mode, align the path left area on standard container width, and put all pages on the left area (home, profile, collections, people). The right area should display the path like today and opening a pebble card should overlay the whole right area. Same, opening settings or a collection or a soul should live in the right area (and overlay on mobile). That again the opportunity to align all pages on M3 color and design for what's remain inconsistent."
**Answers to follow-ups:**
- The work joins the same stack as Parts 8 and up; #1439 and #1440 stay open.
- The right area holds every detail: pebble, soul, collection, settings, glyphs (+ a glyph), achievements, lab (+ changelog, backlog, an announcement), wallet, connections (+ a connection), guides. On mobile these open as an overlay over the tab page.
- Record, carve and the edit flows (`/pebble/[id]/edit`, `/pebble/[id]/edit-pebble`, `/record/complete/[id]`) stay full-screen and immersive.
- Only pages in the two areas move to the M3 roles; record, carve and the edit flows keep the colour world for a later round. The landing, blog, docs and `/u/` are untouched.

**References:**
- Android list-detail: `apps/android/.../navigation/PebblesSceneStrategies.kt`, `docs/superpowers/specs/2026-09-26-android-list-detail-scenes-design.md` (two panes from 840 dp, the rail from 600 dp), the held card (#1135), Settings in M3 list items (#1437).
- Ariko's side sheet: `~/code/ariko/app/admin/(chrome)/layout.tsx` and `@sheet/`. These give us four rules:
  - a null `default.tsx`;
  - a matching null page, so a soft navigation closes the slot;
  - the panel frame lives in a slot layout, so swapping content does not remount it;
  - the room is made with padding, never a translate.
- Next 16: `node_modules/next/dist/docs/01-app/03-api-reference/03-file-conventions/{parallel-routes,intercepting-routes}.md` and `01-app/02-guides/view-transitions.md`.

## Questions for the maintainer

1. **When do two areas start?** The recommendation is **1024 px** (Q1, D1). Android splits at 840 dp, but our left area is the 28 rem reading column. Rail + left at 840 px leaves 296 px, under the Spine's 180 px card floor plus its 82 px margin. At 1024 px it leaves 480 px, which is a phone-width Spine.
2. **Home's week columns on desktop.** They link `/path?week=` today, which moves the left area to the Path tab. The alternative: focus the right-area Path on that week and keep Home on the left. The recommendation is to keep the link (D4): the same on every size, and a real URL.
3. **A pebble on a phone.** Today the Path opens a bottom-sheet peek. The recommendation is the full-screen overlay every other detail uses (D5), as Android's #1135 made the pebble a page of its own on one pane. The alternative keeps the sheet for the pebble only.
4. **Log out's place in Settings.** Part 6 put it above Delete account. Android's #1437 gives it its own group at the very end. The recommendation is to follow #1437 (D7).
5. **The colour-world switcher.** Once every page in the two areas is on M3, a colour world only shows on record, carve and the edit flows. The recommendation is to keep the switcher in Appearance with a line saying where it applies, until those flows move. The alternative hides it now.

## Decisions

**D1. Three layouts by width, two breakpoints, CSS only.**

| Window | Navigation | Areas | A detail opens as |
|---|---|---|---|
| < 600 px | bar (Part 7) | one area: the tab page | a full-screen overlay above the bar |
| 600–1023 px | rail (Part 7) | one area beside the rail | an overlay over the area; the rail stays |
| ≥ 1024 px (`--breakpoint-panes`) | rail | **left** (the tab page, 28 rem) · **right** (flexible, ≥ 480 px) | it covers the right area |

- The left area is exactly the reading column Home and You already use (`max-w-md`, 28 rem with its `px-4`).
- The right area takes the rest.
- A hairline in `--m3-rule` separates the two areas, both on M3 `surface`. There is no card pane: one ground, as Part 7 decided.
- At 200 % zoom a 1440 px window is 720 CSS px, so it drops to one area by itself.
- *Rejected:*
  - 840 px, Android's number: the right area would be 296 px wide (Q1).
  - A resizable divider: Android ships none either (`paneExpansionDragHandle = null`).
  - A JS width read: it flashes the wrong layout at hydration.

**D2. Routing: route groups hold the left area; a root `@detail` slot holds the right.** Every page keeps its URL.
- **Left.** Each tab's pages move under a route group whose **layout renders the tab's screen**: `(home)`, `(path)`, `(people)`, `(collections)`, `(you)`. The tab root and each detail page under the group render `null` as `children`. The group layout therefore stays mounted while the URL moves from `/souls` to `/souls/<id>`, and the list keeps its state and scroll.
- **Right.** `app/@detail/` mirrors each detail URL with the real page:
  - `@detail/souls/[id]/page.tsx`, `@detail/settings/page.tsx`, and so on.
  - These are plain matches, not interceptions, so a hard load and a soft navigation render the same thing. A hard load or a shared link of `/souls/<id>` shows People on the left (the group layout) and the soul on the right, or as the overlay on a phone.
  - `@detail/default.tsx` and `@detail/[...rest]/page.tsx` return `null`, so navigating to a tab root on a soft navigation closes the detail (Ariko's lesson: `default.tsx` alone does not close a slot on a soft navigation).
- **The detail frame.** `DetailPane` is the top app bar, the close action, the enter motion and the overlay or pane geometry. It lives in `@detail/layout.tsx`, so moving from one detail to the next swaps content inside a frame that stays (Ariko's `(.)screens/layout.tsx` lesson). The layout renders the frame only when its child is not null.
- **Parent tabs.** Each detail belongs to one tab's group:

  | Tab | Details |
  |---|---|
  | `(home)` | guides |
  | `(path)` | pebble, wallet |
  | `(people)` | souls, connections |
  | `(collections)` | collections |
  | `(you)` | settings, glyphs, achievements, lab |

  So a hard load knows its left area. `routeShell` (Part 7's table) gains `parentTab` and `detail`; `AppNavigation` marks `parentTab` current (Settings marks You).

  Immersive routes (record, carve, the edit flows) stay outside every group, since a group layout draws its tab under every route below it.
- **The pebble is the one exception.** It opens from every tab, and its left area must not switch to the Path. `@detail/(.)pebble/[id]/page.tsx` intercepts it on a soft navigation, so `children` keeps whatever tab was showing. The plain `@detail/pebble/[id]` serves hard loads with the Path on the left.

  **Spike first (Part 10, task 1):** check that an intercepting and a plain page for the same path can coexist in one slot in Next 16.4. If they cannot, the fallback is no interception: a soft open of a pebble from another tab moves the left area to the Path. This is acceptable and is noted.
- **Gates.** `@detail` renders inside the same `AuthGate` / `ReconsentGate` / `StoreGate` as `children`; the shell wraps both.
- *Rejected:*
  - **Interception everywhere (the photo-modal pattern).** A hard load of `/souls/<id>` renders the standalone page with no list beside it, which fails the brief.
  - **A client pane driven by `usePathname`, with a registry of tab screens in the shell.** It bypasses the router, ships every tab screen in the shell's chunk, and turns route pages into stubs anyway.
  - **A search parameter (`?detail=`).** It is not a real URL for the detail and breaks every existing link.

**D3. The right area's default is the Path's line, mounted once.**
- From 1024 px the shell renders `PathRecto` in the right area: the week roll at the top, then the week pager of Spine feeds.
- It is mounted when `useMediaQuery("(width >= 1024px)")` turns true, under the `@detail` slot. A detail covers it with the line still mounted, inert and `aria-hidden`. So moving the left area or opening and closing a detail never refetches or re-renders the week.
- *Before hydration* the right area is the bare ground: no shift, since its box is CSS-sized.
- *Rejected:*
  - The line as the slot's `default`: soft navigations would remount it, because a catch-all segment is keyed by its params.
  - Mounting it hidden on phones: it would be a second Path under the Path tab.

**D4. The Path tab splits into a verso (left) and a recto (right) from 1024 px; below that it is today's Path.**
- `PathScreen` splits into `PathVerso` (the opening, persons of the week, contents, the bounce and karma pill) and `PathRecto` (the roll, the pager, the feeds).
- A `PathFocusProvider` in the shell holds:
  - the focused week;
  - the press channel (a contents row presses its stone on the recto);
  - `?focus=` and `?week=`, consumed once as today, by the `(path)` layout.
- Below 1024 px `PathScreen` composes both halves as today (spread from 900 px stays for 900–1023 px).
- On every other tab the recto is in the right area and follows the same focus. Its week roll is how you page weeks there.
- Home's week links keep going to `/path?week=` (Q2). The Path tab is the only tab whose left area shows the verso.
- *Rejected:*
  - The verso in the right area: the brief puts every page on the left at the container width.
  - A second, independent focus for the right area: two weeks shown at once.

**D5. A detail is an M3 page in the right area and a full-screen overlay below 1024 px.**
- `DetailPane` gives every detail an M3 small top app bar:
  - the title in `m3-type-title-large`;
  - a **close** icon button (lucide `X`, chrome list) in the right area, or a **back** arrow (`ChevronLeft`) in an overlay;
  - trailing actions from the page;
  - for a detail nested in a detail (lab → changelog), a back arrow to its parent detail at every size.
- **Closing.** A detail opened in-app closes with `router.back()`, so the history entry pops. One opened by a hard load closes with `<Link href={parentTab} replace>`. The pane tells the two apart with a session flag set by in-app navigation. *Rejected:* `router.back()` always, which leaves the app on a hard load; a plain link always, which makes Back reopen what you closed.
- **The overlay (< 1024 px):**
  - `fixed`, over the area, with `surface` ground and safe-area padding;
  - below 600 px it covers the bar, and from 600 px the rail stays usable;
  - `role="dialog"` `aria-modal="true"` `aria-labelledby` its title;
  - `inert` on the left area under it, and the left area's scroll locked through `:has([data-detail])` on `<main>` (no JS);
  - Escape closes it.
- **In the right area (≥ 1024 px):** `role="region"` named by its title. It is not modal: the left area stays live, and the line under it is `inert`.
- **The pebble** uses the same pane over the whole right area, the week roll included. `PebblePeek` and its local open state retire: a stone, a card or a contents row navigates to `/pebble/<id>` (Q3).
- **Held card, where cheap:** a React `<ViewTransition name={`pebble-${id}`}>` on the Spine card and on the detail's card morphs one into the other. There is no card turn (the web has none).
- *Rejected:* a shadcn `Sheet` for details (a side sheet is M3's form for supplementary content, not navigation); a bottom sheet for every detail.

**D6. Every page in the two areas wears the M3 roles and M3 component patterns.**
- An island per group layout and one around the `@detail` slot (`M3Island`).
- `PageHeader` is replaced inside details by the `DetailPane` top app bar.
- `PageLayout` is replaced by the area's own column (`max-w-md` on the left; on the right `max-w-2xl` centred, or full width for the pebble).
- Lists become `ListGroup` and `ListItem` (D7). Content cards become the `pb-card` card (Part 2). Colour-world classes become role classes through the bridge.

| Detail | Today | Becomes |
|---|---|---|
| `/souls/[id]` | `PageLayout`, glyph, `SoulPebbleList` rows | top app bar (name, edit and delete menu), glyph on a `Stone`, pebbles as M3 list items |
| `/collections/[id]` | `PageLayout`, `CollectionPebbleList` | top app bar, mode chip, pebbles as list items |
| `/connections`, `/connections/[id]` | `PageHeader` → `/profile`, list | top app bar; connections as list items; the invite as a filled tonal button |
| `/settings` | `PageHeader`, `SettingsGroup`/`SettingsRow` bordered lists, Save in the header | M3 grouped list items (D7); Save as the top app bar's trailing action |
| `/glyphs`, `/glyphs/[id]` | `PageHeader`, shadcn tabs-as-links, cards | top app bar; M3 secondary tabs; a glyph grid on `surface-container` tiles; the glyph detail in the pane |
| `/achievements` | `AchievementsPageView` (Spine faces, `PageHeader` → `/profile`) | the same view in the pane; the header becomes the top app bar; the Spine sprite is mounted by the pane's island |
| `/lab`, `/lab/changelog`, `/lab/backlog`, `/lab/announcements/[id]` | `LabFeed`, `LabLogList`, `AnnouncementDetail` in `PageLayout` | top app bar; `pb-card` announcements; the timeline as You's `LabTimeline`; backlog rows as list items with the reaction as a trailing icon toggle |
| `/wallet` | `WalletView`, `PageHeader` → `/path` | top app bar; the balance as a display figure; the log as list items |
| `/guides/[slug]` | already M3 (Part 5) | the `PageHeader` back becomes the top app bar |
| `/pebble/[id]` | `MomentRead` in `PageLayout`, or the `PebblePeek` sheet | `MomentRead` in the pane over the whole right area; the toolbar's actions move to the top app bar |

- The detail components keep their data hooks and behaviour; only chrome and roles change.
- `BackPath`, `PageHeader` and `PageLayout` stay in the codebase for the immersive flows and the public pages.

**D7. Settings follows Android's #1437 as far as the web primitives allow.**
- `components/m3/ListGroup.tsx`: a section header in `primary`, `m3-type-label-large`, sentence case, `role="heading" aria-level=2`, above a group.
- `ListItem`: headline and supporting text, optional leading icon and trailing slot; `surface-container` items, 16 px corners at the group's ends and 4 px between, a 2 px gap. It is a link, a button or static.
- `ListSwitchItem`: shadcn `Switch` (added with `npx shadcn add switch`, base-nova), restyled to M3: a check icon in the thumb when on, the row is the control, `role="switch"`, `aria-checked`.
- Text fields stay shadcn `Input` inside a `ListGroupPanel`. Link rows lose their chevrons (#1437).
- Log out gets its own group at the end (Q4). Delete account keeps the error role (orange, never red).
- `SettingsGroup` and `SettingsRow` retire once nothing uses them (a follow-up).
- *Rejected:* a bespoke settings look for the web.

**D8. Accessibility.**
- **Landmarks:**
  - the rail or bar is `<nav aria-label="Main navigation">` (Part 7);
  - the left area is `<main>`;
  - the right area is `<section aria-label>`, named "Path" while the line shows and by the detail's title when a detail covers it;
  - an overlay is a dialog.
- **Headings:** the left area keeps its `<h1>` (greeting, name, page title). A detail's title is an `<h2>` and its sections step down from it, in the pane and in the overlay alike, so the outline is one tree.
- **Focus:**
  - on open, focus moves to the detail's title (`tabIndex=-1`);
  - on close, it returns to the control that opened it when that control is still in the document, else to the left `<main>`'s `<h1>`;
  - in the overlay, `inert` on the area under it is the trap.
- **Keyboard:** DOM order is rail, left, right; Escape closes a detail at every size.
- **Motion:** all motion is off under `prefers-reduced-motion` (D9).
- **Targets:** ≥ 44 px.
- **Zoom:** 200 % drops to one area (D1); nothing scrolls sideways.

**D9. Motion: M3, through React `<ViewTransition>` and CSS.**
- A detail entering the right area: fade and 24 px up, 300 ms, emphasized-decelerate `cubic-bezier(0.05, 0.7, 0.1, 1)`. Leaving: fade, 200 ms, emphasized-accelerate `cubic-bezier(0.3, 0, 0.8, 0.15)`.
- Detail to detail: a 150 ms cross-fade (M3 fade-through).
- The overlay enters from the inline end, 300 ms emphasized-decelerate, and leaves at 200 ms emphasized-accelerate.
- The pebble card morphs from its Spine card (D5).
- Under reduced motion every transition becomes an instant swap.
- These use `<ViewTransition>` with transition types on the detail slot's frame. React 19.3 and Next 16 support it with no configuration; browsers without the API simply swap.
- *Rejected:* a JS animation library; `@starting-style` alone, which cannot animate an unmount.

**D10. Performance.**
- The line is mounted once (D3).
- Group layouts keep each tab's screen across its details (D2).
- `DataProvider` and the store are already shared above both areas, so a detail reads from the cache the tab page warmed.
- The intercepted pebble does not remount the left area.
- The cost is one more client island per group; no new requests.

**D11. Part 6 and Part 7 stay as they are.** Later parts extend Part 7's code:
- `routeShell` gains `parentTab: string | null` and `detail: boolean`, and `m3` covers every right-area route;
- `AppNavigation` marks `parentTab` instead of a prefix match;
- `mainClass` lays out two areas through a new `two-area` utility beside `app-nav-room`.

Part 7's `M3Island` wrappers in `app/souls/page.tsx` and `app/collections/page.tsx` move into the group layouts. Part 6's Session section moves to the end of Settings in Part 12 (Q4).

## Testing
- **Vitest, `lib/config/app-routes.test.ts`:** every right-area URL has its `parentTab` and `detail`; the immersive flows keep theirs; `/pebble/1/edit` is immersive and not a detail.
- **Render tests (`lib/**/*-render.test.ts`, the Part 5 stand-ins):**
  - `DetailPane`: role, name, the close control's kind per mode, the title as `<h2>`.
  - `ListGroup` and `ListSwitchItem`: heading level, `role="switch"`, `aria-checked`.
  - `AppNavigation`: the parent tab is current on a detail.
- **A source test** pins the slot's null pages (`@detail/default.tsx`, `@detail/[...rest]/page.tsx`) and that each `@detail` page renders the same detail module its hard-load twin would. This is Ariko's `screen-sheet-source.test.ts` lesson: deleting a null page reopens the stale-slot bug with every check green.
- **Sandbox:** `ShellFrame` gains a `detail` prop. Screenshots from headless Chromium at 390, 1024 and 1440 px, light and Night, for each tab idle, a soul, Settings and a pebble open. The ground is sampled in both areas.

## Arkaik (the maintainer updates the hosted map)
- **Views:**
  - *gain:* a web two-area shell view (rail, left, right);
  - *change:* People, Collections, You, Home and Path become "left area" views;
  - *become right-area details:* soul, collection, connections, connection, settings, glyphs, glyph, achievements, lab, changelog, backlog, announcement, wallet, guide, pebble. Each gets an edge from its parent tab.
- **Edges:**
  - the pebble gains an edge from every tab;
  - the Path line (recto) gets an edge from the shell as the right area's default.
- **Gone:** `PebblePeek`'s view.
- **Statuses:** acceptances for the two-area layout move to `development` with Part 8.

## Decision log (appended by the last part)

> ## 2026-11-0x — The web lays the tabs out in two areas: the tab page on the left, the Path's line or a detail on the right (#1384)
>
> - **Status:** taken
> - **Scope:** web
> - **Context:** Part 7 gave the web one navigation and one ground. On a wide screen every tab was still one centred column; details were pages of their own on the colour world; the Path's spread was the only two-part page. Android splits list and detail from 840 dp (#940).
> - **Decision:** One area below 1024 px, two from 1024 px. Route groups per tab hold the left area (the tab page, 28 rem). A root `@detail` slot holds the right area, with null `default` and catch-all pages so a soft navigation closes it. The Path's line is mounted once in the right area, under any detail. Every detail is an M3 page there and a full-screen overlay below 1024 px. The pebble is intercepted so it never moves the left area. Every page in the two areas is on the M3 roles; Settings follows Android's #1437 list items.
> - **Why:** Real URLs, hard loads that show list and detail together, no remount of the line, and Android's adaptive pattern at the web's own width. Interception everywhere fails hard loads; a client pane bypasses the router.
> - **Consequences:** A new detail is a `@detail/<path>/page.tsx` plus a null `children` page in its tab's group, and a `routeShell` row. Deleting a null slot page brings back a stale pane. Record, carve and the edit flows stay immersive on the colour world until their own round.
> - **Supersedes / Superseded-by:** extends 2026-11-01 (one navigation, one M3 ground).
> - **Refs:** this spec and plan; `apps/web/app/@detail/`, `apps/web/app/(home|path|people|collections|you)/`, `components/layout/{AppShell,DetailPane}.tsx`, `components/m3/`.
