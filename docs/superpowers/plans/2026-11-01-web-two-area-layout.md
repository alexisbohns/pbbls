# Web two-area layout — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:**
- From 1024 px, the web lays every tab out in two areas: the tab page on the left (28 rem) and, on the right, the Path's line or the detail that covers it.
- Below 1024 px, a detail is a full-screen overlay over the tab page.
- Every page in the two areas wears the M3 roles and M3 component patterns.
- Spec: `docs/superpowers/specs/2026-11-01-web-two-area-layout-design.md` (D1–D11). Issue #1384, milestone M67.

**Architecture:**
- Part 8 splits the Path into a verso and a recto behind a shared focus.
- Part 9 moves each tab into a route group whose layout renders the tab screen (a move, no behaviour change).
- Part 10 builds the shell: the root `@detail` slot and its null pages, the two-area geometry, the line mounted once in the right area, `DetailPane`, and the first two details.
- Parts 11–13 move the remaining details into the slot and align them on M3.
- Part 14 brings the pebble into the right area, retires `PebblePeek`, logs the decision, resolves #1384 and carries the Lab Note.

**Tech stack:** Next.js 16.4 App Router (parallel and intercepting routes, `default.tsx`), React 19.3 `<ViewTransition>`, Tailwind CSS 4, shadcn base-nova, next-intl, Vitest (node, `react-dom/server` render tests with the Part 5 stand-ins), `@pbbls/icons`.

**Stack (continues #1384's gh stack above Part 7, #1440):**

| Part | Branch | PR title | Tasks |
|---|---|---|---|
| 8 | `feat/1384-web-path-halves` | `feat(web): split the path into a verso and a recto behind one focus` | 1–4 |
| 9 | `feat/1384-web-tab-groups` | `chore(web): move each tab into a route group that holds its screen` | 5–8 |
| 10 | `feat/1384-web-two-area-shell` | `feat(web): lay the tabs out in two areas with a detail slot` | 9–17 |
| 11 | `feat/1384-web-people-details` | `feat(web): open souls, collections and connections in the detail area` | 18–21 |
| 12 | `feat/1384-web-settings-m3` | `feat(web): set settings in m3 list groups in the detail area` | 22–26 |
| 13 | `feat/1384-web-you-details` | `feat(web): open glyphs, achievements and the lab in the detail area` | 27–30 |
| 14 | `feat/1384-web-pebble-pane` | `feat(web): open a pebble over the whole detail area` (`Resolves #1384`) | 31–36 |

**Labels, bodies, notes:**
- Labels: `feat`, `ui`, `web` on every part, milestone "M67 · Home & You". Part 9 is a move: its title says `chore`, and it keeps the stack's labels plus `no-lab-note`.
- Parts 8–13: `no-lab-note`, bodies start `Part N of #1384`.
- **Before Part 14 opens:**
  - #1440 (Part 7) becomes `Part 7 of #1384` and loses its Lab Note.
  - Part 14 carries `Resolves #1384` and one Lab Note for Home, You, the navigation and the two areas.
- Never write a Kritik finding id. Every commit ends with the session's attribution trailer.

**Setup:**
- From the main checkout: `git fetch origin`, then a worktree at `.worktrees/home-you-two-area` on `feat/1384-web-hub-nav`.
- Copy `apps/web/.env.local`, then `npm ci`.
- `gh stack add feat/1384-web-path-halves` (branch names as arguments, `gh stack view --json`, `submit --auto`).
- Serve on :3100 (check `lsof -i :3100` first). Never sign in against production; check visuals through `/sandbox/design`.

**The part gate:**
- Run in `apps/web`: `npx eslint .` (0 problems), `npx tsc --noEmit -p .`, `npx vitest run`, `npx next build`.
- Run at the root: `npm run lint:replaces --workspace=@pbbls/icons` (0 hits).
- Delete a generated `apps/web/AGENTS.md` before committing.
- **Visual:** headless Chromium on the sandbox views at 390, 1024 and 1440 px (the frame's "Fill" for the wide two), Paper and Night, EN and one FR pass, with the ground sampled in each area.
- **Move vs rewrite:** within Parts 11–14, each route's move into `@detail` is its own commit with no content change, before the commits that rewrite its chrome, so the review can diff the rewrite alone.

---

## Part 8 — the Path's halves (`feat/1384-web-path-halves`)

Spec D4. A rewrite with no visible change: below 900 px and at the spread, the Path renders as today.

### Task 1: The focus provider

**Files:** create `apps/web/components/path/PathFocusProvider.tsx`, `apps/web/lib/path/path-focus.ts`, `path-focus.test.ts`.

- [ ] `path-focus.ts` (pure): `resolveFocus(entries, focusedKey, today)` and `consumeParams({ focus, week }, entries)`. The logic moves out of `PathScreen` (the `closestEntry`, `focus` wins over `week`, applied once).
- [ ] Tests:
  - the `?focus=` and `?week=` cases `PathScreen` covers today;
  - `focus` beats `week`;
  - an unknown week settles on the closest one.
- [ ] `PathFocusProvider` holds:
  - the focused week key and its setter;
  - the arrow-key handler (moved from `PathScreen`);
  - the press channel (`useSpinePresses`), so a contents row on one half can press a stone on the other.

  Hook: `usePathFocus()`.

### Task 2: `PathVerso` and `PathRecto`

**Files:** create `apps/web/components/path/PathVerso.tsx`, `PathRecto.tsx`; modify `components/path/spine/SpineWeek.tsx` (an `only?: "verso" | "recto"` prop rendering one half), `components/path/WeekPager.tsx`.

- [ ] `PathVerso`: for the focused week, renders the opening, persons of the week, contents and `PathBottomDock`, reading `usePathFocus()`.
- [ ] `PathRecto`: renders `WeekRoll` and a `WeekPager` of recto-only weeks. It reserves no verso room.
- [ ] `SpineWeek` with no `only` stays byte-identical in markup (render test).

### Task 3: `PathScreen` composes the halves

**Files:** `components/path/PathScreen.tsx`, `app/path/page.tsx`.

- [ ] `PathScreen` = `PathFocusProvider` around today's tree, built from the two halves. Its own focus state, keys and param consumption are gone (now in the provider).
- [ ] Render test (`lib/path/path-render.test.ts`): from the fixture store, the markup of `PathScreen` at its default equals the pre-split markup snapshot taken in Task 1 (store it as a text fixture in the test).

### Task 4: Gate, PR

- [ ] Part gate. Visual: `/sandbox/design` → Path at 390 and 1280; identical to Part 7's screenshots (`shots2/path-*`).
- [ ] Commit per task; `gh stack submit --auto`; PR body (Part 8 of #1384, `no-lab-note`).

---

## Part 9 — tabs in route groups (`feat/1384-web-tab-groups`)

Spec D2 (left area). Moves only: every URL, markup and behaviour stays. Each tab screen leaves its page for its group's layout.

### Task 5: Screens out of pages

**Files:**
- create `apps/web/components/souls/PeopleScreen.tsx` (from `app/souls/page.tsx`'s body);
- create `components/collections/CollectionsScreen.tsx` (from `app/collections/page.tsx`);
- create `components/path/PathRoute.tsx` (`app/path/page.tsx`'s `PathContent` and its Suspense).

- [ ] `git mv`-style extraction: the bodies move verbatim; the pages render the screen.
- [ ] `PeopleView` and `CollectionsView` in the sandbox import the screens, not the pages.

### Task 6: The five groups

**Files:** `git mv` into `apps/web/app/(home)/{home,guides}`, `(path)/{path,wallet}` plus `(path)/pebble/[id]/page.tsx`, `(people)/{souls,connections}`, `(collections)/collections`, `(you)/{profile,settings,glyphs,achievements,lab}`.

- [ ] The immersive children of `pebble/[id]` (`edit`, `edit-pebble`) **stay outside every group** at `app/pebble/[id]/{edit,edit-pebble}`: a group layout renders its tab screen on every route below it, and an immersive flow must not draw a tab behind it. First check that `next build` accepts `(path)/pebble/[id]/page.tsx` beside `app/pebble/[id]/edit/` (sibling parents across a group). If it does not, keep the read page outside the group too, and let `(path)` hold the pebble's left area in Part 14 through the slot alone.

- [ ] Each group gets a `layout.tsx` rendering `<M3Island>` + the tab screen + `{children}`. The `(path)` island takes `h-full`; Home keeps its `StoneSprite`; the italic font import moves with the layout.
- [ ] The tab root pages return `null`. Every other page in the group still renders its own content in `children` for now: details move to the slot from Part 10 on.
- [ ] Part 7's `M3Island` wrappers in the People and Collections screens go; the group layout holds the island.
- [ ] `lib/config/store-routes.ts`, `AuthGate`'s list and every import path compile unchanged (URLs did not change).

### Task 7: Pin the groups

**Files:** create `apps/web/lib/config/route-groups.test.ts`.

- [ ] It reads the `app/` tree. It asserts:
  - each tab root URL resolves to exactly one `page.tsx` under its group;
  - each group has a `layout.tsx`;
  - no route left outside a group is a tab or a detail;
  - no immersive route (`routeShell(...).immersive`) sits inside a group.

### Task 8: Gate, PR

- [ ] Part gate. Visual: the five tabs at 390 and 1280, identical to Part 7's; a hard load of `/souls` signed out still redirects.
- [ ] PR body says it is a move (no behaviour change) and lists the `git mv`s.

---

## Part 10 — the shell (`feat/1384-web-two-area-shell`)

Spec D1–D3, D5, D8–D11. After this part, guides and the wallet open in the right area; every other detail still opens as a page in the left area until its part.

### Task 9: The routing spike (first, before anything else)

- [ ] In a scratch branch, check that both of these coexist in Next 16.4 and that each is served by the right page:
  - `app/@detail/pebble/[id]/page.tsx` (plain: hard load);
  - `app/@detail/(.)pebble/[id]/page.tsx` (intercepting: soft navigation from `/home`).
- [ ] Record the answer in the PR body.
- [ ] If they cannot coexist, Part 14 uses the plain page only (spec D2 fallback).

### Task 10: The route table

**Files:** `apps/web/lib/config/app-routes.ts`, `app-routes.test.ts`.

- [ ] `RouteShell` gains `parentTab: string | null` and `detail: boolean`. A `DETAILS` table maps each detail prefix to its tab (spec D2).
- [ ] `m3` covers every detail; `tab` stays "a tab root".
- [ ] Tests:
  - every detail URL of the spec's table maps to its parent tab;
  - `/pebble/1/edit`, `/pebble/1/edit-pebble` and `/record/complete/1` are immersive and not details;
  - `/p/1`, `/u/x`, `/blog` and `/docs` are none of these.

### Task 11: The navigation marks the parent tab

**Files:** `components/layout/AppNavigation.tsx`, `lib/home/nav-render.test.ts`.

- [ ] `current = (routeShell(pathname).parentTab ?? pathname) === href`.
- [ ] The navigation shows on tab roots **and** details: `MainContent`'s `showNavigation` uses `tab || detail`.
- [ ] Tests: `/settings` marks You; `/souls/x` marks People; `/pebble/1` marks Path on a hard load.

### Task 12: The slot

**Files:** create `apps/web/app/@detail/default.tsx`, `@detail/[...rest]/page.tsx` (both `null`, with Ariko's docblock reasons), `@detail/layout.tsx`; modify `app/layout.tsx` (accept `detail`, pass it to `MainContent`); create `lib/config/detail-slot-source.test.ts`.

- [ ] `@detail/layout.tsx` renders `<DetailPane>{children}</DetailPane>`. `DetailPane` renders nothing when its child renders nothing (a `data-detail` marker only when content exists).
- [ ] `detail-slot-source.test.ts` pins:
  - the two null pages exist and return `null`;
  - every `@detail/**/page.tsx` imports its detail module rather than reimplementing it.

### Task 13: Two areas

**Files:**
- `apps/web/app/globals.css`: `--breakpoint-panes: 1024px`; a `two-area` utility giving the left area `w-md shrink-0` and the right `flex-1 border-s border-(--m3-rule)` from `panes:`;
- create `components/layout/AppShell.tsx`;
- modify `components/layout/MainContent.tsx`, `components/layout/main-class.ts`.

- [ ] `AppShell` renders:
  - `<main>` (left: the gates around `children`);
  - `<section aria-label>` (right): `PathRecto` when `useMediaQuery("(width >= 1024px)")` and the route has the navigation, and the `detail` slot above it, inside the same gates.
- [ ] Below 1024 px the right `<section>` holds only the slot (the overlay).
- [ ] The right area's name is "Path" (`nav.path`) idle, else the detail's title (from `DetailPane` through a context).
- [ ] `PathFocusProvider` moves up into `AppShell` so the left verso and the right recto share it. The `(path)` layout renders `PathVerso` from 1024 px and `PathScreen` below (by `useMediaQuery`, with the server and first render as `PathScreen`).
- [ ] Render test: the left and right landmarks and the right's name idle.

### Task 14: `DetailPane`

**Files:** create `components/layout/DetailPane.tsx`, `components/layout/DetailTopBar.tsx`, `lib/hooks/useDetailClose.ts`; `lib/i18n/messages/{en,fr}.json` (`detail.close`, `detail.back`, inserted as text after `nav`).

- [ ] Geometry:
  - right area `absolute inset-0` from `panes:`;
  - below, `fixed inset-0 z-50` under 600 px, and `fixed inset-y-0 end-0 start-24` from 600 px;
  - `surface` ground, safe-area padding.
- [ ] Roles: `role="region"` from 1024 px and `role="dialog" aria-modal="true"` below, both `aria-labelledby` the title. Since roles cannot change by CSS, the pane reads `useMediaQuery("(width >= 1024px)")`; the server render is the region.
- [ ] `DetailTopBar`: the title `<h2 tabIndex={-1}>` in `m3-type-title-large`; leading close (`X`) or back (`ChevronLeft`) icon button, 44 × 44, `PBBLS_ICON_SIZE.toolbar`; a trailing slot.
- [ ] `useDetailClose(parentHref)`:
  - `router.back()` when a session flag says the detail was opened in-app (set on every in-app navigation into a detail);
  - else `router.replace(parentHref)`;
  - Escape calls it.
- [ ] Focus:
  - the title on mount;
  - on unmount, the stored opener if still connected, else the left `<main>`'s `h1`.
- [ ] `inert` on the left `<main>` while an overlay is open, and on `PathRecto` while a pane covers it (read through `data-detail`). Scroll lock with `:has([data-detail])` on `<main>` below 1024 px.
- [ ] Motion: `<ViewTransition>` with enter, exit and update classes in `globals.css` per spec D9, and every animation off under `prefers-reduced-motion: reduce`.
- [ ] Render tests (`lib/layout/detail-pane-render.test.ts`):
  - the region with its name, and the `h2`;
  - the close control's label;
  - nothing rendered for a null child.

### Task 15: The first two details

**Files:**
- `git mv` the bodies into `app/@detail/guides/[slug]/page.tsx` and `@detail/wallet/page.tsx`; their `children` pages under `(home)` and `(path)` return `null`;
- modify `components/home/GuidePage.tsx`, `components/wallet/WalletView.tsx`.

- [ ] Commit 1: the moves.
- [ ] Commit 2: the chrome. `PageHeader` → `DetailTopBar` (via `DetailPane`'s title context); the wallet's balance and log as M3 list items; roles only.

### Task 16: Sandbox

**Files:** `components/sandbox/design/views/ShellFrame.tsx` (two areas, a `detail` prop rendered through `DetailPane`), `ViewsSection.tsx` (a "Wallet open" view).

- [ ] Screenshots at 390, 1024 and 1440, Paper and Night: Home idle with the line on the right (1024 and 1440), Path (verso left, recto right), the wallet open in each mode.
- [ ] The ground is sampled in both areas.

### Task 17: Gate, PR

- [ ] Part gate.
- [ ] PR body:
  - the spike's answer;
  - the screenshots;
  - for the maintainer, signed in: hard load `/wallet` at 1440 and 390; Back and Forward around a guide; the line keeps its week while the left changes tab.

---

## Part 11 — People and Collections details (`feat/1384-web-people-details`)

Spec D6.

### Task 18: Move

- [ ] `git mv` `souls/[id]`, `collections/[id]`, `connections`, `connections/[id]` page bodies into `app/@detail/…`. Their `children` pages under `(people)` and `(collections)` return `null`. One commit.

### Task 19: The soul and the collection

**Files:** `components/souls/{SoulDetailHeader,SoulPebbleList}.tsx`, `components/collections/{CollectionDetailHeader,CollectionPebbleList}.tsx`, create `components/m3/ListItem.tsx` (if Part 12 has not, build it here and Part 12 reuses it).

- [ ] Header actions (edit, delete) go to the top bar's trailing menu.
- [ ] The glyph sits on a `Stone` (Part 3).
- [ ] Pebbles are M3 list items (leading stone print, headline name, supporting date), each a link to `/pebble/<id>`.

### Task 20: Connections

**Files:** `components/connections/{ConnectionsList,ConnectionRow,InviteSection,ConnectionDetail}.tsx`.

- [ ] List items; the invite as a tonal button; the `PageHeader` (back to `/profile`) goes.

### Task 21: Tests, sandbox, gate, PR

- [ ] Render tests: a soul's pebbles are links in a `<ul>` under an `h2`; connections render list items.
- [ ] Sandbox views "Soul open" and "Collection open". Screenshots as Part 10.
- [ ] Part gate, PR.

---

## Part 12 — Settings on M3 (`feat/1384-web-settings-m3`)

Spec D7, Q4.

### Task 22: The list primitives

**Files:**
- `npx shadcn@latest add switch`;
- create `components/m3/ListGroup.tsx`, `ListItem.tsx` (if not from Part 11), `ListSwitchItem.tsx`, `ListGroupPanel.tsx`;
- `app/globals.css` (one `m3-list-item` utility for the segmented corners: 16 px at a group's ends, 4 px between, a 2 px gap).

- [ ] One exported component per file.
- [ ] `ListSwitchItem`: the row is the control (`role="switch"`, `aria-checked`); the thumb carries `Check` (lucide, chrome list) when on.
- [ ] Render tests: the heading level, the switch semantics, the corners at the ends.

### Task 23: Move

- [ ] `git mv` the settings page body into `app/@detail/settings/page.tsx`; `(you)/settings/page.tsx` returns `null`. One commit.

### Task 24: The sections

**Files:** `components/settings/*Section.tsx`, `GlyphHeader.tsx`.

- [ ] Each section becomes a `ListGroup` of `ListItem`s.
- [ ] `Save` moves to the top bar's trailing slot.
- [ ] Link rows lose chevrons.
- [ ] The Session section (Part 6) becomes the last group.
- [ ] Delete account keeps the error role.
- [ ] The theme, locale and colour-world controls stay their dropdowns inside `ListItem`'s trailing slot. The colour world gains a supporting line (Q5).

### Task 25: Retire the old rows

- [ ] `SettingsGroup` and `SettingsRow` keep only what still imports them (`ConnectionsRow` on People moves to `ListItem` here). If nothing does, list them for the follow-up deletion issue; do not delete in this stack.

### Task 26: Tests, sandbox, gate, PR

- [ ] `lib/home/settings-render.test.ts` grows: Session is the last group; Save is in the top bar.
- [ ] Sandbox "Settings open" at the three widths.
- [ ] Part gate, PR.

---

## Part 13 — You's details (`feat/1384-web-you-details`)

Spec D6.

### Task 27: Move

- [ ] `git mv` `glyphs`, `glyphs/[id]`, `achievements`, `lab`, `lab/changelog`, `lab/backlog` and `lab/announcements/[id]` bodies into `@detail`. Their `(you)` children pages return `null`. The achievements layout's Ysabeau cuts and `StoneSprite` move to `@detail/achievements/layout.tsx`. One commit.

### Task 28: Glyphs

**Files:** `components/glyphs/{GlyphTabs,GlyphList,GlyphCard,GlyphDetail,MarketGlyphs,FavouriteGlyphs}.tsx`.

- [ ] Tabs as M3 secondary tabs (links, `aria-current`).
- [ ] Tiles on `surface-container`.
- [ ] The glyph detail is its own detail (back to `/glyphs`).

### Task 29: Achievements and Lab

**Files:** `components/achievements/AchievementsPageView.tsx` (header only), `components/lab/{LabFeed,LabLogList,AnnouncementDetail,AnnouncementRow,LogTimeline,FeaturedCommunityCard}.tsx`.

- [ ] Headers become the top bar.
- [ ] Announcements become `pb-card`s.
- [ ] Backlog rows become list items with the reaction as a trailing icon toggle.
- [ ] The community card reuses You's `CommunityStone`.

### Task 30: Tests, sandbox, gate, PR

- [ ] Render tests: secondary tabs carry `aria-current`; the changelog is a back-able nested detail.
- [ ] Sandbox views "Glyphs open" and "Lab open".
- [ ] Part gate, PR.

---

## Part 14 — the pebble (`feat/1384-web-pebble-pane`)

Spec D2 (exception), D5, D9. `Resolves #1384`.

### Task 31: Move and intercept

**Files:** `app/@detail/pebble/[id]/page.tsx` (the read page's body, moved), `app/@detail/(.)pebble/[id]/page.tsx` (re-exports the same module; dropped if Task 9 said no), `(path)/pebble/[id]/page.tsx` → `null`.

- [ ] The source test (Task 12) pins that both slot pages import one module.

### Task 32: Retire the peek

**Files:** `components/path/PebblePeek.tsx` (deleted), `components/path/PathScreen.tsx`, `components/path/spine/{SpinePebble,SpineCard,SpineEntry,WeekContents}.tsx`.

- [ ] Opening a pebble navigates to `/pebble/<id>` (`router.push`, or a `Link` where the control is one).
- [ ] The Path's `selectedMomentId` state goes.

### Task 33: The card in the pane

**Files:** `components/pebble/{MomentRead,MomentReadToolbar}.tsx`.

- [ ] The toolbar's close goes (the pane closes).
- [ ] Edit and delete move to the top bar's trailing slot.
- [ ] The read view takes the whole right area (no column cap).
- [ ] Deleting navigates with `useDetailClose`.

### Task 34: The held card morph

**Files:** `SpineCard.tsx`, `MomentRead.tsx`.

- [ ] `<ViewTransition name={`pebble-${momentId}`}>` around the Spine card and around the read view's card.
- [ ] No turn.
- [ ] Reduced motion: none.

### Task 35: Decision log, lessons

**Files:** `docs/decisions/log.md` (append the spec's entry, dated the day it lands), this plan's "Lessons learned".

### Task 36: Gate, Lab Note, PR

- [ ] Part gate.
- [ ] Screenshots:
  - a pebble open over the line at 1024 and 1440;
  - as the overlay at 390;
  - opened from a soul (the left stays People).
- [ ] Before opening: edit #1440's body to `Part 7 of #1384` and drop its Lab Note.
- [ ] This PR: `Resolves #1384`; one Lab Note (repo `lab-note` skill) covering Home, You, the navigation and the two areas.
- [ ] For the maintainer, signed in:
  - hard load `/pebble/<id>` and `/souls/<id>` at 1440 and 390;
  - open a pebble from Home, People, a collection and the Path;
  - Back and Forward;
  - `/path?focus=` and `/path?week=`;
  - `/p/<id>` unchanged.

---

## Deviations from the spec

(Written during execution.)

## Lessons learned

- **A `default.tsx` does not close a parallel slot on a soft navigation.** Next keeps the slot's previous page; a null root page and a null catch-all are what close it. Next refuses an optional catch-all (`[[...rest]]`) beside the root page, so it is `page.tsx` plus `[...rest]/page.tsx`.
- **Put a slot's frame in a route group's layout inside the slot** (`@detail/(pane)/layout.tsx`): the null pages sit outside it, so a closed slot draws no frame, and the frame stays mounted from one detail to the next.
- **An intercepting page and a plain page for the same path coexist in one slot** (Next 16.4): soft navigations take the interception (the left area stays), hard loads the plain page. Prove it with a throwaway pair before building on it.
- **A route group's layout draws under every route below it.** Immersive flows (record, carve, the pebble edit flows) stay outside the groups, and `TabArea` steps aside on a group route that is not the tab or one of its details.
- **`useSelectedLayoutSegment("detail")` in a client component under the root layout tells the shell whether a detail is open**, which is simpler and more honest than CSS `:has()` for `inert`, the scroll lock and the landmark's name.
- **A shared-element morph needs its source to leave.** The Spine stays mounted under the pebble's pane, so a `<ViewTransition name>` pair would share a name at once and be skipped; the pane's own motion stands in.
- **Check the shell's route table in the same part as a moved page.** Part 12 shipped Settings in the slot without its `DETAILS` row (a command chain stopped before the edit); a part-level test that every slot page has a table row (`detail-slot-source.test.ts`) catches it, and the fix belongs in that part, not on top.
- **`shadcn add` can write a wrong import alias** (`import { cn } from "cn"`) and install that npm package; read its diff before keeping it. Here the M3 switch is built on Base UI directly.
