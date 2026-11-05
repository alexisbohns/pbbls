# Kritik launch night: crisis resources, iOS consent, RLS denial harness — orchestration plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan thread-by-thread. The orchestrator (main session) runs Thread 0 inline and dispatches one fresh subagent per thread with the brief in that thread's "Subagent brief" box. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the launch-gating High Kritik findings that are tractable in one supervised night: crisis resources on web, iOS and Android (SAF-01 ×3), iOS consent and age attestation at signup (GDP-ios-01, SAF-ios-02, the iOS half of GDP-supabase-01), a schema-driven RLS denial harness (TST-supabase-02) and the migration re-emission CI guard (the open half of REL-supabase-01) — and close in Kritik the five High findings main already fixed.

**Architecture:** Seven threads in three waves. Thread 0 is bookkeeping, done inline by the orchestrator. Thread 1 lands the canonical `crisis-resources.json` with one byte-identical copy and parity test per surface, and merges first (the maintainer verifies every number). Threads 2–4 are the three surface features, each one PR from `main`, sharing one predicate name (`quietSuccess`: a lowlight at intensity 3) and one screen name (Crisis resources). Thread 5 is iOS consent (#821), a two-part stack. Threads 6 and 7 are Supabase-only and never contend for a simulator or Gradle. Each thread runs in its own worktree under `.worktrees/`; iOS threads each get a dedicated simulator and a private DerivedData path.

**Tech Stack:** Next.js 16 + Vitest (node env, `lib/**` tests only); SwiftUI + Swift Testing (xcodebuild, Localizable.xcstrings); Kotlin + Compose M3 Expressive + Navigation 3 + JUnit/Robolectric; Deno harnesses against the linked Supabase project; GitHub Actions.

**Decisions taken for this plan (2026-11-05 evening, with the maintainer):**
- Production error reporting (REL-08 ×3) is deferred: Thread 0 files one decision issue; no SDK tonight.
- The crisis JSON merges before the surface threads fan out (no tree-shaped stack).
- Concurrency cap: 3 build threads + 1 harness thread at any time. The Android onboarding stack in `.worktrees/onboarding` keeps running; it is a second Gradle daemon and counts toward the cap.
- Canonical JSON lives in `packages/shared/crisis-resources.json` (the stub package finally holds something). Copies are byte-identical and pinned by a parity test per surface, like `packages/icons/icons.json` is pinned by `PbblsIconTests` / `PbblsIconsTest`.
- "High distress" = positiveness −1 and intensity 3 on every surface. No emotion-id heuristics: the two sliders are the only signal the user gave, and the predicate must be explainable in one sentence in the privacy policy.
- The quiet branch suppresses what the *client* does (karma line, sound, haptic, achievement queue). The server still grants karma: a user must never lose a reward for being honest, and SAF-ios-03 (karma keyed to disclosure) is a separate product decision.
- Every surface PR carries the `no-lab-note` label; the new Crisis resources view is recorded in the hosted Arkaik map instead (one `view` node per surface, status `development` at PR-open).

---

## Session timeline

| Wave | Threads | Starts when | Build load |
|---|---|---|---|
| 0 | T0 bookkeeping, worktrees, simulators | now | none |
| 1 | T1 crisis JSON · T5 iOS consent part A · T6 denial harness · T7 re-emission guard | after T0 | 1 xcodebuild (T5) + the onboarding Gradle |
| gate | maintainer verifies the numbers in T1, merges it | T1 PR open | — |
| 2 | T2 web crisis · T3 iOS crisis · T4 Android crisis · T5 part B | T1 merged | web + 2 xcodebuild (T3, T5 on separate sims) + 2 Gradle (T4, onboarding): **at the cap** — start T4 only when the onboarding thread is between test runs, or pause it |
| close | reviews, merges in order T6 → T7 → T2 → T4 → T3 → T5, Kritik resolves, journal pebbles | each PR green | — |

Merge order rationale: Supabase threads first (they change CI for everyone), then web (Vercel preview is the fastest device pass), then the natives. Every merge: `git fetch && git checkout main && git pull --ff-only` in the main checkout before the next `gh pr merge --squash`.

## Model routing and review cadence (token budget)

The orchestrator (Fable) coordinates only: dispatches, reads reports, decides, merges. It does not implement or read diffs line by line. Every thread and every review runs on a cheaper model via the Agent tool's `model` parameter:

| Work | Model | Why |
|---|---|---|
| T1, T2, T3, T4, T7 implementation | `sonnet` | well-specified, file-level briefs, the gates are mechanical |
| T5 (consent ledger, false-provenance rule), T6 (security harness, registry decisions) | `opus` | judgement calls with GDPR and RLS consequences |
| Reviews (one per PR, `superpowers:code-reviewer`) | `sonnet` | reads the diff against the plan task and the gate outputs; T5 and T6 reviews also on `sonnet` but with the plan's "rules you cannot bend" quoted in the brief |
| Explore / fact-finding | `haiku` or `sonnet` | read-only |

One combined review per thread PR, never per task. The orchestrator reads only the reviewer's report and the gate summary lines, and opens `gh pr diff` itself only to arbitrate a disagreement between implementer and reviewer.

---

## Thread 0 — Bookkeeping and workspace (orchestrator, inline)

**Files:** none in the repo except worktrees.

- [ ] **Step 1: Resolve the five High findings main already fixed.** Verify each PR touched the surface before resolving (rule from `project-kritik-scoped-reaudit-2026-09`): `git log --oneline main -- <path>` must show the PR number.

| Finding | Evidence path | resolved_by |
|---|---|---|
| F-2026-08-SAF-supabase-02 | `packages/supabase/supabase/migrations/20261006120000_recent_auth_enforced.sql` | https://github.com/alexisbohns/pbbls/pull/1006 |
| F-2026-08-SAF-android-03 | `apps/android/app/src/main/kotlin/app/pbbls/android/core/data/RecentAuth.kt` | https://github.com/alexisbohns/pbbls/pull/982 |
| F-2026-08-SAF-web-03 | `apps/web/lib/auth/recent-auth.ts` (run `git log` to get the web PR; if it is #1006, use that) | the PR `git log` names |
| F-2026-08-SAF-web-01 | `apps/web/lib/auth/registration-gate.ts` | https://github.com/alexisbohns/pbbls/pull/826 |
| F-2026-08-REL-supabase-02 | `.github/workflows/orphan-snap-sweep.yml` | https://github.com/alexisbohns/pbbls/pull/1020 |

Call `mcp__arkaik-mcp__kritik_resolve_finding` once per row. Leave GDP-supabase-01, GDP-ios-01, SAF-ios-02 (closed by T5), REL-supabase-01 (closed by T7), SAF-*-01 (closed by T2–T4) open until those PRs merge.

- [ ] **Step 2: File the three issues the night needs.**

```bash
cd /Users/alexis/code/pbbls
gh issue create --title "[Test] Schema-driven RLS denial harness: every table and definer RPC must have a registered cross-user expectation" \
  --label test --label db --label supabase --milestone "M68 · V1 launch" \
  --body "Cross-user denial is proven for 3 of 36 RLS tables and a handful of RPCs. Build verify-rls-denial.ts: enumerate public tables and security-definer functions from the local stack after db reset, assert anon and cross-user denial per table and non-owner refusal per RPC, fail on any object without a registered expectation. Runs in the supabase.yml schema job (no secret). Plan: docs/superpowers/plans/2026-11-05-kritik-launch-night.md Thread 6."
gh issue create --title "[Chore] CI guard: fail a PR whose new migrations re-emit the same function body twice" \
  --label chore --label infra --label supabase --milestone "M68 · V1 launch" \
  --body "create or replace has no merge semantics; purge_account already lost an append once (20260731090000_purge_account_union.sql). Add a checks-job step that diffs the PR's new migration files against main and fails when two of them create or replace the same public function, or when one re-emits purge_account / remove_connection / handle_new_user without the APPEND marker. Plan: Thread 7."
gh issue create --title "[Chore] Decide production error reporting before launch (web, iOS, Android ship no crash or error sink)" \
  --label chore --label infra --label web --label ios --label android --milestone "M68 · V1 launch" \
  --body "Kritik REL-08 on all three clients. Options: Sentry on all three with PII scrubbing (pebble names, emotions, storage paths), or Play Vitals + MetricKit + a web-only Sentry, or nothing before launch with a written triage cadence. Decision deferred on 2026-11-05; no SDK until it is taken."
```

Write the three numbers down as `RLS`, `GUARD`, `SINK`.

- [ ] **Step 3: Create the worktrees from an updated main and copy the gitignored config.**

```bash
cd /Users/alexis/code/pbbls && git fetch origin && git checkout main && git pull --ff-only
for wt in crisis-json crisis-web crisis-ios crisis-android ios-consent rls-harness ci-guard; do
  git worktree add ".worktrees/$wt" -b "wip/$wt" main
done
# gitignored config the worktrees lack (project-worktree-gitignored-config)
for wt in crisis-ios ios-consent; do cp apps/ios/Config/Secrets.xcconfig ".worktrees/$wt/apps/ios/Config/"; done
cp apps/android/secrets.properties .worktrees/crisis-android/apps/android/
cp apps/web/.env.local .worktrees/crisis-web/apps/web/
for wt in rls-harness ci-guard; do cp -R packages/supabase/supabase/.temp ".worktrees/$wt/packages/supabase/supabase/"; done
for wt in crisis-json crisis-web; do (cd ".worktrees/$wt" && npm ci --silent); done
```

The `wip/*` branches are placeholders; each thread renames its branch to the real name before its first commit (`git branch -m type/N-slug`).

- [ ] **Step 4: Two dedicated simulators for the two iOS threads** (project-ios-parallel-simulators).

```bash
RT=$(xcrun simctl list runtimes -j | jq -r '[.runtimes[] | select(.platform=="iOS" and .isAvailable)] | sort_by(.version) | last | .identifier')
DT=$(xcrun simctl list devicetypes -j | jq -r '.devicetypes[] | select(.name=="iPhone 17") | .identifier')
SIM_CRISIS=$(xcrun simctl create "iPhone 17 crisis" "$DT" "$RT"); SIM_CONSENT=$(xcrun simctl create "iPhone 17 consent" "$DT" "$RT")
echo "$SIM_CRISIS $SIM_CONSENT"
```

Pass each id to its thread as `SIM`. DerivedData per thread: `/private/tmp/claude-503/-Users-alexis-code-pbbls/ec62f6e2-f4ae-4e73-a060-3d2b5bc3d8d5/scratchpad/dd-<thread>`.

- [ ] **Step 5: Dispatch wave 1** (T1, T5-A, T6, T7) with the briefs below, then wait for the T1 PR.

---

## Thread 1 — Canonical crisis resources JSON + per-surface copies + parity tests (#1457)

**Branch:** `chore/1457-crisis-resources` · **PR title:** `chore(core): canonical crisis resources list with per-surface copies and parity tests` · labels `chore`, `core`, `legal`; milestone `M68 · V1 launch`; `no-lab-note`.

**Files:**
- Create: `packages/shared/crisis-resources.json` (canonical)
- Create: `packages/shared/README.md` (replace the stub text: what the file is, the verification rule)
- Create: `apps/web/lib/crisis/crisis-resources.json` (copy), `apps/web/lib/crisis/crisis-resources.ts` (typed decoder), `apps/web/lib/crisis/crisis-resources.test.ts`
- Create: `apps/ios/Pebbles/Resources/crisis-resources.json` (copy), `apps/ios/Pebbles/Features/Support/CrisisResources.swift` (Codable model + loader), `apps/ios/PebblesTests/Features/Support/CrisisResourcesTests.swift`
- Create: `apps/android/app/src/main/assets/crisis-resources.json` (copy), `apps/android/app/src/main/kotlin/app/pbbls/android/core/model/CrisisResources.kt` (serializable model + decoder), `apps/android/app/src/test/kotlin/app/pbbls/android/core/model/CrisisResourcesTest.kt`
- Modify: `apps/ios/project.yml` only if the Resources folder is not picked up implicitly (it is: `Pebbles` source path includes everything; do not touch it)

### The data shape (identical on every surface)

```json
{
  "version": 1,
  "verified_on": "2026-11-05",
  "worldwide": { "label": { "en": "Find a helpline anywhere", "fr": "Trouver une ligne d'écoute partout" }, "url": "https://findahelpline.com" },
  "countries": [
    { "code": "FR", "name": { "en": "France", "fr": "France" }, "lines": [
      { "label": { "en": "3114, national suicide prevention line", "fr": "3114, numéro national de prévention du suicide" }, "number": "3114", "tel": "3114", "note": { "en": "Free, 24/7", "fr": "Gratuit, 24h/24" } },
      { "label": { "en": "Emergency services", "fr": "Urgences" }, "number": "112", "tel": "112" }
    ] },
    { "code": "US", "name": { "en": "United States", "fr": "États-Unis" }, "lines": [ { "label": { "en": "988 Suicide and Crisis Lifeline", "fr": "988, ligne de crise et de prévention du suicide" }, "number": "988", "tel": "988", "note": { "en": "Call or text, 24/7", "fr": "Appel ou SMS, 24h/24" } } ] },
    { "code": "CA", "name": { "en": "Canada", "fr": "Canada" }, "lines": [ { "label": { "en": "988 Suicide Crisis Helpline", "fr": "988, ligne d'aide en cas de crise" }, "number": "988", "tel": "988", "note": { "en": "Call or text, 24/7", "fr": "Appel ou SMS, 24h/24" } } ] },
    { "code": "GB", "name": { "en": "United Kingdom", "fr": "Royaume-Uni" }, "lines": [ { "label": { "en": "Samaritans", "fr": "Samaritans" }, "number": "116 123", "tel": "116123", "note": { "en": "Free, 24/7", "fr": "Gratuit, 24h/24" } } ] },
    { "code": "IE", "name": { "en": "Ireland", "fr": "Irlande" }, "lines": [ { "label": { "en": "Samaritans", "fr": "Samaritans" }, "number": "116 123", "tel": "116123", "note": { "en": "Free, 24/7", "fr": "Gratuit, 24h/24" } } ] },
    { "code": "BE", "name": { "en": "Belgium", "fr": "Belgique" }, "lines": [ { "label": { "en": "Centre de prévention du suicide", "fr": "Centre de prévention du suicide" }, "number": "0800 32 123", "tel": "080032123", "note": { "en": "Free, 24/7", "fr": "Gratuit, 24h/24" } } ] },
    { "code": "CH", "name": { "en": "Switzerland", "fr": "Suisse" }, "lines": [ { "label": { "en": "La Main Tendue", "fr": "La Main Tendue" }, "number": "143", "tel": "143", "note": { "en": "24/7", "fr": "24h/24" } } ] }
  ]
}
```

Rules: `tel` is digits only (what the client dials); `number` is the display form; `note` is optional; every `label`/`name`/`note` carries both `en` and `fr`. **The subagent writes the numbers from issue #1457 and does not search the web; the maintainer verifies each against the official source before merge (the PR body says so and lists the sources to check).** The file ends with a single newline and is written once, then copied with `cp` (never re-serialised: the parity tests are byte comparisons).

### Tasks

- [ ] **T1.1 Write the canonical file and the README**, then `cp` it to the three copy paths.

- [ ] **T1.2 Web decoder and tests.** `apps/web/lib/crisis/crisis-resources.ts`:

```ts
import raw from "./crisis-resources.json"
import type { Locale } from "@/lib/i18n"   // check the exported locale type name in lib/i18n/index.ts and use it

export type Localized = { en: string; fr: string }
export type CrisisLine = { label: Localized; number: string; tel: string; note?: Localized }
export type CrisisCountry = { code: string; name: Localized; lines: CrisisLine[] }
export type CrisisResources = { version: number; verified_on: string; worldwide: { label: Localized; url: string }; countries: CrisisCountry[] }

export const crisisResources: CrisisResources = raw

/** The country to open on: the device region when listed, otherwise FR (the product's home market), never "none". */
export function defaultCountry(region: string | undefined, resources: CrisisResources = crisisResources): CrisisCountry {
  const found = region ? resources.countries.find((c) => c.code === region.toUpperCase()) : undefined
  return found ?? resources.countries.find((c) => c.code === "FR") ?? resources.countries[0]
}

export function pick(text: Localized, locale: Locale): string { return locale === "fr" ? text.fr : text.en }
```

`crisis-resources.test.ts` (Vitest, node): (a) byte parity: `readFileSync(resolve(process.cwd(), "../../packages/shared/crisis-resources.json"), "utf8") === readFileSync(resolve(process.cwd(), "lib/crisis/crisis-resources.json"), "utf8")` (precedent: `lib/config/stone-geometry.test.ts`, which reads a sibling surface's file); (b) every line has digits-only `tel` and both locales on every label; (c) `defaultCountry("gb")` → GB, `defaultCountry("XX")` → FR, `defaultCountry(undefined)` → FR. Run: `cd apps/web && npx vitest run lib/crisis` → 3 tests pass. If `resolveJsonModule` is off in `apps/web/tsconfig.json`, the import fails typecheck: then read the JSON with `readFileSync` in a `lib/crisis/load.ts` instead; do not change tsconfig.

- [ ] **T1.3 iOS model and tests.** `CrisisResources.swift`: `struct Localized: Codable { let en, fr: String; func text(for locale: Locale) -> String }`, `struct CrisisLine: Codable { label, number, tel, note? }`, `struct CrisisCountry: Codable`, `struct CrisisResources: Codable { static let bundled: CrisisResources }` loaded the way `SettingsLink.builtIn` does (`Bundle.main.url(forResource: "crisis-resources", withExtension: "json")`), decoding with a plain `JSONDecoder` (keys are snake_case only for `verified_on`: add `CodingKeys`). `static func defaultCountry(region: String?) -> CrisisCountry` with the same FR fallback. Tests (`@Suite("CrisisResources")`): parity by reading `packages/shared/crisis-resources.json` through `#filePath` (five `deletingLastPathComponent()` from `PebblesTests/Features/Support/`, count them: the precedent `PbblsIconTests.swift` L11-17 reaches the repo root with five), comparing `Data` byte-for-byte with the bundled file; `bundled.countries.count == 7`; `defaultCountry(region: "gb").code == "GB"`; `defaultCountry(region: nil).code == "FR"`. Run from `apps/ios`: `xcodegen generate && TEST_RUNNER_TZ=Europe/Paris xcodebuild test -scheme Pebbles -destination "id=$SIM" -derivedDataPath "$DD" -only-testing:PebblesTests/CrisisResources -skipMacroValidation -quiet CODE_SIGNING_ALLOWED=NO` → passes. Use `SIM_CRISIS` for this thread.

- [ ] **T1.4 Android model and tests.** `CrisisResources.kt` in `core/model` with `@Serializable` data classes (`Localized`, `CrisisLine` with `note: Localized? = null`, `CrisisCountry`, `CrisisResources` with `@SerialName("verified_on") val verifiedOn: String`), `fun decodeCrisisResources(body: String): CrisisResources` using a `Json { ignoreUnknownKeys = true }` like `settingsLinkJson`, and `fun CrisisResources.defaultCountry(region: String?): CrisisCountry`. Reading the asset goes in T4 (a `CrisisResourcesService` in `core/data`, injected `builtIn: () -> String` like `SettingsLinksService`), not here. Test `CrisisResourcesTest` (plain JVM): parity `File("../../../packages/shared/crisis-resources.json").readText() == File("src/main/assets/crisis-resources.json").readText()` (the precedent `PbblsIconsTest` resolves `../../../packages/icons/icons.json` from `apps/android/app`); decode gives 7 countries; `defaultCountry("gb")` → GB; `defaultCountry(null)` → FR. Run: `cd apps/android && export ANDROID_HOME=$HOME/Library/Android/sdk && ./gradlew testDebugUnitTest --tests '*CrisisResourcesTest*' -q` and `./gradlew ktlintCheck -q`.

- [ ] **T1.5 Commit and open the PR.** One commit per surface is fine (`chore(core): canonical crisis resources list`, `chore(web): …`, `chore(ios): …`, `chore(android): …`). PR body: `Resolves #1457`, the source URLs the maintainer verifies (3114.fr, 988lifeline.org, 988.ca, samaritans.org, preventionsuicide.be, 143.ch, findahelpline.com), and the sentence "numbers not yet verified by the maintainer". Gate before opening: web `npm run lint --workspace=apps/web && npx vitest run lib/crisis`; iOS suite above; Android ktlint + the test. Do not run the full suites on three surfaces for a JSON file.

> **Subagent brief (T1):** worktree `/Users/alexis/code/pbbls/.worktrees/crisis-json`, rename the branch to `chore/1457-crisis-resources`. Execute Thread 1 of `docs/superpowers/plans/2026-11-05-kritik-launch-night.md` exactly (read it from the main checkout: `/Users/alexis/code/pbbls/docs/superpowers/plans/…`). `SIM=<SIM_CRISIS>`, `DD=<scratchpad>/dd-crisis-json`. Do not look up phone numbers online; use the issue's list verbatim. Report: the PR URL, the three gate outputs, and any place the plan was wrong.

---

## Thread 2 — Web crisis resources (#1459)

**Branch:** `feat/1459-crisis-resources-web` (plain slug, Vercel must build it) · **PR title:** `feat(web): crisis resources page, Get support row, quiet success for high-distress pebbles` · labels `feat`, `ui`, `legal`, `web`; milestone `M68 · V1 launch`; `no-lab-note`. Starts from `main` after T1 merged.

**Files:**
- Create: `apps/web/app/@detail/(pane)/settings/support/page.tsx` (the Crisis resources page, client component)
- Create: `apps/web/components/support/CrisisResourcesList.tsx` (country select + tel links; the page renders it)
- Create: `apps/web/components/settings/SupportSection.tsx` (one `SettingsRow` with `LifeBuoy`, `href="/settings/support"`)
- Modify: `apps/web/app/@detail/(pane)/settings/page.tsx` — insert `<SupportSection />` directly above `<LegalSection />`
- Modify: `apps/web/lib/record/complete.ts` — add `quietSuccess(draft)`; `apps/web/lib/record/complete.test.ts` — its tests
- Modify: `apps/web/components/record/complete/CompleteSuccessStep.tsx` L42 and L57-63 — hide the karma line and show the calm line when quiet; `CompleteFlow.tsx` L228-234 — pass `quiet`
- Modify: `apps/web/lib/i18n/messages/en.json`, `fr.json` — keys under `settings.support` and `record.flow.success` (insert as text at the namespace anchor, never re-serialise the file)
- Modify: `apps/web/lib/i18n/messages.d.ts` if keys are typed there

### Tasks

- [ ] **T2.1 Predicate (TDD).** In `complete.test.ts` add `describe("quietSuccess")`: `{ positiveness: -1, intensity: 3 }` → true; `(-1, 2)` → false; `(1, 3)` → false; `(0, 3)` → false. Then in `complete.ts`:

```ts
/** A lowlight at full intensity: the one state where celebrating would be wrong. Mirrors Valence.quietSuccess on iOS and Android. */
export function quietSuccess(draft: Pick<CompleteDraft, "positiveness" | "intensity">): boolean {
  return draft.positiveness === -1 && draft.intensity === 3
}
```

Run `npx vitest run lib/record/complete.test.ts` → green. Commit `feat(web): quietSuccess predicate for high-distress pebbles`.

- [ ] **T2.2 Success step.** `CompleteSuccessStep` gains `quiet: boolean`. When quiet: `karma` is 0 whatever `karmaDelta` is (the server still granted it); render instead of the karma line a `<p>` with `t("quiet")` (en: "Thank you for keeping this. If today is heavy, support is one tap away." / fr: "Merci d'avoir gardé ce moment. Si la journée est lourde, un soutien est à un geste.") and a `Link href="/settings/support"` labelled `t("getSupport")` (en "Get support" / fr "Trouver du soutien"). `CompleteFlow` computes `quiet={quietSuccess(flow.draft)}`. The `PebbleFramed animateIn` stays (it is the stone appearing, not a celebration). Keys go in `record.flow.success`. Commit `feat(web): quiet success branch`.

- [ ] **T2.3 Crisis resources page.** `CrisisResourcesList`: `const { locale } = useLocale()`; `defaultCountry(navigator.language.split("-")[1])` inside `useEffect` (SSR-safe: initial FR, then the device region); a shadcn `Select` of countries (name via `pick`), the lines as a `SettingsGroup` of `SettingsRow href={`tel:${line.tel}`} external` with the label, number and note; the worldwide link last as `SettingsRow href external`. Page title `t("settings.support.title")` (en "Get support" / fr "Trouver du soutien"), intro `t("settings.support.intro")` (en "If you are in danger or thinking about hurting yourself, call now. These lines are free and confidential." / fr "Si tu es en danger ou que tu penses à te faire du mal, appelle maintenant. Ces lignes sont gratuites et confidentielles."), footer `t("settings.support.verified", { date })` (en "Numbers checked on {date}" / fr "Numéros vérifiés le {date}"). No red: the page uses the normal text colour; the tel rows use `--primary`. Add the route to the PWA precache if `next.config` lists routes explicitly (check `apps/web/next.config.*` and `public/sw*`; if the precache is automatic, do nothing). Commit `feat(web): crisis resources page`.

- [ ] **T2.4 Settings row.** `SupportSection.tsx` mirrors `LegalSection`'s `LinkGroup` shape: `ListGroupHeader` `t("settings.support.title")`, one `SettingsRow icon={LifeBuoy} href="/settings/support"`. Insert it above `LegalSection` in the settings page. Commit `feat(web): Get support row in Settings`.

- [ ] **T2.5 Gate, map, PR.** `cd apps/web && npm run lint && npx vitest run && npm run build` (build: this adds a route). Arkaik: `create_node` a `view` "Crisis resources (web)" under the Settings view (edge from the web Settings view; find its id with `list_nodes` filtered on "Settings"), status `development`; `update_node` the web Settings view's metadata only if you re-send its full metadata (it is replaced wholesale). PR body: `Resolves #1459`, key files, "Arkaik: view node created", `no-lab-note`. Vercel preview URL goes in the report for the maintainer's device pass.

> **Subagent brief (T2):** worktree `.worktrees/crisis-web` (first `git fetch && git reset --hard origin/main` so T1's merge is in), rename the branch to `feat/1459-crisis-resources-web`, serve on `:3100` if you need the dev server. Execute Thread 2 of the plan. Arkaik writes need `ARKAIK_TOKEN` in the environment: if the `arkaik-mcp` tools are not available, say so in the report and leave the map step to the orchestrator; never edit `docs/arkaik/bundle.json`. Report: PR URL, gate outputs, Vercel preview URL.

---

## Thread 3 — iOS crisis resources (#1460)

**Branch:** `feat/1460-crisis-resources-ios` · **PR title:** `feat(ios): crisis resources sheet, Get support row, quiet success for high-distress pebbles` · labels `feat`, `ui`, `legal`, `ios`; milestone `M68 · V1 launch`; `no-lab-note`. Starts from `main` after T1 merged. Simulator `SIM_CRISIS`, DerivedData `dd-crisis-ios`.

**Files:**
- Create: `apps/ios/Pebbles/Features/Support/CrisisResourcesSheet.swift`
- Modify: `apps/ios/Pebbles/Features/Path/Models/Valence.swift` — `var quietSuccess: Bool { self == .lowlightLarge }`; `PebblesTests/Features/Path/ValenceTests.swift` (create if absent) — the four cases
- Modify: `apps/ios/Pebbles/Features/Complete/CompleteFlowModel.swift` L147-152 — `succeed(with:quiet:)` skips `haptic(.success)` when quiet; `PebblesTests/Features/Complete/CompleteFlowModelTests.swift` — a case asserting no haptic on the quiet path (the recorder at L13)
- Modify: `apps/ios/Pebbles/Features/Complete/CompleteFlowView.swift` L152-181 — when `model.draft.valence?.quietSuccess == true`: skip `karma.notifyEarned` and `achievements.fireCheck()`, call `model.succeed(with:…, quiet: true)`; L83-90 — pass `quiet:` to the step
- Modify: `apps/ios/Pebbles/Features/Complete/Steps/CompleteSuccessStep.swift` L34-44 — the karma line only when `!quiet`; when quiet, the calm sentence and a `Button("Get support")` presenting `CrisisResourcesSheet` via `.sheet(isPresented:)`
- Modify: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift` — a `supportSection` placed directly above `linkSections` (L159): one `Button` row with `Label("Get support", systemImage: "lifepreserver")` setting `@State private var showsCrisisResources = true`, and a `.sheet(isPresented: $showsCrisisResources) { CrisisResourcesSheet() }` next to the other sheets at L191-199. Presented, not pushed: this sheet has no `navigationDestination` and the other rows present too
- Modify: `apps/ios/Pebbles/Resources/Localizable.xcstrings` — insert the new keys as text at the end of the `strings` object (never re-serialise the catalog); `LocalizationTests.everyEntryHasBothLocales` is the gate

### Tasks

- [ ] **T3.1 Predicate (TDD)**: `ValenceTests` with `#expect(Valence.lowlightLarge.quietSuccess)` and `#expect(!Valence.lowlightMedium.quietSuccess)`, `!highlightLarge`, `!neutralLarge`. Implement. Commit `feat(ios): Valence.quietSuccess`.
- [ ] **T3.2 Model**: test "quiet success does not buzz": `model.succeed(with: result, quiet: true)` → recorder has no `.success`; the existing `outcomes()` keeps `.success` on the default path. Implement `func succeed(with result: CompletionResult, quiet: Bool = false)`. Commit `feat(ios): quiet completion skips the success haptic`.
- [ ] **T3.3 View wiring and step**: in `complete()`, `let quiet = model.draft.valence?.quietSuccess ?? false`; `if !quiet { karma.notifyEarned(…); achievements.fireCheck() }`. In the step, `quiet` hides the karma text and shows: `Text("Thank you for keeping this. If today is heavy, support is one tap away.")` (fr: "Merci d'avoir gardé ce moment. Si la journée est lourde, un soutien est à un geste.") and `Button("Get support")` (fr "Trouver du soutien"). Commit `feat(ios): quiet success branch`.
- [ ] **T3.4 CrisisResourcesSheet**: `NavigationStack { List { intro; Picker("Country", selection: $code) over bundled.countries; ForEach(lines) { Link(destination: URL(string: "tel:\(line.tel)")!) { VStack(alignment: .leading) { Text(line.label.text(for: locale)); Text(line.number).font(.title3); if let note { Text(note.text(for: locale)).foregroundStyle(.secondary) } } } .pebblesListRow(...) }; Link(worldwide.label, destination: worldwide.url); footer "Numbers checked on \(verified_on)" } .pebblesList().pebblesToolbarTitle("Get support") }` with a `PebbleToolbarButton("Done")`. Initial `code` = `CrisisResources.defaultCountry(region: Locale.current.region?.identifier).code`. Strings: "Get support", "If you are in danger or thinking about hurting yourself, call now. These lines are free and confidential.", "Country", "Numbers checked on %@", "Done" with their fr values (fr: "Si tu es en danger ou que tu penses à te faire du mal, appelle maintenant. Ces lignes sont gratuites et confidentielles.", "Pays", "Numéros vérifiés le %@", "OK"). No red. Commit `feat(ios): crisis resources sheet`.
- [ ] **T3.5 Settings row**, as described in Files. Commit `feat(ios): Get support row in Settings`.
- [ ] **T3.6 Gate, map, PR.** `xcodegen generate && TEST_RUNNER_TZ=Europe/Paris xcodebuild test -scheme Pebbles -destination "id=$SIM" -derivedDataPath "$DD" -only-testing:PebblesTests/CompleteFlowModel -only-testing:PebblesTests/ValenceTests -only-testing:PebblesTests/CrisisResources -only-testing:PebblesTests/Localization -skipMacroValidation -quiet CODE_SIGNING_ALLOWED=NO` → green; then one full `xcodebuild build` (no test) to prove the app compiles; the References suite fails locally on main (project-ios-local-reference-baseline), CI is the gate. Arkaik view node "Crisis resources (iOS)" under the iOS Settings view, `development`. PR body `Resolves #1460`, `no-lab-note`, "device pass owed on the next TestFlight build".

> **Subagent brief (T3):** worktree `.worktrees/crisis-ios` (reset to `origin/main` first), branch `feat/1460-crisis-resources-ios`, `SIM=<SIM_CRISIS>`, `DD=<scratchpad>/dd-crisis-ios`; delete only that DD folder before a trusted run, never the global DerivedData. Execute Thread 3. Keep Liquid Glass toolbar buttons and the sheet presentation native (feedback_platform_native_chrome). Report: PR URL, gate output, anything the plan got wrong about line numbers.

---

## Thread 4 — Android crisis resources (#1461)

**Branch:** `feat/1461-crisis-resources-android` · **PR title:** `feat(android): crisis resources screen, Get support row, quiet success for high-distress pebbles` · labels `feat`, `ui`, `legal`, `android`; milestone `M68 · V1 launch`; `no-lab-note`. Starts from `main` after T1 merged. `export ANDROID_HOME=$HOME/Library/Android/sdk` first, always.

**Files:**
- Modify: `core/model/Valence.kt` — `val quietSuccess: Boolean get() = this == LOWLIGHT_LARGE`; test `core/model/ValenceTest.kt` (create if absent)
- Create: `core/data/CrisisResourcesService.kt` — `@Singleton class CrisisResourcesService @Inject constructor(@ApplicationContext context: Context)` with the internal `builtIn: () -> String` constructor like `SettingsLinksService` L46-61, exposing `val resources: CrisisResources` (decoded once, lazily); Hilt-provided like `SettingsLinksService`
- Create: `features/profile/support/CrisisResourcesScreen.kt` (+ `CrisisResourcesViewModel.kt` holding `selectedCode` and the resources)
- Modify: `navigation/PebblesKeys.kt` — `@Serializable data object CrisisResources : PebblesKey` (a modal, not a `BarKey`); `navigation/PebblesEntryProvider.kt` — `entry<PebblesKey.CrisisResources>(metadata = NavTransitions.forKey(PebblesKey.CrisisResources)) { CrisisResourcesScreen(onDismiss = navigator::goBack) }`; `test/…/navigation/PebblesKeysTest.kt` — add it to the round-trip list (L81) and the modals list (L157)
- Modify: `features/profile/SettingsContent.kt` — a `ListGroup(header = stringResource(R.string.settings_support_get_header), items = listOf({ SettingsLinkItem(stringResource(R.string.settings_support_get_row)) { actions.onOpenCrisisResources() } }))` placed directly above the links sections (L402); `SettingsActions` gains `onOpenCrisisResources: () -> Unit = {}`; `SettingsScreen.kt` and the Settings entry (L257-267) wire it to `navigator.navigate(PebblesKey.CrisisResources)`
- Modify: `features/path/complete/CompleteFlowModel.kt` L77-80 — `succeed(result, quiet: Boolean = false)` skips `haptic(TapHaptic.SUCCESS)` when quiet; `CompleteFlowModelTest` L83-93 — a quiet case asserting haptics `[WARNING]` only
- Modify: `features/path/complete/CompleteFlowViewModel.kt` ~L201-215 — `val quiet = answers.valence?.quietSuccess == true`; `if (!quiet) { karma.notifyEarned(…); achievements.fireCheck() }`; `model.succeed(result, quiet)`; `CompleteFlowViewModelTest` — a quiet case: achievements not checked, karma not notified
- Modify: `features/path/flow/steps/RecordSuccessStep.kt` L44-53 — `quiet: Boolean` parameter; L86-105 karma line only when `!quiet`; when quiet, `Text(R.string.record_success_quiet)` and a `TextButton(onClick = onOpenSupport) { Text(R.string.record_success_get_support) }`; `CompleteFlowScreen.kt` L154-163 passes `quiet` and `onOpenSupport`
- Modify: `res/values/strings.xml`, `res/values-fr/strings.xml` — `settings_support_get_header` ("Get support"/"Trouver du soutien"), `settings_support_get_row` ("Helplines, free and confidential"/"Lignes d'écoute, gratuites et confidentielles"), `record_success_quiet`, `record_success_get_support`, `crisis_intro`, `crisis_country`, `crisis_worldwide`, `crisis_verified_on` (`%1$s`), `crisis_done`; en and fr key sets must stay identical (`LocalizationParityTest` L66-75)
- Create: `screenshotTest/kotlin/app/pbbls/android/CrisisResourcesScreenshots.kt` — one preview; references are CI-only (project-android-screenshot-gate-ci-only): do not re-baseline locally, note in the PR that the screenshot job needs its baseline approved

### Tasks

- [ ] **T4.1 Predicate (TDD)** `ValenceTest`: LOWLIGHT_LARGE true, LOWLIGHT_MEDIUM / HIGHLIGHT_LARGE / NEUTRAL_LARGE false. Commit `feat(android): Valence.quietSuccess`.
- [ ] **T4.2 Model and ViewModel quiet paths** with their two tests (`CompleteFlowModelTest`, `CompleteFlowViewModelTest`, mirroring the existing cases at L83-93 and L80-101). Commit `feat(android): quiet completion skips haptic, karma and achievements`.
- [ ] **T4.3 Success step** quiet rendering and the `CompleteFlowScreen` wiring (`onOpenSupport = { navigator.navigate(PebblesKey.CrisisResources) }` reaches the screen through the existing callbacks; if `CompleteFlowScreen` has no navigator, add `onOpenSupport: () -> Unit` to its parameters and wire it in the entry provider). Commit `feat(android): quiet success branch`.
- [ ] **T4.4 Service, key, screen.** `CrisisResourcesScreen`: M3 `Scaffold` with a `TopAppBar` (title `settings_support_get_header`, a back-arrow navigation icon; `crisis_done` is unused on Android, drop it from the string list), intro text, an `ExposedDropdownMenuBox` of countries, a `ListGroup` of `SegmentedListItem` per line opening `Intent(Intent.ACTION_DIAL, Uri.parse("tel:${line.tel}"))`, the worldwide link via `openSettingsLink`-style `CustomTabsIntent` or `ACTION_VIEW`, and the verified-on footer. Initial country: `defaultCountry(Locale.getDefault().country)`. Native M3 only (feedback_platform_native_chrome). Konsist: everything above lives in `features/profile/support` or `core/*`; never import `features` from `core`. Commit `feat(android): crisis resources screen`.
- [ ] **T4.5 Settings row + strings**, run `./gradlew testDebugUnitTest --tests '*LocalizationParityTest*' --tests '*PebblesKeysTest*' -q`. Commit `feat(android): Get support row in Settings`.
- [ ] **T4.6 Gate, map, PR.** `./gradlew ktlintCheck testDebugUnitTest lint -q` (lint is the real gate, feedback_upstream_version_lint). Arkaik view node "Crisis resources (Android)" under the Android Settings view, `development`; record nothing in `docs/arkaik/`. PR body `Resolves #1461`, `no-lab-note`, "screenshot baseline for CrisisResourcesScreenshots to approve in CI", "device pass on the Play build owed".

> **Subagent brief (T4):** worktree `.worktrees/crisis-android` (reset to `origin/main` first), branch `feat/1461-crisis-resources-android`, `export ANDROID_HOME=$HOME/Library/Android/sdk`. Another Gradle daemon runs in `.worktrees/onboarding`; if a build fails with a daemon or port clash, retry once with `--no-daemon`. Execute Thread 4. Report: PR URL, the three gate outputs, Konsist result.

---

## Thread 5 — iOS consent at signup and the consent gate (#821)

**Stack of two parts, `gh stack`:** part A `feat/821-consent-signup-ios` (`feat(ios): record every consent at signup, versioned, with the surface`), part B `feat/821-consent-gate-ios` (`feat(ios): gate the app until every consent is on record`). Labels `feat`, `auth`, `ios`; milestone `M68 · V1 launch`; `no-lab-note` (the consent gate view goes in the Arkaik map). Simulator `SIM_CONSENT`, DerivedData `dd-ios-consent`. Part A starts in wave 1; part B after A is reviewed (same worktree, stacked branch).

The issue text (#821) is the spec: read it in full with `gh issue view 821` before starting. The Android implementation is the reference and iOS mirrors it 1:1 in behaviour: `core/model/LegalVersions.kt`, `core/data/ConsentService.kt`, `core/data/ConsentPreferences.kt`, `features/consent/ConsentGateLogic.kt`, `ConsentGateViewModel.kt`, `ConsentGateScreen.kt`, and `docs/superpowers/specs/2026-09-27-android-consent-gate-design.md` §5.

### Part A — signup payload and the two missing checkboxes

**Files:**
- Create: `apps/ios/Pebbles/Services/LegalVersions.swift` — `enum LegalVersions { static let terms = "1.1.0"; static let privacy = "1.3.0" }` **read the current versions from the frontmatter of `apps/web/docs/terms/en.md` and `apps/web/docs/privacy/en.md` (or wherever `docs/terms/en.md` lives: `git ls-files | grep 'terms/en.md'`) and pin those**; `PebblesTests/Services/LegalVersionsTests.swift` reads both frontmatters through `#filePath` and asserts equality (the Android `LegalVersionsTest` is the model)
- Create: `apps/ios/Pebbles/Services/ConsentMetadata.swift` — `static func signup(now: Date) -> [String: AnyJSON]` producing exactly the keys in the #821 table: `terms_accepted_at`, `terms_version`, `privacy_accepted_at`, `privacy_version`, `health_data_consent_at`, `health_data_consent_version`, `age_attested_at`, `age_attestation_version`, `signup_surface: "ios"`, with timestamps at whole seconds (`ISO8601DateFormatter` with `.withInternetDateTime`, no fractional seconds); `PebblesTests/Services/ConsentMetadataTests.swift` asserts the key set, the surface and a whole-second timestamp (a foreign-payload test: compare against the Android `consentMetadata()` fixture if one exists under `apps/android/app/src/test`, else against the literal expected dictionary)
- Modify: `apps/ios/Pebbles/Services/SupabaseService.swift` L70-87 — `signUp` sends `ConsentMetadata.signup(now:)`
- Modify: `apps/ios/Pebbles/Features/Auth/AuthView.swift` — two more `PebblesCheckbox` rows at L72-87 (`healthDataAccepted`: "I agree that Pebbles stores the emotions I record (health data)." / fr "J'accepte que Pebbles conserve les émotions que j'enregistre (données de santé)."; `ageAttested`: "I am 16 years old or over." / fr "J'ai 16 ans ou plus."), reset in `.onChange(of: mode)` L125-131, required by `canSubmit` L172-190 in signup mode, and **the Apple and Google buttons (L221-243) are disabled in signup mode until all four are checked** (web precedent: `canSubmitRegistration` gates both OAuth buttons); `PebblesTests/Features/Auth/AuthViewTests.swift` (create or extend) — `canSubmit` false with three of four, true with four

- [ ] **T5A.1** LegalVersions + test → commit `feat(ios): pin the legal document versions`.
- [ ] **T5A.2** ConsentMetadata + test → commit `feat(ios): signup consent metadata with the ios surface`.
- [ ] **T5A.3** signUp wiring + AuthView checkboxes + canSubmit test → commit `feat(ios): ask for health-data consent and the 16+ attestation at signup`.
- [ ] **T5A.4** Gate: `-only-testing:PebblesTests/LegalVersions -only-testing:PebblesTests/ConsentMetadata -only-testing:PebblesTests/AuthView -only-testing:PebblesTests/Localization` green, `xcodebuild build` green. Open PR A: `Resolves #821` goes on **part B** only; part A's body says "Part 1 of #821".

### Part B — the post-auth consent gate overlay

**Files:**
- Create: `apps/ios/Pebbles/Services/ConsentService.swift` — `func missing() async throws -> [ConsentKind]` reading `user_consents` for the session user (the kinds `terms`, `privacy`, `health_data`, `age_assurance`; a row satisfies when its version is same-or-newer than `LegalVersions`), `func record(kind:version:source:)` calling `rpc("record_consent")` with `source` = `ios_oauth` for Apple/Google accounts (`session.user.appMetadata["provider"]`) else `ios_settings`; a protocol so tests fake it
- Create: `apps/ios/Pebbles/Services/ConsentPreferences.swift` — `UserDefaults` cache of "passed for user id X at versions Y" so a consented user is not locked out offline
- Create: `apps/ios/Pebbles/Features/Consent/ConsentGateLogic.swift` — pure: `static func decide(rows:, versions:, cache:, online:) -> Decision` (`.pass`, `.ask([ConsentKind])`, `.blockedOffline`); `PebblesTests/Features/Consent/ConsentGateLogicTests.swift` — every case in the Android `ConsentGateLogicTest`, ported one to one
- Create: `apps/ios/Pebbles/Features/Consent/ConsentGateView.swift` — an overlay in `RootView`'s authed inner `ZStack` (L125-156), above the tabs and below `HeldCardHost`; asks only the missing kinds as switches; fails closed with Retry and Log out; never offers withdrawal (`age_assurance`, `terms`, `privacy` are non-withdrawable)
- Modify: `apps/ios/Pebbles/RootView.swift` — `@State private var consentDecision`; evaluate on `session?.user.id` change (L224-226) and on foreground; the onboarding `fullScreenCover` (L133-138) must wait for `.pass`
- Modify: `Localizable.xcstrings` — the gate's strings with fr

- [ ] **T5B.1** Logic + ported tests (TDD) → commit `feat(ios): consent gate decision logic`.
- [ ] **T5B.2** ConsentService + Preferences (+ a test for the cache round-trip) → commit `feat(ios): consent ledger service and offline cache`.
- [ ] **T5B.3** Overlay view + RootView wiring → commit `feat(ios): gate the app until every consent is on record`.
- [ ] **T5B.4** Gate: the four suites + Localization + `xcodebuild build`. Arkaik: `create_node` view "Consent gate (iOS)" (the Android one exists: mirror its edges), `development`. Open PR B stacked on A with `Resolves #821`. Report that the device pass (fresh Apple sign-in on the maintainer's iPhone 15, then `select * from user_consents` for that user) is the maintainer's. Reviewer subagent on the combined diff before merge.

> **Subagent brief (T5):** worktree `.worktrees/ios-consent`, `SIM=<SIM_CONSENT>`, `DD=<scratchpad>/dd-ios-consent`. Execute Thread 5 part A, open its PR, report; you will be resumed for part B. Read #821 in full and the Android files named above before writing a line. The one rule you cannot bend: `signup_surface: "ios"` is sent on every signup (a missing key stamps `web_register`, false provenance that cannot be corrected). Timestamps at whole seconds. Never run against a real user; use the `grades-verify-*@example.test` naming if you sign up a throwaway on the linked project, and delete it through the delete-account function.

---

## Thread 6 — Schema-driven RLS denial harness (issue `RLS`)

**Branch:** `test/RLS-rls-denial-harness` · **PR title:** `test(db): schema-driven RLS denial harness over every table and definer function` · labels `test`, `db`, `supabase`; milestone `M68 · V1 launch`; no Lab Note (`no-lab-note`).

**Design.** The harness runs **in the `schema` job against the local stack after `supabase db reset`** (no secret, so it can be a required check), and also locally against a native Postgres. It has two layers:

1. **Inventory**: `psql` queries over `pg_class`/`pg_policies`/`pg_proc` produce the live list of `public` tables with RLS and of `security definer` functions in `public` and `private`.
2. **Expectations**: `packages/supabase/scripts/rls-expectations.json`, a registry keyed by object name. Every table entry says what anon and a non-owner authenticated user may do (`{"select": "none" | "all" | "own" | "public-rows", "insert": …, "update": …, "delete": …}`); every function entry is one of `"owner-only"`, `"admin-only"`, `"trigger"` (not callable; the harness asserts `execute` is revoked from `anon` and `authenticated`), `"public-read"` (callable by anyone, returns a projection: the harness asserts it never returns `user_id`), `"service-only"`. **An object absent from the registry fails the run.** That is the completeness check the finding asks for.
3. **Probes**: with the local stack's anon key and two throwaway users (created with the `signUp` pattern from `verify-pebble-visibility.ts` L73-81 against `http://127.0.0.1:54321`), for every table: anon `select` returns 0 rows or 401/permission error; user B `select` on a row user A inserted returns 0 rows (or the `public-rows` subset); B `update`/`delete` on A's row affects 0 rows; for every `owner-only`/`admin-only` function: B calling with A's ids gets an error or an empty result. Seeding one row per table is the hard part: the registry entry carries a `seed` object (`{"via": "rpc", "name": "save_moment", "args": {...}}` or `{"via": "insert", "row": {...}}`) or `"seed": "none"` for reference tables (`emotions`, `domains`, `card_types`, `achievements`, `guides`, `settings_links`, `reserved_handles`, `emotion_categories`, `glyphs`-system rows), which are asserted read-only for both users instead.

**Files:**
- Create: `packages/supabase/scripts/verify-rls-denial.ts` (Deno, same shape as the other harnesses: `check()`, `Summary:`, exit code)
- Create: `packages/supabase/scripts/rls-expectations.json` (one entry per live object: 36 tables, ~79 functions; the subagent derives the list from the local catalog, not from grep)
- Create: `packages/supabase/scripts/rls-inventory.sql` (the two catalog queries, run with `psql "$DATABASE_URL" -At -F, -f`)
- Modify: `packages/supabase/package.json` — `"db:verify:rls": "deno run --allow-env --allow-net --allow-read --allow-run scripts/verify-rls-denial.ts"`; **not** added to the `db:verify` chain (it needs the catalog, so it is a `schema`-job gate, not a `verify`-job one)
- Modify: `.github/workflows/supabase.yml` `schema` job — after "Apply every migration to an empty database" (L369-371): a step `RLS denial over the whole catalog` running `.github/scripts/verify-harness.sh "RLS denial" db:verify:rls` with `SUPABASE_URL=http://127.0.0.1:54321`, `SUPABASE_ANON_KEY` from `supabase status -o env`, `DATABASE_URL=postgresql://postgres:postgres@127.0.0.1:54322/postgres`
- Modify: `packages/supabase/CLAUDE.md` harness table — one row (`db:verify:rls`, local stack, schema job) and the orphan prefix `rls-verify-`

- [ ] **T6.1** Inventory SQL + a tiny Deno test (`deno test`) that parses its CSV. Run it against the local Homebrew Postgres after replaying the chain (project-local-postgres-for-migrations: `createdb pbbls_rls && for f in supabase/migrations/*.sql; do psql pbbls_rls -f $f; done`, or `supabase db reset` if the maintainer's Docker-free setup allows; prefer the native Postgres). Commit `test(db): rls catalog inventory`.
- [ ] **T6.2** Registry: generate the skeleton from the inventory (`"TODO"` values), then fill every entry by reading the policies (`select * from pg_policies where schemaname='public'`) and the function grants. Any entry still `"TODO"` fails the harness. Commit `test(db): rls expectations registry`.
- [ ] **T6.3** Harness: inventory → registry completeness → probes. Seeds via existing RPCs wherever one exists (AGENTS.md rule: never stitch client writes where an RPC exists). Commit `test(db): rls denial harness`.
- [ ] **T6.4** Workflow step + CLAUDE.md row + run `actionlint` on the workflow (project-arkaik-screenshot-events: run actionlint on workflow edits). `npm run lint --workspace=packages/supabase` (deno check). Commit `chore(ci): run the rls denial harness in the schema job`.
- [ ] **T6.5** PR body: `Resolves #RLS`, the counts (tables, functions, registry entries), the objects found with no policy at all if any (those are findings for the maintainer, listed by name, never by Kritik id). Reviewer subagent before merge.

> **Subagent brief (T6):** worktree `.worktrees/rls-harness`, branch `test/<RLS>-rls-denial-harness`. Execute Thread 6. Never run this harness against the linked production project: `SUPABASE_URL` must be `127.0.0.1` or the harness refuses (add that guard first). Report: PR URL, the inventory counts, every registry decision you were unsure about (list them, the orchestrator decides), and the gate outputs.

---

## Thread 7 — Migration re-emission CI guard (issue `GUARD`)

**Branch:** `chore/GUARD-migration-reemission-guard` · **PR title:** `chore(ci): fail a PR whose new migrations re-emit the same function twice or drop an append marker` · labels `chore`, `infra`, `supabase`; milestone `M68 · V1 launch`; `no-lab-note`.

**Files:**
- Create: `.github/scripts/check-migration-reemissions.sh`
- Modify: `.github/workflows/supabase.yml` `checks` job — `actions/checkout@v7` gets `fetch-depth: 0` (needed to diff against main), and a step after "Migration versions are unique" (L304-310): `Re-emitted function bodies` running the script with `origin/${{ github.base_ref }}`
- Create: `.github/scripts/check-migration-reemissions.test.sh` (bats-free: a shell test that builds two temp migration files and asserts the exit codes)

The script:

```bash
#!/usr/bin/env bash
# Fails when the migrations added by this PR (a) re-emit the same public function more than once,
# or (b) re-emit purge_account / remove_connection / handle_new_user without their APPEND marker.
# create or replace has no merge semantics; see 20260731090000_purge_account_union.sql.
set -euo pipefail
base="${1:?base ref}"; dir="${2:-packages/supabase/supabase/migrations}"
mapfile -t new < <(git diff --name-only --diff-filter=A "$base...HEAD" -- "$dir" | grep '\.sql$' || true)
[ ${#new[@]} -eq 0 ] && { echo "no new migrations"; exit 0; }
fail=0
# (a) same function created in two new files
grep -hoiE 'create (or replace )?function (public|private)\.[a-z_0-9]+' "${new[@]}" \
  | sed -E 's/.*function //' | sort | uniq -d | while read -r fn; do
    echo "::error::$fn is re-emitted by more than one new migration: union the bodies in one file"; fail=1; done
# (b) marker presence
declare -A marker=( [public.purge_account]='>>> APPEND new per-user tables' [public.remove_connection]='>>> M52/M53: sever seams' [public.handle_new_user]='>>> APPEND later signup-metadata' )
for f in "${new[@]}"; do for fn in "${!marker[@]}"; do
  if grep -qiE "create (or replace )?function $fn\b" "$f" && ! grep -q "${marker[$fn]}" "$f"; then
    echo "::error file=$f::$fn re-emitted without its append marker"; fail=1; fi
done; done
exit $fail
```

(The `while read` subshell cannot set `fail`: write (a) with a temp file or `mapfile` so the exit code is real; the test must catch that.)

- [ ] **T7.1** Script + shell test (two fixtures: duplicate function → exit 1; marker missing → exit 1; clean → exit 0). Run `bash .github/scripts/check-migration-reemissions.test.sh`. Commit `chore(ci): migration re-emission guard script`.
- [ ] **T7.2** Workflow step, `actionlint .github/workflows/supabase.yml`. Commit `chore(ci): run the re-emission guard on supabase PRs`.
- [ ] **T7.3** PR body `Resolves #GUARD`; after merge the orchestrator resolves F-2026-08-REL-supabase-01 with this PR URL.

> **Subagent brief (T7):** worktree `.worktrees/ci-guard`, branch `chore/<GUARD>-migration-reemission-guard`. Execute Thread 7. Small task: no subagents of your own, no exploration beyond the three files. Report: PR URL and the test output.

---

## Close-out (orchestrator)

- [ ] Merge in order T6 → T7 → T2 → T4 → T3 → T5 (A then B; retarget B to main before merging A, project-gh-stack-vs-rebaseline-bot). Fast-forward the main checkout between merges.
- [ ] Kritik resolves, each with its PR URL: SAF-web-02 (T2), SAF-ios-01 (T3), SAF-android-01 (T4), GDP-ios-01 + SAF-ios-02 + GDP-supabase-01 (T5-B), TST-supabase-02 (T6), REL-supabase-01 (T7). Then `kritik_scope` to see the cells a scoped re-audit should re-score; do not score tonight.
- [ ] Arkaik: confirm the App flipped the new view nodes to `releasing`; never set `live`.
- [ ] Journal: one pebble per merged thread with `node scripts/journal/drop-pebble.mjs` (`--valence highlight` for the crisis and consent threads, `neutral` for T6/T7, `--collection` per surface, `--size small`; T1 counts with its surface threads, not alone).
- [ ] Commit this plan on its own docs branch (`docs/1457-kritik-launch-night-plan`, `docs(core): kritik launch night orchestration plan`, labels `docs`, `core`, milestone M68, `no-lab-note`) with the lessons learned appended; it is untracked in the main checkout tonight.
- [ ] Remove the seven worktrees and the two simulators (`git worktree remove`, `xcrun simctl delete`).
- [ ] Decision log: one entry, "Crisis resources: the quiet branch is client-side only, keyed on valence (−1, 3), the server still grants karma", status accepted, refs #1457/#1459/#1460/#1461.
- [ ] Update memory: `reference_v1_launch_inventory_2026_11` (Stack A safety items done), a new `project_kritik_launch_night_2026_11` with the merged PRs and what is still owed (device passes, the error-sink decision issue, the 11 wrongly-resolved findings still needing a UI reopen).

## What is explicitly out of tonight

Error reporting SDKs (issue `SINK`); web colour contrast tokens (A11Y-web-01: design call); iOS Dynamic Type on PebblesFont (A11Y-ios-01); karma keyed to disclosure (SAF-ios-03: product call); the Supabase console config snapshot (PLT-supabase-02); the 11 wrongly-resolved findings (UI reopen only).

## Lessons learned (2026-11-05, after the night)

- **Plan line numbers and suite names drift; file paths and type names hold.** Every implementer reported the `-only-testing` filters wrong: xcodebuild takes the Swift TYPE name (`CrisisResourcesTests`), not the `@Suite` display name. Brief with type names.
- **A test that scans migrations must key on the definition, not a mention.** The consent metadata test broke main the moment a later migration merely revoked a grant on `handle_new_user` (#1557).
- **Squash-merging a stack's parent makes the child conflict on any append-at-end file.** `Localizable.xcstrings` clashed exactly where both parts appended; merging `origin/main` into the child and keeping the child's side resolved it. Retarget the child before merging the parent, as the memory says, and expect this on catalogs.
- **The re-emission guard needed a third check nobody asked for.** The two the finding named (duplicate in one PR, missing marker) do not catch the historical incident; the useful check is "the new body keeps every table the latest base body deletes". Dry-run guards against the real history before trusting a fixture.
- **The RLS harness found three things on its first run** (anon-readable `log_reactions` with `user_id`, anon-executable `is_admin`/`can_use_glyph`, eight trigger functions executable by anon): a completeness gate pays on day one.
- **The classifier blocks merges and `db push` in auto mode** until the maintainer says "merge everything that goes green"; plan the night so merges batch at the end, or get that sentence up front.
- **Sonnet carried five of seven threads** with one review round each; Opus was right for the consent gate and the harness, where both implementers argued correctly against the brief (decision log over issue text; views in scope).
