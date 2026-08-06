# Arkaik bootstrap — the Pebbles proving run

**Date:** 2026-08-06
**Method:** `~/code/arkaik/docs/bootstrap.md`, sequence fixed by
`~/code/arkaik/docs/superpowers/specs/2026-08-04-bootstrap-method-design.md` §8
**Mode:** brownfield (173 existing nodes), hosted (`prj_5dDiZc-G6lseF3cb` @ arkaik.app)
**CLI:** `arkaik` npm-linked to `~/code/arkaik/packages/cli` (local build)

This is the method's first full-parity run on a real product. It is the proving
ground, so this doc records what happened — including what the method got wrong
— not just the output.

---

## Step 0 — the opener

`arkaik --version` reported **0.1.1** while `packages/cli/package.json` said
**0.1.2**: the linked `dist/` was stale. Rebuilt with `npm run build -w arkaik`
before touching anything.

`arkaik init --update` then reported:

```
Upgraded skill v3.0.0 -> v3.2.0.
```

**The method's number-one documented failure mode was live in this repo.** The
installed skill predated the `acceptance` and `decision` species, so every
wave-2 and wave-3 node this run produces would have been rejected at the gate,
and only at the gate. The warning earned its place.

`arkaik init --bootstrap` then installed `arkaik-bootstrap` v1.0.0 beside the
maintenance skill. `values.md` is present, so value mapping is in scope.

## Corpus

```
324 merged PRs, 184 docs, 53 surfaces
```

Matches §8's expectation exactly (324 PRs).

---

## Wave 0 — recon

Run by the operator agent rather than a subagent: the profile drives every
later wave, and the product understanding it produces is what makes the
adversarial review of waves 1–3 possible.

**Output:** `.arkaik/bootstrap/profile.json` — 10 areas, 8 eras, platform axis
`["web", "ios", "android"]`.

`products` is deliberately **not** declared. Pebbles is one product with four
surfaces; the admin app is its back-office, not a sibling app. The existing 173
nodes carry no product field, and declaring one now would demand assigning a
product to every one of them for no gain.

### Finding 1 — areas must span the pre-monorepo layout

First profile draft used only `apps/web/...`, `apps/ios/...`, `apps/android/...`
paths. Coverage check:

```
PRs matching NO area: 124 / 324   (79 of them feat-labeled)
```

Cause: PR #222 (2026-04-10) migrated the repo to Turborepo. Before it, the web
app lived at the repo root — `app/`, `components/`, `lib/`. Every genesis-era
PR was invisible to every area, which would have starved the wave-1 and wave-2
agents of the entire founding history of the product.

Fix: each area carries **both** layouts (`components/record` *and*
`apps/web/components/record`). After the fix:

```
PRs matching NO area: 42 / 324   (6 feat-labeled, all infra/process)
uncovered surfaces:    0 / 53
```

The 42 remainders are chores, CI, dependency bumps and docs — correctly outside
anatomy. All 42 still reach wave 3, because era slices filter on date, not path.

> **Method lesson.** A brownfield repo that changed its own directory layout
> mid-history needs the *historical* paths in the profile, not just today's. The
> wave-0 checklist item "every area has at least one real path" passes trivially
> against today's tree while the slice silently starves. The check that catches
> it is *corpus* coverage — count the PRs matching no area — and that check
> should be in the wave-0 reviewer checklist.

### Finding 2 — adjacent eras may not share a boundary date

The walkthrough says "windows are half-open, so adjacent eras may share a
boundary date but must never overlap." Written literally (`to: 2026-04-10` /
`from: 2026-04-10`), `plan` rejects it:

```
profile.json eras "web-prototype" and "platform-foundation" have overlapping
date windows.
```

`era-window.ts` expands a **date-only** `to` to the *start of the next day* so
the whole named day is included. So a date-only `to` is inclusive-of-day, and
the next era must start the **following** day. Corrected windows are adjacent,
not shared.

> **Method lesson.** The doc's phrasing is true only for `to` values carrying an
> explicit time component. For the date-only form everyone actually writes, the
> rule is "`to` is the last day of the era." Worth fixing in `bootstrap.md` and
> in the wave-0 checklist.

### Wave 0 gate

| Check | Result |
|---|---|
| Area ids / era slugs kebab-case | pass (`plan` accepted) |
| Areas cover the product's code | pass — 0/53 surfaces uncovered |
| Era windows bounded, non-overlapping | pass (after Finding 2) |
| Eras cover the PR timeline | pass — **324/324 PRs in exactly one era** |
| Platform axis matches how it ships | pass — web/ios/android |

Era shape independently corroborated by Lab Note density, which tracks the
Lab Note pipeline going live rather than being fitted by hand:

| Era | PRs | Lab Notes |
|---|---|---|
| web-prototype (03-26 → 04-09) | 92 | 0 |
| platform-foundation (04-10 → 04-11) | 13 | 0 |
| ios-arrival (04-12 → 04-29) | 41 | 0 |
| back-office-and-insight (04-30 → 05-08) | 20 | 0 |
| cross-surface-alignment (05-09 → 06-28) | 53 | 1 |
| karma-economy (06-29 → 07-10) | 23 | 10 |
| android-arrival (07-11 → 07-18) | 42 | 16 |
| store-readiness (07-19 → 08-04) | 40 | 18 |

`plan` expanded to **31 units**: 10 × w1, 10 × w2, 8 era units + `w3-decisions`
+ `w3-status-arcs`.

---

## Wave 1 — anatomy reconcile

Driver: **warm in-session subagents**, the method's documented default. All 10
units dispatched concurrently in one message.

### Finding 3 — concurrent units cannot each write the manifest

`SKILL.md` tells each unit agent: "When yours is written, set your unit's status
to `done` in `.arkaik/bootstrap/manifest.json`." With the *documented default
driver* — 10 warm subagents in one session — that is 10 concurrent
read-modify-write cycles on one JSON file, with no locking. Lost updates are not
a risk, they are the expected outcome.

Mitigation for this run: every unit agent is explicitly told **not** to touch the
manifest; the operator marks units `done` after review. This is also better
gate discipline — a unit is `done` when it has *passed review*, not when its
author says so.

> **Method lesson.** Either the CLI should own the status write
> (`arkaik bootstrap done <unit>`, doing an atomic rewrite), or the skill should
> say the operator marks units done. As written, the skill's instruction is
> unsafe under the driver the method itself recommends.

### Hosted preflight

Checked before doing any work, so an auth failure would surface now rather than
after four waves of tokens:

```
prj_5dDiZc-G6lseF3cb  Pebbles (173 nodes)
```

Hosted node count equals the local bundle's — cache and remote are in sync at
the start of the run, so the restore delta at the end is purely this run's work.

### Finding 4 — `corpus` never inventories dot-directories

The `w1-connections` agent flagged `apps/web/app/.well-known/apple-app-site-association/route.ts`
as a real route handler owned by no area. Checking, the cause is not the profile:

```
grep -c "well-known" .arkaik/corpus/surfaces.json   ->  0
```

`arkaik bootstrap corpus` never inventoried it. The surface scanner skips
dot-directories, so `.well-known/` route handlers are invisible to **every**
slice in **every** run, no matter how the areas are drawn. That class of file is
not incidental: Apple App Site Association and Android `assetlinks.json` are the
transport that makes deep-link invites work at all.

Added `API-apple-app-site-association` by reviewer overrule.

> **Method lesson.** A CLI bug, not a judgment one. The surface scanner should
> either stop skipping dot-directories or state the exclusion, because a silent
> omission at corpus time is unrecoverable downstream — no agent can map a file
> it is never shown.

### Finding 5 — a reconcile can shrink a playlist but can never remove the orphaned edges

**This finding began as a reviewer error, and the error is worth recording as
carefully as the finding.**

The reviewer audited the bundle for the skill's playlist ↔ `composes` invariant
and reported that **all 12 flows had a playlist and zero `composes` edges** — 41
edges missing product-wide — and dispatched six units to repair them. That
audit was wrong. It filtered edges on `e.kind`, which is the **fragment
contract's** field name; a bundle edge carries **`edge_type`**. Every comparison
tested `undefined === "composes"`, so every flow read as having no edges at all.

The real state of the map before the run: **11 of 12 flows agreed exactly.** The
hand-maintained map was in far better shape than the audit claimed.

Cost of the error: six units were told to add `composes` edges that already
existed. No damage reached the bundle — an identical edge from two sources is a
documented silent no-op, and the post-merge check confirms 12 of 13 flows
agreeing — but it spent real agent tokens, and two units were also handed a
"the validator enforces nothing" claim that was false.

> **Reviewer lesson, the transferable one.** The fragment contract and the bundle
> schema use *different field names for the same concept* — `kind` when an agent
> writes an edge, `edge_type` once merge has landed it. Any reviewer script that
> reads the bundle with the contract's vocabulary silently matches nothing and
> reports a catastrophe. Silent-zero is the dangerous failure shape: it looks
> exactly like a real finding. A reviewer check should assert its own
> denominator first — had the script printed "composes edges found in bundle: 0"
> against 337 total edges, the bug was obvious.
>
> Twice more the same class bit the same audit: a playlist walker that did not
> recurse junction `cases[].entries[]` under-counted four flows, and one that did
> not recurse `condition` `if_true[]`/`if_false[]` wrongly failed
> `F-admin-back-office` (16 entries read as 1). **The playlist grammar has four
> recursion points — `entries`, `cases`, `if_true`, `if_false` — and a walker
> missing any of them under-reports rather than errors.**

### The finding that survived

One flow genuinely violated the invariant, and still does after the merge:

```
BAD F-record-pebble   playlist 11   composes 16
    EXTRA: V-emotion-pearl, V-cards-thoughts, V-pebble-revelation,
           V-record-celebration, V-karma-flash
```

`w1-path-and-capture` established that PR #161 deleted the multi-step record flow
and archived four of its steps. It correctly rewrote the flow's playlist and
added a matching set of `composes` edges. But the **pre-existing edges to the now
archived steps remain**, because:

- the fragment contract's `edges` array is **add-only** — there is no
  remove-edge op, by design, since bootstrap never deletes; and
- `arkaik validate` enforces playlist-ref ⇒ edge (so nothing is dangling) but
  **not** edge ⇒ playlist-ref, so a `composes` edge to a view no longer in the
  journey raises nothing.

So a brownfield reconcile can shrink a flow, and the map keeps edges asserting
that the flow still contains four screens the product deleted. The unit predicted
this precisely and asked for a human sweep.

> **Method lesson.** "Bootstrap never deletes" is right for *nodes* — retire
> preserves history and a human decides removal. Applied to *edges* it has no
> escape hatch at all: an edge cannot be retired, only orphaned. Either the
> contract needs an edge-level retire, or `validate` needs to warn on a
> `composes` edge whose target is absent from the flow's playlist (or is
> `archived`), so the sweep is at least visible. Today it is neither blocked nor
> reported.

### Finding 6 — concurrent units collide in the shared scratchpad

Three separate unit agents reported the same thing independently: they wrote
their slice to `scratchpad/slice.json`, and a sibling unit overwrote it
mid-read. Each recovered by switching to a unit-scoped filename.

Nothing in the method warns about it, and the failure is silent and
data-corrupting rather than loud — an agent can read half of one unit's slice
and half of another's. `SKILL.md`'s worked example is
`arkaik bootstrap slice <unit> > slice.json`, which is precisely the colliding
form.

> **Method lesson.** The skill's example should be
> `arkaik bootstrap slice <unit> > <unit>.slice.json`, or `slice` should default
> to writing a unit-scoped file. A one-word doc fix that removes a silent
> cross-contamination class.

### Finding 7 — the churn guard is per-unit, so the wave's total churn is unbounded

Every unit came in under the guard. The aggregate did not:

| Unit | update + retire |
|---|---|
| w1-profile-and-library | 28 |
| w1-data-platform | 26 |
| w1-path-and-capture | 19 |
| w1-entry | 18 |
| w1-glyphs | 14 |
| w1-connections | 12 |
| w1-lab | 6 |
| w1-admin | 6 |
| w1-render-and-design | 2 |
| w1-karma | 1 |
| **Total** | **132 ops against a 173-node map** |

The guard is worded per unit — "a **unit** proposing `retire` or `update` on more
than 20%" — and 20% of 173 is 34. Ten units can each sit at 33 and collectively
rewrite nearly twice the map without a single stop-for-review firing. Here the
real figure is softened because many `update`s are status/platform restatements
that merge no-ops, but the guard cannot know that in advance: it counts
proposals, not effects.

**Measured after the merge**, not just proposed: of the 173 original nodes, **101
were genuinely changed** (58.4%), 72 untouched, and 83 added — the map went
173 → 256 nodes and 337 → 581 edges in one wave. Archived nodes went 10 → 20.

So the wave really did rewrite well over half the map while every single unit sat
comfortably under the per-unit guard, the highest being 28 of a permitted 34.

> **Method lesson.** The guard needs a wave-level companion — total distinct
> nodes touched by update-or-retire across all units, against the same 20%. That
> is the number a human actually wants to review before a merge rewrites their
> map.

### Finding 8 — the id-convergence model is blind to same-concept, different-title

The fragment contract's coordination model is explicit: "ids are derived
deterministically from titles, so independent agents converge on the same id for
the same thing and collide loudly on different things." It worked exactly as
designed where two units named a thing identically — `w1-data-platform` and
`w1-profile-and-library` both declared `DM-achievements` / `DM-achievement-unlocks`,
and `w1-admin` and `w1-data-platform` both declared the five achievement admin
RPCs, all silent no-ops.

But it is one-sided. `w1-karma` mapped the unlock celebration as
`V-achievement-unlock-moment` ("Achievement Unlock Moment"); `w1-profile-and-library`
mapped **the same screen** as `V-achievement-moment` ("Achievement Moment"). Same
surface, two titles, therefore two ids, therefore no collision — merge would have
landed both, silently, as separate views of one screen.

It was caught only because `w1-karma` predicted it in its report and the reviewer
went looking. Resolved by keeping karma's node (its `created_ts` is the true
first-shipping instant, #676's, and it carries per-surface `platformNotes`).

> **Method lesson.** Loud collision on *different* titles is the easy half. The
> dangerous half is *silent duplication* on different titles for the same thing,
> and the contract has no defence against it. A merge-time near-duplicate warning
> (same species, high title similarity, overlapping edges) would have flagged
> this one instantly.

### Finding 9 — merge detects structural conflicts, not semantic ones

`w1-glyphs` **retired** `API-get-marks` / `API-create-mark` / `API-delete-mark` as
prototype fictions. `w1-data-platform`, reading all 61 migrations, **retitled the
same three** to `select glyphs` / `insert glyphs` / `delete glyphs` as real
PostgREST contracts. Both ops are individually valid, target an existing node,
and collide on nothing merge checks — so merged as-is each node would come out
**retitled *and* archived**.

Adjudicated in favour of the retitles: glyph CRUD is live on all three surfaces,
and the map's own convention already names PostgREST calls as endpoints
(`API-get-wallet-summary` is titled `select v_wallet_summary`). Archiving a live
capability is a false negative, which is the more expensive error in a map.

> **Method lesson.** Two units can hold *opposite* verdicts about one node and
> merge will apply both. Worth a merge-time check for the specific contradiction
> of a node being both patched and retired in the same run — cheap to detect,
> and it is exactly the case where the two agents disagree about reality rather
> than about formatting.

### Finding 10 — the schema cannot express database lineage

The wave-1 merge dry-run failed with 27 errors, all one class:

```
ERROR [edge-semantics] Edge e-DM-v-pebbles-full-DM-pebbles:
  "queries" not valid from data-model to data-model
```

`w1-data-platform` had wired each database **view** to the tables it reads.
Checking the schema's edge table, the complete legal set is:

| kind | source → target |
|---|---|
| `composes` | flow→view, flow→flow, view→flow, view→view |
| `calls` | view→api, flow→api, api→api, api→view |
| `displays` | view→data-model |
| `queries` | api-endpoint→data-model |

There is **no legal edge between two data-models**, of any kind. So the fact that
`v_pebbles_full` reads eleven specific tables — real, useful, and exactly the
sort of thing a product map of a database-backed app should carry — is not
expressible. The 27 edges were dropped.

This is not an agent error; the unit picked the only kind that reads correctly in
English, and no kind would have worked.

> **Method lesson.** Worth a schema question upstream: either a `derives-from`
> (or `reads`) edge for data-model→data-model lineage, or an explicit note in
> `schema.md` that DB-internal lineage is deliberately out of scope so agents
> stop rediscovering the gap. Notable that this only bites on a repo whose map
> models physical tables *and* views — the `DM-` concept/table rule invites
> exactly that, then the edge table forbids connecting them.

### The gate

The merge dry-run is doing real work as a gate: it caught a 27-error class that
no unit's own self-validation found, because each unit validated its fragment in
isolation and the rule being broken is a *schema* rule, not a fragment-shape one.

### Wave 1 gate

| Check | Result |
|---|---|
| Species correct (route=`API-`, page=`V-`, journey=`F-`, stored=`DM-`) | pass |
| No `DM-` concept/table id collisions | pass — 28 `DM-` added, 0 collisions |
| Flow playlists agree with `composes` | 12 / 13 — `F-record-pebble` carries 5 stale edges (Finding 5) |
| Edge kinds legal | pass — after dropping 27 `DM-`→`DM-` (Finding 10) |
| `created_ts` from real PRs, never today | pass — 83 adds, range 2026-03-27 → 2026-07-30, 0 suspect |
| Churn guard, per unit | pass — max 28 of 34 |
| Nothing deleted; every retire has an actionable reason | pass — 22 archived, 0 without a reason |
| `arkaik bootstrap merge` | clean |
| `arkaik validate` **warning-clean** | **pass — VALID, zero warnings** |

Cross-fragment checks the reviewer ran that the CLI does not: nodes both updated
and retired (0), same id with different titles (0), same edge pair with
conflicting kinds (0), nodes patched by two units (10 — all disjoint keys,
`title` from `w1-data-platform` vs `status`/`platforms` from
`w1-profile-and-library`, one status-change each, explicit `changed_ts` — exactly
the clean composition the contract predicts).

**Result: 173 → 256 nodes, 337 → 581 edges, 253 → 420 journal events.**

The single most valuable product finding of the wave: `w1-profile-and-library`
traced which PRs re-emitted `bundle.json` and showed the **last stretch of the
product shipped without ever updating the map** — #677/#678/#680 (M50 public
profiles, all three surfaces), #679/#681 and #684/#685/#686 (M48 achievements),
#636/#638 (account deletion on iOS/Android). The entire public-profile surface
was absent from the map. That is the honest reason wave-1 churn ran high: the map
was not wrong so much as *behind*.

---

## Wave 2 — acceptances, values, platform scoping

10 units, one per area, dispatched concurrently. Every unit was told the
wave-level stake explicitly — that a wave collapsing onto one value element is
rejected *in full*, all 10 units — rather than being left to discover the balance
rule from the reference. Each was also asked to report its own value histogram,
so the balance check is computable before the merge rather than after.

Three unit briefs carried forward specific truths wave 1 had established, so
per-platform scoping would be grounded rather than assumed:

- **w2-connections** — `assetlinks.json` still holds the literal
  `REPLACE_WITH_PLAY_APP_SIGNING_SHA256` placeholder, so Android App Links cannot
  verify and the invite-accept screen is unreachable from a real link. Told not
  to claim `live` on android.
- **w2-lab** — web filters the feed by platform, iOS and Android do not, so an
  `ios`-tagged log renders on Android. Told to encode that asymmetry rather than
  paper over it.
- **w2-render-and-design** — the wobble renderer is compiled out of production on
  all three surfaces. Told it must not be claimed live anywhere.

Two units got a shape warning instead of facts, because their areas have almost
no views of their own and the natural failure is writing unit tests as
acceptances: **w2-data-platform** was redirected from "the RPC inserts a row" to
the cross-cutting guarantees (deletion really purges, drafts survive, the
cross-user projection is an allowlist), anchored on the views where a user meets
them; **w2-render-and-design** was told to promise only what a user could notice,
not components or tokens.

### The values balance gate — passed, decisively

The wave's own hard gate is that no single value element may carry more than half
the acceptances; the documented failure mode is a wave collapsing into ~90%
`simplifies`.

**153 acceptances, 242 `covers` edges, 27 of the 30 Bain elements used.**

| element | n | share of acceptances |
|---|---|---|
| `avoids-hassles` | 30 | 19.6% |
| `informs` | 30 | 19.6% |
| `reduces-risk` | 24 | 15.7% |
| `design-aesthetics` | 18 | 11.8% |
| `reduces-anxiety` | 16 | 10.5% |
| `organizes` | 15 | 9.8% |
| `sensory-appeal` | 14 | 9.2% |
| … 20 more elements … | | |
| **`simplifies`** | **1** | **0.7%** |

The collapse element was used **once in 153 acceptances**. Three units used it
zero times and said so explicitly; several reported having considered and
rejected it in favour of a more specific element (`w2-path-and-capture`: "the
one-screen composer is `therapeutic-value` + `self-actualization`, not
`simplifies`").

> **Method observation.** The gate did its work *before* it ever had to fire —
> because each unit was told the wave-level stake up front and asked to report
> its own histogram. A gate that only runs at the end can reject a wave; a gate
> the workers know about shapes the work. Worth stating in `waves.md` that the
> balance rule belongs in the unit brief, not only in the reviewer checklist.

### Wave 2 structural checks

| Check | Result |
|---|---|
| Exactly one Given/When/Then per acceptance | pass — 0 violations of 153 |
| 1–3 values per acceptance | pass — 0 over three, **0 with none** |
| Every `covers` target resolves | pass — 0 unresolved |
| Every `covers` target is a view or flow | pass — 0 pointing at `DM-`/`API-` |
| No anchorless acceptances | pass — 0 |
| `platformStatuses` keys ⊆ node's `platforms` | pass — 0 stray |
| Duplicate acceptance ids across units | **1 — resolved** |

### Finding 11 — the same-id/different-title collision worked exactly as advertised

`w2-karma` and `w2-profile-and-library` independently minted
`AC-achievement-unlock-celebration` for the same promise. Both derived the same
id; their titles differed. That is a **hard merge failure naming both units** —
the loud half of the coordination model, and a direct contrast with Finding 8,
where two units named one concept differently and would have slipped through
silently.

Resolving it produced the more interesting result: the two units disagreed about
the acceptance's `created_ts`, and the disagreement was substantive.
`w2-profile-and-library` argued #684, not #676, because #676 shipped a *toast
pill* which #684 explicitly deleted and replaced with the chained card-per-badge
queue the gherkin actually promises. Verified:

```
#676 2026-07-30T07:58:56Z  unlock moment on web     (toast pill)
#684 2026-07-30T13:30:12Z  rewarding moment on web  (card queue)
```

Adjudicated: keep karma's wording (its gherkin generalises to "one or more
achievements" where the other fixes on "two badges"), take profile's timestamp.

> **The transferable rule, now in the record: node birth ≠ promise birth.** The
> *view* `V-achievement-unlock-moment` keeps #676, because the surface first
> existed then. The *acceptance* takes #684, because the promise first held then.
> A brownfield run will hit this whenever a surface shipped before the behavior
> it now guarantees, and nothing in the current skill says which instant to use.

### Wave 2 gate

```
Merged 20 fragments: +152 nodes, +241 edges, +152 events
Nodes: 408    Edges: 822    Journal: 572
Result: VALID   (warning-clean)
```

**256 → 408 nodes** (152 acceptances), **581 → 822 edges** (241 `covers`),
**420 → 572 journal events**.

Edge composition after two waves: `covers` 241, `queries` 218, `composes` 144,
`calls` 117, `displays` 102.

### Finding 12 — `validate`'s summary does not count acceptances

Cosmetic but misleading at exactly the wrong moment:

```
Nodes: 408 (88 views, 13 flows, 63 data-models, 92 api-endpoints)
```

88 + 13 + 63 + 92 = **256**. The 152 `acceptance` nodes — the entire output of
wave 2 — are in the total but absent from the breakdown, so the one command the
method designates as *the gate* renders the wave that just ran as invisible. A
reader checking whether wave 2 landed would find the parenthetical unchanged from
wave 1's.

> **Method lesson.** The species breakdown should enumerate every species present
> (`decision` will hit this too in wave 3), or at least carry an "other" bucket
> so the numbers reconcile. Trivial to fix, and it undermines confidence in the
> gate precisely when the gate matters most.

---

## Wave 3 — story

8 era units + `w3-decisions` + `w3-status-arcs`, dispatched concurrently.

### Finding 13 — the skill's edge table is stale, and it silently disables the `decision` species

`w3-decisions` was told (correctly, per its brief) to verify edge kinds against
`.claude/skills/arkaik/references/schema.md` before writing any. That table
lists exactly five kinds — `composes`, `calls`, `displays`, `queries`,
`covers` — and closes with:

> Any other source → target combination for a given edge type is invalid.

Read literally, a `decision` node may connect to nothing at all, which would make
the species inert. But three lines above the table, the same file declares:

```ts
type EdgeTypeId = "composes" | "calls" | "displays" | "queries" | "covers"
                | "supersedes" | "generates" | "impacts";
```

The authoritative source — `packages/schema/src/ids.ts`'s `VALID_EDGE_SEMANTICS`,
which is what `arkaik validate` actually enforces — has all three:

```ts
supersedes: [["decision", "decision"]]
generates:  [["decision", "acceptance"]]
impacts:    [["decision", "flow"], ["decision", "view"],
             ["decision", "data-model"], ["decision", "api-endpoint"]]
```

So the skill's reference table is stale relative to both its own type union and
the validator. An agent obeying the doc would have produced a decisions wave with
zero edges and reported it as a finding — the doc would have caused the defect it
appeared to describe.

> **Method lesson.** The edge table is duplicated prose that has already drifted
> from `VALID_EDGE_SEMANTICS`. It should be generated from the schema package, or
> replaced by a pointer to it. Note the shape of the near-miss: the agent would
> have been *correct relative to its instructions* and the run would have looked
> clean.

### Finding 14 — two platform vocabularies, one field name

The map's platform axis is `PlatformId = "web" | "ios" | "android"`. The repo's
Lab Notes use a different, overlapping vocabulary for their own `platform:` key —
`all | webapp | ios | android | project | infra`.

Wave 3 reads Lab Notes as free copy and writes `deliverable.shipped` events that
also carry a `platform` field. Two of those six tokens (`ios`, `android`) are
identical across the vocabularies; the rest are not, and `webapp` vs `web` is a
trap that looks like a typo rather than a category error. The reviewer's own era
briefs propagated the confusion by pointing agents at the Lab Note vocabulary.

Both units that hit it caught it independently and used `PlatformId`, one citing
the schema line number and noting that merge does not validate the Lab Note
schema — so a wrong token here would have landed silently.

> **Method lesson.** Where wave 3 tells agents to lift copy from Lab Notes, it
> should say explicitly that the note's `platform:` value is **not** the event's
> `platform` value, and that the latter is the map's three-token axis. Silent
> acceptance makes this a data-quality bug rather than a merge failure.

### Finding 15 — `has_lab_note` is a substring match, and the method tells agents to trust it blindly

`SKILL.md` states wave 3's cheapest and most load-bearing rule:

> **A PR with a Lab Note is user-visible by definition** — and the note is a
> benefit-first title and summary already written for you. Use it.

`w3-android-arrival` discovered that `#553`'s Lab Note section reads, in full:

> *"Not user-facing. This is infrastructure / automation for the maintainer's
> development loop. Delete this section."*

`arkaik bootstrap corpus` sets `has_lab_note` by substring-matching the
`## Lab Note` heading, so **the flag fires on an explicit refusal to write one**.
Audited across the corpus:

```
PRs flagged has_lab_note:                       45
… whose section is a refusal, skip or deferral:  6   (13%)
```

Three distinct false-positive classes, all real:

- **Explicit refusal** — #553 ("Not user-facing… Delete this section"), #534
  ("Skipped — sub-project B ships no user-visible screen").
- **Deferred / milestone-level note** — #536, #550, #596: a real note exists but
  is explicitly held for a later combined publication, so treating it as *this*
  PR's shipped copy double-counts the milestone.
- **The heading appearing in prose** — #613 is the PR that *builds the Lab Note
  pipeline*, so it discusses the heading rather than carrying one.

An agent following the instruction literally would have shipped the Play-Store CI
automation and a debug-only token preview as user-facing deliverables. The unit
caught all four refusals/skips by reading the section body instead of trusting
the flag, and articulated the right rule: **a note saying "this is not
user-facing" is the gate being applied, not a Lab Note.**

> **Method lesson.** Two fixes, and both are cheap. `corpus` should set
> `has_lab_note` only when the section contains a parseable note (a ```yaml fence,
> or at minimum an EN title/summary pair) rather than when the heading string
> appears. And `SKILL.md` should soften "user-visible by definition" to "read the
> section — a refusal or a deferral is not a note." As written, the one signal the
> method tells agents to trust *without* judgment is the one that needs judgment.

### Two rules wave 3 invented that the method should adopt

The skill's user-visible filter is three bullets: Lab Note = in, chores/CI/refactors
= out, "judge the rest." Across 324 PRs that third bullet is where nearly all the
work is, and two units independently produced sharper formulations of it.

**1. The symptom test** (`w3-cross-surface-alignment`):

> A fix is a deliverable when **a user could have described the symptom**; a
> refactor is not, however large the diff.

It is operational in a way "could a user notice?" is not, because it forces you
to write the symptom sentence. Its worked examples: "photos re-fetched on every
scroll pass" (#417) and "the record form opened on a spinner" (#418) are things a
user would report; a colour-token pass is not. The same unit used it to exclude
#419 — a repeat-call guard where "the numbers rendered identically before and
after" — which is exactly the case a naive read of "it's a fix" gets wrong.

**2. Maintainer signal first** (`w3-store-readiness`):

> A Lab Note means in. An explicit `no-lab-note` label means the maintainer
> already ruled it not user-facing, and I don't overturn it. Only PRs carrying
> **neither** signal get judged by me.

This is strictly better than judging every PR blind, because this repo *has* a
maintainer ruling recorded on many PRs and the method never tells agents to look
for it. It also produced the run's most careful single judgment: #624 carries no
note, but it merged **13 minutes before** #627 adopted the Lab Note requirement —
so the missing note is a *date*, not a ruling, and the PR was judged on merits
(it closed an anon-key read of 182 pebbles across 20 users) rather than excluded
on a technicality.

The two rules compose: use the maintainer's signal where it exists, apply the
symptom test where it does not.

> **Method lesson.** Both belong in `SKILL.md`'s "What counts as user-visible"
> section. Note also that they are in tension with Finding 15 — "a Lab Note means
> in" is only safe once `has_lab_note` stops firing on refusals.

### Finding 16 — `w3-status-arcs` is asked to enforce a rule it cannot see

The unit's rule is precise: every anatomy node gets an arc **ending at its
snapshot status**. Its contract is equally precise: *never read the bundle*, use
`arkaik bootstrap index`. But the index is four columns:

```
id    species    title    product
```

**`status` is not among them.** So the one unit whose entire job is reconciling
history against each node's current status is structurally unable to read that
status. It is the only unit in the method with this problem — waves 1 and 2 write
statuses, wave 3's era units don't need them.

The agent worked around it by reconstructing status from three indirect sources:
the journal sidecar (legal to read — it is not the bundle), the wave-1/wave-2
*fragments* (read, never edited) for the `status`/`platformStatuses` each unit
declared, and `created_ts` → PR resolution to date each node's birth. That
recovered most of the map, and it reported the shortfall honestly: **74 nodes
were unreadable** — connections surfaces, analytics views and RPCs, wallet and
glyph-economy endpoints, the never-built cairn screens — because no fragment
declares a status for them and the journal carries no status event. It flagged
them rather than guessing.

> **Method lesson.** Add `status` (and ideally `platforms`) to
> `arkaik bootstrap index`. It is ~6KB today precisely so agents can afford it;
> two more columns is a rounding error against a 426KB bundle, and it turns
> `w3-status-arcs` from an inference exercise into a lookup. Without it the unit
> either guesses or under-delivers, and under-delivering is only the better
> outcome because this agent chose it.

### Finding 17 — retiring an already-archived node leaves no trace in the journal

`w1-path-and-capture` retired four prototype screens (`V-emotion-pearl`,
`V-cards-thoughts`, `V-pebble-revelation`, `V-record-celebration`) that PR #161
deleted. Merge applied the `retired_reason` — but emitted **no event**, because
those nodes were *already* `archived` in the map and merge only emits
`node.status_changed` on an actual change. 16 of the 22 retirements did fire; these
four did not.

Result: four nodes whose entire recorded history is a single `node.created`, no
trace of the death the retire op documented, and a `retired_reason` no timeline
shows. `w3-status-arcs` caught it and wrote the missing transitions at #161's
merge instant, grounding `from: live` in wave 1's own retire reasons plus #161's
file list.

> **Method lesson.** A `retire` op that no-ops on status should still record
> *something* — the reason is new information even when the status is not. The
> silent case is the dangerous one: the map gains a documented cause of death that
> no timeline reflects, and only an unusually thorough later unit will notice.

### Wave 3 status arcs — the restraint is the result

```
408 nodes examined
396 left alone (born at their current status)
 10 given an arc, via 12 events, 8 platform-scoped
```

The skill's hardest instruction in this unit is negative: *never invent a
transition that did not happen; a born-live node's arc is its `node.created`
alone.* The tempting failure was available and large — 141 uniformly-live
acceptances that really did ship platform-by-platform over months. Writing their
per-platform arrivals would have meant ~350 events, each needing a `from` the
node never held and a guessed PR. The unit named that as "the fabricated
staircase at scale" and declined it, confining itself to the 14 acceptances whose
per-platform truth genuinely diverges — of which only 3 had a platform arriving
later than birth.

It also declined the one case the brief had *pointed it at*:
`AC-karma-flash-ceramic-sound` was born iOS-live, so nothing ever transitioned,
and the honest arc is silence. A unit that took the brief as a hint to produce an
event would have manufactured one.

### Wave 3 gate

```
Merged 30 fragments: +40 nodes, +149 edges, +282 events
Nodes: 448    Edges: 971    Journal: 854
Result: VALID   (warning-clean)
```

Reviewer checks the CLI does not run, all clean: 242 wave-3 events, **0** carrying
`id`/`actor`, **0** duplicate `deliverable_id`, **0** deliverables whose `ts` is
not the real merge instant of its PR, **0** platform tokens outside the map's
axis (after the reconciliation in Finding 14), **0** decision nodes without a
`metadata.source` naming file and entry.

| Check (waves.md) | Result |
|---|---|
| Lab-Note PRs became deliverables; chores/CI/refactors did not | pass — with Finding 15's correction applied |
| Deliverable and release timestamps are real merge instants | pass — 217/217 verified against the corpus |
| Every decision traces to a real document | pass — 40/40 carry `metadata.source` |
| Arcs end at snapshot status, no invented transitions, distinct instants | pass — 396 of 408 nodes deliberately untouched |
| Nothing project-shaped deleted or rewritten | pass — story added only |

**217 deliverables from 324 PRs (67%).** Per era: web-prototype 60,
platform-foundation 7, ios-arrival 36, back-office-and-insight 17,
cross-surface-alignment 35, karma-economy 20, android-arrival 21,
store-readiness 21. Eight `release.tagged`, one per era — seven using the era
slug, one (`M36`) using a real GitHub milestone whose title names the era.

---

## The run, end to end

| | before | after |
|---|---|---|
| Nodes | 173 | **448** |
| Edges | 337 | **971** |
| Journal events | 253 | **854** |

**Species:** 88 view, 13 flow, 63 data-model, 92 api-endpoint, **152 acceptance**,
**40 decision**.

**Edges:** 241 `covers`, 218 `queries`, 144 `composes`, 117 `calls`, 102
`displays`, 102 `impacts`, 43 `generates`, 4 `supersedes`.

**Journal:** 449 `node.created`, 217 `deliverable.shipped`, 104
`node.status_changed`, 65 `edge.added`, 8 `release.tagged`, 7 `node.updated`, 2
`decision.status_changed`, 1 `idea.proposed`, 1 `node.deleted`.

Every wave gated: adversarial review → `merge` → `validate` **warning-clean**.
No wave was rejected; wave 1 was blocked once by the merge gate (27 illegal
edges) and re-merged after repair.

---

## Landing on the hosted project

The three most serious findings of the run are here, and all three were invisible
until `restore --dry-run` ran.

### Finding 18 — the local validator passes bundles the server rejects

The first dry-run failed outright:

```
The server's validator rejected the bundle — nothing was written.
  Invalid input: expected array, received undefined
  Invalid input: expected array, received undefined
```

`arkaik validate` had just reported **VALID, warning-clean**. The cause:
`F-connect`'s playlist junction used `{label, view_id}` for its cases, where the
schema's `JunctionCase` requires `{label, entries: [...]}`. Two cases, two errors.

**The malformed junction predated this run entirely** — it is byte-identical in
the pre-wave-1 bundle, and the hosted project was carrying it too. So the repo's
designated gate had been green for months over data the server would refuse.

Two things make this worse than a simple validator gap. The server's message
names neither the field, nor the node, nor the path — locating it needed a
hand-written recursive walk of every flow's playlist. And the method's whole
promise is that `validate` warning-clean is what a wave must meet; if that check
is weaker than the one at the landing step, every wave gate in the run was
measured against the wrong bar.

> **Method lesson.** `validate` must enforce `JunctionCase` shape, and the server's
> rejections need a field path. Until both, "validate is warning-clean" cannot be
> read as "this will land."

### Finding 19 — the hosted project had drifted ahead of the committed cache, and a naive restore would have destroyed the difference

`docs/arkaik/bundle.json` is described as "the local cache." It was **stale**, and
the dry-run delta said so in a way that is easy to skim past:

```
nodes  173 -> 448  (+276 -1 ~172)
edges  337 -> 971  (+641 -7 ~330)
```

**`-1` node and `-7` edges in a method whose first principle is "bootstrap never
deletes."** Fetching the hosted export (`GET /api/graph/projects/<id>/export`)
explained it: on **2026-07-31** someone had deleted `DM-bounce` in the arkaik.app
UI and created `DM-bounces` in its place, rewiring 7 edges — fixing exactly the
kebab-drift that `w1-data-platform` independently flagged as *"no rename op
exists, flagged for a human."* The human had already done it. The repo never
learned.

Restoring as-built would have resurrected the node the maintainer deleted, dropped
its replacement and its 7 edges, and thrown away three hosted-only events with no
local equivalent — including a `request.filed` recording real beta feedback
("Android beta testers ask to record pebbles without a connection") and the
GitHub/Figma/Notion `refs` on `V-landing`.

Resolved by making the local bundle adopt the maintainer's decision rather than
overwrite it: renamed the node, repointed 8 edges and 3 journal references, ported
`V-landing.metadata.refs`, and appended the three hosted-only events. Final delta:

```
nodes  173 -> 448  (+275 -0 ~173)
edges  337 -> 971  (+634 -0 ~337)
```

**Additive only.** And a check worth keeping: all 173 shared nodes ended
**byte-identical in `description`** — which is only true because wave 1 was
instructed never to patch `description`. That single restraint is what made the
restore safe.

> **Method lesson.** A hosted brownfield run must **diff against the hosted
> export before restoring**, not against the committed cache — they are different
> documents and the hosted one is authoritative. `restore --dry-run`'s `-N`
> figures are the alarm, and the method should say plainly: *any* negative in a
> never-deletes run means stop and fetch the export. The existing history-loss
> guard does not cover this — it compares event **counts**, and 854 > 290 sailed
> through while a real deletion hid inside the node delta.

### Restore

```
Backed up to docs/arkaik/.backups/2026-08-06T00-20-19-342Z-bundle.json
Restored. New version 8.
  nodes  173 -> 448  (+275 -0 ~173)
  edges  337 -> 971  (+634 -0 ~337)
  events 290 -> 857  (+854 -287 ~0)
```

No `412` occurred, so no re-run was needed.

---

## Landed

- **PR #696** — `chore(facility): bootstrap the arkaik map from the repo's own history`, labels `docs, chore, facility, no-lab-note`, milestone `M50 · Public profiles`. Resolves #690 and #683; leaves #691 open with this run as evidence.
- **Hosted** `prj_5dDiZc-G6lseF3cb` at version 8. Backup at `docs/arkaik/.backups/2026-08-06T00-20-19-342Z-bundle.json` (gitignored, local only).
- **`arkaik init --remove-bootstrap`** run; the one-time skill is gone and the maintenance `arkaik` skill remains.

## The findings, ranked

Nineteen findings against the method. The ones worth acting on first:

**Correctness — these let a bad run look clean:**

1. **Finding 18** — `arkaik validate` passes bundles the hosted validator rejects (`JunctionCase` shape). Every wave gate in this run was measured against a weaker bar than the landing step.
2. **Finding 19** — a hosted brownfield run must diff against the **hosted export**, not the committed cache. `restore --dry-run` showed `-1` node / `-7` edges; the history-loss guard compares event *counts* and never fires on it.
3. **Finding 13** — the skill's edge table is stale and, read literally, forbids every `decision` edge. An agent obeying the doc produces a decisions wave with zero edges and reports it as a finding.
4. **Finding 15** — `has_lab_note` is a substring match that fires on refusals ("Not user-facing… Delete this section"), and the skill says to trust it without judgment. 6 of 45 flagged PRs are false positives.

**Coverage — these silently starve a wave:**

5. **Finding 1** — a repo that moved its own directory layout needs the *historical* paths in the profile; the wave-0 checklist passes against today's tree while the slice starves. Check *corpus* coverage instead.
6. **Finding 4** — `corpus` never inventories dot-directories, so `.well-known/` route handlers are invisible to every slice in every run.
7. **Finding 16** — `bootstrap index` omits `status`, so `w3-status-arcs` cannot see the very field its rule is written against.

**Contract and coordination:**

8. **Finding 8** — the id-convergence model catches same-id/different-title loudly, but is blind to different-id/**same-concept**, which lands two nodes for one screen silently.
9. **Finding 9** — merge detects structural conflicts, not semantic ones: two units held opposite verdicts (retire vs retitle) on one node and both would have applied.
10. **Finding 3** — `SKILL.md` tells every unit to write `manifest.json`, under a driver the method itself recommends that runs 10 units concurrently.
11. **Finding 7** — the churn guard is per-unit; ten units each under 20% rewrote 58% of the map with no stop firing.
12. **Finding 5** — a reconcile can shrink a flow's playlist but can never remove the orphaned `composes` edges: `edges` is add-only and `validate` checks one direction.
13. **Finding 17** — a `retire` on an already-archived node emits no event, so the map gains a documented cause of death no timeline shows.

**Papercuts:** Finding 2 (era `to` is inclusive-of-day, contradicting the walkthrough), Finding 6 (the skill's own `slice.json` example collides across concurrent units), Finding 10 (no `data-model → data-model` edge, so DB lineage is inexpressible), Finding 12 (`validate`'s species breakdown omits `acceptance` and `decision` — 192 of 448 nodes uncounted), Finding 14 (two platform vocabularies sharing one field name, unvalidated).

**And one that is not the method's fault:** Finding 11's corollary — **node birth ≠ promise birth**. A view keeps the instant the surface first existed; an acceptance takes the instant the promise first held. Nothing in the skill says which to use, and a brownfield run hits it whenever a surface shipped before the behavior it now guarantees.

## What the method got right

Worth recording, because the findings above are all failures:

- **The slice/fragment split held.** Ten units, ~1.3MB of corpus, no unit ever read the bundle, and merge resolved every cross-fragment edge without any unit coordinating with another.
- **Byte-identical convergence worked.** `DM-achievements`, `DM-achievement-unlocks` and five achievement admin RPCs were declared independently by two units each and merged as silent no-ops.
- **The wave gate caught what unit self-validation could not.** All 27 illegal `DM-→DM-` edges passed each unit's own checks and failed at merge, because the rule broken was a *schema* rule, not a fragment-shape one.
- **Telling workers the wave-level stake shaped the work.** The values gate never had to fire: 27 of 30 elements used, top element 19.6%, `simplifies` once in 153 acceptances against a documented ~90% failure mode.
- **"Never invent a transition"** survived contact with a large temptation: 396 of 408 nodes were left alone rather than given a plausible staircase.
