# P0 web hardening: a CI gate for `apps/web`, and a real failure state for a failed store load — design

- **Date:** 2026-09-03
- **Findings:** `F-2026-08-TST-web-01` (TST-06), `F-2026-08-REL-web-01` (REL-01) — Kritik audit `2026-08`, both severity high
- **Surface:** `apps/web`, `.github/workflows/`, root docs
- **Milestone:** proposed — see "Proposed issues"
- **Stack:** two parts, `main → part 1 → part 2`

## Context

The 2026-08 Kritik audit raised two P0 findings against the web surface. They are
unrelated in mechanism and related in consequence: one says nothing proves the web
app still works, the other says the web app lies to the user when it doesn't. They
ship as one stack because part 1 installs the gate that runs the tests part 2 adds.

Two entries in `docs/decisions/log.md` constrain the design directly, and one
existing module is the in-repo precedent for part 2's contract:

- **`2026-07-30 — Cross-surface data shapes are tested with foreign payloads` (#651,
  log.md:316–325).** The M47 postmortem: a formatter bug shipped because every test
  encoded with the same formatter it decoded with, and the failure was *silent at
  runtime — no error, no log, just the wrong time*. That entry is why
  `wobble.golden.test.ts`, `settled-feed.test.ts` and `flow.test.ts` exist, and it is
  the exact class of bug an ungated suite cannot catch. It also names the standing
  rule part 2 obeys on the logging side.
- **`2026-09-02 — The four anon contract harnesses become a CI gate` (#741,
  log.md:470–479)** and **`2026-09-02 — The nightly harness run logs a result table`
  (#743, log.md:481–490).** These are the template for part 1: the trigger reasoning,
  the fork gate, the concurrency stance, the "CI and a human invoke exactly the same
  command" rule. `supabase.yml` is what a workflow in this repo looks like.
- **`apps/web/lib/data/settled-feed.ts:1–18`** — written for #439, when one missing
  column blanked the entire web Lab. Its docstring states the contract part 2 extends
  to the store: *"a failing feed is an isolated, recorded event rather than a
  page-level one"*, and a full-screen error is reserved for total failure. The store
  load is the all-or-nothing counterpart that never got the same pass.

## Verification at HEAD (drift from the 2026-08 snapshot)

Both findings were re-verified against `main` at `26be933c`. Every path below is
current; the audit's `/home/user/pbbls/` prefix maps to `/Users/alexis/code/pbbls/`.

### Finding A — `F-2026-08-TST-web-01` · **still valid**, two stale sentences

**What still holds (verified):**

| Claim | Evidence at HEAD |
|---|---|
| No workflow references `apps/web` | `grep -rn 'apps/web' .github/` → 3 hits, all in `copilot-instructions.md`. Zero in `.github/workflows/*.yml`. |
| `turbo.json` has no `test` task | `turbo.json` defines `build`, `dev`, `lint` only. |
| Root `package.json` has no `test` script | `dev`, `build`, `lint` only. |
| No pre-commit tooling | No husky/lefthook at root; `.git/hooks` holds only `.sample` files. |
| The suite exists and is green | `npm run test --workspace=apps/web` → **12 files, 125 tests passed, 268 ms**. |
| Lint is green and enforces the boundary | `npm run lint --workspace=apps/web` → clean. `apps/web/eslint.config.mjs:22–33` restricts `@/lib/data/local-provider` and `@/lib/data/supabase-provider`; `:36–41` exempts `components/layout/DataProvider.tsx`. |
| `apps/web/vercel.json` is schema-only | Two lines, `$schema` only. |
| Root `CLAUDE.md` PR checklist step 5 omits tests | "Run lint/build at the **scope of your change**". |
| The PR template omits tests | `.github/PULL_REQUEST_TEMPLATE.md` checklist: `npm run build`, `npm run lint`. No test line. |
| `next build` no longer lints | `node_modules/next/dist/docs/01-app/02-guides/upgrading/version-16.md:1084` — verbatim: "`next build` no longer runs linting." So the Vercel deploy build catches type errors only. |
| The golden fixture lives outside `apps/web` | `apps/web/lib/wobble/wobble.golden.test.ts:31–35` resolves `../../../../apps/ios/PebblesTests/Wobble/WobbleGolden.json`; the file exists (7,404 bytes). Nothing in CI watches `apps/ios/**` — confirmed, there is still no iOS workflow. |

**What has drifted:**

1. **"exactly four workflows" is stale.** There are **five**: `supabase.yml` landed in
   #742/#744. The audit's `grep -rn 'apps/web|packages/supabase|migrations'
   .github/workflows/*.yml returns zero hits` is now false for `packages/supabase` —
   it returns many hits, all from `supabase.yml`.
2. **"a schema change… runs nothing at all" is now half-answered, and half
   unanswerable.** `supabase.yml` gates `packages/supabase/**` on PRs and nightly, so a
   schema change is no longer unguarded. The remaining half does **not** get fixed by
   adding `packages/supabase/**` to a web path filter, because **`apps/web` consumes no
   generated types**: `grep -rn "Database" apps/web/{lib,components,app}` returns
   nothing, and `apps/web/lib/supabase/client.ts` builds an untyped
   `createBrowserClient`. The Vitest suite is `environment: node` over pure `lib/`
   modules (`apps/web/vitest.config.ts:16–19`, `include: ["lib/**/*.test.ts"]`) and
   touches no Supabase client. A schema change therefore *cannot* break a web test or
   a web lint rule today — the harnesses in `supabase.yml` are the only proof that
   exists for that direction, which is the design #741 chose deliberately.
3. **The aggravating factor about `apps/ios/**` is fully intact and is the sharper
   half of the finding.** Regenerating `WobbleGolden.json` breaks a *web* test, and
   nothing anywhere in CI would notice.

**Verdict: valid.** The core claim — nothing gates `apps/web` — is unchanged. Fix the
workflow count and drop the `packages/supabase/**` clause from the remediation.

### Finding B — `F-2026-08-REL-web-01` · **still valid**, one verifier claim needs correcting

**What still holds (verified, line numbers re-anchored to HEAD):**

| Claim | Evidence at HEAD |
|---|---|
| `error` + `refreshStore` exist on the context | `apps/web/lib/data/provider-context.ts:6–13` (`DataContextValue`), `apps/web/components/layout/DataProvider.tsx:67` (both passed into the provider value). |
| The catch swallows and empties the store | `DataProvider.tsx:33–40` — `setError`, `setProvider(null)`, `setStore(EMPTY_STORE)`, **no log**. This violates `docs/agents/data-and-async.md:20`, which makes a `console.warn`/`console.error` in every failing async path mandatory. |
| Zero components read `error` or `refreshStore` | 29 `useDataProvider()` call sites (30 lines including the definition at `provider-context.ts:17`). Every one destructures some subset of `provider`/`store`/`setStore`/`loading`. **None** names `error` or `refreshStore`. |
| `/path` falls through to the first-run CTA | `app/path/page.tsx:8–12` → `PathScreen`; `PathScreen.tsx:85–91` shows the spinner only while `loading`, and once `loading` is false with zero pebbles renders `WeekRoll`/`WeekPager` → `WeekPath.tsx:47–49` → `PathEmptyState` → `components/path/PathEmptyState.tsx:16–19`, the `Carve a pebble` CTA linking to `/record`. |
| Wallet conflates empty with failed | `components/wallet/WalletView.tsx:37–38` — `history.length === 0 && !loading` renders `t("empty")` ("No karma movements yet."), ignoring the `error` that `lib/data/useWallet.ts:19,44,62,77` computes and returns. |
| No SW softening | `app/sw.ts:20–26` — Supabase is `NetworkOnly` by design (the comment explains why: a cached 401 caused permanent blank pages). |
| `app/error.tsx` can never fire | The error is caught, so no render throws. `app/error.tsx:24` already implements exactly the retry affordance the store path lacks, via Next 16's `unstable_retry`. |
| 6 of 9 parallel queries throw | `lib/data/supabase-provider.ts` `loadFromSupabase` — `pebbles`, `pebblesRender`, `souls`, `collections`, `glyphs`, `entitled` throw; `collectionPebbles`, `karma`, `bounce` have no error check and silently degrade. The verifier's nit is correct. |
| Empty-state copy is week-scoped | `lib/i18n/messages/en.json` → `path.empty.currentWeek` = "Fresh week" / "Carve your first pebble of the week." The verifier's nit is correct: it is not literally a first-run message, but it is a *nothing-here-yet* message shown to a user whose years of entries failed to load, with a CTA into a flow that will throw. |
| Mutations throw at a signed-in user | `setProvider(null)` + e.g. `lib/data/usePebbles.ts:20` `if (!provider) throw new Error("Not authenticated")`. Confirmed. |

**What needs correcting:**

The verifier wrote that the `activeUserIdRef` latch means *"only a hard page reload
recovers"*. That is true of the **automatic** path and **false of the retry path**:

- `DataProvider.tsx:57` — `if (activeUserIdRef.current === user.id) return` — blocks the
  *effect* from ever re-loading, because `loadData` sets the ref at `:20` **before** the
  request, so a failed load latches identically to a successful one. No `online` /
  `visibilitychange` recovery exists. That half is confirmed.
- But `refreshStore` (`:62–64`) calls `loadData(user.id)` **directly**, bypassing the
  effect entirely. A retry button wired to it re-fetches today, unmodified.

This matters for the design: the latch is not an obstacle to the retry button, it is
an obstacle to *unattended* recovery. Part 2 fixes both, but they are different fixes,
and conflating them would produce a retry-on-every-render storm (see D6).

**Verdict: valid as written**, with the latch claim narrowed as above.

## The stack

```
main
 └── PART 1  test/<issue>-web-ci-gate         → F-2026-08-TST-web-01
      └── PART 2  fix/<issue>-dataprovider-error   → F-2026-08-REL-web-01
```

Order is dependency, not convenience: part 2 adds Vitest coverage for the new
route-gating and reducer-shaped logic, and part 1 is what makes those tests run on a
PR. Landing them the other way round would merge part 2's tests into the same void
that finding A describes.

---

## Part 1 — `web.yml`: run ESLint and Vitest on every PR

**Branch:** `test/<issue>-web-ci-gate` · **PR title:** `test(web): gate lint and the vitest suite in CI`

### Tasks

1. Add `.github/workflows/web.yml` (one job, two checks).
2. Add a test line to the `.github/PULL_REQUEST_TEMPLATE.md` checklist.
3. Amend root `CLAUDE.md` PR checklist step 5 to name the test suite, and add `web.yml`
   to the "CI gates worth knowing about" paragraph.
4. Append the decision-log entry (`docs/decisions/log.md`).

### D1 — No path filter. The trigger is every pull request.

```yaml
on:
  pull_request:
  push:
    branches: [main]
```

The remediation as written ("paths covering `apps/web/**`, `packages/supabase/**`, and
the workflow file itself") **plus** "mark the job a required status check" is a
contradiction in GitHub's semantics, and the path list is wrong on its own terms.

- **Path filters and required checks do not compose.** A job skipped by a job-level
  `if:` reports success and satisfies branch protection — that is why `supabase.yml`'s
  fork gate is written as an `if:` and not a filter. A *workflow* skipped by a `paths:`
  filter never reports at all, so a required check stays permanently pending and blocks
  every PR that doesn't touch the filtered paths. Path filtering here would either
  defeat the "required" half of the remediation or require a companion no-op workflow
  publishing the same check name.
- **The correct filter is nearly "everything".** `apps/web/**` for the app,
  `apps/ios/PebblesTests/Wobble/WobbleGolden.json` because `wobble.golden.test.ts:31–35`
  reads it, `.github/workflows/web.yml` for itself — and `packages/supabase/**` buys
  **nothing**, per drift item 2. A filter that excludes only `docs/`, `apps/android/`
  and the iOS app minus one fixture is a footgun with no payoff.
- **The job is cheap and side-effect free.** Unlike `android.yml` (JDK + Gradle,
  minutes) and `supabase.yml` (production writes), this is `npm ci` + ESLint + a 268 ms
  Vitest run. The two reasons this repo path-filters do not apply. Running
  unconditionally is consistent with the *reasoning* behind house style even though it
  departs from its shape.

**If the maintainer prefers a filtered trigger anyway**, the exact list is:

```yaml
    paths:
      - 'apps/web/**'
      - 'apps/ios/PebblesTests/Wobble/WobbleGolden.json'   # the golden parity anchor
      - 'package-lock.json'
      - '.github/workflows/web.yml'
```

…and the job must then **not** be marked required, or a companion no-op must publish
the check name. Recommendation stands: no filter, mark required.

### D2 — Answering the brief's question: does `apps/ios/**` belong in the filter?

**With no filter, the question dissolves** — an iOS-side regeneration of
`WobbleGolden.json` runs the web parity test like any other change, which is the
correct outcome and the one the verifier flagged as missing. **If a filter is used,
then yes**, but only the single fixture path, not `apps/ios/**`: the rest of the iOS
app cannot affect a web test, and watching all of it would run this job on every
Swift PR for nothing.

### D3 — One job, both checks, `!cancelled()` on the second

```yaml
name: Web

# apps/web had no CI at all: the 125-test Vitest suite and the ESLint config that
# enforces the data-layer boundary ran only when a human remembered to. Vercel's
# deploy build does not close the gap — Next 16 removed linting from `next build`
# (node_modules/next/dist/docs/01-app/02-guides/upgrading/version-16.md:1084), so
# it catches type errors and nothing else.
#
# No `paths:` filter, deliberately (D1): a workflow skipped by a path filter never
# reports, so a required check would stay pending forever. The job is npm ci +
# eslint + a 268 ms Vitest run — cheap enough that filtering buys nothing, and the
# wobble golden fixture it asserts against lives in apps/ios/, so the honest filter
# would have to reach outside apps/web anyway.
on:
  pull_request:
  push:
    branches: [main]

concurrency:
  group: web-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

jobs:
  check:
    name: eslint · vitest
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm

      - run: npm ci

      # Catches violations of the no-restricted-imports data-layer boundary and
      # no-explicit-any, neither of which any deploy build sees.
      - name: ESLint
        run: npm run lint --workspace=apps/web

      # `!cancelled()` so a lint failure still reports the test result — a red PR
      # should name every problem at once, not one per push (the supabase.yml
      # pattern).
      - name: Vitest
        if: '!cancelled()'
        run: npm run test --workspace=apps/web
```

`cancel-in-progress: true` is safe here and correct — unlike `supabase.yml`, this job
signs nothing up and writes nowhere, so superseding an in-flight run costs nothing.

### D4 — No `test` task in `turbo.json`, no root `test` script

The finding's evidence notes their absence; adding them would be wrong.
`apps/ios/package.json` `test` runs `xcodegen && xcodebuild` and
`apps/android/package.json` `test` runs Gradle — a root `turbo test` on an
`ubuntu-latest` runner would attempt both. The workflow calls
`npm run test --workspace=apps/web`, which is verbatim the command root `CLAUDE.md`
documents. This preserves #741's rule that **CI and a human invoke exactly the same
command**.

### D5 — Fix the documentation hole in the same PR

The finding is explicit that the checklist compounds the gap. Two edits:

- `.github/PULL_REQUEST_TEMPLATE.md` checklist gains
  `- [ ] Tests pass for the touched workspace (e.g. `npm run test --workspace=apps/web`)`.
- Root `CLAUDE.md` PR checklist step 5 becomes "Run lint, **the workspace test suite**,
  and build at the scope of your change", and the "CI gates worth knowing about"
  paragraph gains `web.yml`.

This is not a per-PR learning promotion (which `CLAUDE.md` forbids outside the
milestone grooming pass) — it is a checklist correcting itself to match a gate landing
in the same commit. Worth saying in the PR body so a reviewer doesn't flag it.

### Files touched

| File | Change |
|---|---|
| `.github/workflows/web.yml` | new |
| `.github/PULL_REQUEST_TEMPLATE.md` | one checklist line |
| `CLAUDE.md` | PR checklist step 5; CI-gates paragraph |
| `docs/decisions/log.md` | one appended entry |

**Not touched: `apps/web/vercel.json`.** A sibling stack is speccing region pinning
there; this part has no reason to open the file, and no conflict is expected.

### Independent verification

```bash
npm run lint --workspace=apps/web        # green
npm run test --workspace=apps/web        # 12 files, 125 tests
```

Plus, on the PR itself: the `Web / eslint · vitest` check must appear and pass. A
deliberate throwaway commit (import `@/lib/data/supabase-provider` from a component,
or perturb a `wobble.golden` expectation) should turn it red before being reverted —
the gate is unproven until it has failed once on purpose.

### Why this is a standalone reviewable unit

It is describable without a forward reference: *"the web suite and lint config run on
CI now, and the checklist says so."* A reviewer needs to check three things — the
trigger, the two commands, the doc edits — and can say yes or no without knowing part 2
exists. It changes no application code, so it cannot regress the product.

### Test plan

No new Vitest files. The gate's own test is that the existing 125 pass in CI and that
an intentional violation fails it (above). Verify the ESLint step catches a boundary
violation specifically, not just a syntax error, since that rule is the one Vercel's
build stopped seeing.

---

## Part 2 — A failed store load renders a failure state with a working retry

**Branch:** `fix/<issue>-dataprovider-error` · **PR title:** `fix(web): show a retryable failure state when the store fails to load`

### Tasks

1. Log the swallowed error in `DataProvider`'s catch.
2. Add `lib/config/store-routes.ts` — the store-backed route prefixes.
3. Add `components/layout/StoreLoadError.tsx` — the failure state.
4. Add `components/layout/StoreGate.tsx` — route-scoped branch on `error`.
5. Mount `StoreGate` inside `AuthGate` in `MainContent`.
6. Add unattended recovery (`online` + `visibilitychange`) and an in-flight guard in
   `DataProvider`.
7. Branch `WalletView` on the `error` `useWallet` already returns.
8. Add `errors.store.*` and `wallet.historyError` to `en.json` / `fr.json`.
9. New Vitest file for the route matcher.

### D6 — The failure state lives in one shared shell, gated by an explicit route list

**Decision:** a `StoreGate` component wrapping `{children}` inside `AuthGate` in
`components/layout/MainContent.tsx:54`.

```tsx
"use client"

import { usePathname } from "next/navigation"
import { useDataProvider } from "@/lib/data/provider-context"
import { STORE_BACKED_PREFIXES } from "@/lib/config/store-routes"
import { StoreLoadError } from "@/components/layout/StoreLoadError"

export function StoreGate({ children }: { children: React.ReactNode }) {
  const pathname = usePathname()
  const { error, loading, refreshStore } = useDataProvider()

  const needsStore = STORE_BACKED_PREFIXES.some(
    (prefix) => pathname === prefix || pathname.startsWith(prefix + "/"),
  )

  if (needsStore && error !== null) {
    return <StoreLoadError onRetry={refreshStore} retrying={loading} />
  }

  return <>{children}</>
}
```

**Why a shell and not per screen.** The finding names `/path`, souls, collections and
wallet, but the defect is not per-screen — it is one global failure expressing itself
as *whatever each screen shows when its slice of the store is empty*. Nine or more
screens each growing their own `if (error)` branch would be nine places to forget,
nine copies of the copy, and a guarantee that the tenth screen ships without it. One
shell fixes every current and future store-backed route in one reviewable diff.

**Why an inclusion list and not an exclusion list.** The store error is global, but
some routes are legitimately store-free and a signed-in user can be on them:
`/u/[handle]`, `/p/[id]`, `/docs`, `/offline`, `/login`, `/onboarding`, the landing
page. Failing *open* on an unlisted route preserves today's behavior (a
silent degradation we already have); failing *closed* would show a false error page on
a public profile that renders perfectly. The safer default is the inclusion list.

```ts
// lib/config/store-routes.ts
/**
 * Routes whose screens read the eagerly-loaded global store (or the provider
 * that owns it). On these, a failed store load must show a failure state rather
 * than each screen's "nothing here yet" copy.
 *
 * Deliberately NOT derived from AuthGate's PROTECTED_PREFIXES: that list answers
 * "does this route require a session", which is a different question with a
 * different (and currently different) membership.
 */
export const STORE_BACKED_PREFIXES = [
  "/path", "/record", "/pebble", "/collections", "/souls", "/glyphs",
  "/carve", "/profile", "/connections", "/wallet", "/achievements",
  "/drafts", "/settings",
] as const
```

`/wallet`, `/achievements` and `/drafts` are in this list and are **not** in
`AuthGate`'s `PROTECTED_PREFIXES` (`components/auth/AuthGate.tsx:7–19`) — they reach
the store through components rather than through `app/*/page.tsx`. That divergence is
noted, not fixed here (see Risks).

**Side effect worth stating:** because `/record` is in the list, the verifier's
aggravating factor (2) — the `PathEmptyState` CTA leading a signed-in user into a flow
that throws `Not authenticated` because `setProvider(null)` — is closed as a
consequence, not as a separate fix.

### D7 — What the failure state looks like

It reuses `components/layout/EmptyState.tsx`, so it inherits the layout every other
zero-content screen uses, and pairs it with the retry affordance `app/error.tsx:24`
already establishes for the unreachable render-throw path.

```tsx
"use client"

import { RefreshCw } from "lucide-react"
import { useTranslations } from "next-intl"
import { Button } from "@/components/ui/button"
import { EmptyState } from "@/components/layout/EmptyState"

type StoreLoadErrorProps = {
  onRetry: () => void
  retrying: boolean
}

export function StoreLoadError({ onRetry, retrying }: StoreLoadErrorProps) {
  const t = useTranslations("errors.store")
  return (
    <EmptyState
      title={t("title")}
      description={t("description")}
      action={
        <Button variant="outline" onClick={onRetry} disabled={retrying}>
          <RefreshCw
            className={retrying ? "size-4 animate-spin" : "size-4"}
            data-icon="inline-start"
          />
          {retrying ? t("retrying") : t("retry")}
        </Button>
      }
    />
  )
}
```

Copy must do one job: say the *record could not be loaded*, never that it is empty.
Proposed `errors.store` keys (EN, with a real FR adaptation using "Tu", not a literal
translation):

| Key | EN | FR |
|---|---|---|
| `title` | "We couldn't reach your pebbles" | "Impossible de retrouver tes pebbles" |
| `description` | "Your record is safe — the connection dropped on the way. Try again in a moment." | "Ton parcours est intact, c'est la connexion qui a lâché en route. Réessaie dans un instant." |
| `retry` | "Try again" | "Réessayer" |
| `retrying` | "Loading…" | "Chargement…" |

`errors.title` / `errors.description` / `errors.retry` already exist and are
deliberately generic ("Something went wrong"). A nested `errors.store` block is added
rather than reused, because the whole point is that the user must be able to tell
"we couldn't load it" from "there's nothing here".

**Formatting note for the implementer:** `en.json` / `fr.json` are
formatting-sensitive catalogs. Insert the new block as text at the `errors` anchor;
never round-trip the whole file through a JSON serializer.

### D8 — Retry is `refreshStore`, unchanged; the latch is fixed separately

`refreshStore` already works (see the verification section): it calls `loadData`
directly and never consults the effect's guard. The button needs no new plumbing —
which is exactly what the finding predicted ("this is a rendering change only") and
what the verifier's "only a hard page reload recovers" overstated.

What is genuinely broken is *unattended* recovery, and the fix is **not** to teach the
effect's guard about `error`:

```ts
// WRONG — do not do this
if (activeUserIdRef.current === user.id && !error) return
```

That turns every render into a retry against a backend that is already failing: a
request storm, and each failure sets state which re-renders which re-fires. The guard
stays as-is and recovery becomes event-driven — bounded, and tied to the two events
that actually mean conditions may have changed.

```ts
const inFlightRef = useRef(false)

const loadData = useCallback(async (userId: string) => {
  activeUserIdRef.current = userId
  inFlightRef.current = true
  setLoading(true)
  setError(null)

  try {
    // …unchanged…
  } catch (err) {
    if (activeUserIdRef.current !== userId) return
    // docs/agents/data-and-async.md: no async failure path is silent. The #651
    // postmortem is the cost of a failure with "no error, no log".
    console.error("[DataProvider] store load failed", err)
    setError(err instanceof Error ? err : new Error("Failed to load data"))
    setProvider(null)
    setStore(EMPTY_STORE)
  } finally {
    // Same condition as setLoading: a superseded load must not clear the flag
    // for the newer one.
    if (activeUserIdRef.current === userId) {
      inFlightRef.current = false
      setLoading(false)
    }
  }
}, [])

const refreshStore = useCallback(() => {
  if (!user || inFlightRef.current) return
  void loadData(user.id)
}, [user, loadData])

// The load effect fires once per user per page lifetime, because activeUserIdRef
// is set BEFORE the request — so a failed load latches exactly like a successful
// one. That latch is kept deliberately (making it error-aware would retry on
// every render). Recovery is event-driven instead.
useEffect(() => {
  if (!error || !user) return

  const retry = () => {
    if (document.visibilityState !== "visible") return
    if (inFlightRef.current) return
    void loadData(user.id)
  }

  window.addEventListener("online", retry)
  document.addEventListener("visibilitychange", retry)
  return () => {
    window.removeEventListener("online", retry)
    document.removeEventListener("visibilitychange", retry)
  }
}, [error, user, loadData])
```

Three recovery routes now exist where zero did: the button, coming back online, and
returning to a backgrounded tab — the last of which is the dominant PWA case.

### D9 — `setProvider(null)` on failure stays

Considered and rejected: keeping the provider alive on a failed load would let mutation
hooks write against an in-memory `EMPTY_STORE` and `setStore` merges on top of it,
which is a data-integrity risk far worse than a thrown "Not authenticated". With
`/record` behind `StoreGate` the user never reaches a mutation in this state, so the
throw becomes unreachable rather than user-facing.

### D10 — `WalletView` branches on the error it already receives

This is the one screen with its own independent fetch (`useWallet` is on-demand, not
part of the eager store load), so `StoreGate` covers case A (store failed → `/wallet`
gated) but not case B (store fine, `getWallet`/`getWalletHistory` failed).

```tsx
{history.length === 0 && !loading ? (
  <p className="text-sm text-muted-foreground">
    {error ? t("historyError") : t("empty")}
  </p>
) : ( … )}
```

`wallet.historyError`: EN "We couldn't load your karma history." / FR "Impossible de
charger ton historique de karma." A retry for the wallet's own fetch is **not** added —
`useWallet` exposes no refetch, and adding one is a hook redesign outside this
finding. Noted in Risks.

### Files touched

| File | Change |
|---|---|
| `apps/web/components/layout/DataProvider.tsx` | `console.error`; `inFlightRef`; recovery effect; `refreshStore` guard |
| `apps/web/components/layout/StoreGate.tsx` | new |
| `apps/web/components/layout/StoreLoadError.tsx` | new |
| `apps/web/lib/config/store-routes.ts` | new |
| `apps/web/components/layout/MainContent.tsx` | wrap `{children}` in `StoreGate` |
| `apps/web/components/wallet/WalletView.tsx` | branch on `error` |
| `apps/web/lib/i18n/messages/en.json`, `fr.json` | `errors.store.*`, `wallet.historyError` |
| `apps/web/lib/config/store-routes.test.ts` | new |

### Independent verification

```bash
npm run lint --workspace=apps/web
npm run test --workspace=apps/web
npm run build --workspace=apps/web      # touches i18n catalogs + a layout component
```

Manual, in the browser: `DevTools → Network → Offline`, hard-reload `/path` while
signed in. Expected: the failure copy and a retry button, **never** "Fresh week"; a
labelled `[DataProvider] store load failed` in the console; back online, the page
recovers without a reload; the button re-fetches while online.

### Why this is a standalone reviewable unit

*"When the store fails to load, store-backed screens say so and offer a retry that
works."* No forward reference, no dependency on anything above it, no schema or
contract change — this is `apps/web` only, and per the standing cross-surface rules
nothing here crosses a boundary, so iOS/Android/admin are unaffected. It builds on
part 1 only in that part 1 runs its tests.

### Test plan

Vitest is `environment: node` over `lib/**/*.test.ts` — it cannot render React. So the
testable seam is deliberately the pure one:

- **`lib/config/store-routes.test.ts`** (new). Exercises the same prefix matcher
  `StoreGate` uses, extracted as a pure `isStoreBackedRoute(pathname: string): boolean`
  in `store-routes.ts` so the component holds no logic:
  - `/path`, `/path/2026-W19`, `/souls/abc`, `/wallet` → `true`
  - `/`, `/login`, `/docs/privacy`, `/u/alexis`, `/p/<id>`, `/offline`,
    `/onboarding/step-1` → `false`
  - `/pathological` → `false` (prefix matching must not match on a bare
    `startsWith`; this is the bug the `pathname === p || startsWith(p + "/")` shape
    prevents, and `AuthGate.tsx:30–32` uses the same shape).
- The `DataProvider` changes, `StoreGate` rendering and `WalletView` branch are
  covered manually (above). Adding jsdom + Testing Library to reach them is a real
  improvement and a separate decision — see Risks.

---

## Out of scope

- Converting `loadFromSupabase` to `Promise.allSettled` + the existing
  `settledOr`/`allFailedError` helpers so a single failing query degrades instead of
  emptying the store. Genuinely attractive (the helpers exist, tested, for exactly this
  shape) and genuinely a redesign of the load contract. Separate stack.
- Adding an error check to the three queries that silently degrade
  (`collectionPebbles`, `karma`, `bounce`).
- Any change to `apps/admin`, which has no test script and no CI either.
- `apps/ios` / `apps/android` CI.
- A `typecheck` script / `tsc --noEmit` step for `apps/web`.
- `apps/web/vercel.json` — untouched by both parts.

## Risks / open questions

1. **Is "required status check" actually wanted?** D1 argues no-filter + required. That
   makes every PR, including docs-only ones, wait on an `npm ci`. If the maintainer
   would rather not, the filtered variant in D1 is ready — but then it must not be
   marked required. **Needs a human decision before part 1 opens.**
2. **Node version is unpinned repo-wide.** No `.nvmrc`, no `engines`. `arkaik.yml` uses
   node 20, local dev is 26, Vercel uses its own default. The workflow pins 20 to match
   the existing workflow; pinning the repo properly is out of scope and worth its own
   chore.
3. **`AuthGate.PROTECTED_PREFIXES` and `STORE_BACKED_PREFIXES` diverge.** `/wallet`,
   `/achievements` and `/drafts` are store-backed but unprotected — a signed-out user
   reaching them today gets whatever those screens render with a null provider. That is
   a pre-existing gap outside both findings; flagged, not touched.
4. **No component-level test coverage on web at all.** The suite is pure `lib/`
   modules by design (`vitest.config.ts:5–8`). Every rendering claim in part 2 is
   verified by hand. Adding jsdom + Testing Library would let `StoreGate`, the
   `PathEmptyState` regression and the `WalletView` branch be pinned — it is the single
   highest-value follow-up to this stack and deserves its own issue.
5. **`useWallet` has no refetch**, so D10 ships copy without a retry on that one path.
6. **The stack proves the gate works only once part 1 has failed on purpose.** A
   workflow that has never gone red is not a gate. Build that into the part 1 PR
   description as a reviewer instruction.
7. **Milestone is unconfirmed** — the GitHub API is unreachable from this session; see
   below.

## Proposed issues

Labels drawn from root `CLAUDE.md`: species ∈ `feat|fix|bug|chore|docs|test|quality`;
scope = domain ∈ `core|ui|db|api|auth|facility|legal` and/or surface ∈
`web|ios|android|supabase`.

### Part 1

- **Title:** `[Test] Gate apps/web on CI — run ESLint and the Vitest suite on every PR`
- **Species:** `test`
- **Scope:** `web`, `facility`
- **PR labels:** same (no species swap; `test` is not `bug`)
- **Milestone:** the one carrying #739 / #741 / #743 (the Kritik `2026-08` remediation
  run). Confirm before opening — the last milestone named in a spec is
  `M58 · Dynamic and picture-first Path`, which is product work and probably the wrong
  home. If no hardening milestone exists, propose one.

### Part 2

- **Title:** `[Bug] A failed store load shows the first-run empty state on /path and other core screens`
- **Species:** `bug` (per the PR checklist, the **PR** carries `fix`)
- **Scope:** `web`, `ui`, `core`
- **PR labels:** `fix`, `web`, `ui`, `core`
- **Milestone:** same as part 1.

### Optional follow-up (not part of this stack)

- **Title:** `[Test] Add jsdom + Testing Library so web components are testable`
- **Species:** `test` · **Scope:** `web` — Risk 4.

## Bookkeeping

- **Decision log:** part 1 appends one entry (`web.yml`'s trigger stance is a real
  decision that a future agent could wrongly reverse — specifically the
  path-filter/required-check interaction). Part 2 appends none: it implements a rule
  `docs/agents/data-and-async.md` and the `settled-feed.ts` docstring already state.
- **Arkaik:** no bundle update. Neither part adds, renames or removes a screen, route,
  data model or endpoint; `StoreGate` is a shell component, not a view node.
- **Lab Note:** part 1 is infra → **no** note, delete the section (add `no-lab-note` if
  the advisory workflow comments). Part 2 is user-facing → **required**,
  `species: feature`, `platform: webapp` (a user-facing fix is a `feature` per the
  contract).
- **Task size:** part 1 is small (workflow + doc lines); part 2 is medium (multi-file,
  one feature, well under 500 LOC). Neither warrants the full planning ceremony beyond
  this spec; workspace-scoped lint/test/build is the gate.
