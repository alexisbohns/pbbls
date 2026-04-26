# Admin: split Logs into Features and Announcements views

**App**: `apps/admin`
**Date**: 2026-04-26
**Milestone**: 26

## Summary

Replace the single `/logs` listing in the back-office with two species-scoped views — **Features** and **Announcements** — each presented as stacked tables (Linear "inbox-zero" style) grouped by the field that actually matters for that species. The current `/logs` route redirects to `/logs/features`. The sidebar gains a non-interactive "Logs" group label with the two views as children.

## Motivation

Today's `/logs` page mixes both species in one table with redundant filters. Features and announcements have different mental models:

- A **feature** lives on a status timeline (backlog → planned → in_progress → shipped). The grouping the operator wants is "what's the active worklist?" — with Shipped as a separate accumulating tail.
- An **announcement** lives on a publish lifecycle (draft → published). The operator wants to see drafts first, then a chronological feed of what's been published.

Splitting the views removes filter friction, makes section grouping the primary axis, and sets up a clean architecture where each section is independently fetchable (and could be promoted to its own page later without rework).

## Scope

### In scope

- New routes: `/logs/features`, `/logs/announcements`.
- `/logs` redirects to `/logs/features`.
- Sidebar restructure: "Logs" becomes an inert group label with two children.
- Stacked-tables UI per view, with section headers showing `Title · count`.
- Streaming for the long-tail / less-urgent buckets via React `<Suspense>`.
- Per-view "New log" button that prefills species via query param.
- Platform filter retained on Features only.
- Deletion of `LogsFilters.tsx` and `LogsTable.tsx` once the new views replace them.

### Out of scope

- Realtime updates.
- Pagination or virtualisation (revisit when Shipped exceeds ~200 rows).
- Bulk actions or row reordering.
- Section-order customisation.
- Changes to log creation/edit flows beyond the species prefill.

## User experience

### Sidebar

- A `SidebarGroup` with a `SidebarGroupLabel` reading "Logs". The label is purely visual — no `href`, no click handler.
- Two `SidebarMenuItem`s under it:
  - **Features** → `/logs/features`, icon `Sparkles` (lucide).
  - **Announcements** → `/logs/announcements`, icon `Megaphone` (lucide).
- Active state matches when `pathname === href || pathname.startsWith(href + "/")`.
- Always expanded — no collapse toggle.

### Features view (`/logs/features`)

- Header: `<h1>Features</h1>` + `<Link href="/logs/new?species=feature">New log</Link>`.
- Optional **platform filter** — see [Platform filter semantics](#platform-filter-semantics) below.
- Four stacked sections, fixed order:
  1. **In progress · N**
  2. **Planned · N**
  3. **Backlog · N**
  4. **Shipped · N** *(streamed in via Suspense)*
- All sections render even when empty, with a dashed-border placeholder ("No features in progress.").
- Columns: Title · Platform · Updated.

### Announcements view (`/logs/announcements`)

- Header: `<h1>Announcements</h1>` + `<Link href="/logs/new?species=announcement">New log</Link>`.
- No filters.
- Two stacked sections, fixed order:
  1. **Drafts · N** — synchronous fetch in the page.
  2. **Published · N** — streamed in via Suspense.
- Both sections render even when empty.
- Columns: Title · Platform · Updated. (No Status column — announcements don't use status meaningfully.)

### Empty states

- Section header still shows `· 0`.
- Body shows the existing dashed-border placeholder, with text adapted per section (e.g. "No drafts.", "No shipped features.").

## Architecture

### File layout

```
apps/admin/app/(authed)/logs/
  page.tsx                                # redirect → /logs/features
  _components/
    LogForm.tsx                           # existing
    MarkdownField.tsx                     # existing
    CoverImageInput.tsx                   # existing
    DeleteLogButton.tsx                   # existing
  features/
    page.tsx                              # NEW — server component
    _components/
      FeatureSection.tsx                  # NEW — generic stacked-table section
      FeaturesActiveSections.tsx          # NEW — three active statuses from one query
      FeaturesShippedSection.tsx          # NEW — async, Suspense-wrapped
      PlatformFilter.tsx                  # NEW — extracted from LogsFilters
  announcements/
    page.tsx                              # NEW — server component
    _components/
      AnnouncementsDraftsSection.tsx      # NEW — synchronous section wrapper
      AnnouncementsPublishedSection.tsx   # NEW — async, Suspense-wrapped
  new/page.tsx                            # existing — extended to read ?species=
  [id]/page.tsx                           # existing, unchanged
```

To delete once the migration lands and nothing references them:

```
apps/admin/app/(authed)/logs/_components/LogsFilters.tsx
apps/admin/app/(authed)/logs/_components/LogsTable.tsx
```

### Data flow

**Features** view:

- `page.tsx` reads and validates `searchParams.platform`.
- One Supabase query for active statuses:
  ```ts
  supabase
    .from("logs")
    .select("*")
    .eq("species", "feature")
    .in("status", ["in_progress", "planned", "backlog"])
    .order("updated_at", { ascending: false })
    // optional .eq("platform", platform) when filter is active
  ```
- Result grouped in memory into `Record<"in_progress" | "planned" | "backlog", LogRow[]>`.
- Three `<FeatureSection>` rendered in fixed order from that grouping.
- `<Suspense fallback={<FeatureSectionSkeleton title="Shipped" />}>` wraps `<FeaturesShippedSection platform={platform} />`, which performs its own query (`status=shipped`, same platform filter, same ordering) inside an async server component. The page itself does **not** await the Shipped component — Next.js streams its HTML in once its query resolves, while the active sections are already painted.

**Announcements** view:

- `page.tsx` runs the Drafts query synchronously:
  ```ts
  supabase
    .from("logs")
    .select("*")
    .eq("species", "announcement")
    .eq("published", false)
    .order("updated_at", { ascending: false })
  ```
- Renders `<AnnouncementsDraftsSection logs={drafts} />` immediately.
- `<Suspense fallback={<SectionSkeleton title="Published" />}>` wraps `<AnnouncementsPublishedSection />`, which runs its own query for `published=true`. As with Features/Shipped, the page does not await the Published component — its HTML streams in independently.

### Section component contract

```ts
type SectionProps = {
  title: string                       // e.g. "In progress"
  logs: LogRow[]
  emptyLabel: string                  // e.g. "No features in progress."
  showPlatform?: boolean              // default true
}
```

The section renders an `<h2>{title} · {logs.length}</h2>` followed by a `<Table>` (using the existing shadcn `Table` primitive) when non-empty, or the dashed-border empty placeholder when empty. Counts always render, including 0.

A small shared `LogRowItem` (or inline rows in `FeatureSection`/`AnnouncementsSection`) renders one row with the columns listed above. Title is a `Link` to `/logs/[id]`.

### Platform filter semantics

The schema defines `platform: 'web' | 'ios' | 'android' | 'all'`, where `'all'` denotes a log that applies to every platform. The current `PLATFORM_OPTIONS` list conflates this schema value with a UI "All platforms" label, which is fine for a column display but ambiguous for filtering. The filter on the Features view defines its own semantics:

- The filter is driven by a `?platform=` searchParam, valid values `web | ios | android`. Absence of the param means **no filter** (show all rows regardless of platform).
- When the param is present (e.g. `?platform=ios`), the query matches rows where `platform IN ('ios', 'all')` — a cross-platform log applies to iOS too, so it must show up in the iOS-filtered view.
- The select UI offers four options: "Any platform" (clears the param), "Web", "iOS", "Android". The `'all'` schema value is **not** a selectable filter — it doesn't make sense to ask "show me only cross-platform logs."
- Implementation: `PlatformFilter.tsx` is a new client component (similar in shape to today's `FilterSelect` inside `LogsFilters.tsx`), pushing to `/logs/features` with the param set or removed. A new options list lives in `lib/logs/options.ts` (e.g. `PLATFORM_FILTER_OPTIONS`) so `PLATFORM_OPTIONS` keeps its current display-only meaning.
- Validation: `page.tsx` validates the param with a small `isPlatformFilter` guard (analogous to `isLogSpecies`/`isLogStatus`); invalid values are treated as absent.

### Skeletons

Suspense fallbacks render a skeleton with the section header (so the header position doesn't shift on stream-in) plus 3 skeleton rows of the same height as a real row. Implementation uses the existing `components/ui/skeleton.tsx`.

### Sidebar component

`apps/admin/components/layout/Sidebar.tsx` is restructured:

- Top-level group with `SidebarGroupLabel` "Logs" (no link, no click).
- Two `SidebarMenuItem`s for the two children, each using `SidebarMenuButton` with `render={<Link href={…}/>}` per the project's Base UI convention (no `asChild`).
- Active matching as described above.

### "New log" prefill

`apps/admin/app/(authed)/logs/new/page.tsx`:

- Read `searchParams.species`, validate with `isLogSpecies`.
- Pass the validated species (or `undefined`) as a new `initialSpecies?: LogSpecies` prop on `LogForm`.
- `LogForm` uses `initialSpecies` to seed the species field (defaulting to today's behaviour when absent).

If `LogForm` doesn't currently accept an initial-species prop, this is a small additive change — no behavioural change when `initialSpecies` is omitted.

## Routing details

- `app/(authed)/logs/page.tsx` becomes a single-line server component returning `redirect("/logs/features")` from `next/navigation`.
- Both new view pages declare `searchParams` as a `Promise<{ … }>` per the existing `LogsPage` convention in this Next.js version.

## Error handling & logging

- Every Supabase select includes `if (error) console.error("[logs/<view>/<section>] select failed:", error.message)` on the failure branch, returning `[]` so the section shows its empty state instead of throwing. This mirrors the pattern already used in `app/(authed)/logs/page.tsx`.
- No timeouts are added at this layer — these are server components on the admin app and current code doesn't wrap server-side selects with `withTimeout`. We stay consistent with the surrounding code.

## Accessibility

- Section headers use `<h2>` so the page has a clear outline (`<h1>` for the view title, `<h2>` per section).
- Tables use the existing shadcn `Table` primitive (semantic `<table>` markup).
- Sidebar items remain keyboard-navigable via `SidebarMenuButton` + `Link`.
- The "Logs" group label is non-interactive, so it isn't focusable — screen readers announce it as a group heading via `SidebarGroupLabel`'s built-in semantics.
- Empty states include text, not icon-only.

## Migration / rollout

- Single PR.
- Old `/logs` route's redirect preserves any existing bookmarks pointing at the back-office root listing.
- After the new views land and pass review, delete `LogsFilters.tsx` and `LogsTable.tsx` in the same PR. No deprecation window — internal admin app, single user surface.
- Update the Arkaik product map (`docs/arkaik/bundle.json`) to reflect the new routes and removed old route, per the project rule.

## Risks

- **Suspense behaviour with the admin auth boundary**: streaming server components must not depend on auth state that's only resolved at the layout level in a way that defers headers. Today's admin pages are plain async server components in `(authed)/`, so this should be fine, but the implementer should sanity-check that `<Suspense>` actually streams rather than blocking on the parent `await`.
- **Empty Shipped at first paint**: the skeleton must occupy the same vertical space as a small populated section, or the page will visibly jolt when Shipped streams in. The 3-skeleton-rows approach mitigates this.
- **Future volume on Shipped**: when Shipped exceeds a few hundred rows, we'll want pagination or a date-bounded "recent shipped" view. Out of scope for this PR; flagged here.

## Open questions

None at spec time — all clarifications resolved during brainstorming.

## Acceptance checklist

- [ ] Sidebar shows non-interactive "Logs" group with Features and Announcements children; correct active state on each.
- [ ] `/logs` redirects to `/logs/features`.
- [ ] Features view renders four sections in fixed order with counts; Shipped streams in via Suspense.
- [ ] Platform filter on Features narrows all four sections.
- [ ] Announcements view renders Drafts immediately and Published via Suspense; both sections always visible.
- [ ] Empty sections show the placeholder text and `· 0` count.
- [ ] "New log" buttons on each view prefill species in the form.
- [ ] Old `LogsFilters.tsx` and `LogsTable.tsx` deleted, no remaining references.
- [ ] Arkaik bundle updated.
- [ ] `npm run build` and `npm run lint` pass for `apps/admin`.
