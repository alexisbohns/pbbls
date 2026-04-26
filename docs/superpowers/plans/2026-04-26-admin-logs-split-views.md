# Admin Logs Split Views Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the admin app's `/logs` route into two species-scoped views (`/logs/features` and `/logs/announcements`) using Linear-style stacked-table grouping, with React `<Suspense>` streaming the long-tail "Shipped" / "Published" sections.

**Architecture:** Two new server-component routes under `app/(authed)/logs/`. Each view fetches its primary (urgent) data synchronously and renders streamed sub-sections via `<Suspense>` and async server components. A new shared `FeatureSection` component renders one stacked table per group. The sidebar gains a non-interactive "Logs" group label with two children. The old `/logs` route redirects to `/logs/features`.

**Tech Stack:** Next.js 16 App Router (server components + Suspense streaming), React 19, TypeScript strict, Supabase JS (PostgREST), shadcn/ui (`base-nova` style on `@base-ui/react`), Tailwind CSS 4.

**Spec:** `docs/superpowers/specs/2026-04-26-admin-logs-split-views-design.md`
**Issue:** #319 (Milestone 26)
**Branch:** `feat/319-admin-logs-split-views`

**Project conventions in play:**
- V1 has no tests — verification is `npm run build`, `npm run lint`, and manual smoke check in the browser. Each task ends with a commit.
- Conventional commits, lowercase, no period: `type(scope): description`. Scope for this work is `core` (matches PR #318 precedent for the back-office app).
- Base UI primitives: no `asChild`. Nav-as-button uses `<Link className={buttonVariants(...)}>`; primitives that accept `render` (e.g. `SidebarMenuButton`, `BreadcrumbLink`) use `render={<Link href=…/>}`.
- Server components await `searchParams` typed as `Promise<{…}>` per the existing `LogsPage` convention in this Next.js version.
- Error logging on every Supabase select: `if (error) console.error("[logs/<view>/<section>] select failed:", error.message)` and return `[]`.

---

## File Structure

| File | Action | Purpose |
|---|---|---|
| `apps/admin/lib/logs/options.ts` | Modify | Add `PLATFORM_FILTER_OPTIONS` (without `'all'` schema value) and `isPlatformFilter` type guard |
| `apps/admin/app/(authed)/logs/_components/FeatureSection.tsx` | Create | Generic stacked-table section: header `Title · count`, table or empty placeholder. Used by both views. |
| `apps/admin/app/(authed)/logs/_components/FeatureSectionSkeleton.tsx` | Create | Suspense fallback: header + 3 skeleton rows, same height as a populated section |
| `apps/admin/app/(authed)/logs/features/page.tsx` | Create | Features view: fetch active statuses, render 3 sections + `<Suspense>` around shipped |
| `apps/admin/app/(authed)/logs/features/_components/FeaturesShippedSection.tsx` | Create | Async server component: own query for `status=shipped`, renders one `FeatureSection` |
| `apps/admin/app/(authed)/logs/features/_components/PlatformFilter.tsx` | Create | Client component: select bound to `?platform=` searchParam |
| `apps/admin/app/(authed)/logs/announcements/page.tsx` | Create | Announcements view: fetch drafts synchronously, render section + `<Suspense>` around published |
| `apps/admin/app/(authed)/logs/announcements/_components/AnnouncementsPublishedSection.tsx` | Create | Async server component: own query for `published=true`, renders one `FeatureSection` |
| `apps/admin/app/(authed)/logs/page.tsx` | Modify | Replace listing with `redirect("/logs/features")` |
| `apps/admin/app/(authed)/logs/_components/LogForm.tsx` | Modify | Accept optional `initialSpecies?: LogSpecies` prop; use it when `log` is null |
| `apps/admin/app/(authed)/logs/new/page.tsx` | Modify | Read and validate `?species=`, pass as `initialSpecies` to `LogForm` |
| `apps/admin/components/layout/Sidebar.tsx` | Modify | Replace single "Logs" item with non-interactive group label + two children |
| `apps/admin/app/(authed)/logs/_components/LogsFilters.tsx` | Delete | Replaced by per-view `PlatformFilter` |
| `apps/admin/app/(authed)/logs/_components/LogsTable.tsx` | Delete | Replaced by `FeatureSection` |

---

## Task 1: Branch + spec commit

**Files:**
- Already created: `docs/superpowers/specs/2026-04-26-admin-logs-split-views-design.md`
- Already created: `docs/superpowers/plans/2026-04-26-admin-logs-split-views.md` (this file)

- [ ] **Step 1: Create the feature branch**

```bash
git checkout -b feat/319-admin-logs-split-views
```

- [ ] **Step 2: Stage and commit the spec + plan together**

```bash
git add docs/superpowers/specs/2026-04-26-admin-logs-split-views-design.md docs/superpowers/plans/2026-04-26-admin-logs-split-views.md
git commit -m "$(cat <<'EOF'
docs(core): add M26 admin logs split views design and plan

Resolves #319 (planning artifacts).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Extend `lib/logs/options.ts` with platform filter helpers

**Files:**
- Modify: `apps/admin/lib/logs/options.ts`

The schema's `platform: 'web' | 'ios' | 'android' | 'all'` value `'all'` denotes a cross-platform log. The filter should not let users pick `'all'` as a filter (meaningless). It should also not be a sentinel for "no filter". Filter values are `web | ios | android`; absence = no filter.

- [ ] **Step 1: Add `PlatformFilter` type, options list, and guard**

Append to `apps/admin/lib/logs/options.ts`:

```ts
export type PlatformFilter = "web" | "ios" | "android"

export const PLATFORM_FILTER_OPTIONS: ReadonlyArray<{ value: PlatformFilter; label: string }> = [
  { value: "web", label: "Web" },
  { value: "ios", label: "iOS" },
  { value: "android", label: "Android" },
]

const PLATFORM_FILTER_VALUES = PLATFORM_FILTER_OPTIONS.map((o) => o.value)

export function isPlatformFilter(value: string | undefined): value is PlatformFilter {
  return value !== undefined && (PLATFORM_FILTER_VALUES as readonly string[]).includes(value)
}
```

- [ ] **Step 2: Verify lint + types**

```bash
cd apps/admin && npm run lint
```

Expected: clean. If any unused-import warnings on the new exports, ignore — they'll be consumed in later tasks.

- [ ] **Step 3: Commit**

```bash
git add apps/admin/lib/logs/options.ts
git commit -m "$(cat <<'EOF'
feat(core): add platform filter options and guard for logs

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Create the generic `FeatureSection` component

**Files:**
- Create: `apps/admin/app/(authed)/logs/_components/FeatureSection.tsx`

This is the shared stacked-table section, used by both Features and Announcements views. Renders an `<h2>` with `Title · count`, then a `<Table>` or the dashed-border empty placeholder.

- [ ] **Step 1: Write the component**

Create `apps/admin/app/(authed)/logs/_components/FeatureSection.tsx`:

```tsx
import Link from "next/link"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import type { LogRow } from "@/lib/logs/types"

function formatDate(value: string) {
  return new Date(value).toLocaleString(undefined, {
    year: "numeric",
    month: "short",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  })
}

export function FeatureSection({
  title,
  logs,
  emptyLabel,
}: {
  title: string
  logs: LogRow[]
  emptyLabel: string
}) {
  return (
    <section className="space-y-3">
      <h2 className="text-base font-semibold">
        {title} <span className="text-muted-foreground font-normal">· {logs.length}</span>
      </h2>
      {logs.length === 0 ? (
        <div className="border-border rounded-md border border-dashed p-8 text-center">
          <p className="text-muted-foreground text-sm">{emptyLabel}</p>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Title</TableHead>
              <TableHead>Platform</TableHead>
              <TableHead>Updated</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {logs.map((log) => (
              <TableRow key={log.id}>
                <TableCell className="font-medium">
                  <Link href={`/logs/${log.id}`} className="hover:underline">
                    {log.title_en}
                  </Link>
                </TableCell>
                <TableCell>{log.platform}</TableCell>
                <TableCell className="text-muted-foreground text-sm">
                  {formatDate(log.updated_at)}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </section>
  )
}
```

- [ ] **Step 2: Type-check**

```bash
cd apps/admin && npx tsc --noEmit
```

Expected: clean. The component is unused at this point but should compile.

- [ ] **Step 3: Commit**

```bash
git add apps/admin/app/\(authed\)/logs/_components/FeatureSection.tsx
git commit -m "$(cat <<'EOF'
feat(core): add FeatureSection stacked-table component

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Create the `FeatureSectionSkeleton` component

**Files:**
- Create: `apps/admin/app/(authed)/logs/_components/FeatureSectionSkeleton.tsx`

The skeleton must occupy the same vertical space as a populated section so the page doesn't jolt when the streamed section paints in.

- [ ] **Step 1: Write the component**

Create `apps/admin/app/(authed)/logs/_components/FeatureSectionSkeleton.tsx`:

```tsx
import { Skeleton } from "@/components/ui/skeleton"

export function FeatureSectionSkeleton({ title }: { title: string }) {
  return (
    <section className="space-y-3">
      <h2 className="text-base font-semibold">
        {title} <span className="text-muted-foreground font-normal">· …</span>
      </h2>
      <div className="border-border space-y-2 rounded-md border p-3">
        <Skeleton className="h-6 w-full" />
        <Skeleton className="h-6 w-full" />
        <Skeleton className="h-6 w-full" />
      </div>
    </section>
  )
}
```

- [ ] **Step 2: Type-check**

```bash
cd apps/admin && npx tsc --noEmit
```

Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add apps/admin/app/\(authed\)/logs/_components/FeatureSectionSkeleton.tsx
git commit -m "$(cat <<'EOF'
feat(core): add FeatureSectionSkeleton suspense fallback

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Create the `PlatformFilter` client component

**Files:**
- Create: `apps/admin/app/(authed)/logs/features/_components/PlatformFilter.tsx`

Client component that pushes to `/logs/features` with `?platform=` set or removed. UI shows "Any platform" + Web / iOS / Android.

- [ ] **Step 1: Create the directory and file**

Create `apps/admin/app/(authed)/logs/features/_components/PlatformFilter.tsx`:

```tsx
"use client"

import { useId } from "react"
import { useRouter, useSearchParams } from "next/navigation"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Label } from "@/components/ui/label"
import { PLATFORM_FILTER_OPTIONS } from "@/lib/logs/options"

const ANY = "any"

export function PlatformFilter() {
  const id = useId()
  const router = useRouter()
  const params = useSearchParams()
  const current = params.get("platform") ?? ANY

  function onChange(value: string) {
    const next = new URLSearchParams(params)
    if (value === ANY) {
      next.delete("platform")
    } else {
      next.set("platform", value)
    }
    router.push(`/logs/features${next.size ? `?${next.toString()}` : ""}`)
  }

  return (
    <div className="space-y-1 text-sm">
      <Label htmlFor={id} className="text-muted-foreground">
        Platform
      </Label>
      <Select value={current} onValueChange={(v) => onChange(v ?? ANY)}>
        <SelectTrigger id={id} className="w-44">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={ANY}>Any platform</SelectItem>
          {PLATFORM_FILTER_OPTIONS.map((o) => (
            <SelectItem key={o.value} value={o.value}>
              {o.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}
```

- [ ] **Step 2: Type-check**

```bash
cd apps/admin && npx tsc --noEmit
```

Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add apps/admin/app/\(authed\)/logs/features/_components/PlatformFilter.tsx
git commit -m "$(cat <<'EOF'
feat(core): add PlatformFilter client component

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Create the `FeaturesShippedSection` async server component

**Files:**
- Create: `apps/admin/app/(authed)/logs/features/_components/FeaturesShippedSection.tsx`

Async server component. Own query for `species=feature, status=shipped`, applies the same platform filter semantics as the active query (selected platform OR `'all'`).

- [ ] **Step 1: Write the component**

Create `apps/admin/app/(authed)/logs/features/_components/FeaturesShippedSection.tsx`:

```tsx
import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { PlatformFilter as PlatformFilterValue } from "@/lib/logs/options"
import type { LogRow } from "@/lib/logs/types"
import { FeatureSection } from "../../_components/FeatureSection"

export async function FeaturesShippedSection({
  platform,
}: {
  platform: PlatformFilterValue | undefined
}) {
  const supabase = await createServerSupabaseClient()

  let query = supabase
    .from("logs")
    .select("*")
    .eq("species", "feature")
    .eq("status", "shipped")
    .order("updated_at", { ascending: false })

  if (platform) {
    query = query.in("platform", [platform, "all"])
  }

  const { data, error } = await query

  if (error) {
    console.error("[logs/features/shipped] select failed:", error.message)
  }

  const logs: LogRow[] = data ?? []

  return <FeatureSection title="Shipped" logs={logs} emptyLabel="No shipped features." />
}
```

- [ ] **Step 2: Type-check**

```bash
cd apps/admin && npx tsc --noEmit
```

Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add apps/admin/app/\(authed\)/logs/features/_components/FeaturesShippedSection.tsx
git commit -m "$(cat <<'EOF'
feat(core): add FeaturesShippedSection streamed server component

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Create the `/logs/features` page

**Files:**
- Create: `apps/admin/app/(authed)/logs/features/page.tsx`

Server component. Reads `searchParams.platform`, validates with `isPlatformFilter`. One query for active statuses, groups in memory, renders three `FeatureSection`s. Wraps `FeaturesShippedSection` in `<Suspense>` so it streams independently. Page does NOT await the shipped section.

- [ ] **Step 1: Write the page**

Create `apps/admin/app/(authed)/logs/features/page.tsx`:

```tsx
import { Suspense } from "react"
import Link from "next/link"
import { Plus } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { isPlatformFilter } from "@/lib/logs/options"
import type { LogRow, LogStatus } from "@/lib/logs/types"
import { FeatureSection } from "../_components/FeatureSection"
import { FeatureSectionSkeleton } from "../_components/FeatureSectionSkeleton"
import { PlatformFilter } from "./_components/PlatformFilter"
import { FeaturesShippedSection } from "./_components/FeaturesShippedSection"

type SearchParams = Promise<{ platform?: string }>

const ACTIVE_STATUSES = ["in_progress", "planned", "backlog"] as const satisfies readonly LogStatus[]

export default async function FeaturesPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams
  const platform = isPlatformFilter(params.platform) ? params.platform : undefined

  const supabase = await createServerSupabaseClient()

  let query = supabase
    .from("logs")
    .select("*")
    .eq("species", "feature")
    .in("status", ACTIVE_STATUSES)
    .order("updated_at", { ascending: false })

  if (platform) {
    query = query.in("platform", [platform, "all"])
  }

  const { data, error } = await query

  if (error) {
    console.error("[logs/features/active] select failed:", error.message)
  }

  const active: LogRow[] = data ?? []
  const grouped: Record<(typeof ACTIVE_STATUSES)[number], LogRow[]> = {
    in_progress: [],
    planned: [],
    backlog: [],
  }
  for (const log of active) {
    if (log.status === "in_progress" || log.status === "planned" || log.status === "backlog") {
      grouped[log.status].push(log)
    }
  }

  return (
    <section className="space-y-6">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Features</h1>
        <Link href="/logs/new?species=feature" className={buttonVariants()}>
          <Plus className="size-4" aria-hidden />
          New log
        </Link>
      </header>
      <PlatformFilter />
      <div className="space-y-8">
        <FeatureSection
          title="In progress"
          logs={grouped.in_progress}
          emptyLabel="No features in progress."
        />
        <FeatureSection title="Planned" logs={grouped.planned} emptyLabel="No planned features." />
        <FeatureSection title="Backlog" logs={grouped.backlog} emptyLabel="No backlog features." />
        <Suspense fallback={<FeatureSectionSkeleton title="Shipped" />}>
          <FeaturesShippedSection platform={platform} />
        </Suspense>
      </div>
    </section>
  )
}
```

- [ ] **Step 2: Type-check + lint**

```bash
cd apps/admin && npx tsc --noEmit && npm run lint
```

Expected: clean.

- [ ] **Step 3: Smoke-test in the browser**

Start the dev server and visit `/logs/features` (the route exists now even though the sidebar still links to `/logs`). Confirm:
- Three active sections render with counts.
- Shipped section renders (skeleton flicker, then content).
- Setting `?platform=ios` narrows all four sections.

If you don't have an admin dev script handy, the project root convention is `npm run dev --workspace=apps/admin` (or whichever the admin's package.json `dev` script is).

- [ ] **Step 4: Commit**

```bash
git add apps/admin/app/\(authed\)/logs/features/page.tsx
git commit -m "$(cat <<'EOF'
feat(core): add Features view at /logs/features

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Create the `AnnouncementsPublishedSection` async server component

**Files:**
- Create: `apps/admin/app/(authed)/logs/announcements/_components/AnnouncementsPublishedSection.tsx`

Mirrors `FeaturesShippedSection` but for announcements. Own query for `species=announcement, published=true`. No platform filter on announcements per the spec.

- [ ] **Step 1: Write the component**

Create `apps/admin/app/(authed)/logs/announcements/_components/AnnouncementsPublishedSection.tsx`:

```tsx
import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { LogRow } from "@/lib/logs/types"
import { FeatureSection } from "../../_components/FeatureSection"

export async function AnnouncementsPublishedSection() {
  const supabase = await createServerSupabaseClient()

  const { data, error } = await supabase
    .from("logs")
    .select("*")
    .eq("species", "announcement")
    .eq("published", true)
    .order("updated_at", { ascending: false })

  if (error) {
    console.error("[logs/announcements/published] select failed:", error.message)
  }

  const logs: LogRow[] = data ?? []

  return <FeatureSection title="Published" logs={logs} emptyLabel="No published announcements." />
}
```

- [ ] **Step 2: Type-check**

```bash
cd apps/admin && npx tsc --noEmit
```

Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add apps/admin/app/\(authed\)/logs/announcements/_components/AnnouncementsPublishedSection.tsx
git commit -m "$(cat <<'EOF'
feat(core): add AnnouncementsPublishedSection streamed server component

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Create the `/logs/announcements` page

**Files:**
- Create: `apps/admin/app/(authed)/logs/announcements/page.tsx`

Fetches drafts inline, renders Drafts section synchronously, wraps Published in `<Suspense>`. No platform filter. Page does NOT await the published section.

- [ ] **Step 1: Write the page**

Create `apps/admin/app/(authed)/logs/announcements/page.tsx`:

```tsx
import { Suspense } from "react"
import Link from "next/link"
import { Plus } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { LogRow } from "@/lib/logs/types"
import { FeatureSection } from "../_components/FeatureSection"
import { FeatureSectionSkeleton } from "../_components/FeatureSectionSkeleton"
import { AnnouncementsPublishedSection } from "./_components/AnnouncementsPublishedSection"

export default async function AnnouncementsPage() {
  const supabase = await createServerSupabaseClient()

  const { data, error } = await supabase
    .from("logs")
    .select("*")
    .eq("species", "announcement")
    .eq("published", false)
    .order("updated_at", { ascending: false })

  if (error) {
    console.error("[logs/announcements/drafts] select failed:", error.message)
  }

  const drafts: LogRow[] = data ?? []

  return (
    <section className="space-y-6">
      <header className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Announcements</h1>
        <Link href="/logs/new?species=announcement" className={buttonVariants()}>
          <Plus className="size-4" aria-hidden />
          New log
        </Link>
      </header>
      <div className="space-y-8">
        <FeatureSection title="Drafts" logs={drafts} emptyLabel="No drafts." />
        <Suspense fallback={<FeatureSectionSkeleton title="Published" />}>
          <AnnouncementsPublishedSection />
        </Suspense>
      </div>
    </section>
  )
}
```

- [ ] **Step 2: Type-check + lint**

```bash
cd apps/admin && npx tsc --noEmit && npm run lint
```

Expected: clean.

- [ ] **Step 3: Smoke-test**

Visit `/logs/announcements`. Confirm Drafts paints first, Published streams in (skeleton flicker, then content).

- [ ] **Step 4: Commit**

```bash
git add apps/admin/app/\(authed\)/logs/announcements/page.tsx
git commit -m "$(cat <<'EOF'
feat(core): add Announcements view at /logs/announcements

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Make `/logs` redirect to `/logs/features`

**Files:**
- Modify: `apps/admin/app/(authed)/logs/page.tsx`

Replace the existing listing implementation entirely. After this change, `LogsFilters` and `LogsTable` have no callers — they'll be removed in Task 14.

- [ ] **Step 1: Replace the file content**

Overwrite `apps/admin/app/(authed)/logs/page.tsx` with:

```tsx
import { redirect } from "next/navigation"

export default function LogsPage() {
  redirect("/logs/features")
}
```

- [ ] **Step 2: Type-check**

```bash
cd apps/admin && npx tsc --noEmit
```

Expected: clean.

- [ ] **Step 3: Smoke-test**

Visit `/logs`. Confirm a redirect to `/logs/features`.

- [ ] **Step 4: Commit**

```bash
git add apps/admin/app/\(authed\)/logs/page.tsx
git commit -m "$(cat <<'EOF'
feat(core): redirect /logs to /logs/features

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Restructure the sidebar with a non-interactive "Logs" group

**Files:**
- Modify: `apps/admin/components/layout/Sidebar.tsx`

Replace the single Logs item with a `SidebarGroupLabel` "Logs" + two `SidebarMenuItem`s under it (Features, Announcements). The label has no link and no click handler. Active matching uses `pathname.startsWith` so child routes (e.g. `/logs/features?platform=ios`) keep the active state.

- [ ] **Step 1: Rewrite the file**

Overwrite `apps/admin/components/layout/Sidebar.tsx` with:

```tsx
"use client"

import Link from "next/link"
import { usePathname } from "next/navigation"
import { Megaphone, Sparkles } from "lucide-react"
import {
  Sidebar,
  SidebarContent,
  SidebarGroup,
  SidebarGroupContent,
  SidebarGroupLabel,
  SidebarHeader,
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
} from "@/components/ui/sidebar"

const LOG_ITEMS = [
  { href: "/logs/features", label: "Features", icon: Sparkles },
  { href: "/logs/announcements", label: "Announcements", icon: Megaphone },
] as const

export function AppSidebar() {
  const pathname = usePathname()

  return (
    <Sidebar>
      <SidebarHeader>
        <span className="px-2 py-1 text-sm font-semibold">Back-office</span>
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup>
          <SidebarGroupLabel>Logs</SidebarGroupLabel>
          <SidebarGroupContent>
            <SidebarMenu>
              {LOG_ITEMS.map(({ href, label, icon: Icon }) => {
                const active = pathname === href || pathname.startsWith(`${href}/`)
                return (
                  <SidebarMenuItem key={href}>
                    {/* Base UI sidebar uses render prop instead of asChild */}
                    <SidebarMenuButton render={<Link href={href} />} isActive={active}>
                      <Icon aria-hidden />
                      <span>{label}</span>
                    </SidebarMenuButton>
                  </SidebarMenuItem>
                )
              })}
            </SidebarMenu>
          </SidebarGroupContent>
        </SidebarGroup>
      </SidebarContent>
    </Sidebar>
  )
}
```

- [ ] **Step 2: Type-check + lint**

```bash
cd apps/admin && npx tsc --noEmit && npm run lint
```

Expected: clean. The previous `ScrollText` import is gone — verify no stale references remain.

- [ ] **Step 3: Smoke-test**

Confirm in the browser:
- "Logs" appears as a non-clickable group label.
- "Features" and "Announcements" are clickable; the correct one is highlighted on each route.
- Visiting `/logs/features?platform=ios` keeps "Features" highlighted.

- [ ] **Step 4: Commit**

```bash
git add apps/admin/components/layout/Sidebar.tsx
git commit -m "$(cat <<'EOF'
feat(core): split sidebar Logs into Features and Announcements

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Extend `LogForm` to accept an `initialSpecies` prop

**Files:**
- Modify: `apps/admin/app/(authed)/logs/_components/LogForm.tsx`

Add an optional `initialSpecies?: LogSpecies` prop. When `log` is null and `initialSpecies` is provided, seed the species `Select`'s `defaultValue` with it. No behavioural change when `initialSpecies` is omitted.

- [ ] **Step 1: Add the import**

Edit `apps/admin/app/(authed)/logs/_components/LogForm.tsx` line 18 area — change the imports block to include `LogSpecies`:

Replace:
```ts
import type { LogRow } from "@/lib/logs/types"
```

With:
```ts
import type { LogRow, LogSpecies } from "@/lib/logs/types"
```

- [ ] **Step 2: Add the prop and use it**

Replace the function signature:

```tsx
export function LogForm({
  log,
  action,
  submitLabel,
  extraActions,
}: {
  log: LogRow | null
  action: LogFormAction
  submitLabel: string
  extraActions?: React.ReactNode
}) {
```

With:

```tsx
export function LogForm({
  log,
  action,
  submitLabel,
  extraActions,
  initialSpecies,
}: {
  log: LogRow | null
  action: LogFormAction
  submitLabel: string
  extraActions?: React.ReactNode
  initialSpecies?: LogSpecies
}) {
```

- [ ] **Step 3: Use the prop in the species `FieldSelect`**

Replace:

```tsx
        <FieldSelect
          label="Species"
          name="species"
          defaultValue={log?.species}
          options={SPECIES_OPTIONS}
        />
```

With:

```tsx
        <FieldSelect
          label="Species"
          name="species"
          defaultValue={log?.species ?? initialSpecies}
          options={SPECIES_OPTIONS}
        />
```

- [ ] **Step 4: Type-check**

```bash
cd apps/admin && npx tsc --noEmit
```

Expected: clean.

- [ ] **Step 5: Commit**

```bash
git add apps/admin/app/\(authed\)/logs/_components/LogForm.tsx
git commit -m "$(cat <<'EOF'
feat(core): support initialSpecies in LogForm

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 13: Wire `/logs/new` to read `?species=` and prefill the form

**Files:**
- Modify: `apps/admin/app/(authed)/logs/new/page.tsx`

Convert to an async component that reads `searchParams`, validates `species` with `isLogSpecies`, and passes it as `initialSpecies` to `LogForm`. Update the breadcrumb's "Logs" link to point to `/logs/features` (since `/logs` now redirects there anyway, this avoids a double hop).

- [ ] **Step 1: Replace the file**

Overwrite `apps/admin/app/(authed)/logs/new/page.tsx` with:

```tsx
import Link from "next/link"
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"
import { isLogSpecies } from "@/lib/logs/options"
import { LogForm } from "../_components/LogForm"
import { createLog } from "../actions"

type SearchParams = Promise<{ species?: string }>

export default async function NewLogPage({ searchParams }: { searchParams: SearchParams }) {
  const params = await searchParams
  const initialSpecies = isLogSpecies(params.species) ? params.species : undefined
  const breadcrumbHref = initialSpecies === "announcement" ? "/logs/announcements" : "/logs/features"
  const breadcrumbLabel = initialSpecies === "announcement" ? "Announcements" : "Features"

  return (
    <section className="space-y-6">
      <Breadcrumb>
        <BreadcrumbList>
          <BreadcrumbItem>
            {/* Base UI BreadcrumbLink uses render prop instead of asChild */}
            <BreadcrumbLink render={<Link href={breadcrumbHref} />}>{breadcrumbLabel}</BreadcrumbLink>
          </BreadcrumbItem>
          <BreadcrumbSeparator />
          <BreadcrumbItem>
            <BreadcrumbPage>New log</BreadcrumbPage>
          </BreadcrumbItem>
        </BreadcrumbList>
      </Breadcrumb>
      <header>
        <h1 className="text-2xl font-semibold">New log</h1>
        <p className="text-muted-foreground text-sm">Create a changelog entry or announcement.</p>
      </header>
      <LogForm log={null} action={createLog} submitLabel="Save draft" initialSpecies={initialSpecies} />
    </section>
  )
}
```

- [ ] **Step 2: Type-check**

```bash
cd apps/admin && npx tsc --noEmit
```

Expected: clean.

- [ ] **Step 3: Smoke-test**

- Visit `/logs/new?species=feature`. Confirm Species select reads "Feature" and breadcrumb says "Features".
- Visit `/logs/new?species=announcement`. Confirm "Announcement" preselected and breadcrumb says "Announcements".
- Visit `/logs/new` with no param. Confirm species select is empty (placeholder visible) and breadcrumb falls back to "Features".

- [ ] **Step 4: Commit**

```bash
git add apps/admin/app/\(authed\)/logs/new/page.tsx
git commit -m "$(cat <<'EOF'
feat(core): prefill species on /logs/new from query param

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 14: Delete `LogsFilters.tsx` and `LogsTable.tsx`

**Files:**
- Delete: `apps/admin/app/(authed)/logs/_components/LogsFilters.tsx`
- Delete: `apps/admin/app/(authed)/logs/_components/LogsTable.tsx`

Both are unused after Tasks 7, 9, 10. Also check that `[id]/page.tsx`'s breadcrumb back-link still works — it currently points at `/logs`, which now redirects to `/logs/features`; that's fine (one extra hop, acceptable for now).

- [ ] **Step 1: Confirm no remaining imports**

```bash
grep -r "LogsFilters\|LogsTable" /Users/alexis/code/pbbls/apps/admin --include="*.ts" --include="*.tsx"
```

Expected: only the file definitions themselves match. No imports anywhere.

If anything else matches, stop and investigate before deleting.

- [ ] **Step 2: Remove the files**

```bash
rm apps/admin/app/\(authed\)/logs/_components/LogsFilters.tsx apps/admin/app/\(authed\)/logs/_components/LogsTable.tsx
```

- [ ] **Step 3: Type-check + lint**

```bash
cd apps/admin && npx tsc --noEmit && npm run lint
```

Expected: clean.

- [ ] **Step 4: Commit**

```bash
git add -A apps/admin/app/\(authed\)/logs/_components/
git commit -m "$(cat <<'EOF'
chore(core): remove obsolete LogsFilters and LogsTable

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 15: Final verification + open the PR

**Files:**
- None modified. This is the verification + PR-opening task.

- [ ] **Step 1: Run the full build from the repo root**

```bash
npm run build
```

Expected: all workspaces build cleanly. If `apps/admin` fails specifically, fix before proceeding.

- [ ] **Step 2: Run lint from the repo root**

```bash
npm run lint
```

Expected: clean.

- [ ] **Step 3: Manual smoke test (full flow)**

Start the admin dev server and walk through:

1. Sidebar shows non-clickable "Logs" group label with two children below.
2. Visit `/logs` → redirects to `/logs/features`.
3. `/logs/features`:
   - Header reads "Features", "New log" button visible.
   - Platform filter reads "Any platform" by default.
   - Four sections render in order: In progress → Planned → Backlog → Shipped, each with `Title · count`.
   - Shipped shows skeleton briefly, then content.
   - Selecting "iOS" pushes `?platform=ios` and narrows all sections (including any cross-platform `'all'` rows).
4. Click "New log" on Features → `/logs/new?species=feature`. Species select reads "Feature", breadcrumb says "Features".
5. `/logs/announcements`:
   - Header reads "Announcements", "New log" button visible.
   - Drafts paints first, then Published streams in.
   - No platform filter visible.
6. Click "New log" on Announcements → `/logs/new?species=announcement`. Species select reads "Announcement", breadcrumb says "Announcements".
7. Empty case: if no rows in a section, the dashed-border placeholder shows the section-specific message.

If anything fails, fix in a new task and re-verify.

- [ ] **Step 4: Push the branch**

```bash
git push -u origin feat/319-admin-logs-split-views
```

- [ ] **Step 5: Open the PR**

Inherit labels and milestone from issue #319. Per project workflow: ask the user to confirm `feat` species label + `core` scope label + Milestone 26 before opening.

```bash
gh pr create --title "feat(core): split admin Logs into Features and Announcements views" --body "$(cat <<'EOF'
Resolves #319

## Summary

- Two species-scoped views replace the single `/logs` listing: `/logs/features` and `/logs/announcements`.
- Features view: stacked tables grouped by status (In progress · Planned · Backlog · Shipped), platform filter, Shipped streamed via Suspense.
- Announcements view: stacked tables grouped by published-state (Drafts · Published), Published streamed via Suspense.
- Sidebar restructured: non-interactive "Logs" group label with the two children.
- `/logs` redirects to `/logs/features`.
- `/logs/new?species=…` prefills the species field.
- Removed obsolete `LogsFilters.tsx` and `LogsTable.tsx`.

Spec: `docs/superpowers/specs/2026-04-26-admin-logs-split-views-design.md`
Plan: `docs/superpowers/plans/2026-04-26-admin-logs-split-views.md`

## Test plan

- [ ] `/logs` redirects to `/logs/features`
- [ ] Features view renders four sections with counts; Shipped streams in
- [ ] `?platform=ios` narrows all four sections including `'all'` rows
- [ ] Announcements view renders Drafts then streams Published
- [ ] Empty sections show placeholder with `· 0`
- [ ] "New log" prefills species per source view
- [ ] Sidebar shows non-clickable "Logs" group with active state on the right child

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 6: Apply labels + milestone via `gh`**

After the PR opens, attach the species/scope labels and milestone (after user confirmation per project rule):

```bash
gh pr edit <pr-number> --add-label feat --add-label core --milestone "M26"
```

Confirm the PR URL with the user.

---

## Notes for the executing engineer

- **Do not** introduce a generic "section + skeleton" abstraction beyond what's in the plan. Two views, two streamed sections — three usages don't justify further generalisation.
- **Do not** add platform filtering to Announcements. The spec is explicit.
- **Do not** restore a status column on Announcements. The spec drops it intentionally.
- The `FeatureSection` name is slightly imperfect (it's reused by Announcements); leave it. Renaming is a separate concern.
- The Suspense streaming behaviour relies on the page **not** awaiting the streamed component. Keep `<FeaturesShippedSection>` and `<AnnouncementsPublishedSection>` as direct children of `<Suspense>`, never `await`ed at the page level.
- The Arkaik product map (`docs/arkaik/bundle.json`) does **not** currently model the admin back-office (PR #318 didn't either). Do not add admin nodes in this PR.
