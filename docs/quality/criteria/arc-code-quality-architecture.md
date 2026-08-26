# ARC — Code Quality & Architecture

> Generated from [`library/framework.json`](../library/framework.json) v0.1.0 — do not edit by hand.

Single responsibility, RPC reuse conventions, layering, typing discipline, naming conventions, duplication, platform idioms, migration/schema quality.

---

## ARC-01 · Responsibility and layer separation

**Is business logic kept out of view components, with all data access funneled through a single isolated data-layer boundary, and does each module own one responsibility?**

`layering` · applies to: `web` `ios` `android` `admin` · default impact **3/5** · weight **3/3**

View components render state and dispatch intents; they contain no persistence calls, no network calls, and no non-trivial business rules. All data access flows through one named boundary per surface (a provider interface, repository, or view-model layer) so the storage backend can be swapped without touching views. Modules and components are cohesive: a file that mixes rendering, IO, and domain rules is a violation regardless of line count.

*Why it matters:* In a multi-client product sharing one database, layer bleed on any client turns every schema change into a hunt through view code on four surfaces. An isolated data layer is also the precondition for testing business rules without a UI.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Views call the database or network client directly; business rules (validation, derivation, scoring) live inline in render code; no data-layer abstraction exists. |
| **1 · Ad-hoc** | A data layer or hook/view-model pattern exists in a few places, but many views still import the storage client directly and mix rules with rendering; no stated rule about it. |
| **2 · Defined** | The boundary is named and documented (a provider interface, repository classes, or view-model convention) and most views respect it, but grep still finds direct storage-client imports or business logic in view files, with no mechanism catching new violations. |
| **3 · Managed** | Every view on the surface goes through the boundary; business logic lives in hooks, view models, or pure utilities that have unit tests; code review guidance explicitly names the rule and reviewers apply it. |
| **4 · Verified** | The boundary is machine-enforced: a lint rule (e.g. no-restricted-imports on concrete providers), an architecture test, or a CI grep fails the build when a view imports the storage client or a concrete provider directly. |

### Audit checklist

- [ ] Identify the surface's data boundary: on web/admin open the provider or repository module (grep for 'DataProvider', 'Repository', 'createClient'); confirm exactly one module family owns storage-client construction.
- [ ] Web/admin: run grep -rn "supabase\.\|createClient(" over components/ and app/ excluding the data-layer directory; every hit inside a view component is a layering finding.
- [ ] Web/admin: open the ESLint config and check for a no-restricted-imports (or equivalent) rule that blocks views from importing concrete providers; note whether it is 'error' severity.
- [ ] iOS: grep View structs for URLSession, SupabaseClient, or SQL/keychain access inside body or view files; confirm a service or store layer mediates, and views hold only observable state.
- [ ] Android: grep @Composable functions for repository, DAO, Retrofit/Ktor, or Supabase client calls; confirm ViewModels (or an explicit state-holder layer) sit between composables and data.
- [ ] Pick the two most complex screens on the surface and trace where validation and derived values are computed; flag any file that mixes rendering with IO and domain rules.
- [ ] Check that business-rule modules (hooks, view models, utilities) have unit tests that run without rendering a UI.

### Monitoring signals

- grep -rn 'createClient(' apps/<surface>/components apps/<surface>/app returns hits only inside the designated data-layer path
- ESLint no-restricted-imports (or an architecture test on mobile) guarding concrete data providers exists at error severity
- CI runs the surface's lint task on every PR touching that workspace
- Count of view files importing the storage client SDK directly trends to zero

### References

- [ISO/IEC 25010:2023 Product quality model — Maintainability: Modularity, Modifiability](https://www.iso.org/standard/78176.html)
- [Android Developers: Guide to app architecture — UI layer / Data layer separation](https://developer.android.com/topic/architecture)
- [Apple Developer Documentation: SwiftUI Model data — Managing model data in your app](https://developer.apple.com/documentation/swiftui/model-data)

### Typical remediation

Extract inline data calls into the existing provider/repository layer (create one if absent), move business rules into hooks, view models, or pure utilities with unit tests, then add a lint or architecture rule so the boundary cannot regress silently.

*Issue skeleton:* [`templates/arc-01.md`](../templates/arc-01.md)

---

## ARC-02 · RPC-first server-side write conventions

**Do all surfaces follow the RPC-first convention for multi-table writes: existing RPCs are reused and extended rather than re-implemented as chained client calls, sibling RPCs stay payload-symmetric, and transaction logic lives server-side by design?**

`rpc-convention` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **4/5** · weight **3/3**

The architecture places multi-table write logic in named database functions by convention: before implementing a multi-statement flow, a surface checks for an existing RPC and extends it rather than re-implementing the logic as chained client calls, even when the RPC is missing a small piece of what is needed. Sibling RPCs (create/update pairs) keep symmetric payload contracts so no path silently drops fields, and ownership checks live inside the function body where every caller inherits them. Whether a given write actually executes atomically at runtime is REL-03's concern; this criterion audits the convention that makes atomicity the default outcome rather than an accident.

*Why it matters:* A convention that concentrates write logic server-side is what keeps four clients from drifting into four divergent implementations of the same flow. When the rule is absent, each surface re-invents the write, asymmetries accumulate between sibling RPCs, and the atomicity defects REL-03 measures become structurally inevitable rather than exceptional.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No convention exists: client surfaces implement multi-table flows however each author preferred, no guidance says when an RPC is required, and no RPC inventory is discoverable. |
| **1 · Ad-hoc** | One or two RPCs exist (often added after an incident), but nothing tells a contributor to look for or extend them; new flows re-implement logic client-side and sibling RPC contracts already diverge. |
| **2 · Defined** | A written rule requires RPC reuse and extension for multi-table writes and most flows comply, but grep still finds client-side re-implementations of existing RPC logic on some surfaces, and create/update sibling symmetry is unchecked. |
| **3 · Managed** | Every multi-table flow maps to exactly one named RPC that all surfaces call, contributor guidance states the check-then-extend rule where agents load it, sibling RPCs are payload-symmetric, and ownership checks sit inside the function bodies. |
| **4 · Verified** | The convention is enforced mechanically: a check flags new client code composing multiple sequential table writes in one handler, sibling RPC signature symmetry is diffed in review or CI, and the RPC inventory is derivable from migrations by grep. |

### Audit checklist

- [ ] Inventory server-side write functions: grep migrations for 'create or replace function'; build the list of named RPCs and which logical flows each one owns.
- [ ] Check contributor guidance (CLAUDE.md/AGENTS.md or equivalent) states the rule: before any multi-table write, look for an existing RPC and extend it rather than re-implementing it client-side; absence of a written rule caps the level at 1.
- [ ] On each client surface, grep for chained table-write builders (.from(...).insert/update/delete, or the SDK equivalent) inside a single function/handler; each hit is a candidate re-implementation to trace against the RPC inventory.
- [ ] Diff sibling RPC signatures (create_X vs update_X): confirm payload keys are symmetric; asymmetries mean one path silently drops fields.
- [ ] Open the bodies of the multi-table RPCs and confirm ownership checks (auth.uid() comparison or equivalent) execute inside the function, before any write, so the check travels with the function rather than being repeated per caller.
- [ ] Spot-check a recently added multi-table flow in git history: was the existing RPC extended, or was a parallel client-side implementation added beside it?

### Monitoring signals

- grep -rn for multiple sequential '.from(' write calls within one function body across client surfaces returns nothing outside the data layer's single-table helpers
- Sibling RPC signature diffs (create_X vs update_X) show symmetric payload keys
- Contributor guidance loaded by agents states the check-then-extend RPC rule
- Every 'security definer' write RPC body contains an auth.uid() (or equivalent) ownership check

### References

- [PostgreSQL Documentation: Transactions — 3.4. Transactions (atomicity of function bodies)](https://www.postgresql.org/docs/current/tutorial-transactions.html)
- [Supabase Docs: Database Functions — Calling functions via RPC](https://supabase.com/docs/guides/database/functions)
- [ISO/IEC 25010:2023 Product quality model — Reliability: Recoverability](https://www.iso.org/standard/78176.html)

### Typical remediation

Write the RPC-first rule where contributors and agents load it, port each client-side re-implementation onto the existing RPC (extending it where a piece is missing), restore payload symmetry between sibling RPCs, and add a review or CI check that flags multi-write handlers in client code. Runtime atomicity defects found along the way are filed under REL-03.

*Issue skeleton:* [`templates/arc-02.md`](../templates/arc-02.md)

---

## ARC-03 · Strict typing and exhaustiveness discipline

**Is the type system used at full strength: no any/Any escapes, no unchecked casts or force-unwraps, exhaustive handling of closed enums, and shared data shapes modeled from generated database types?**

`typing` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **3/5** · weight **2/3**

Compiler strictness is on and escape hatches are banned: no explicit any, ts-ignore, force casts (as!), force unwraps (!, !!), or unchecked as casts in production code. Closed sets (enums, sealed classes, union types) are switched exhaustively without a default/else that swallows new cases. Types describing the shared database contract are generated from the schema and imported, never hand-retyped per surface.

*Why it matters:* With one database contract and four independently compiled clients, the type system is the only compile-time proof that a surface still matches the schema. Every any or force cast is a place where a schema change ships as a runtime crash or silent data corruption instead of a build failure.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Strict mode is off or riddled with exceptions; any/Any, force casts, and force unwraps are common; DB row shapes are hand-written dictionaries or loose maps. |
| **1 · Ad-hoc** | Strict mode is on but escapes are frequent and unremarked; some entities are typed, others passed as untyped JSON; enums handled with catch-all defaults. |
| **2 · Defined** | A written no-any/no-force-cast rule exists and most code complies; generated DB types exist but regeneration after migrations is manual and sometimes skipped; grep still finds escapes without justification comments. |
| **3 · Managed** | Escapes are rare, individually justified in a comment, and rejected in review; closed enums are matched exhaustively; generated DB types are the single source for shared shapes on every surface, regenerated with each migration. |
| **4 · Verified** | Automation enforces it: lint rules at error severity ban any/ts-ignore (and mobile linters ban force casts/unwraps), CI fails on violations, and a CI check verifies the committed generated types match the migration set (drift is detected). |

### Audit checklist

- [ ] Web/admin: open tsconfig.json and confirm strict: true; open the ESLint config and confirm @typescript-eslint/no-explicit-any at 'error'; grep -rn 'as any\|@ts-ignore\|@ts-expect-error' over src, excluding tests, and audit every hit.
- [ ] Supabase: confirm a generated types file (database.ts or equivalent) is committed; git log it against the migrations directory to verify it was regenerated in the same commits as recent migrations.
- [ ] Trace one shared entity (the product's core record type) from generated DB types to each surface's model; hand-written duplicates of DB row shapes are findings.
- [ ] iOS: grep non-test sources for 'as!' and 'try!' and audit force-unwrap density; check switches over project-owned enums avoid 'default:' so new cases fail the build.
- [ ] Android: grep main sources for '!!' and for ' as ' casts not followed by '?'; check 'when' over sealed classes/enums compiles exhaustively without an 'else' branch.
- [ ] Check decoding of cross-surface payloads: unknown enum values and explicit nulls must be handled by typed fallbacks, not crashes or silent Any maps.
- [ ] Check CI: does any job run the type-checker/lint per workspace, and is there a check that regenerates DB types and fails on diff?

### Monitoring signals

- grep -rn 'as any|@ts-ignore' over web/admin app code returns zero hits
- grep -rn '!!' over Android main sources and 'as!' over iOS non-test sources returns zero (or only comment-justified) hits
- CI job exists that fails on lint/type-check per workspace
- CI or a documented harness verifies the committed generated DB types match the migration set (no drift)

### References

- [typescript-eslint: no-explicit-any — Rule: @typescript-eslint/no-explicit-any](https://typescript-eslint.io/rules/no-explicit-any/)
- [TypeScript TSConfig Reference — strict](https://www.typescriptlang.org/tsconfig#strict)
- [CWE-704: Incorrect Type Conversion or Cast — CWE-704](https://cwe.mitre.org/data/definitions/704.html)

### Typical remediation

Turn the escape-hatch bans into error-severity lint rules per surface, replace hand-written DB shapes with imports from the generated types, remove default/else arms on closed enums, and add a CI step that regenerates DB types and fails on diff.

*Issue skeleton:* [`templates/arc-03.md`](../templates/arc-03.md)

---

## ARC-04 · Naming and file convention consistency

**Are naming and file-layout conventions written down per surface and consistently applied, so an auditor can predict a file's location and name from its role?**

`conventions` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **2/5** · weight **1/3**

Each surface has documented conventions covering file naming per artifact kind (components, hooks/view models, utilities, config), casing, one-primary-export-per-file, and directory placement, aligned with the platform vendor's style guide. Sampling any directory shows the conventions actually hold, and formatters/linters encode the mechanically checkable parts. Branch, commit, and issue conventions are documented alongside.

*Why it matters:* Conventions are the cheapest form of architecture: they make grep and code review reliable, and on a mirrored multi-client codebase they let a change on one platform be located instantly on its sibling. Convention drift is also the earliest visible symptom of unreviewed code.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No written conventions; casing and placement vary by author; multiple unrelated exports per file are common. |
| **1 · Ad-hoc** | Informal habits exist (some directories consistent), but nothing is written down and new files diverge freely. |
| **2 · Defined** | Conventions are documented (contributor docs or agent instructions) and mostly followed, but sampling finds violations and nothing mechanical catches new ones. |
| **3 · Managed** | Sampling any directory shows near-total conformance; reviewers enforce the documented rules; migrations/SQL objects follow a stated naming scheme (timestamped files, snake_case objects). |
| **4 · Verified** | Formatters and linters (ESLint, SwiftLint, ktlint, SQL naming checks where applicable) encode the mechanically checkable conventions and run in CI; violations fail the build rather than reaching review. |

### Audit checklist

- [ ] Locate the written conventions (CONTRIBUTING, CLAUDE.md/AGENTS.md, or docs/); confirm they specify casing per artifact kind and export policy; absence of a written rule caps the level at 1.
- [ ] Web/admin: sample components/ and lib/ directories; check PascalCase component files with one exported component, use-prefixed camelCase hooks, kebab-case utilities; list violations.
- [ ] iOS/Android: sample sources against the Swift API Design Guidelines and Kotlin Coding Conventions respectively; check type names, file-per-type, and package/directory structure.
- [ ] Supabase: check migration filenames follow the timestamped scheme, SQL objects are snake_case, and function names state their action (verb_noun).
- [ ] Open the lint configs (ESLint config, .swiftlint.yml, ktlint task) and map which documented conventions are mechanically enforced versus review-only.
- [ ] Check recent merge history: do branch names and commit messages follow the documented format (conventional commits or equivalent)?

### Monitoring signals

- SwiftLint and ktlint configs exist and run under the workspace lint task in CI
- grep for hook files not matching the use-prefix pattern (or components not PascalCase) returns zero
- A written conventions section exists in contributor-facing docs and was updated within the last two quarters
- Commit history conforms to the stated commit format (spot-check last 30 commits)

### References

- [Swift.org API Design Guidelines — Naming](https://www.swift.org/documentation/api-design-guidelines/)
- [Kotlin Coding Conventions — Naming rules](https://kotlinlang.org/docs/coding-conventions.html)
- [ISO/IEC 25010:2023 Product quality model — Maintainability: Analysability](https://www.iso.org/standard/78176.html)

### Typical remediation

Write the missing conventions down in contributor docs, rename outliers in a dedicated mechanical PR (no logic changes), and move every mechanically checkable rule into the surface's linter so drift fails CI.

*Issue skeleton:* [`templates/arc-04.md`](../templates/arc-04.md)

---

## ARC-05 · Duplication control and dead code removal

**Is logic factored so each rule lives in one place per surface, is intentional cross-platform mirroring distinguished from accidental duplication, and is dead code actively removed?**

`duplication` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **2/5** · weight **2/3**

Within a surface, a business rule, formatter, or data mapping exists exactly once; repeated blocks are extracted into shared utilities. Across surfaces, duplication that a mirroring policy mandates (e.g. two native clients kept 1:1) is documented as intentional and kept in sync deliberately, while shareable contract logic lives in the database or a shared package. Unreferenced exports, orphaned files, commented-out blocks, and remnant feature flags are deleted, not accumulated.

*Why it matters:* Duplicated rules drift independently, and on a shared-database product a drifted copy means two clients disagree about the same data. Dead code inflates audit surface and misleads both humans and coding agents into extending abandoned paths.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Copy-paste is the default reuse mechanism; orphaned files and commented-out blocks are common; no one can say which duplication is intentional. |
| **1 · Ad-hoc** | Some extraction into utilities happened opportunistically; dead code is removed only when it breaks something; unused exports accumulate. |
| **2 · Defined** | A stated preference for factoring exists and shared utilities are the norm, but a duplication scan still finds repeated non-trivial blocks, and no tooling reports unused exports; cross-platform mirroring is practiced but its sync obligations are implicit. |
| **3 · Managed** | Duplication within a surface is rare and justified when present; the mirroring policy is written down with a rule that a change on one mirrored client triggers checking the sibling; dead-code sweeps happen on a stated cadence (e.g. at milestone audits). |
| **4 · Verified** | Tooling detects drift: a dead-export analyzer (knip/ts-prune or equivalent) and/or a duplication detector runs in CI or on a scheduled audit, and its findings are triaged; mirrored-surface divergence is caught by review checklist or contract tests rather than by users. |

### Audit checklist

- [ ] Run a duplication detector (e.g. npx jscpd) over each TypeScript surface; manually review the top clusters for extracted-vs-copied business rules.
- [ ] Run a dead-export analyzer (npx knip or ts-prune) on web/admin; list exported symbols with no importers and files imported by nothing.
- [ ] grep -rn for commented-out code blocks (consecutive '// ' lines containing statements) and stale flags (TODO remove, deprecated, legacy) across surfaces.
- [ ] Pick three cross-surface behaviors (a formatter, a validation rule, a derived value) and compare the client implementations; where a mirroring policy exists, verify the copies are currently identical in behavior; where none exists, check the logic lives in the shared contract layer (DB function, generated types) rather than N copies.
- [ ] Supabase: check for orphaned database objects: views/functions no migration or client references anymore (grep client codebases for each RPC and view name).
- [ ] Check whether any scheduled audit, review checklist, or CI job owns dead-code and duplication detection, or whether removal is purely incidental.

### Monitoring signals

- knip/ts-prune (or equivalent) is configured and its unused-export count is tracked, trending down
- jscpd (or equivalent) duplication percentage per surface is measured and below an agreed threshold
- grep for 'TODO remove|@deprecated' returns only items younger than one milestone
- Every RPC and view name in migrations has at least one client call site or a documented reason to exist

### References

- [CWE-1041: Use of Redundant Code — CWE-1041](https://cwe.mitre.org/data/definitions/1041.html)
- [CWE-561: Dead Code — CWE-561](https://cwe.mitre.org/data/definitions/561.html)
- [ISO/IEC 25010:2023 Product quality model — Maintainability: Reusability](https://www.iso.org/standard/78176.html)

### Typical remediation

Extract the top duplication clusters into shared utilities per surface, document which cross-platform duplication is policy, delete unreferenced exports and orphaned DB objects in a dedicated PR, and add a dead-export analyzer to the audit cadence or CI.

*Issue skeleton:* [`templates/arc-05.md`](../templates/arc-05.md)

---

## ARC-06 · Platform idiom adherence

**Does each client use its framework's current idioms (App Router server/client split, modern SwiftUI observation and concurrency, Compose state hoisting and unidirectional data flow) rather than legacy or foreign patterns?**

`idioms` · applies to: `web` `ios` `android` `admin` · default impact **3/5** · weight **2/3**

Code reads like the platform vendor's current guidance: on Next.js App Router, components are server components by default with 'use client' only where interactivity demands it, and data flows through server-side fetching or a deliberate client data layer, not ad-hoc useEffect chains. On SwiftUI (iOS 17+), observation uses the @Observable macro era and Swift Concurrency, not mixed legacy ObservableObject/completion-handler styles. On Compose, state is hoisted, composables are side-effect free outside effect handlers, and data flows unidirectionally from state holders. One era of idiom per surface; deliberate exceptions are commented.

*Why it matters:* Idiom mixing doubles the number of patterns every maintainer and reviewer must hold, defeats framework-level correctness guarantees (observation tracking, recomposition skipping, server-side rendering), and is where subtle staleness and lifecycle bugs breed.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | The framework is fought rather than used: everything is a client component, views own ad-hoc mutable state, side effects run in render/composition bodies, legacy and modern observation freely mixed. |
| **1 · Ad-hoc** | Modern idioms appear in newer code but coexist unmanaged with legacy patterns; no stated target idiom; copying an old file propagates the old style. |
| **2 · Defined** | The target idioms are written down (surface docs or contributor guide) and dominate new code, but grep finds unjustified legacy patterns and misplaced side effects with no migration plan or lint coverage. |
| **3 · Managed** | One idiom era per surface with commented exceptions; server/client component split is deliberate and minimal; state holders and effect handlers are used correctly across sampled screens; reviews enforce it. |
| **4 · Verified** | Idiom rules are mechanized where tooling allows: Compose lint/rules and SwiftLint custom rules run in CI, 'use client' usage is audited by a scripted check or lint, and violations fail the build. |

### Audit checklist

- [ ] Web/admin: grep -rn '"use client"' under app/ and components/; for each hit, verify the component actually needs interactivity/hooks; count client components at route roots (a client root converts the whole subtree).
- [ ] Web/admin: grep for useEffect-based data fetching in page-level components; compare against the surface's stated data-fetching pattern (server components, route handlers, or a client data provider); note that the installed framework version's own docs (in node_modules or vendor site) override memory of older APIs.
- [ ] iOS: count occurrences of 'ObservableObject'/'@Published' versus '@Observable'; mixed usage in same-era code without comments is a finding; grep for completion-handler async (escaping closures with Result) in new code paths.
- [ ] Android: sample composables for state created and mutated locally that belongs hoisted; grep for side effects (IO, repository calls) outside LaunchedEffect/remember/derivedStateOf; confirm screen state flows from a ViewModel/StateFlow via lifecycle-aware collection.
- [ ] Check the surface docs name the target idiom era explicitly and whether any lint (Compose lint, SwiftLint rules, ESLint plugin) encodes it.
- [ ] Diff two screens of similar age per surface; divergent idioms in same-era code indicate drift rather than migration.

### Monitoring signals

- Ratio of 'use client' files to total components stays below an agreed threshold and each is justifiable
- grep for legacy observation ('ObservableObject') in iOS 17+ code returns only commented, deliberate exceptions
- Compose lint / SwiftLint runs in CI with idiom-relevant rules enabled
- grep for repository/IO calls inside composable bodies (outside effect handlers) returns nothing

### References

- [Next.js Documentation: App Router — Server and Client Components](https://nextjs.org/docs/app)
- [Apple Developer Documentation: Migrating from the Observable Object protocol to the Observable macro — Observation migration guide](https://developer.apple.com/documentation/swiftui/migrating-from-the-observable-object-protocol-to-the-observable-macro)
- [Android Developers: State and Jetpack Compose — State hoisting](https://developer.android.com/develop/ui/compose/state)

### Typical remediation

Name the target idiom per surface in its docs, migrate stragglers screen-by-screen (never half a screen), comment the deliberate exceptions, and enable the platform lint rules that keep the idiom from regressing.

*Issue skeleton:* [`templates/arc-06.md`](../templates/arc-06.md)

---

## ARC-07 · Error handling as code structure

**Is failure a first-class code path in the code's structure: no swallowed errors, and typed errors surfaced at layer boundaries so callers can branch on failure kind?**

`error-handling` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **3/5** · weight **2/3**

Every catch/failure path either handles the error meaningfully or propagates it; empty catch blocks, ignored error returns, and try? discards on operations that can fail user-visibly are structural defects. Errors cross layer boundaries as typed values or structured responses (database functions raise distinct exceptions with stable messages or codes; edge/API functions return structured error bodies), so callers branch on failure kind rather than parse strings, and each layer's failure contract is explicit. Runtime failure policy is owned elsewhere: timeout values and retry rules by REL-02, and user-facing error states plus logging of the cause by REL-01.

*Why it matters:* When failure is not a designed code path, every layer improvises: errors vanish inside catches, null returns masquerade as success, and string parsing substitutes for contracts. Structured, typed failure paths are what make the runtime policies REL-01 and REL-02 audit even expressible, and what makes cross-surface debugging possible when four clients share one backend.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Empty catch blocks and ignored error returns are common; DB functions fail by returning null; no layer has an error contract. |
| **1 · Ad-hoc** | Some paths propagate errors, inconsistently; no stated rule exists and error shapes differ per author. |
| **2 · Defined** | A written rule bans swallowed errors and most code complies, but grep still finds empty catches, and DB error signaling is mixed (some raise, some return null). |
| **3 · Managed** | No failure path swallows errors; DB functions raise distinct exceptions with stable messages/codes; API/edge responses carry structured error bodies; failure paths for the critical flows are exercised by tests. |
| **4 · Verified** | Enforced: a lint rule or CI grep rejects empty catch blocks and discarded error returns, and layer error contracts are asserted by tests so a null-returning regression in a DB function fails the build. |

### Audit checklist

- [ ] grep -rn 'catch {}\|catch (\w*) {}\|catch (_\|catch (e: Exception) { }' (adapt per language) across all surfaces; every empty or action-free catch is a finding unless the ignore is commented with a reason.
- [ ] Supabase: open the write RPCs and check failure signaling: 'raise exception' with distinct messages/codes versus silent 'return null'; open edge functions and check error responses are structured (status + body), not empty 500s.
- [ ] iOS: grep for 'try?' discarding results on operations that can fail user-visibly; check Task bodies catch and surface errors rather than letting them vanish.
- [ ] Trace one failure per surface across a layer boundary: confirm the error arrives at the caller as a typed value or structured response it can branch on, not a null, a sentinel string, or nothing.
- [ ] Check whether any known deadlock-prone callback context (e.g. auth state change callbacks holding a client lock) is documented and that no await/blocking call sits inside it.

### Monitoring signals

- grep for empty catch blocks across surfaces returns zero unjustified hits
- All write RPC bodies signal failure with raise exception (distinct message), none with bare return null
- Edge/API error responses carry a structured body callers can branch on (spot-check of failure paths returns no empty 500s)

### References

- [CWE-390: Detection of Error Condition Without Action — CWE-390](https://cwe.mitre.org/data/definitions/390.html)
- [CWE-544: Missing Standardized Error Handling Mechanism — CWE-544](https://cwe.mitre.org/data/definitions/544.html)
- [ISO/IEC 25010:2023 Product quality model — Reliability: Fault tolerance](https://www.iso.org/standard/78176.html)

### Typical remediation

Adopt one typed failure pattern per surface (error enum or structured response shape per layer), sweep existing empty catches and ignored returns, make DB functions raise distinct exceptions instead of returning null, and encode the bans as lint rules or CI greps. Timeout and retry policy belongs to REL-02; user-facing error states and failure logging to REL-01.

*Issue skeleton:* [`templates/arc-07.md`](../templates/arc-07.md)

---

## ARC-08 · Migration and schema change quality

**Are schema changes append-only, safely re-runnable where intended, free of create-or-replace clobber hazards, paired with regenerated client types, and complete (RLS, derived-data sync, harness updates) within the same change?**

`schema-quality` · applies to: `supabase` · default impact **4/5** · weight **3/3**

The migration history is append-only and timestamped; shipped migrations are never edited. Re-emissions of whole function bodies via create-or-replace are treated as a known hazard: because replace has no merge semantics and produces no VCS conflict, two migrations re-emitting the same function are diffed pairwise and manually unioned before applying. Every migration ships as a complete unit: new tables get RLS in the same file, security definer functions pin search_path, reference-data inserts are deterministic (stable keys, on-conflict clauses) and re-sync any dependent derived catalogs in the same transaction, and generated client types plus contract harnesses are updated in the same commit.

*Why it matters:* The schema is the only contract four clients share, so a defective migration is a simultaneous four-surface incident. The create-or-replace clobber failure mode is especially dangerous because it silently deletes previously appended behavior (including deletion/purge logic with legal weight) while git reports no conflict.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Migrations are edited after shipping or applied ad hoc from consoles; functions are replaced wholesale with no body diffing; new tables ship without RLS; client types drift from the schema. |
| **1 · Ad-hoc** | Timestamped append-only files exist, but completeness is accidental: some tables gained RLS later, type regeneration is sporadic, function re-emission collisions have occurred or are unexamined. |
| **2 · Defined** | Written rules cover append-only history, type regeneration, and the replace hazard, and recent migrations comply, but conformance depends on memory: no check pairs types with migrations, and function-body unions are done by hand without a recorded diff. |
| **3 · Managed** | Every recent migration is a complete unit (RLS with the table, search_path pinned, deterministic seeds, derived-data sync in-transaction, types and harnesses in the same commit); batches containing repeated re-emissions of one function are pairwise diffed before apply, and the practice is documented with markers in the function bodies. |
| **4 · Verified** | Drift is machine-detected: CI validates that generated types match the migration set, a script flags batches re-emitting the same function twice, checks fail on new tables without RLS or definer functions without pinned search_path, and contract harnesses run against a real database after destructive-flow changes. |

### Audit checklist

- [ ] git log the migrations directory: confirm no shipped migration file was modified after its introducing commit (append-only history).
- [ ] grep -c 'create or replace function' per migration file; for every function name emitted in two or more migrations, diff the bodies pairwise and verify each later emission contains the earlier appends (the clobber check); pay special attention to functions with in-body append markers.
- [ ] For each migration creating a table, confirm 'enable row level security' plus policies appear in the same file; grep for 'security definer' and confirm each such function sets search_path.
- [ ] Check reference-data inserts use deterministic keys and on-conflict handling, and that any derived catalog depending on reference tables is re-synced inside the same migration/transaction that inserts into them.
- [ ] git log pairing: confirm the generated client types file changes in the same commits as recent migrations, and that a regeneration command is documented in contributor docs.
- [ ] For destructive or cascading flows (account purge and kin), confirm the contract harness gained matching seeds and zero-row assertions in the same change as any migration touching the flow, and check when the harness last ran.
- [ ] Check CI: does any workflow validate migrations, type drift, or harness execution, or is all of the above manual discipline?

### Monitoring signals

- A script or CI step flags batches containing two or more re-emissions of the same function name
- CI (or a documented pre-merge step) regenerates client types and fails on diff against the committed file
- grep finds zero 'security definer' functions without 'set search_path' and zero created tables without RLS in the same migration
- Contract harnesses for destructive flows exist and their last green run postdates the last migration touching those flows

### References

- [PostgreSQL Documentation: CREATE FUNCTION — CREATE OR REPLACE FUNCTION semantics](https://www.postgresql.org/docs/current/sql-createfunction.html)
- [Supabase Docs: Database Migrations — Local development and migrations workflow](https://supabase.com/docs/guides/deployment/database-migrations)
- [Supabase Docs: Row Level Security — Enabling RLS on tables](https://supabase.com/docs/guides/database/postgres/row-level-security)

### Typical remediation

Codify the migration checklist (RLS with table, pinned search_path, deterministic seeds, in-transaction derived-data sync, types plus harness in-commit), add a CI check for type drift and for duplicate function re-emissions in one batch, and resolve any found clobber by writing a new migration that unions the divergent bodies.

*Issue skeleton:* [`templates/arc-08.md`](../templates/arc-08.md)
