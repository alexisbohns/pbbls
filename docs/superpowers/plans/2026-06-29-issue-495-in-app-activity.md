# In-App Activity Primitive (Opal-style karma credit) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a non-invasive in-app activity primitive to `apps/web` (Sonner-based) and wire its first consumer: a glanceable "+N karma" pill that fires when a pebble action credits karma.

**Architecture:** Copy admin's themed Sonner `Toaster` into `apps/web`, mount it bottom-center above the nav bar. A small `KarmaActivityPill` renders inside `toast.custom()`; a plain module function `notifyKarma(amount, reason)` is the feature-agnostic API. The trigger is explicit: the pebble mutation hooks diff `store.karma` before/after the store reload that already happens, and fire on a positive delta. No backend change, no realtime.

**Tech Stack:** Next.js 16 (App Router), React 19, TypeScript strict, Tailwind 4, Sonner 2, next-intl, next-themes, Lucide.

> **Testing note:** `apps/web` has no test runner in V1 (consistent with sub-project A). Verification per task is **workspace lint** + (final) **`next build`** + a **manual pass**. The primitive's only logic — the `amount <= 0` guard and the reason→label mapping — is trivial and verified by the manual pass. Steps below use implement → lint → commit, with a dedicated final verification task.

> **Spec:** `docs/superpowers/specs/2026-06-29-issue-495-in-app-activity-design.md`. Read it if a decision here is unclear.

---

## File Structure

| File | Responsibility |
|------|----------------|
| `apps/web/components/ui/sonner.tsx` (create) | Themed `Toaster` wrapper, bottom-center, offset to clear the nav bar. Drop-in copy of admin's, retuned for web. |
| `apps/web/app/layout.tsx` (modify) | Mount `<Toaster />` once inside `ThemeProvider`. |
| `apps/web/lib/i18n/messages/en.json` + `fr.json` (modify) | `activity` namespace: visible amount, screen-reader sentence, tap label. Reuses existing `wallet.reason.*`. |
| `apps/web/components/activity/KarmaActivityPill.tsx` (create) | The compact dark capsule rendered inside the custom toast; tappable → `/wallet`; a11y + reduced-motion. |
| `apps/web/lib/activity/karma-activity.tsx` (create) | `notifyKarma(amount, reason)` — the imperative primitive over `toast.custom()`. |
| `apps/web/lib/data/usePebbles.ts` (modify) | Store-diff trigger in `addPebble` / `updatePebble`; fire `notifyKarma` on positive delta. |

---

## Task 1: Add Sonner + themed Toaster wrapper, mounted bottom-center

**Files:**
- Modify: `apps/web/package.json` (add `sonner` dep)
- Create: `apps/web/components/ui/sonner.tsx`
- Modify: `apps/web/app/layout.tsx`
- Reference: `apps/admin/components/ui/sonner.tsx` (the wrapper being adapted)

- [ ] **Step 1: Add the Sonner dependency**

Run (from repo root):
```bash
npm install sonner@^2.0.7 --workspace=apps/web
```
Expected: `apps/web/package.json` gains `"sonner": "^2.0.7"` under `dependencies`, lockfile updated.

- [ ] **Step 2: Create the themed Toaster wrapper**

Create `apps/web/components/ui/sonner.tsx`. This is admin's wrapper with three web-specific changes: `position="bottom-center"`, a `mobileOffset` that clears the mobile `BottomNav` (which is `fixed bottom-0`, ~64px tall plus `--safe-area-bottom`), and the same CSS-variable theming.

```tsx
"use client"

import { useTheme } from "next-themes"
import { Toaster as Sonner, type ToasterProps } from "sonner"
import { CircleCheckIcon, InfoIcon, TriangleAlertIcon, OctagonXIcon, Loader2Icon } from "lucide-react"

const Toaster = ({ ...props }: ToasterProps) => {
  const { theme = "system" } = useTheme()

  return (
    <Sonner
      theme={theme as ToasterProps["theme"]}
      position="bottom-center"
      // Lift toasts above the mobile BottomNav (fixed bottom-0, ~64px + safe area).
      // On desktop there is no bottom nav, so the default desktop offset is fine.
      mobileOffset={{ bottom: "calc(80px + env(safe-area-inset-bottom))" }}
      className="toaster group"
      icons={{
        success: <CircleCheckIcon className="size-4" />,
        info: <InfoIcon className="size-4" />,
        warning: <TriangleAlertIcon className="size-4" />,
        error: <OctagonXIcon className="size-4" />,
        loading: <Loader2Icon className="size-4 animate-spin" />,
      }}
      style={
        {
          "--normal-bg": "var(--popover)",
          "--normal-text": "var(--popover-foreground)",
          "--normal-border": "var(--border)",
          "--border-radius": "var(--radius)",
        } as React.CSSProperties
      }
      {...props}
    />
  )
}

export { Toaster }
```

Note: `mobileOffset`/`offset` are Sonner v2 props. If ESLint or the type checker rejects the `calc(...)` string value for `mobileOffset`, consult the installed Sonner types (`node_modules/sonner/dist/index.d.ts`) and fall back to a numeric `{ bottom: 80 }` plus the safe-area handled by the toast's own padding. Per AGENTS.md, prefer reading the installed package over assuming an API.

- [ ] **Step 3: Mount the Toaster in the root layout**

In `apps/web/app/layout.tsx`, add the import near the other layout imports:
```tsx
import { Toaster } from "@/components/ui/sonner";
```

Then render it inside `ThemeProvider`, immediately after `<ThemeColorSync />`:
```tsx
                  <ThemeProvider>
                    <ThemeColorSync />
                    <Toaster />
                    <div className="flex h-full pl-[var(--safe-area-left)] pr-[var(--safe-area-right)]">
                      <MainContent>{children}</MainContent>
                    </div>
                  </ThemeProvider>
```

- [ ] **Step 4: Lint the workspace**

Run:
```bash
npm run lint --workspace=apps/web
```
Expected: PASS (no new errors).

- [ ] **Step 5: Commit**

```bash
git add apps/web/package.json package-lock.json apps/web/components/ui/sonner.tsx apps/web/app/layout.tsx
git commit -m "feat(ui): add sonner toaster to web app"
```

---

## Task 2: Add the `activity` i18n namespace (EN + FR)

**Files:**
- Modify: `apps/web/lib/i18n/messages/en.json`
- Modify: `apps/web/lib/i18n/messages/fr.json`
- Reference: existing `wallet.reason.*` keys (reused for the reason label)

- [ ] **Step 1: Add the `activity` namespace to `en.json`**

Insert a new `"activity"` object (place it alphabetically/near `wallet`, valid JSON — mind the trailing comma):
```json
  "activity": {
    "amount": "+{amount} karma",
    "srEarned": "Earned {amount} karma — {reason}.",
    "viewWallet": "View wallet"
  },
```

- [ ] **Step 2: Add the matching `activity` namespace to `fr.json`**

```json
  "activity": {
    "amount": "+{amount} karma",
    "srEarned": "Vous avez gagné {amount} karma — {reason}.",
    "viewWallet": "Voir le porte-karma"
  },
```

Note: confirm the FR wording for "wallet" matches the existing `wallet.title` in `fr.json` (sub-project A). If A used a different term than "porte-karma", match it here for consistency.

- [ ] **Step 3: Verify `wallet.reason.*` exists in BOTH locale files**

Run:
```bash
grep -A8 '"reason"' apps/web/lib/i18n/messages/en.json apps/web/lib/i18n/messages/fr.json
```
Expected: both files show `pebble_created` and `pebble_enriched` under `wallet.reason`. The pill reuses these; if FR is missing any, add it mirroring EN before proceeding.

- [ ] **Step 4: Validate JSON**

Run:
```bash
node -e "JSON.parse(require('fs').readFileSync('apps/web/lib/i18n/messages/en.json','utf8'));JSON.parse(require('fs').readFileSync('apps/web/lib/i18n/messages/fr.json','utf8'));console.log('valid')"
```
Expected: prints `valid`.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/i18n/messages/en.json apps/web/lib/i18n/messages/fr.json
git commit -m "feat(ui): add activity i18n namespace (en/fr)"
```

---

## Task 3: Build the `KarmaActivityPill` component

**Files:**
- Create: `apps/web/components/activity/KarmaActivityPill.tsx`
- Reference: `apps/web/lib/types.ts` (`KarmaReason`)

The pill matches the approved mockup C: a compact **always-dark** capsule (the "island" signature — intentionally theme-independent, with a subtle lift in dark mode for separation), `✨` + amber **+N karma**, tappable, accessible, reduced-motion aware. The reason is not shown visually (mockup C had no subtitle) but is spoken to screen readers for context.

- [ ] **Step 1: Create the component**

```tsx
"use client"

import { useRouter } from "next/navigation"
import { useTranslations } from "next-intl"
import { Sparkle } from "lucide-react"
import { toast } from "sonner"
import type { KarmaReason } from "@/lib/types"

type KarmaActivityPillProps = {
  toastId: string | number
  amount: number
  reason: KarmaReason
}

export function KarmaActivityPill({ toastId, amount, reason }: KarmaActivityPillProps) {
  const router = useRouter()
  const t = useTranslations("activity")
  const tReason = useTranslations("wallet.reason")

  const handleTap = () => {
    toast.dismiss(toastId)
    router.push("/wallet")
  }

  // Full spoken sentence: "Earned 5 karma — Pebble created. View wallet."
  const label = `${t("srEarned", { amount, reason: tReason(reason) })} ${t("viewWallet")}.`

  return (
    <button
      type="button"
      onClick={handleTap}
      aria-label={label}
      className="flex items-center gap-2 rounded-full bg-neutral-900 px-4 py-2 text-sm font-semibold text-white shadow-lg ring-1 ring-white/10 transition active:scale-95 dark:bg-neutral-800 motion-reduce:transition-none motion-reduce:active:scale-100"
    >
      <Sparkle aria-hidden className="size-4 text-amber-300" />
      <span aria-hidden>{t("amount", { amount })}</span>
    </button>
  )
}
```

Why `aria-label` on the button with `aria-hidden` inner content: the visible "+5 karma" is decorative; the button's accessible name is the full sentence, so when Sonner announces the toast (it renders into an `aria-live` region) a screen reader hears the complete context plus the tap affordance.

- [ ] **Step 2: Lint the workspace**

Run:
```bash
npm run lint --workspace=apps/web
```
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add apps/web/components/activity/KarmaActivityPill.tsx
git commit -m "feat(ui): add karma activity pill component"
```

---

## Task 4: Build the `notifyKarma` primitive

**Files:**
- Create: `apps/web/lib/activity/karma-activity.tsx`
- Reference: `apps/web/components/activity/KarmaActivityPill.tsx` (Task 3), `apps/web/lib/types.ts` (`KarmaReason`)

`.tsx` because it returns JSX from `toast.custom`. Stable `id` makes a new credit replace the current pill (one at a time, newest wins).

- [ ] **Step 1: Create the primitive**

```tsx
import { toast } from "sonner"
import { KarmaActivityPill } from "@/components/activity/KarmaActivityPill"
import type { KarmaReason } from "@/lib/types"

// Stable id → a new credit replaces the current pill rather than stacking.
const KARMA_ACTIVITY_ID = "karma-activity"

/**
 * Fire a glanceable "+N karma" activity pill. No-op for non-positive amounts
 * (deletions/clawbacks must stay silent). Feature-agnostic: any credit source
 * calls this with the amount and the reason.
 */
export function notifyKarma(amount: number, reason: KarmaReason): void {
  if (amount <= 0) return
  toast.custom(
    (id) => <KarmaActivityPill toastId={id} amount={amount} reason={reason} />,
    { id: KARMA_ACTIVITY_ID, duration: 3000 },
  )
}
```

- [ ] **Step 2: Lint the workspace**

Run:
```bash
npm run lint --workspace=apps/web
```
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add apps/web/lib/activity/karma-activity.tsx
git commit -m "feat(core): add notifyKarma activity primitive"
```

---

## Task 5: Wire the explicit-fire trigger into the pebble hooks

**Files:**
- Modify: `apps/web/lib/data/usePebbles.ts`
- Reference: `apps/web/lib/activity/karma-activity.tsx` (Task 4)

The store is reloaded inside `createPebble`/`updatePebble`, so `provider.getStore().karma` is correct right after the await. Diff against the value captured before the action; fire only on a positive delta.

- [ ] **Step 1: Import the primitive**

At the top of `apps/web/lib/data/usePebbles.ts`, add:
```ts
import { notifyKarma } from "@/lib/activity/karma-activity"
```

- [ ] **Step 2: Diff-and-fire in `addPebble`**

Replace the existing `addPebble` body:
```ts
  const addPebble = async (input: CreatePebbleInput): Promise<Pebble> => {
    if (!provider) throw new Error("Not authenticated")
    const before = store.karma
    const pebble = await provider.createPebble(input)
    setStore(provider.getStore())
    const delta = provider.getStore().karma - before
    if (delta > 0) notifyKarma(delta, "pebble_created")
    return pebble
  }
```

- [ ] **Step 3: Diff-and-fire in `updatePebble`**

Replace the existing `updatePebble` body:
```ts
  const updatePebble = async (id: string, input: UpdatePebbleInput): Promise<Pebble> => {
    if (!provider) throw new Error("Not authenticated")
    const before = store.karma
    const pebble = await provider.updatePebble(id, input)
    setStore(provider.getStore())
    const delta = provider.getStore().karma - before
    if (delta > 0) notifyKarma(delta, "pebble_enriched")
    return pebble
  }
```

Leave `removePebble` untouched — deletions claw back karma (delta < 0) and must stay silent (D4).

- [ ] **Step 4: Lint the workspace**

Run:
```bash
npm run lint --workspace=apps/web
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add apps/web/lib/data/usePebbles.ts
git commit -m "feat(core): fire karma activity pill on pebble credit"
```

---

## Task 6: Final verification (build + manual pass)

**Files:** none (verification only)

- [ ] **Step 1: Production build of the web workspace**

Run:
```bash
npm run build --workspace=apps/web
```
Expected: build succeeds; no type errors.

- [ ] **Step 2: Manual pass (dev server)**

Run `npm run dev --workspace=apps/web`, sign in, then verify:
- Create a pebble that earns karma → a compact "+N karma" pill rises bottom-center, above the nav bar, then auto-dismisses (~3s).
- Create two pebbles quickly → only one pill shows at a time (replace, not stack).
- Tap the pill → navigates to `/wallet` and the pill dismisses.
- Delete a pebble → **no** pill.
- Toggle the OS "reduce motion" setting → the pill fades without the scale/rise.
- Switch to dark mode and a non-default color-world → the pill remains legible (dark capsule, amber amount, visible ring).
- With a screen reader (VoiceOver), creating a pebble announces "Earned N karma — Pebble created. View wallet."

- [ ] **Step 3: Confirm no commit needed**

This task changes no files. If the manual pass surfaces a defect, fix it in the relevant task's file, re-lint, and commit with a `fix(...)` message describing the correction.

---

## Housekeeping (at PR time, not per-task)

- **Arkaik:** no new route/data model/endpoint → no-op.
- **Decision log:** append one entry — *web in-app notifications = Sonner + explicit-fire + no realtime* (foundation future features build on).
- **Lab Note:** user-facing (`feat`) → bilingual EN/FR blurb in the PR body (proposal only).
- **PR:** branch `feat/495-in-app-activity-primitive`; title `feat(ui): in-app karma activity primitive`; body opens `Resolves #495`; inherit #495 labels (`feat`, `ui`, `core`, `web`) + milestone `M36 · Pebblestore & Karma Economy` (confirm with user).
