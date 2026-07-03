# Admin Domain Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give admins a back-office view to edit each domain's name + description and upload/replace its glyph, and render domain glyphs on the web.

**Architecture:** Reuse the existing `domains.default_glyph_id → glyphs(id)` link (glyph = system-owned row, `user_id = NULL`). Three `is_admin`-gated `security definer` RPCs (list / update text / set glyph in-place) plus one read view drive a new admin route that reuses the admin SVG→adjust→preview pipeline. Web reads the glyph from the view via a module-cached hook and renders it in the three domain surfaces; domain **text stays in the i18n catalogs** (unchanged).

**Tech Stack:** Postgres (Supabase migrations + RPCs), Next.js 16 server components + server actions (admin), Next.js client hooks + SVG (web), TypeScript strict.

**Testing note:** This repo has **no test framework yet (V1)**; the gate is workspace lint/build + manual verification (per `CLAUDE.md` task-size triage). Steps therefore verify with `npm run lint`/`build` and explicit manual checks rather than unit tests. Keep logic test-ready (the SVG pipeline is already pure).

**Issue:** #518

---

## File Structure

**Database — `packages/supabase`**
- Create: `supabase/migrations/20260703000000_admin_domain_management.sql` — view `v_domains_with_glyph` + 3 RPCs + grants.
- Modify (generated): `types/database.ts` — regenerated after the migration.

**Admin — `apps/admin`**
- Create: `lib/domains/types.ts` — `AdminDomain` row type + error-code→copy map.
- Create: `app/(authed)/domains/actions.ts` — `updateDomain`, `setDomainGlyph` server actions.
- Create: `app/(authed)/domains/page.tsx` — server list of domains.
- Create: `app/(authed)/domains/[id]/page.tsx` — server editor loader.
- Create: `app/(authed)/domains/[id]/_components/DomainEditor.tsx` — client editor (text + SVG upload/adjust/preview).
- Modify: `components/layout/Sidebar.tsx` — add "Reference → Domains" nav.

**Web — `apps/web`**
- Create: `lib/data/useDomainGlyphs.ts` — module-cached hook → `Map<slug, DomainGlyph>`.
- Create: `components/record/DomainGlyph.tsx` — presentational `<svg>` + `StrokeRenderer`.
- Modify: `components/record/DomainPicker.tsx` — render glyph per tile.
- Modify: `components/record/DomainPopover.tsx` — render glyph per row.
- Modify: `components/pebble/PebbleDetailTiles.tsx` — render glyph per option.

**Docs**
- Update Arkaik (`docs/arkaik/bundle.json`) — new admin Domains view node.

---

## Task 1: Database migration — view + RPCs

**Files:**
- Create: `packages/supabase/supabase/migrations/20260703000000_admin_domain_management.sql`
- Modify: `packages/supabase/types/database.ts` (regenerated)

- [ ] **Step 1: Write the migration**

Create `packages/supabase/supabase/migrations/20260703000000_admin_domain_management.sql`:

```sql
-- Admin domain management (#518)
-- Adds a read view exposing each domain's glyph, plus is_admin-gated RPCs to
-- edit a domain's name/label and to set/replace its glyph in place.
--
-- Model recap:
--   • domains.default_glyph_id -> glyphs(id); the glyph is SYSTEM-OWNED
--     (glyphs.user_id = NULL, shape_id = NULL — both nullable since the
--     remote-pebble-engine migration).
--   • "Description" is the existing domains.label column (no new column).
--   • Replace-in-place keeps ONE glyph per domain (no orphans) so every
--     consumer (compose-pebble edge fallback, web cache) reflects the change.

-- ============================================================
-- 1. Read view: domains + their glyph (web/iOS consumption)
-- ============================================================
-- security_invoker so the caller's glyphs RLS governs; domain glyphs are
-- system-owned (NULL user_id) and therefore readable. Mirrors v_glyph_market.
create view public.v_domains_with_glyph with (security_invoker = true) as
select
  d.id,
  d.slug,
  d.name,
  d.label,
  d.default_glyph_id,
  g.strokes,
  g.view_box
from public.domains d
left join public.glyphs g on g.id = d.default_glyph_id;

grant select on public.v_domains_with_glyph to anon, authenticated;

-- ============================================================
-- 2. admin_list_domains — rows for the admin editor
-- ============================================================
create or replace function public.admin_list_domains()
returns table (
  id uuid,
  slug text,
  name text,
  label text,
  default_glyph_id uuid,
  strokes jsonb,
  view_box text
)
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  return query
    select d.id, d.slug, d.name, d.label, d.default_glyph_id, g.strokes, g.view_box
    from public.domains d
    left join public.glyphs g on g.id = d.default_glyph_id
    order by d.name;
end;
$$;

-- ============================================================
-- 3. admin_update_domain — edit name + description (label)
-- ============================================================
create or replace function public.admin_update_domain(
  p_domain_id uuid,
  p_name text,
  p_label text
)
returns void
language plpgsql security definer set search_path = public as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if btrim(coalesce(p_name, '')) = '' then
    raise exception 'bad_name';
  end if;
  update public.domains
     set name = btrim(p_name),
         label = btrim(coalesce(p_label, ''))
   where id = p_domain_id;
  if not found then
    raise exception 'not_found';
  end if;
end;
$$;

-- ============================================================
-- 4. admin_set_domain_glyph — set/replace the domain glyph in place
-- ============================================================
create or replace function public.admin_set_domain_glyph(
  p_domain_id uuid,
  p_strokes jsonb,
  p_view_box text
)
returns uuid
language plpgsql security definer set search_path = public as $$
declare
  v_glyph_id uuid;
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'not_admin' using errcode = '42501';
  end if;
  if p_strokes is null or jsonb_array_length(p_strokes) = 0 then
    raise exception 'empty_glyph';
  end if;

  select default_glyph_id into v_glyph_id
  from public.domains where id = p_domain_id;
  if not found then
    raise exception 'not_found';
  end if;

  if v_glyph_id is null then
    -- First glyph for this domain: system-owned (NULL user_id), shapeless.
    insert into public.glyphs (user_id, shape_id, name, strokes, view_box)
    values (null, null, null, p_strokes, p_view_box)
    returning id into v_glyph_id;

    update public.domains set default_glyph_id = v_glyph_id where id = p_domain_id;
  else
    -- Replace in place: same glyph_id, so FKs and caches keep pointing at it.
    update public.glyphs
       set strokes = p_strokes,
           view_box = p_view_box,
           updated_at = now()
     where id = v_glyph_id;
  end if;

  return v_glyph_id;
end;
$$;

-- ============================================================
-- 5. Grants: authenticated only; the is_admin guard does the gating.
-- ============================================================
revoke all on function public.admin_list_domains()                      from public, anon;
revoke all on function public.admin_update_domain(uuid, text, text)     from public, anon;
revoke all on function public.admin_set_domain_glyph(uuid, jsonb, text) from public, anon;
grant execute on function public.admin_list_domains()                      to authenticated;
grant execute on function public.admin_update_domain(uuid, text, text)     to authenticated;
grant execute on function public.admin_set_domain_glyph(uuid, jsonb, text) to authenticated;
```

> If `20260703000000` collides with an existing filename, bump to the next free
> `20260703NNNNNN` timestamp. Keep the SQL identical.

- [ ] **Step 2: Apply the migration to the remote DB**

This repo is remote-first (no local Docker — see project memory). Push with the
project's usual method, e.g.:

Run: `npx supabase db push` (from `packages/supabase`, against the linked remote)
Expected: the migration applies with no error; `v_domains_with_glyph` and the 3
functions are created.

If you cannot push (missing remote credentials), stop and hand back to the user —
type generation in Step 3 reads the remote schema and will otherwise not reflect
these objects.

- [ ] **Step 3: Regenerate + commit types**

Run:
```bash
npm run db:types --workspace=packages/supabase
git add packages/supabase/types/database.ts
```
Expected: `database.ts` now contains `v_domains_with_glyph` under `Views` and
`admin_list_domains` / `admin_update_domain` / `admin_set_domain_glyph` under
`Functions`.

Verify quickly:
```bash
grep -c "admin_set_domain_glyph\|v_domains_with_glyph" packages/supabase/types/database.ts
```
Expected: `>= 2`.

- [ ] **Step 4: Commit**

```bash
git add packages/supabase/supabase/migrations/20260703000000_admin_domain_management.sql packages/supabase/types/database.ts
git commit -m "feat(db): admin domain management RPCs + v_domains_with_glyph view"
```

---

## Task 2: Admin — types + server actions

**Files:**
- Create: `apps/admin/lib/domains/types.ts`
- Create: `apps/admin/app/(authed)/domains/actions.ts`

- [ ] **Step 1: Create the domain types**

Create `apps/admin/lib/domains/types.ts`:

```ts
// apps/admin/lib/domains/types.ts
import type { GlyphStroke } from "@/lib/pebblestore/types"

/** A row from the admin_list_domains RPC. */
export type AdminDomain = {
  id: string
  slug: string
  name: string
  label: string
  default_glyph_id: string | null
  strokes: GlyphStroke[] | null
  view_box: string | null
}

/** Map the SQL error contract to English admin copy. */
export function domainErrorMessage(code: string): string {
  switch (code) {
    case "not_admin":
      return "You are not authorized to perform this action."
    case "not_found":
      return "That domain no longer exists."
    case "bad_name":
      return "A name is required."
    case "empty_glyph":
      return "This glyph has no usable strokes to save."
    default:
      return "Something went wrong. Check the server console for details."
  }
}
```

- [ ] **Step 2: Create the server actions**

Create `apps/admin/app/(authed)/domains/actions.ts`:

```ts
"use server"

import { revalidatePath } from "next/cache"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { domainErrorMessage } from "@/lib/domains/types"
import type { GlyphStroke } from "@/lib/pebblestore/types"

export type ActionResult = { error: string } | undefined

const LIST_PATH = "/domains"

export async function updateDomain(input: {
  id: string
  name: string
  label: string
}): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("admin_update_domain", {
    p_domain_id: input.id,
    p_name: input.name,
    p_label: input.label,
  })
  if (error) {
    console.error("[domains] updateDomain failed:", error.message)
    return { error: domainErrorMessage(error.message) }
  }
  revalidatePath(LIST_PATH)
  revalidatePath(`${LIST_PATH}/${input.id}`)
  return undefined
}

export async function setDomainGlyph(input: {
  id: string
  strokes: GlyphStroke[]
  viewBox: string
}): Promise<ActionResult> {
  const supabase = await createServerSupabaseClient()
  const { error } = await supabase.rpc("admin_set_domain_glyph", {
    p_domain_id: input.id,
    p_strokes: input.strokes as unknown as never, // jsonb
    p_view_box: input.viewBox,
  })
  if (error) {
    console.error("[domains] setDomainGlyph failed:", error.message)
    return { error: domainErrorMessage(error.message) }
  }
  revalidatePath(LIST_PATH)
  revalidatePath(`${LIST_PATH}/${input.id}`)
  return undefined
}
```

- [ ] **Step 3: Lint the workspace**

Run: `npm run lint --workspace=apps/admin`
Expected: no errors in the new files. (`admin_update_domain` / `admin_set_domain_glyph`
must exist in `database.ts` from Task 1 or the `.rpc()` names will type-error.)

- [ ] **Step 4: Commit**

```bash
git add apps/admin/lib/domains/types.ts "apps/admin/app/(authed)/domains/actions.ts"
git commit -m "feat(admin): domain management types + server actions"
```

---

## Task 3: Admin — list page

**Files:**
- Create: `apps/admin/app/(authed)/domains/page.tsx`

- [ ] **Step 1: Create the list page**

Create `apps/admin/app/(authed)/domains/page.tsx`:

```tsx
import Link from "next/link"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import { GlyphPreview } from "@/components/pebblestore/GlyphPreview"
import type { AdminDomain } from "@/lib/domains/types"

export default async function DomainsPage() {
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("admin_list_domains")

  if (error) {
    console.error("[domains] admin_list_domains failed:", error.message)
  }
  const domains: AdminDomain[] = (data ?? []) as AdminDomain[]

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold">Domains</h1>
        <p className="text-sm text-muted-foreground">
          Edit a domain&rsquo;s name, description, and glyph. Names/descriptions are
          also localized in the app catalogs — edits here update the fallback.
        </p>
      </header>

      {error ? (
        <p className="text-sm text-destructive">
          Could not load domains. Check the server console.
        </p>
      ) : (
        <ul className="grid gap-3 sm:grid-cols-2">
          {domains.map((d) => (
            <li key={d.id}>
              <Link
                href={`/domains/${d.id}`}
                className="flex items-center gap-4 rounded-lg border p-4 transition-colors hover:bg-muted/50"
              >
                <span className="grid size-14 shrink-0 place-items-center rounded-md border bg-card text-foreground">
                  {d.strokes && d.strokes.length > 0 && d.view_box ? (
                    <GlyphPreview
                      strokes={d.strokes}
                      viewBox={d.view_box}
                      className="size-10"
                    />
                  ) : (
                    <span className="text-xs text-muted-foreground">—</span>
                  )}
                </span>
                <span className="min-w-0">
                  <span className="block font-medium">{d.name}</span>
                  <span className="block truncate text-sm text-muted-foreground">
                    {d.label || "No description"}
                  </span>
                  <span className="block text-xs text-muted-foreground/70">{d.slug}</span>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
```

- [ ] **Step 2: Lint the workspace**

Run: `npm run lint --workspace=apps/admin`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add "apps/admin/app/(authed)/domains/page.tsx"
git commit -m "feat(admin): domains list page"
```

---

## Task 4: Admin — editor page + component

**Files:**
- Create: `apps/admin/app/(authed)/domains/[id]/page.tsx`
- Create: `apps/admin/app/(authed)/domains/[id]/_components/DomainEditor.tsx`

- [ ] **Step 1: Create the editor loader page**

Create `apps/admin/app/(authed)/domains/[id]/page.tsx`:

```tsx
import Link from "next/link"
import { notFound } from "next/navigation"
import { ChevronLeft } from "lucide-react"
import { createServerSupabaseClient } from "@/lib/supabase/server"
import type { AdminDomain } from "@/lib/domains/types"
import { DomainEditor } from "./_components/DomainEditor"

export default async function DomainEditorPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  const supabase = await createServerSupabaseClient()
  const { data, error } = await supabase.rpc("admin_list_domains")

  if (error) {
    console.error("[domains/[id]] admin_list_domains failed:", error.message)
  }
  const domain = ((data ?? []) as AdminDomain[]).find((d) => d.id === id)
  if (!domain) notFound()

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <Link
        href="/domains"
        className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground"
      >
        <ChevronLeft className="size-4" aria-hidden />
        All domains
      </Link>
      <DomainEditor domain={domain} />
    </div>
  )
}
```

- [ ] **Step 2: Create the editor component**

Create `apps/admin/app/(authed)/domains/[id]/_components/DomainEditor.tsx`:

```tsx
"use client"

import { useMemo, useState, useTransition } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Switch } from "@/components/ui/switch"
import { GlyphPreview } from "@/components/pebblestore/GlyphPreview"
import {
  GLYPH_CANVAS_VIEWBOX,
  IDENTITY_ADJUST,
  type Adjust,
  type GlyphStroke,
} from "@/lib/pebblestore/types"
import { svgToStrokes } from "@/lib/pebblestore/svg-to-strokes"
import { bakeAdjust } from "@/lib/pebblestore/transform-path"
import type { AdminDomain } from "@/lib/domains/types"
import { setDomainGlyph, updateDomain } from "../../actions"

export function DomainEditor({ domain }: { domain: AdminDomain }) {
  // Text fields
  const [name, setName] = useState(domain.name)
  const [label, setLabel] = useState(domain.label)

  // Glyph editing: null strokes = keep the current glyph (no new SVG staged).
  const [strokes, setStrokes] = useState<GlyphStroke[] | null>(null)
  const [viewBox, setViewBox] = useState(domain.view_box ?? GLYPH_CANVAS_VIEWBOX)
  const [skipped, setSkipped] = useState<string[]>([])
  const [parseError, setParseError] = useState<string | null>(null)
  const [adjust, setAdjust] = useState<Adjust>(IDENTITY_ADJUST)

  const [formError, setFormError] = useState<string | null>(null)
  const [pending, startTransition] = useTransition()

  const hasNewGlyph = strokes !== null && strokes.length > 0

  // Preview: staged (adjusted) strokes if a new SVG is loaded, else the current
  // domain glyph as stored.
  const previewStrokes = useMemo(() => {
    if (hasNewGlyph) return bakeAdjust(strokes as GlyphStroke[], viewBox, adjust)
    return domain.strokes ?? []
  }, [hasNewGlyph, strokes, viewBox, adjust, domain.strokes])

  const previewViewBox = hasNewGlyph ? viewBox : domain.view_box ?? GLYPH_CANVAS_VIEWBOX

  const onFile = async (file: File | undefined) => {
    if (!file) return
    setParseError(null)
    try {
      const text = await file.text()
      const r = svgToStrokes(text)
      if (r.strokes.length === 0) {
        setParseError("No supported strokes found in this SVG (see the supported subset).")
        setStrokes(null)
        setSkipped(r.skipped)
        return
      }
      setStrokes(r.strokes)
      setViewBox(r.viewBox)
      setSkipped(r.skipped)
      setAdjust(IDENTITY_ADJUST)
    } catch (e) {
      setParseError(e instanceof Error ? e.message : String(e))
      setStrokes(null)
    }
  }

  const onSave = () => {
    setFormError(null)
    startTransition(async () => {
      const textRes = await updateDomain({ id: domain.id, name: name.trim(), label: label.trim() })
      if (textRes?.error) {
        setFormError(textRes.error)
        return
      }
      if (hasNewGlyph) {
        const baked = bakeAdjust(strokes as GlyphStroke[], viewBox, adjust)
        const glyphRes = await setDomainGlyph({ id: domain.id, strokes: baked, viewBox })
        if (glyphRes?.error) {
          setFormError(glyphRes.error)
          return
        }
        // Clear the staged glyph so the preview now reflects the saved state.
        setStrokes(null)
      }
      toast.success("Domain saved")
    })
  }

  const canSave = name.trim().length > 0 && !pending

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-lg font-semibold">{domain.name}</h1>
        <p className="text-xs text-muted-foreground">{domain.slug}</p>
      </div>

      <GlyphPreview
        strokes={previewStrokes}
        viewBox={previewViewBox}
        className="mx-auto aspect-square w-40 rounded-md border bg-card text-foreground"
      />

      <div className="grid gap-4 sm:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="domain-name">Name</Label>
          <Input id="domain-name" value={name} onChange={(e) => setName(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="domain-label">Description</Label>
          <Input id="domain-label" value={label} onChange={(e) => setLabel(e.target.value)} />
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="svg-file">Replace glyph (SVG)</Label>
        <Input
          id="svg-file"
          type="file"
          accept=".svg,image/svg+xml"
          onChange={(e) => onFile(e.target.files?.[0])}
        />
        {parseError ? <p className="text-sm text-destructive">{parseError}</p> : null}
        {skipped.length > 0 ? (
          <p className="text-xs text-muted-foreground">
            Skipped (unsupported): {skipped.join(", ")}. Glyphs are stroke-only — filled
            shapes import as outlines.
          </p>
        ) : null}
      </div>

      {hasNewGlyph ? (
        <fieldset className="space-y-3 rounded-lg border p-4">
          <legend className="px-1 text-sm font-medium">Adjust</legend>
          <div className="space-y-1">
            <Label htmlFor="adjust-scale">Scale: {adjust.scale.toFixed(2)}</Label>
            <input
              id="adjust-scale"
              type="range"
              min={0.3}
              max={1.5}
              step={0.05}
              value={adjust.scale}
              onChange={(e) => setAdjust((a) => ({ ...a, scale: Number(e.target.value) }))}
              className="w-full"
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1">
              <Label htmlFor="adjust-x">Offset X: {adjust.offsetX}</Label>
              <input
                id="adjust-x"
                type="range"
                min={-50}
                max={50}
                step={1}
                value={adjust.offsetX}
                onChange={(e) => setAdjust((a) => ({ ...a, offsetX: Number(e.target.value) }))}
                className="w-full"
              />
            </div>
            <div className="space-y-1">
              <Label htmlFor="adjust-y">Offset Y: {adjust.offsetY}</Label>
              <input
                id="adjust-y"
                type="range"
                min={-50}
                max={50}
                step={1}
                value={adjust.offsetY}
                onChange={(e) => setAdjust((a) => ({ ...a, offsetY: Number(e.target.value) }))}
                className="w-full"
              />
            </div>
          </div>
          <div className="flex gap-6">
            <label className="flex items-center gap-2 text-sm">
              <Switch
                checked={adjust.flipH}
                onCheckedChange={(v) => setAdjust((a) => ({ ...a, flipH: v }))}
              />
              Flip H
            </label>
            <label className="flex items-center gap-2 text-sm">
              <Switch
                checked={adjust.flipV}
                onCheckedChange={(v) => setAdjust((a) => ({ ...a, flipV: v }))}
              />
              Flip V
            </label>
          </div>
        </fieldset>
      ) : null}

      {formError ? <p className="text-sm text-destructive">{formError}</p> : null}
      <Button disabled={!canSave} onClick={onSave}>
        {pending ? "Saving…" : "Save"}
      </Button>
    </div>
  )
}
```

- [ ] **Step 3: Lint the workspace**

Run: `npm run lint --workspace=apps/admin`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add "apps/admin/app/(authed)/domains/[id]"
git commit -m "feat(admin): domain editor (name, description, glyph upload)"
```

---

## Task 5: Admin — sidebar navigation

**Files:**
- Modify: `apps/admin/components/layout/Sidebar.tsx`

- [ ] **Step 1: Add a Reference group with a Domains item**

In `apps/admin/components/layout/Sidebar.tsx`:

1. Add `Shapes` to the lucide import:
```tsx
import { BarChart3, Megaphone, Shapes, Sparkles, Store } from "lucide-react"
```

2. Add a nav-items constant next to the others:
```tsx
const REFERENCE_ITEMS = [
  { href: "/domains", label: "Domains", icon: Shapes },
] as const
```

3. Add a `SidebarGroup` (place it directly after the Pebblestore group, before Logs):
```tsx
<SidebarGroup>
  <SidebarGroupLabel>Reference</SidebarGroupLabel>
  <SidebarGroupContent>
    <SidebarMenu>
      {REFERENCE_ITEMS.map(({ href, label, icon: Icon }) => {
        const active = pathname === href || pathname.startsWith(`${href}/`)
        return (
          <SidebarMenuItem key={href}>
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
```

- [ ] **Step 2: Lint the workspace**

Run: `npm run lint --workspace=apps/admin`
Expected: no errors.

- [ ] **Step 3: Manual verification (admin end-to-end)**

Start the admin app and sign in as an admin user.
- Navigate to **Reference → Domains**: the 18 (+ any legacy) domains list, each with
  a glyph thumbnail or `—` placeholder.
- Open a domain, edit the **Name** and **Description**, click **Save** → toast
  "Domain saved". Reload → the values persist.
- Upload an SVG, adjust it, **Save** → the preview updates to the saved glyph; reload
  the list → the thumbnail shows the new glyph.
- Confirm replace-in-place: the domain's `default_glyph_id` is unchanged after a second
  glyph upload (query the DB or re-open — same glyph id, new drawing).

- [ ] **Step 4: Commit**

```bash
git add apps/admin/components/layout/Sidebar.tsx
git commit -m "feat(admin): add Domains to the back-office sidebar"
```

---

## Task 6: Web — domain glyph hook

**Files:**
- Create: `apps/web/lib/data/useDomainGlyphs.ts`

- [ ] **Step 1: Create the hook**

Create `apps/web/lib/data/useDomainGlyphs.ts` (mirrors `useEmotionsWithPalette`):

```ts
"use client"

import { useEffect, useState } from "react"
import { createClient } from "@/lib/supabase/client"
import { withTimeout } from "@/lib/utils/with-timeout"
import type { MarkStroke } from "@/lib/types"

/** A domain's glyph: strokes + its square viewBox. Keyed by domain slug. */
export type DomainGlyph = { strokes: MarkStroke[]; viewBox: string }

type Row = {
  slug: string | null
  strokes: MarkStroke[] | null
  view_box: string | null
}

// Module-level cache — reference data is read-only and user-agnostic. Mirrors
// useEmotionsWithPalette.
let cached: Map<string, DomainGlyph> | null = null
let inflight: Promise<Map<string, DomainGlyph>> | null = null

async function fetchGlyphs(): Promise<Map<string, DomainGlyph>> {
  const supabase = createClient()
  const { data, error } = await withTimeout(
    supabase.from("v_domains_with_glyph").select("slug, strokes, view_box"),
    8000,
    "fetch v_domains_with_glyph",
  )
  if (error) throw new Error(error.message)

  const map = new Map<string, DomainGlyph>()
  for (const r of (data ?? []) as Row[]) {
    // Domains without a glyph (LEFT JOIN → null) are skipped; the surfaces fall
    // back to text-only, matching today's behavior.
    if (r.slug && r.strokes && r.strokes.length > 0 && r.view_box) {
      map.set(r.slug, { strokes: r.strokes, viewBox: r.view_box })
    }
  }
  return map
}

/**
 * Returns a `slug → DomainGlyph` map, or null until loaded. Failures resolve to
 * an empty map (text-only fallback) — a missing glyph is never fatal.
 */
export function useDomainGlyphs(): Map<string, DomainGlyph> | null {
  const [glyphs, setGlyphs] = useState<Map<string, DomainGlyph> | null>(cached)

  useEffect(() => {
    if (glyphs) return
    if (!inflight) {
      inflight = fetchGlyphs()
        .then((m) => {
          cached = m
          return m
        })
        .catch((e) => {
          console.warn("[useDomainGlyphs] fetch failed:", e)
          cached = new Map()
          return cached
        })
        .finally(() => {
          inflight = null
        })
    }
    let active = true
    inflight.then((m) => {
      if (active) setGlyphs(m)
    })
    return () => {
      active = false
    }
  }, [glyphs])

  return glyphs
}
```

- [ ] **Step 2: Lint the workspace**

Run: `npm run lint --workspace=apps/web`
Expected: no errors. (`v_domains_with_glyph` must be in `database.ts` from Task 1.)

- [ ] **Step 3: Commit**

```bash
git add apps/web/lib/data/useDomainGlyphs.ts
git commit -m "feat(web): useDomainGlyphs hook (reads v_domains_with_glyph)"
```

---

## Task 7: Web — DomainGlyph component

**Files:**
- Create: `apps/web/components/record/DomainGlyph.tsx`

- [ ] **Step 1: Create the presentational component**

Create `apps/web/components/record/DomainGlyph.tsx`:

```tsx
import type { MarkStroke } from "@/lib/types"
import { StrokeRenderer } from "@/components/carve/StrokeRenderer"

type DomainGlyphProps = {
  strokes: MarkStroke[]
  viewBox: string
  className?: string
}

/**
 * Renders a domain's glyph as plain strokes in its square viewBox — same model
 * as the glyphs GlyphPreview, but takes raw strokes (the domain hook returns
 * strokes + viewBox, not a full Mark).
 */
export function DomainGlyph({ strokes, viewBox, className }: DomainGlyphProps) {
  return (
    <svg viewBox={viewBox} className={className} aria-hidden="true">
      <StrokeRenderer strokes={strokes} />
    </svg>
  )
}
```

- [ ] **Step 2: Lint the workspace**

Run: `npm run lint --workspace=apps/web`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/record/DomainGlyph.tsx
git commit -m "feat(web): DomainGlyph presentational component"
```

---

## Task 8: Web — render glyphs in DomainPicker

**Files:**
- Modify: `apps/web/components/record/DomainPicker.tsx`

- [ ] **Step 1: Wire the hook + glyph into the tiles**

Edit `apps/web/components/record/DomainPicker.tsx`:

1. Add imports (below the existing ones):
```tsx
import { useDomainGlyphs, type DomainGlyph as DomainGlyphData } from "@/lib/data/useDomainGlyphs"
import { DomainGlyph } from "@/components/record/DomainGlyph"
```

2. Change `DomainTile` to accept an optional glyph and render it before the text.
Replace the `DomainTile` signature + the `<span className="font-medium">{name}</span>`
opening so the tile becomes:
```tsx
function DomainTile({ domain, glyph, selected, onToggle }: {
  domain: Domain
  glyph?: DomainGlyphData
  selected: boolean
  onToggle: () => void
}) {
  const { name, label } = useDomainLocalized(domain)
  return (
    <li>
      <button
        type="button"
        aria-pressed={selected}
        onClick={onToggle}
        className={cn(
          "flex w-full items-center gap-3 rounded-lg px-3 py-2 text-sm transition-all duration-100 active:scale-[0.97] outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
          selected
            ? "bg-primary/10 text-primary ring-2 ring-primary"
            : "bg-muted/50 hover:bg-muted",
        )}
      >
        {glyph ? (
          <DomainGlyph
            strokes={glyph.strokes}
            viewBox={glyph.viewBox}
            className="size-8 shrink-0"
          />
        ) : null}
        <span className="flex min-w-0 flex-col items-start">
          <span className="font-medium">{name}</span>
          <span
            className={cn(
              "text-xs",
              selected ? "text-primary/70" : "text-muted-foreground",
            )}
          >
            {label}
          </span>
        </span>
      </button>
    </li>
  )
}
```

3. In `DomainPicker`, read the hook and pass the glyph by slug:
```tsx
export function DomainPicker({ value, onChange }: DomainPickerProps) {
  const t = useTranslations("record.domain")
  const glyphs = useDomainGlyphs()
  // ...unchanged toggle...
```
and in the `.map`:
```tsx
{DOMAINS.map((domain) => (
  <DomainTile
    key={domain.id}
    domain={domain}
    glyph={glyphs?.get(domain.slug)}
    selected={value.includes(domain.id)}
    onToggle={() => toggle(domain.id)}
  />
))}
```

- [ ] **Step 2: Lint the workspace**

Run: `npm run lint --workspace=apps/web`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/record/DomainPicker.tsx
git commit -m "feat(web): show domain glyph in DomainPicker"
```

---

## Task 9: Web — render glyphs in DomainPopover

**Files:**
- Modify: `apps/web/components/record/DomainPopover.tsx`

- [ ] **Step 1: Read the file to confirm the row markup**

Run: `cat apps/web/components/record/DomainPopover.tsx`
Note the per-domain row component (it uses `useDomainLocalized` and maps `DOMAINS`),
mirroring `DomainPicker`.

- [ ] **Step 2: Wire the hook + glyph**

Apply the same pattern as Task 8:
1. Add imports:
```tsx
import { useDomainGlyphs, type DomainGlyph as DomainGlyphData } from "@/lib/data/useDomainGlyphs"
import { DomainGlyph } from "@/components/record/DomainGlyph"
```
2. Call `const glyphs = useDomainGlyphs()` in the component that maps `DOMAINS`.
3. Pass `glyph={glyphs?.get(domain.slug)}` into each row, and in the row render, before
the name text:
```tsx
{glyph ? (
  <DomainGlyph strokes={glyph.strokes} viewBox={glyph.viewBox} className="size-6 shrink-0" />
) : null}
```
Add the `glyph?: DomainGlyphData` prop to the row component's props type. Keep the
existing layout classes; only insert the glyph as a leading element and wrap the text in
a flex container if needed so the glyph sits to its left.

- [ ] **Step 3: Lint the workspace**

Run: `npm run lint --workspace=apps/web`
Expected: no errors.

- [ ] **Step 4: Commit**

```bash
git add apps/web/components/record/DomainPopover.tsx
git commit -m "feat(web): show domain glyph in DomainPopover"
```

---

## Task 10: Web — render glyphs in PebbleDetailTiles

**Files:**
- Modify: `apps/web/components/pebble/PebbleDetailTiles.tsx`

- [ ] **Step 1: Wire the hook + glyph into `DomainOption`**

Edit `apps/web/components/pebble/PebbleDetailTiles.tsx`:

1. Add imports:
```tsx
import { useDomainGlyphs, type DomainGlyph as DomainGlyphData } from "@/lib/data/useDomainGlyphs"
import { DomainGlyph } from "@/components/record/DomainGlyph"
```

2. Give `DomainOption` an optional glyph and render it inside the row:
```tsx
function DomainOption({
  domain,
  glyph,
  selected,
  onSelect,
}: {
  domain: (typeof DOMAINS)[number]
  glyph?: DomainGlyphData
  selected: boolean
  onSelect: () => void
}) {
  const { name, label } = useDomainLocalized(domain)
  return (
    <SelectableItem selected={selected} onSelect={onSelect}>
      <span className="flex items-center gap-2">
        {glyph ? (
          <DomainGlyph strokes={glyph.strokes} viewBox={glyph.viewBox} className="size-6 shrink-0" />
        ) : null}
        <span className="flex flex-col items-start">
          <span>{name}</span>
          <span className="text-xs text-muted-foreground">{label}</span>
        </span>
      </span>
    </SelectableItem>
  )
}
```

3. In `DomainTile` (the exported one in this file), read the hook and pass the glyph in
the `DOMAINS.map` that renders `DomainOption`:
```tsx
const domainGlyphs = useDomainGlyphs()
// ...
{DOMAINS.map((domain) => (
  <DomainOption
    key={domain.id}
    domain={domain}
    glyph={domainGlyphs?.get(domain.slug)}
    selected={value.includes(domain.id)}
    onSelect={() => toggle(domain.id)}
  />
))}
```

- [ ] **Step 2: Lint + build the workspace**

Run: `npm run lint --workspace=apps/web`
Then: `npm run build --workspace=apps/web`
Expected: both green. (Build catches any RSC/client-boundary or type issue across the
three modified surfaces.)

- [ ] **Step 3: Manual verification (web)**

Run the web app, open the record flow and a pebble detail:
- Domain picker, domain popover, and the pebble-detail domain tile each show the glyph
  beside the domain name (for domains that have one).
- A domain with no glyph still renders text-only (no layout break).
- Edit a domain's glyph in the admin app, hard-refresh web → the new glyph appears.

- [ ] **Step 4: Commit**

```bash
git add apps/web/components/pebble/PebbleDetailTiles.tsx
git commit -m "feat(web): show domain glyph in PebbleDetailTiles"
```

---

## Task 11: Docs — Arkaik map

**Files:**
- Modify: `docs/arkaik/bundle.json`

- [ ] **Step 1: Update the Arkaik map**

Use the `arkaik` skill to add a new view node for the admin **Domains** screen
(`apps/admin/app/(authed)/domains`), including the editor sub-route, under the admin
app's back-office area. Link it to the `domains` data model. Follow the skill's schema
and validation.

- [ ] **Step 2: Commit**

```bash
git add docs/arkaik/bundle.json
git commit -m "docs(arkaik): add admin Domains view node"
```

---

## Final verification & PR

- [ ] **Step 1: Full lint/build for touched workspaces**

Run:
```bash
npm run lint --workspace=apps/admin
npm run lint --workspace=apps/web
npm run build --workspace=apps/web
```
Expected: all green.

- [ ] **Step 2: Open the PR**

- Branch: `feat/518-admin-domain-management` (create before the first commit if not
  already on it).
- Title: `feat(admin): domain management (name, description, glyph)`.
- Body starts with `Resolves #518`; list key files + implementation notes.
- Labels/milestone: inherit from #518 (`feat`, `db`, `ui`, `core`, `facility`; milestone
  M35) — confirm with the user.
- **Lab Note (EN/FR):** user-facing (web now shows domain glyphs) → include a short
  bilingual blurb.

---

## Self-Review (completed by plan author)

- **Spec coverage:** view + 3 RPCs (Task 1) ✓ · admin list/editor/nav (Tasks 3–5) ✓ ·
  glyph replace-in-place (Task 1 §4) ✓ · description = `label` (Tasks 1,4) ✓ · edit-only,
  no add/delete (no such UI) ✓ · web glyph consumption in 3 surfaces (Tasks 6–10) ✓ ·
  text stays in catalogs (no i18n edits) ✓ · legacy Greek rows listed (list renders all
  rows) ✓ · iOS out of scope (untouched) ✓ · Arkaik + Lab Note (Task 11, PR) ✓.
- **Placeholders:** none — every code step is complete; Task 9's edit references the
  file's existing row component, which Step 1 reads first.
- **Type consistency:** `AdminDomain`, `DomainGlyph`, `GlyphStroke`/`MarkStroke`, and the
  RPC names (`admin_list_domains`, `admin_update_domain`, `admin_set_domain_glyph`) match
  across DB, admin, and web tasks. `strokes` is nullable at every read boundary and guarded
  before use.
