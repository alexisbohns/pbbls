# PRF — Performance & Efficiency

> Generated from [`library/framework.json`](../library/framework.json) v0.1.0 — do not edit by hand.

Core Web Vitals, bundles, media pipeline, query efficiency, mobile startup, caching, network frugality.

---

## PRF-01 · Core Web Vitals budgets and measurement

**Are Core Web Vitals (LCP, INP, CLS) explicitly budgeted, continuously measured, and gated so regressions on key routes are detected rather than discovered by users?**

`web-vitals` · applies to: `web` · default impact **3/5** · weight **3/3**

Web surfaces define numeric p75 targets for LCP (<= 2.5 s), INP (<= 200 ms), and CLS (<= 0.1) on their key routes, and measure them via lab runs (Lighthouse or equivalent) or field telemetry (a web-vitals reporting hook or platform analytics). Framework idioms that protect these metrics are applied deliberately: server-rendered above-the-fold content, streaming with Suspense for slow data, optimized font loading, and layout space reserved for late-arriving content. A budget breach is treated as a defect with an owner, not as ambient noise.

*Why it matters:* For a PWA that is the primary client, vitals are the felt product quality; an unmeasured surface always drifts because every regression is invisible until a user complains. Budgets turn a vague aspiration into an auditable gate.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No vitals targets exist anywhere in the repo or docs; no web-vitals reporting, no Lighthouse config, no analytics integration; above-the-fold routes contain render-blocking patterns (unoptimized fonts, client-only rendering of primary content) with no sign anyone looked. |
| **1 · Ad-hoc** | A one-off Lighthouse run or a vitals mention appears in a doc or PR, or an analytics package is installed but its data is not referenced anywhere; no numeric budget, no recurring measurement, offenders unfixed. |
| **2 · Defined** | Numeric budgets for LCP/INP/CLS are written down for named routes; measurement happens manually or occasionally (a dashboard someone checks); known offenders are listed even if not yet fixed; new PRs are not systematically checked. |
| **3 · Managed** | Vitals are measured continuously (field RUM hook or scheduled lab runs) on key routes; regressions are triaged with owners; layout-stability and interaction-latency patterns (reserved dimensions, deferred hydration, streaming) are part of review practice across the surface. |
| **4 · Verified** | A CI gate (Lighthouse CI assertions or an equivalent budget file) fails the build when a key route exceeds budget, and field monitoring with alerting covers real traffic; a vitals regression is mechanically detected before or immediately after release. |

### Audit checklist

- [ ] Grep package.json of each Next.js app for a vitals pipeline: `web-vitals`, `@vercel/analytics`, `@vercel/speed-insights`, or a custom onLCP/onINP/onCLS reporting hook in the client bootstrap (root layout or a providers file).
- [ ] Search for a lab gate: `lighthouserc*`, `lhci`, `unlighthouse`, or a budgets JSON, and a CI workflow (.github/workflows/*.yml) that runs it; note whether it asserts thresholds or only reports.
- [ ] Search docs/ and README for written numeric budgets naming routes and thresholds; absence of any number anywhere caps the level at 1.
- [ ] Open the root layout and the main entry route: check fonts load via the framework's font module (next/font) rather than <link> to a font CDN, and check whether primary content is server-rendered or hidden behind a client-only fetch spinner.
- [ ] Grep app code for raw `<img ` and for media containers without explicit width/height or aspect-ratio; each is a CLS suspect on routes with photos.
- [ ] Trace the heaviest interactive element (main composer or picker) for INP hazards: synchronous work in event handlers, large state updates re-rendering whole trees, missing use of transitions or deferred values.

### Monitoring signals

- CI workflow contains a Lighthouse CI (or equivalent) step with assertions; its absence is the drift signal.
- Grep for `onINP|onLCP|onCLS|reportWebVitals|SpeedInsights` returns at least one wired call site in each Next.js app.
- Grep for `<img ` in app/ and components/ returns zero hits (framework image component used instead).

### References

- [Web Vitals (web.dev) — Core Web Vitals thresholds: LCP 2.5 s, INP 200 ms, CLS 0.1 at p75](https://web.dev/articles/vitals)
- [Interaction to Next Paint (INP) — What is INP / Good INP scores](https://web.dev/articles/inp)
- [Performance budgets 101 — Choosing metrics and budgets](https://web.dev/articles/performance-budgets-101)
- [ISO/IEC 25010:2023 — Performance efficiency (time behaviour)](https://www.iso.org/standard/78176.html)

### Typical remediation

Adopt the framework's field reporting (web-vitals hook or platform analytics), write p75 budgets for the 3 to 5 key routes, then add a Lighthouse CI assertion job so the budget is enforced rather than remembered. Fix the top offender per metric first (usually the LCP image and one slow interaction).

*Issue skeleton:* [`templates/prf-01.md`](../templates/prf-01.md)

---

## PRF-02 · Client JavaScript bundle discipline

**Is shipped client JavaScript kept intentionally small, with server/client component boundaries pushed to the leaves, heavy dependencies audited, and bundle size tracked so growth is a visible decision?**

`bundle` · applies to: `web` `admin` · default impact **2/5** · weight **2/3**

Client bundles are a managed budget, not an accident of imports. Interactive code is isolated in leaf client components rather than marking layouts or whole routes as client; heavy or duplicative dependencies (full lodash, moment-class date libs, chart or animation runtimes) are either avoided, tree-shaken via scoped imports, or loaded dynamically for below-the-fold and modal-only UI. A bundle analysis tool or size gate exists so a size regression shows up in review.

*Why it matters:* On a shared-DB multi-client product the web client is often the widest-reaching surface; every kilobyte of client JS taxes INP and startup on mid-range phones. Bundle growth is monotonic unless something makes it visible.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No analyzer or size tooling anywhere; `"use client"` sits on layouts or top-level pages; heavy libraries are imported wholesale at module top level with no dynamic imports in the codebase. |
| **1 · Ad-hoc** | Some routes happen to be server components and one or two dynamic imports exist, but the pattern is inconsistent and clearly incidental; no tooling, no budget, no review practice. |
| **2 · Defined** | Client/server boundaries are deliberately placed (client directives mostly on leaf components) and a bundle analyzer is configured, but it is run manually and nothing prevents regression; known heavy deps are documented as debt. |
| **3 · Managed** | Boundaries, scoped imports, and dynamic loading are the norm across the surface; bundle output is reviewed on dependency changes; adding a heavy dependency requires justification in review. |
| **4 · Verified** | A CI size gate (size-limit, bundle-analyzer diff, or first-load-JS assertion per route) fails on unapproved growth; the budget file is in the repo and drift is caught mechanically. |

### Audit checklist

- [ ] Grep for `"use client"` across app/ and components/; flag any occurrence in a layout.tsx, top-level page.tsx, or provider that wraps whole routes, and check whether interactivity could live in a smaller leaf.
- [ ] Open package.json dependencies and flag known heavyweights (moment, full lodash, large chart/canvas/animation runtimes, markdown or editor toolchains); for each, grep import sites to see if it is scoped, dynamically imported, or top-level everywhere.
- [ ] Grep for `next/dynamic`, `React.lazy`, and `import(` to inventory dynamic loading; verify modal-only, admin-only, and below-the-fold components (charts, editors, animation players) are behind one.
- [ ] Check for analyzer/gate tooling: `@next/bundle-analyzer`, `size-limit`, or a build script printing first-load JS, and whether any CI workflow runs it.
- [ ] Run the production build once and read the framework's route table output (first load JS per route); record the worst three routes as the audit baseline.

### Monitoring signals

- A size gate exists in CI (size-limit or first-load-JS assertion) and its budget file is committed.
- Grep for `"use client"` in `**/layout.tsx` returns zero hits.
- Grep for top-level imports of the identified heavy dependencies outside dynamically imported modules returns zero hits.

### References

- [Reduce JavaScript payloads with code splitting — Code splitting strategies](https://web.dev/articles/reduce-javascript-payloads-with-code-splitting)
- [React Server Components — Server Components without a framework / usage](https://react.dev/reference/rsc/server-components)
- [Performance budgets 101 — Quantity-based metrics](https://web.dev/articles/performance-budgets-101)

### Typical remediation

Run the bundle analyzer, split the top offenders behind dynamic imports, push client directives down to leaves, then freeze the result with a committed size budget enforced in CI.

*Issue skeleton:* [`templates/prf-02.md`](../templates/prf-02.md)

---

## PRF-03 · Image and media delivery pipeline

**Is every media render path served an appropriately sized, modern-format, lazily loaded variant through cacheable URLs, instead of full-size originals behind per-render regenerated links?**

`media-pipeline` · applies to: `web` `ios` `android` `admin` `supabase` · default impact **3/5** · weight **3/3**

User photos and other media are resized or transformed to the rendered slot (responsive srcset/sizes on web, downsampling image loaders on mobile, storage-side transform parameters where available), encoded in modern formats where supported, deferred when offscreen, and rendered into containers with reserved dimensions. Storage-backed URLs, signed or public, are generated with lifetimes and cache headers that allow CDN and client reuse; signing happens in batches or behind a memoized TTL cache, never freshly per list item per render. Uploads are compressed client-side before transfer when the source is a camera-resolution original. Where media is access-controlled, cacheability is evaluated within PRV-05's constraint: signed URLs carry a deliberate TTL and are reused across renders during their validity window, never regenerated per render, and no cache lifetime outlives the access grant. Where the two criteria pull in opposite directions, PRV-05 wins.

*Why it matters:* In a photo-carrying product, media dominates bytes, LCP, memory, and egress cost on every surface at once. A single full-resolution original rendered into a thumbnail slot can outweigh the entire application bundle.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Originals are uploaded and rendered untouched; raw <img> or unconfigured image views fetch full-size files into thumbnails; signed URLs are regenerated on every render; no lazy loading, no reserved dimensions, no cache headers on upload. |
| **1 · Ad-hoc** | One path (for example the web timeline) uses the framework image component or a lazy container, but other render paths fetch originals; URL signing and cache headers are whatever the storage SDK defaults gave; no upload-time compression. |
| **2 · Defined** | A deliberate pipeline exists (transform parameters or client-side resize on upload, framework image components, lazy lists) with documented gaps, for example detail views still fetch originals or signed URLs are cached only in one store. |
| **3 · Managed** | All render paths on the surface request sized variants, reserve layout, and lazy-load; signed URLs are batch-generated or TTL-cached in the data layer; uploads set cache control and are compressed; the behavior is covered by review or tests on the media data layer. |
| **4 · Verified** | Automation enforces the pipeline: lint or CI forbids raw image tags and unbatched signing call sites, upload paths are tested for size ceilings and cache headers, and an egress or payload metric is monitored so a regression to originals is detected. |

### Audit checklist

- [ ] Grep web/admin for `<img ` versus the framework image component; for each framework usage, verify `sizes`/`fill` or explicit width/height match the rendered slot, not the original.
- [ ] Grep all surfaces for signed-URL generation (`createSignedUrl`, `createSignedUrls`) and classify each call site: batched or per-item, memoized with a TTL or regenerated per render, and whether the TTL both allows CDN/browser reuse within a session and respects PRV-05's access-grant bound; where the two conflict, PRV-05 wins.
- [ ] Trace the upload path on each client: is the camera/gallery original downscaled and recompressed before upload (canvas/createImageBitmap on web, UIImage JPEG/HEIC compression on iOS, Bitmap/Compose-side compression on Android), and does the upload set a cacheControl value?
- [ ] Check whether render paths use storage transform parameters (width/quality/resize) or always fetch the stored original; compare the pixel size fetched for a list thumbnail against the slot size.
- [ ] On iOS/Android, confirm async image loading with a bounded disk/memory cache (AsyncImage with URLCache configuration, or Coil/Glide) and that list containers are lazy so offscreen media is neither fetched nor decoded.
- [ ] Verify media containers reserve space (aspect-ratio, fixed frames) so late-loading photos cannot shift layout.

### Monitoring signals

- Grep for signed-URL generation inside list-item render code or per-item loops returns nothing (batch or cached-map call sites only).
- Grep for `<img ` in web/admin app code returns nothing.
- Upload call sites all pass an explicit cacheControl/content-type; a grep for storage upload without cacheControl returns nothing.

### References

- [Serve responsive images — Resizing and srcset/sizes](https://web.dev/articles/serve-responsive-images)
- [Browser-level image lazy loading — loading="lazy" semantics](https://web.dev/articles/browser-level-image-lazy-loading)
- [Supabase Storage image transformations — Transforming images on the fly (width, quality)](https://supabase.com/docs/guides/storage/serving/image-transformations)
- [Supabase Storage CDN fundamentals — Cache control and CDN caching of assets](https://supabase.com/docs/guides/storage/cdn/fundamentals)

### Typical remediation

Introduce a single media-URL helper per surface that batches or TTL-caches signing and appends transform parameters for the requested slot size; compress on upload with a hard ceiling; then forbid raw image tags and direct signing calls outside the helper via lint.

*Issue skeleton:* [`templates/prf-03.md`](../templates/prf-03.md)

---

## PRF-04 · Indexes match access paths and RLS predicates

**Does every hot query path, including the implicit filters injected by RLS policies, have a matching index, and is this verified with query plans rather than assumed?**

`query-efficiency` · applies to: `supabase` · default impact **3/5** · weight **3/3**

On a shared multi-tenant database, RLS predicates (owner checks, membership subqueries) execute against every candidate row, so each column referenced in a policy's USING/WITH CHECK, each foreign key, and each hot client filter/order column carries an appropriate index (btree, composite with the tenant column leading, partial where selective). Stable per-request functions such as auth.uid() are wrapped as scalar subselects so they evaluate once per statement instead of once per row. EXPLAIN evidence exists for the hot paths and re-running it is scripted, not tribal.

*Why it matters:* RLS turns every table scan into a per-row policy evaluation; a missing index or an unwrapped auth function degrades all four clients simultaneously and only shows up once real data volume arrives, which is the most expensive moment to discover it.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Tables have only primary keys; policies filter on unindexed columns; auth.uid() appears bare inside policy predicates; no EXPLAIN output or advisor report exists anywhere. |
| **1 · Ad-hoc** | Some indexes exist where a migration author happened to add them, with no correspondence to policy predicates or client order-by columns; foreign keys are partially unindexed; no plan evidence. |
| **2 · Defined** | Indexes deliberately cover the main owner/tenant columns and most policy predicates, and the gaps are known; auth functions are wrapped in newer policies but older ones remain unwrapped; EXPLAIN was run ad hoc during development. |
| **3 · Managed** | Every policy predicate column, foreign key, and hot filter/order path is indexed, verified with recorded EXPLAIN (ANALYZE) output under a representative authenticated role; new migrations adding filters or policies include their indexes in the same change. |
| **4 · Verified** | A runnable harness or CI step executes the database linter/advisors (unindexed foreign keys, auth function per-row evaluation) and EXPLAIN assertions on the hot RPCs against a real instance; index drift fails the pipeline. |

### Audit checklist

- [ ] Grep migrations for `create policy` and extract every column referenced in USING/WITH CHECK, including columns inside membership subqueries; build the list of predicate columns per table.
- [ ] Grep migrations for `create index` and diff against the predicate-column list plus the columns used by client `.eq/.in/.order` calls and RPC WHERE/ORDER BY clauses; every uncovered hot column is a finding.
- [ ] Grep policies and security-definer RPC bodies for bare `auth.uid()` not wrapped as `(select auth.uid())`; each bare occurrence in a row-level predicate is a per-row evaluation finding.
- [ ] List foreign key columns (grep `references ` in migrations) and confirm each has an index or a documented reason not to.
- [ ] Run `explain (analyze, buffers)` as an authenticated role on the top three list queries/RPCs (timeline fetch, connection lookups, media listing) against seeded data; record whether plans use index scans or fall back to sequential scans with policy re-checks.
- [ ] Check whether keyset-pagination order columns have a composite index led by the owner/tenant column so page N is as cheap as page 1.

### Monitoring signals

- Grep for `auth.uid()` in policy bodies not preceded by `select ` returns nothing.
- A committed script (packages/*/scripts) runs advisors or EXPLAIN assertions against the linked instance; its presence and green run is the signal.
- Database linter report shows zero `unindexed_foreign_keys` findings.

### References

- [Supabase Row Level Security — RLS performance recommendations (add indexes, wrap functions in select)](https://supabase.com/docs/guides/database/postgres/row-level-security)
- [PostgreSQL Indexes — Chapter 11, index types and multicolumn indexes](https://www.postgresql.org/docs/current/indexes.html)
- [Supabase database advisors — Performance advisors incl. unindexed foreign keys](https://supabase.com/docs/guides/database/database-advisors)

### Typical remediation

Wrap auth functions as scalar subselects in a corrective migration, add composite indexes matching each policy predicate and hot order path, then commit a harness that runs the database advisors and EXPLAIN checks so the next unindexed predicate is caught in CI.

*Issue skeleton:* [`templates/prf-04.md`](../templates/prf-04.md)

---

## PRF-05 · Bounded, batched, lean client reads

**Do client data layers fetch lists with a constant number of round trips, an explicit page bound, and only the columns they render, with no per-item follow-up queries?**

`query-efficiency` · applies to: `web` `ios` `android` `admin` · default impact **3/5** · weight **3/3**

Every list read carries an explicit limit or range, growing histories paginate by keyset cursor rather than deep offset, and related rows arrive in the same round trip via embedding, an RPC join, or one IN-batched query, never via an awaited query inside a loop over items. Projections name the columns the UI renders instead of selecting entire wide rows. The number of requests to render any screen is independent of the number of items on it.

*Why it matters:* N+1 loops and unbounded selects are invisible at development data volumes and dominate latency, egress cost, and battery at real volumes; on a shared DB they multiply across every client surface that copies the pattern.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | List queries have no limit; data layers loop over fetched items issuing one query each for related rows; select-star is the default projection everywhere. |
| **1 · Ad-hoc** | Some queries carry limits and one or two screens batch their reads, but the pattern is inconsistent; at least one production screen still issues O(n) requests or an unbounded read. |
| **2 · Defined** | The data layer deliberately paginates and batches on the main screens; remaining N+1 or select-star sites are known and listed; pagination may still be offset-based on unbounded histories. |
| **3 · Managed** | All list reads are bounded, keyset-paginated where histories grow, batched to O(1) round trips per screen, and project named columns; the shape is consistent across the surface and checked in review, mirrored on sibling clients. |
| **4 · Verified** | Automation guards the shape: a lint rule or CI grep fails on awaited queries inside loops and on select-star in the data layer, and a request-count assertion or recorded har/trace test pins the round-trip count of key screens. |

### Audit checklist

- [ ] Grep each client's data layer for loops containing awaited queries: `for`/`map`/`forEach` bodies with `await` on the DB client (web/admin), and per-item async fetches inside list builders on iOS/Android; each hit is an N+1 candidate to trace.
- [ ] Grep for `.select("*")` / `select('*')` and wildcard projections; for each on a multi-column or sensitive table, compare against the columns the rendering component actually uses.
- [ ] List every query that renders a list and confirm it carries `.limit(` or `.range(`; flag list fetches with neither as unbounded reads.
- [ ] For unbounded histories (timelines, activity feeds), verify pagination is keyset (cursor on an indexed, tie-broken column) rather than growing offsets, and that the fetch-one-extra-row pattern or equivalent detects the last page.
- [ ] Trace one full render of the primary list screen per surface and count DB round trips; the count must not depend on the number of items (embedded relations or batched IN queries).
- [ ] Check the sibling mobile client mirrors the same batched shape rather than re-deriving a per-item loop.

### Monitoring signals

- Grep for `await` inside loop bodies in lib/data (and mobile data services) returns nothing unexplained.
- Grep for wildcard select in data-layer files returns nothing.
- Every file defining a list query matches a grep for `limit(|range(`.

### References

- [PostgREST resource embedding — Embedding related resources in one request](https://postgrest.org/en/stable/references/api/resource_embedding.html)
- [PostgREST pagination and count — Limits, offsets, and Range headers](https://postgrest.org/en/stable/references/api/pagination_count.html)
- [Use The Index, Luke: No Offset — Keyset pagination vs OFFSET](https://use-the-index-luke.com/no-offset)

### Typical remediation

Collapse per-item loops into embedded selects, RPC joins, or one IN-batched query; add explicit limits and convert deep-offset paths to keyset cursors; then add a data-layer lint (or CI grep) for awaited-query-in-loop and wildcard projections.

*Issue skeleton:* [`templates/prf-05.md`](../templates/prf-05.md)

---

## PRF-06 · Mobile cold start, frames, and animation

**Are cold start and frame rendering kept within platform vitals, with blocking work off the launch path and main thread, contained recomposition/invalidation scope, and animations running on the render path?**

`mobile-startup` · applies to: `ios` `android` · default impact **3/5** · weight **2/3**

The app reaches its first interactive frame without synchronous network or heavy disk work in the launch path; long-running work runs off the main thread; lazy lists supply stable keys and avoid per-frame allocation; state is modeled so a change invalidates only the views that read it (stable types, derived/memoized state) instead of recomposing or re-evaluating whole screens; animations use the toolkit's declarative animation APIs or a dedicated runtime rather than re-laying-out full hierarchies per frame. Startup and jank are profiled with platform tools, and release builds ship startup optimizations where the platform offers them (for example baseline profiles on Android).

*Why it matters:* Store-vitals dashboards and user retention both punish slow launches and dropped frames; on declarative UI toolkits the dominant failure mode is unscoped invalidation, which no amount of hardware hides on mid-range devices.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Launch path performs synchronous network or migration work before first frame; lists are non-lazy or keyless; state changes rebuild entire screens; no profiling artifact, benchmark, or baseline profile exists in the repo. |
| **1 · Ad-hoc** | Lazy containers and async loading exist where the toolkit made them the default, but launch-path work and invalidation scope were never examined; at least one screen visibly re-renders wholesale on unrelated state changes. |
| **2 · Defined** | Launch sequence is deliberately structured (deferred init, async first fetch) and hot lists use keys and remembered/derived state; known jank spots or missing baseline profiles are documented; profiling was done manually at least once. |
| **3 · Managed** | Startup and frame discipline is systematic across screens: no blocking work pre-first-frame, contained invalidation verified on hot screens, animation code uses render-path APIs, and profiling (Instruments traces, Macrobenchmark or equivalent) is part of the working practice with recorded results. |
| **4 · Verified** | CI runs startup/frame benchmarks (Macrobenchmark with startup and frame-timing metrics, or scripted launch-time measurement) with thresholds, release builds include baseline profiles where applicable, and a regression in launch time or jank fails or flags the pipeline. |

### Audit checklist

- [ ] Read the app entry (App/Scene init and root view on iOS, Application.onCreate and MainActivity on Android) and flag synchronous network calls, migrations, or large decoding before first frame; check third-party SDK init is lazy or moved to androidx.startup/background.
- [ ] Grep Compose list code for `LazyColumn`/`LazyRow` item `key =` usage and for `remember`/`derivedStateOf` around computed values read in composition; flag unkeyed lists and computations re-run per recomposition.
- [ ] On iOS, inspect hot SwiftUI views for over-broad observation (whole-store @Observable/ObservedObject dependencies feeding large bodies) and heavy work inside `body`; check `.task`/`.onAppear` ordering relative to first paint.
- [ ] Grep for main-thread blockers: `DispatchQueue.main.sync`, semaphores on the main actor (iOS); `runBlocking`, disk/DB access without a dispatcher in UI code (Android).
- [ ] Check animation implementations use declarative APIs (withAnimation/animate*AsState, or a dedicated animation runtime for vector assets) rather than timers mutating layout-affecting state per frame.
- [ ] Look for benchmark/profiling infrastructure: a Macrobenchmark or baselineProfile Gradle module, Instruments trace notes or signposts, and whether any of it runs in CI.

### Monitoring signals

- A Macrobenchmark (startup/frame timing) module exists and runs in CI; baseline profile artifacts present in release builds.
- Grep for `runBlocking|DispatchQueue.main.sync` in app UI code returns nothing.
- Grep Compose lists for `key =` shows every LazyColumn/LazyRow itemized with stable keys.

### References

- [Android: App startup time — Cold start expectations and diagnosis](https://developer.android.com/topic/performance/vitals/launch-time)
- [Jetpack Compose performance — Stability, keys, deferred reads](https://developer.android.com/develop/ui/compose/performance)
- [Baseline Profiles overview — Startup improvement via profile-guided compilation](https://developer.android.com/topic/performance/baselineprofiles/overview)
- [Apple: Reducing your app's launch time — Minimize work at launch](https://developer.apple.com/documentation/xcode/reducing-your-app-s-launch-time)

### Typical remediation

Defer all non-essential launch work behind first frame, key the lazy lists and contain state reads with derived/memoized values, move blocking calls off the main thread, then pin the result with a startup benchmark in CI and (on Android) a baseline profile.

*Issue skeleton:* [`templates/prf-06.md`](../templates/prf-06.md)

---

## PRF-07 · Layered caching strategy and offline reads

**Does each class of data (immutable assets, media, private user data) have a named caching policy, and does a cold open on a dead network render cached content with a clear sync state instead of a spinner or crash?**

`caching` · applies to: `web` `ios` `android` · default impact **2/5** · weight **2/3**

Caching is a designed layer, not SDK defaults: static assets are cached long under hashed URLs, media rides CDN and client caches with correct lifetimes, and user data is cached locally (service-worker runtime caching, an in-memory store, or an on-device database) with explicit staleness and invalidation rules. Offline or flaky-network opens show the last known content and an honest sync indicator. Sign-out purges every cache that held private content, on every surface.

*Why it matters:* A journaling product is opened in pockets of dead connectivity; the difference between a cached read and a spinner is whether the product is trusted with daily moments. Unpurged private caches also turn a performance layer into a privacy defect on shared devices.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | No service-worker runtime caching or on-device persistence beyond framework defaults; offline open shows a blank screen, infinite spinner, or crash; no cache invalidation on sign-out. |
| **1 · Ad-hoc** | Some caching exists because a library shipped it (an image loader's disk cache, default SW precache) but nobody chose policies; offline behavior differs by screen and was never tested; sign-out purge unexamined. |
| **2 · Defined** | Cache policies are chosen per data class and visible in config (SW runtime routes with named strategies, a client store with staleness rules, bounded media caches) with documented gaps; offline open works on at least the primary screen; sign-out purge exists on some surfaces. |
| **3 · Managed** | The full read path is cache-layered on every surface with consistent staleness rules, offline cold-open renders cached content with a sync state, and sign-out purges private caches everywhere; behavior verified by a repeatable manual or automated offline test. |
| **4 · Verified** | Automated tests exercise offline cold-open and sign-out purge (SW integration tests, airplane-mode UI tests, or harness scripts), and cache configuration drift (a route losing its strategy, a purge call removed) is caught by CI. |

### Audit checklist

- [ ] Open the service worker source/config (Serwist/Workbox sw entry and its runtime caching rules); enumerate route classes and their strategies, and flag authenticated API responses that are SW-cached without an eviction/purge story.
- [ ] Identify the client-side data cache on web (SWR/React Query/custom provider store): what is its staleness rule, what survives a reload, and what renders offline.
- [ ] On iOS/Android, check for on-device persistence backing list screens (SwiftData/CoreData/files, Room/DataStore) and run or reason through an airplane-mode cold open of the primary screen.
- [ ] Verify media loader caches are bounded (URLCache/Coil disk cache size) and that CDN-cacheable URLs are stable across sessions rather than re-signed with short expiries that defeat the cache.
- [ ] Trace sign-out on each surface and list every cache purged (SW caches, client store, keychain/preferences, image disk caches, local DB); anything holding private content and not purged is a finding.
- [ ] Search docs for a written cache policy per data class; absence caps the level at 2.

### Monitoring signals

- SW config enumerates named runtime caching strategies per route class (its absence or an empty runtimeCaching is the drift signal).
- Sign-out call sites match a grep for cache purge/clear on every surface.
- An offline test (integration or scripted) exists and passes in CI.

### References

- [HTTP Caching (RFC 9111) — Freshness and validation model](https://www.rfc-editor.org/rfc/rfc9111)
- [web.dev PWA course: Caching — Caching strategies (cache-first, stale-while-revalidate)](https://web.dev/learn/pwa/caching)
- [Offline UX design guidelines — Communicating state when offline](https://web.dev/articles/offline-ux-design-guidelines)

### Typical remediation

Write the one-page cache policy table (data class, layer, TTL, invalidation), implement it in the SW runtime routes and client stores, back mobile lists with on-device persistence, and wire sign-out purges; then encode offline cold-open and purge as tests.

*Issue skeleton:* [`templates/prf-07.md`](../templates/prf-07.md)

---

## PRF-08 · Network and battery frugality

**Is recurring network work event-driven or justified and bounded (no unscoped polling, realtime subscriptions torn down when hidden, backoff on failure, deduplicated in-flight requests), so radios and batteries are not taxed by invisible traffic?**

`network-frugality` · applies to: `web` `ios` `android` `admin` · default impact **2/5** · weight **2/3**

Recurring fetches are push/realtime-driven or are bounded polls with an explicit interval and a written justification; realtime channels are scoped to visible screens and removed on navigation or backgrounding; failed requests retry with exponential backoff and jitter instead of tight loops; concurrent requests for the same resource are coalesced; scheduled background work uses the platform scheduler (BGTaskScheduler, WorkManager) with constraints rather than timers. The steady-state network profile of an idle, backgrounded app is near zero.

*Why it matters:* Chatty clients drain batteries, burn metered data, and multiply DB load across every user simultaneously; platform vitals and OS-level background policing both punish apps that poll, and a tight retry loop against a failing endpoint is a self-inflicted outage amplifier.

### Maturity anchors

| Level | Anchor |
| --- | --- |
| **0 · Absent** | Timers poll endpoints regardless of visibility; realtime channels are opened and never removed; failures retry immediately in a loop; background work runs on ad-hoc timers; nobody can state the idle network profile. |
| **1 · Ad-hoc** | Some polling has a sane interval or one screen cleans up its subscription, but the patterns are accidental; at least one leaked channel, visibility-blind poll, or retry-without-backoff exists in production paths. |
| **2 · Defined** | Polling intervals, subscription teardown, and retry behavior are deliberate on the main flows and documented; known exceptions (an admin dashboard poll, a legacy timer) are listed; deduplication may be partial. |
| **3 · Managed** | The surface is systematically event-driven: every channel has a matching teardown, polls are visibility-gated and justified, retries back off with jitter, in-flight requests are deduplicated, and background work goes through the platform scheduler; verified by review or tests. |
| **4 · Verified** | Automation guards frugality: lint/CI greps forbid unjustified timers and channel-without-teardown, and an idle-traffic or energy measurement (network log assertion, platform energy diagnostics in CI or release checks) detects regressions. |

### Audit checklist

- [ ] Grep all surfaces for polling primitives: `setInterval` (web/admin), `Timer.scheduledTimer`/`Task.sleep` loops (iOS), `Handler.postDelayed`/`while(true)` with delay (Android); for each hit record interval, screen scope, visibility gating, and stated justification.
- [ ] Grep for realtime usage (`channel(`, `.subscribe(`) and pair every subscription with its teardown (`removeChannel`/`unsubscribe`) in the component/view lifecycle (unmount, onDisappear, DisposableEffect); unpaired sites are leaks.
- [ ] Read the retry/error paths in the shared fetch helpers: confirm exponential backoff with jitter and a retry cap, not immediate or fixed-interval retries.
- [ ] Check for request deduplication in the data layer (in-flight promise map, SWR-style dedupe, or actor-serialized fetches) so double-mounted screens do not double-fetch.
- [ ] On mobile, verify any background refresh uses WorkManager/BGTaskScheduler with constraints (network type, charging) rather than foreground timers, and that backgrounding closes or pauses realtime sockets.
- [ ] Approximate the idle profile: with the app open but untouched for a minute (or by reading the code paths), list every request that would fire; each one needs a reason.

### Monitoring signals

- Grep for `setInterval|scheduledTimer|postDelayed` in app code returns nothing, or only sites carrying a justification comment.
- Every `channel(`/`.subscribe(` call site pairs with a teardown in the same lifecycle scope (a grep-diff of subscribe vs remove counts per file is the quick check).
- The shared retry helper matches a grep for backoff/jitter; no bare retry loop exists.

### References

- [Android: Optimize for Doze and App Standby — Restrictions on network and jobs in Doze](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Energy Efficiency Guide for iOS Apps — Minimize and defer networking](https://developer.apple.com/library/archive/documentation/Performance/Conceptual/EnergyGuide-iOS/index.html)
- [Supabase Realtime — Channels and subscription lifecycle](https://supabase.com/docs/guides/realtime)

### Typical remediation

Replace polls with realtime or visibility-gated fetch-on-focus, add teardown to every subscription site, centralize retries behind one backoff-with-jitter helper with deduplication, and move background refresh onto the platform scheduler; then add the greps as CI checks.

*Issue skeleton:* [`templates/prf-08.md`](../templates/prf-08.md)
