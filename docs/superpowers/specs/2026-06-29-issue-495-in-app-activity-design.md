# In-App Activity Primitive (Opal-style karma credit) — Design Spec

> **Sub-project B of 4** in **M36 · Pebblestore & Karma Economy**. Issue #495.
> Pairs with Sub-project A (#494, merged) which produces the credit events this primitive reacts to.

**Goal:** Introduce a non-invasive, glanceable in-app activity primitive for `apps/web`, whose first consumer animates a "+N karma" pill when the karma wallet is credited.

**Status:** Approved design. Next step: implementation plan (writing-plans).

---

## 1. Intent & constraints

Surface moments worth noticing — starting with **"karma pouch credited"** — as a small, transient, delightful activity that never blocks the user. Inspired by Opal's Dynamic-Island streak increment, **not** an invasive full sheet.

**Web reality check:** a PWA cannot render into the iOS Dynamic Island hardware region, and a top-anchored pill fights the status-bar safe area in standalone mode. So the foundation is **Sonner** (already proven in `apps/admin`), styled and placed to chase the Opal *feeling* within web limits — a compact pill, bottom-center, in the thumb zone next to the karma counter it relates to.

Non-negotiables from the issue:
- Any feature can fire a transient, non-blocking activity via **one call**.
- A wallet credit surfaces a glanceable **"+N karma"** pill, never a full-screen interruption.
- Theming-aware (light/dark + color-world), accessible (respects reduced-motion, screen-reader friendly), bilingual (EN/FR).

## 2. Decisions (the forks, settled)

| # | Decision | Why |
|---|----------|-----|
| D1 | **Foundation = Sonner**, copied from admin into `apps/web`. | Proven wrapper; gives queueing, timeout, swipe-dismiss, `aria-live`, reduced-motion handling for free. A web PWA can't use a real Dynamic Island. |
| D2 | **Compact pill, bottom-center** via `toast.custom()`. | Keeps the Opal compact-glance feeling; sits in the thumb zone above the nav bar, beside the karma stat that's incrementing; avoids the status-bar collision a top pill would cause. |
| D3 | **Explicit fire at the action site**, amount from **store-diff**. | Cleanest *primitive* (feature-agnostic, imperative — exactly what the issue asks for); carries a *reason* label; zero backend change, no realtime, no false-fire surface. Rejected: central karma-watcher (no reason, load-time false fires) and Supabase realtime (heaviest, double-fire risk, overkill for a personal PWA). |
| D4 | **Credit-only.** delta ≤ 0 stays silent. | Matches the "increment" spirit. Deletions/clawbacks must not nag; spends are sub-project C's concern. |
| D5 | **Single pill, replace-on-new** (stable `id: "karma-activity"`). | At most one glanceable pill; newest credit wins. No stacking, no summing. On-brand for "one living moment." |
| D6 | **Tappable → `/wallet`** (then dismiss). | Connects B's transient pill to A's durable wallet history; a natural "see more" gesture. |
| D7 | **Plain module primitive**, not a hook/context. | Sonner's `toast` is a global singleton importable anywhere, so a `useActivity()` hook would be pure ceremony. |

## 3. Architecture & components

Data flow today (verified): `usePebbles.addPebble` → `provider.createPebble` → `compose-pebble` edge function → `provider.loadFromSupabase()` reloads the whole store (karma comes from `v_karma_summary`) → hook calls `setStore`. So `store.karma` is **fresh and correct immediately after any mutation**, but the delta is not returned and `store.karma_log` is always `[]`. The store-diff trigger (D3) exploits exactly this: read `karma` before, read it after the reload, fire on the positive difference.

### Files

**Create:**

- `apps/web/components/ui/sonner.tsx` — themed `Toaster` wrapper, copied from `apps/admin/components/ui/sonner.tsx`. Differences:
  - `position="bottom-center"`.
  - Bottom offset that clears the nav bar + bottom safe area (nav is ~64px; offset accounts for `var(--safe-area-bottom)`).
  - Keeps admin's CSS-variable theming (`--popover`, `--popover-foreground`, `--border`, `--radius`) and Lucide status icons (the icons are unused by the custom karma pill but kept so the wrapper is a drop-in for future default toasts).

- `apps/web/components/activity/KarmaActivityPill.tsx` — the custom pill content rendered inside `toast.custom()`. Compact dark capsule: `✨` icon + **+N karma** (amount tinted), optional reason subtext. Themed for light/dark + color-world via existing CSS variables. The whole pill is a button that calls the dismiss + navigate-to-`/wallet` handler. Includes visually-hidden screen-reader text describing the event.

- `apps/web/lib/activity/karma-activity.ts` — the primitive. Exports `notifyKarma(amount: number, reason: KarmaActivityReason)`:
  - Guard: `if (amount <= 0) return` (defensive; callers already gate on delta > 0).
  - Calls `toast.custom((id) => <KarmaActivityPill ... />, { id: "karma-activity", duration: 3000 })`.
  - `KarmaActivityReason` is a small union (`"pebble_created" | "enriched"`) — extends as future credit sources appear.

**Modify:**

- `apps/web/app/layout.tsx` — mount `<Toaster />` once, inside `ThemeProvider`, beside `<ThemeColorSync />`.
- `apps/web/lib/data/usePebbles.ts` — in `addPebble` and `updatePebble`, capture `const before = store.karma` before the await, then after `setStore(provider.getStore())` compute `const delta = provider.getStore().karma - before` and `if (delta > 0) notifyKarma(delta, reason)` (`"pebble_created"` for add, `"enriched"` for update). `store` is already in scope.
- `apps/web/lib/i18n/messages/en.json` + `fr.json` — add an `activity` namespace: the unit label ("karma"), the reason labels, and the screen-reader sentence template.
- `apps/web/package.json` — add `sonner` dependency (matching admin's version).

### Why these boundaries

The **primitive** (`karma-activity.ts` + `KarmaActivityPill.tsx` + the `Toaster` mount) knows nothing about pebbles — it only knows "show a +N karma pill." The **wiring** (the two edits in `usePebbles.ts`) knows nothing about Sonner — it only knows "a credit of N happened for reason R, announce it." This is the decoupling the issue asks for: the next credit source (a grant, a purchase refund in C) adds one `notifyKarma(...)` call and nothing else.

## 4. Behavior

- **One pill at a time.** The stable `id` means rapid successive credits replace, not stack. If three pebbles are created in a burst, the user sees the latest "+N karma," not a tower of toasts.
- **Duration** ~3s, then auto-dismiss. Sonner's swipe-to-dismiss remains available.
- **Tap** anywhere on the pill → dismiss it and `router.push("/wallet")`.
- **Entrance:** a soft rise + fade (translateY + opacity). Sonner owns enter/exit; the custom content adds no competing animation beyond what reduced-motion allows.

## 5. Accessibility, motion, i18n

- **Screen reader:** Sonner mounts toasts in an `aria-live` region. The pill includes visually-hidden text built from the `activity` namespace, e.g. EN "Earned 5 karma for carving a pebble." / FR "Vous avez gagné 5 karma pour avoir taillé un caillou." The visible "+5 karma" is decorative-adjacent but also readable; the hidden sentence gives full context.
- **Reduced motion:** under `prefers-reduced-motion: reduce`, drop the rise/scale and fade only. Use Tailwind's `motion-reduce:` variants on the pill; rely on Sonner honoring reduced motion for its own transitions (verify during implementation; if Sonner still animates, pass options to soften).
- **Theming:** the pill uses the same CSS variables as the rest of the app, so light/dark and all five color-worlds are covered without per-world code.
- **Bilingual:** all strings come from `en.json`/`fr.json`. No hardcoded copy in the component.

## 6. Out of scope

- Persistent notification center / inbox / history feed — the **wallet page** (Sub-project A) is the durable home for karma history.
- Push / OS-level notifications.
- Triggers other than karma credit — glyph purchased, submission approved, etc. land with their own features (reusing this primitive).
- Spend/withdraw pills — sub-project C decides whether spends surface an activity.

## 7. Acceptance

- [ ] Any feature can fire a transient, non-blocking pill via a single `notifyKarma(amount, reason)` call.
- [ ] Creating a pebble that earns karma surfaces a glanceable bottom-center "+N karma" pill, not a full-screen interruption.
- [ ] The pill replaces (does not stack) on rapid successive credits.
- [ ] Tapping the pill opens `/wallet` and dismisses.
- [ ] delta ≤ 0 (deletions/clawbacks) fires nothing.
- [ ] Respects `prefers-reduced-motion`; screen-reader announces the credit; EN + FR.
- [ ] Light/dark + color-world themed.

## 8. Housekeeping (at PR time)

- **Arkaik:** no new route, data model, or endpoint → **no-op**.
- **Decision log:** one entry — *web in-app notifications = Sonner + explicit-fire + no realtime* (a foundation future features build on).
- **Lab Note:** user-facing (`feat`) → bilingual EN/FR blurb in the PR body (proposal only).
- **Testing:** no web test runner in V1; the `delta > 0` guard and reason→label map are pure and trivially checkable. Verification = workspace lint + `next build` + manual pass (create a pebble, observe the pill, tap to wallet, toggle reduced-motion and dark/color-world).
