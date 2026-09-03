# Vercel as an undeclared processor: EU region pinning + processor-inventory correction — design

**Date:** 2026-09-03
**Finding:** Kritik `F-2026-08-GDP-admin-01` (criterion GDP-06, surface `admin`, severity **high / P0**, cost S, impact 3 × likelihood 4)
**Milestone:** M55 · Compliance batch A (proposed — see "Proposed issues")
**Status:** design only. No config, policy or code changed by this document.

> **This spec proposes published legal text. Every wording block below is marked
> DRAFT and requires maintainer sign-off — and, for the Chapter V paragraphs,
> legal review — before it is merged.** The author is an engineering agent, not
> counsel. Nothing here is a legal opinion; every claim is either grounded in a
> file in this repo or explicitly marked as an open question for the maintainer.

## Context

Kritik's 2026-08 audit raised three P0s. Two are being specced elsewhere; this
one stands alone. Its claim: **the platform that serves both Next.js apps is
absent from the privacy policy's Art. 28 sub-processor inventory, and the repo
contains no configuration that would keep its function runtime inside the EU.**

The precedent for how a Kritik P0 lands here is `F-2026-08-SEC-supabase-01`
(#739/#740, decision log 2026-09-02): one finding, one focused change, one
decision-log entry. This one differs in a way that shapes the whole design —
half of it is **published legal text on a user-facing route**, which is not
revertible in the sense config is.

## 1. Verification at HEAD (`main`, clean, 2026-09-03)

The audit ran against a snapshot with paths prefixed `/home/user/pbbls/`. Every
citation was re-read at the real root. **Verdict: the finding is fully valid.
Nothing has drifted. Four citations are sharpened and three material facts the
audit did not have are added below.**

### 1.1 Citation-by-citation

| Audit citation | Verdict at HEAD | Note |
|---|---|---|
| Root `CLAUDE.md` — "Web and admin deploy to Vercel" | **Holds** | `CLAUDE.md:19` verbatim: "Web and admin deploy to Vercel (root directory set per app)". |
| `apps/web/docs/privacy/en.md:159-176` — inventory lists Supabase + Google only | **Holds, exactly** | `159` = `## 6. Sub-Processors and Partners`; `163` = `### 6.1 Supabase Inc.`; `170` = `### 6.2 Google (Gemma LLM)`; `176` = the last bullet of 6.2. Line numbers are correct to the line. |
| FR equivalent "sec. 6" (verifier, unpinned) | **Holds — now pinned** | `apps/web/docs/privacy/fr.md:141-158`. Same two entries, same order. |
| `find` for `vercel.json` returns only `apps/web/vercel.json`, sole content the `$schema` key | **Holds** | Exact file content is two lines plus braces: `{ "$schema": "https://openapi.vercel.sh/vercel.json" }`. `find . -name vercel.json -not -path '*/node_modules/*'` returns that one path. |
| `apps/admin` has no `vercel.json` | **Holds** | Directory listing confirms: no `vercel.json`, no `.vercelignore`. |
| `apps/admin/next.config.ts` is empty | **Holds** | `const nextConfig: NextConfig = {}`. |
| No `preferredRegion` / `regions` / `runtime` export in `apps/admin` | **Holds — and is now known to be true of `apps/web` too** | A repo-wide grep over `apps/web` and `apps/admin` for `preferredRegion`, `export const runtime`, `export const regions` returns **zero** hits. The verifier only checked admin; web is equally unpinned. |
| `admin_list_glyph_submissions` returns `su.email` / `ou.email` at `20260701102810_glyph_marketplace_curation.sql:189-191` | **Holds, exactly** | `189: su.email as submitter_email`, `191: ou.email as owner_email`, joined `left join auth.users su/ou`. |
| Moderation page is an async Server Component reaching that RPC through the cookie-bound server client | **Holds** | `apps/admin/app/(authed)/pebblestore/glyphs/page.tsx` is `export default async function` → `listSubmissions()` (`apps/admin/lib/pebblestore/fetchers.ts:4`) → `createServerSupabaseClient()` (`apps/admin/lib/supabase/server.ts`, `cookies()` from `next/headers`). |
| Policy claims "No transfer outside the EU is involved in primary storage" (7.1) and "No transfer to external CDNs" (7.3) | **Holds** | `en.md:183` and `en.md:193`; `fr.md:163` and `fr.md:173`. |
| Policy still cites the invalidated Privacy Shield | **Holds — location corrected** | It is in **§6.2**, not §7.2: `en.md:174` "Google Cloud (United States with Privacy Shield / Data Privacy Framework)", `fr.md:156` same. §7.2 already says DPF only. The fix therefore lands in the §6.2 block, not the §7.2 block. |

### 1.2 Three material facts the audit did not have

**(a) The *mentions légales* have the same gap, and it is a different legal
duty.** `apps/web/docs/legal-notice/en.md` and `fr.md` name a single
**"Hosting Provider" / "Hébergeur": Supabase Inc.**, Singapore, "Servers
located in: Paris, France (EU region)". The audit never opened this file
("vercel appears nowhere in `apps/web/docs`" — correct, and that is precisely
the defect). Under French **LCEN art. 6-III** the mentions légales must
identify the host of the *site*; the site is served by Vercel, and Supabase
hosts the *data*. This is a third published document with the same omission,
it sits in the same blast radius, and it belongs in the same change.

**(b) `apps/web` is not the client-side-only PWA the finding assumed.** The
finding analysed admin only and left web implicit. Web has four server-side
entry points that read personal data in the Vercel function runtime. Full
analysis in §2.

**(c) Google Gemma is not wired to anything.** A case-insensitive grep for
`gemma` across the whole repo returns **17 hits, all in `apps/web/docs/`**
(privacy ×7, terms ×2, credits ×2 per locale) — and **zero** in `apps/web/lib`,
`apps/*/`, `packages/supabase/supabase/functions/` (the four edge functions are
`compose-pebble`, `compose-pebble-update`, `backfill-pebble-render`,
`delete-account` — all deterministic SVG composition and account purge), or any
migration. There is no API key, no model call, no feature flag. The inventory
entry describes a processor that has never processed anything.

### 1.3 What is *not* wrong, and should not be "fixed"

- **The self-hosted-fonts claim is true.** `apps/web/app/layout.tsx` imports
  `@fontsource-variable/ysabeau`, `@fontsource/reenie-beanie`,
  `@fontsource-variable/caveat` as npm packages, bundled at build. No
  `fonts.googleapis.com` request exists. §7.3's *first* sentence is accurate;
  only its second sentence ("No transfer to external CDNs") is not.
- **Vercel Image Optimization is not a user-media path.** Six web components
  import `next/image`, but every render of *user* content is a plain `<img>`
  with an eslint-disable and a reason: `PebbleDetail.tsx:226` ("base64 data
  URL"), `PathPebbleRow.tsx:111` ("signed Storage URL"),
  `AnnouncementRow.tsx:24` ("public Supabase storage URL"). `next.config.ts`
  has no `images.remotePatterns`, which is consistent. No user photo is
  proxied or re-encoded by Vercel. Nothing to declare here.
- **The build is not a personal-data path.** The build environment has no
  Supabase credentials — `apps/web/app/invite/[token]/page.tsx` is
  `export const dynamic = "force-dynamic"` with the comment *"the build
  environment has no Supabase env vars, so a build-time prerender attempt
  would throw"*. So Vercel's build region, wherever it is, never sees user
  rows. This removes one thing the policy would otherwise have to say.
- **No Vercel Analytics package.** Neither `apps/web/package.json`,
  `apps/admin/package.json` nor the root manifest depends on `@vercel/analytics`
  or `@vercel/speed-insights`. §13.3's "No analytics tools" is true *of the
  repo*. Whether it is true of the dashboard is an open question (§7).

## 2. What personal data actually transits Vercel

The finding's data-flow claim is about admin. Getting web right changes what
the policy has to say, so both are enumerated.

### 2.1 `apps/web` — mostly a browser-side PWA, with four server doors

There is **no `middleware.ts`** and **no `"use server"` file anywhere** in
`apps/web` (grep over `app/`, `lib/`, `components/` returns nothing). The
authed app — `/path`, `/record`, `/profile`, `/settings`, `/wallet`,
`/achievements` — is client components talking to Supabase from the browser;
`app/page.tsx`, `app/wallet/page.tsx` and `app/achievements/page.tsx` are
one-line shells around a client component. So the audit's implicit model
("every page render passes personal data through Vercel") is **false for the
bulk of web**.

But `createServerSupabaseClient` (`apps/web/lib/supabase/server.ts`, `cookies()`
from `next/headers`) has exactly four consumers, and all four are personal-data
paths:

| Entry point | Runs in Vercel function | Personal data processed there |
|---|---|---|
| `app/auth/callback/route.ts` | Yes, every sign-in | **The highest-sensitivity path in the repo.** Receives the OAuth/email-confirm `code`, calls `exchangeCodeForSession`, then `auth.getUser()` (id, **email**, `user_metadata.full_name`), reads `profiles.onboarding_completed`, may `insert` a `profiles` row, and **writes the Supabase session cookies**. Session-establishing credential material transits the function runtime. |
| `app/u/[handle]/page.tsx` | Yes, per request | `get_public_profile(p_handle)` → `display_name`, `handle`, glyph, 28-day assiduity grid and pebble counts. Rendered server-side *and* embedded in OG/Twitter `generateMetadata`. Cookie-bound client, so the visitor's own session cookie is read on the way in. |
| `app/p/[id]/page.tsx` | Yes, per request | `get_shared_pebble(p_pebble_id)` → `name`, `description`, `happened_at`, `intensity`, `positiveness`, `emotion` (`20260817130000_pebble_visibility_grades.sql:106-127`). This is a record of a named person's emotional state — the policy itself qualifies emotion data as sensitive (§4.1). Rendered server-side. |
| `app/invite/[token]/page.tsx` | Yes, `force-dynamic` | `preview_connection_invite(p_token)` → the inviter's identity, shown to an anonymous visitor. |

`app/docs/[slug]/page.tsx` uses `generateStaticParams`, so **the privacy policy
itself is a statically generated page served from Vercel's edge cache** — a
detail with some irony, and the reason §7.3's CDN sentence cannot be rescued by
a function-region setting.

### 2.2 `apps/admin` — server-rendered end to end

Every route under `app/(authed)/` is an async Server Component behind
`AuthedLayout` → `requireAdmin()` (`apps/admin/lib/supabase/admin-guard.ts`),
which calls `auth.getUser()` and returns `{ id, email }` — **the operator's
email is then rendered into `<TopBar email={admin.email} />` on every single
admin page render.** On top of that:

- `/pebblestore/glyphs` → `admin_list_glyph_submissions` → `submitter_email` +
  `owner_email` from `auth.users` for **end users who are not the operator**
  (migration lines 189/191). This is the sharpest instance: third-party
  personal data, materialised in a Vercel function's memory, per page view.
- `/analytics` → thirteen `is_admin`-gated definer RPCs
  (`apps/admin/lib/analytics/fetchers.ts`): `get_kpi_daily`,
  `get_active_users_series`, `get_pebble_volume_series`,
  `get_pebble_enrichment`, `get_user_averages_series`,
  `get_bounce_distribution_today`, `get_emotion_share`, `get_domain_share`,
  `get_quality_signals_today`, `get_retention_cohorts`. **Correction to the
  audit:** these are *aggregates*, not per-user rows —
  `v_analytics_emotion_share_weekly` groups to `(bucket_week, emotion)` with a
  `share_pct` and carries no user id (`20260501000003_analytics_meaning_share.sql:28-40`).
  The audit called this "aggregate special-category analytics", which is fair
  as a description but should not be over-claimed in the policy: population-level
  emotion distributions are not, on their own, personal data. **The
  policy wording proposed in §5 therefore does not claim they are.**

### 2.3 The layer no repo config can move

Independently of rendering mode, **Vercel terminates TLS and routes every
request** to `pbbls.app` and the admin subdomain at the anycast edge PoP
nearest the visitor. Every request — including requests for purely static
assets and for the privacy policy page itself — therefore causes Vercel to
process the visitor's **IP address, requested URL and `sb-*` session cookie**
at a location determined by network topology, not by any repo setting. That is
processing within the meaning of Art. 4(2), by a processor the notice does not
name. **This is the part of the finding that region pinning does not fix**, and
it is why the split in §3 puts config *below* text rather than instead of it.

## 3. Part decision: a two-part stack, config below text

**Decision: two parts, stacked, config first.** Considered and rejected: a
single PR (the total diff is ~2 small JSON files plus ~60 lines of Markdown,
and root `CLAUDE.md`'s task-size triage would put that in "small").

Why the split wins anyway — measured against the four binding rules in
`CLAUDE.md` § *Shipping larger work — parts, tasks, and stacked PRs*:

- **Different reviewer audiences, which the convention names explicitly.**
  Part 1 is reviewed by whoever owns deploys: does `regions` belong in
  `vercel.json`, is `cdg1` right, does the build still pass. Part 2 is reviewed
  by the maintainer as data controller, and plausibly by counsel: is this
  sentence *true*, and does it say enough. Handing one reviewer both asks them
  to context-switch between "is this JSON key correct" and "does this paragraph
  correctly characterise a Chapter V transfer".
- **Asymmetric irreversibility.** A wrong `regions` value is a redeploy. A
  wrong sentence in a published privacy policy is itself a compliance defect
  (Art. 12(1) — transparent, intelligible; Art. 5(1)(a) — fairness and
  transparency), it renders under a bumped `last_updated` on `/docs/privacy`,
  and §15 of the policy commits the controller to *notifying users of material
  changes*. Publishing it and retracting it is worse than the status quo.
- **Different blocking dependencies.** Part 1 blocks on nothing. Part 2 blocks
  on facts only the maintainer holds (§7): the actual dashboard region, the DPA
  reference, and whether legal review is required. If they were one PR, the
  strictly-beneficial config change would sit behind a legal review it does not
  need — pure loss.
- **A real, one-directional dependency, so the order is forced, not chosen.**
  Part 2's proposed §7.1 and §7.4 both assert *"server-side rendering runs in
  Vercel's Paris region"*. That sentence must not be published before the
  config that makes it true. Config is therefore **strictly lower**. This is
  "order by dependency, never by convenience" applied literally.
- **Each part stands alone without referring forward.** Part 1: *"both Next.js
  apps deploy to Vercel with no region configuration; pin their functions to
  the EU."* Part 2: *"the privacy policy and the mentions légales omit the
  platform that serves the app, and two sections make claims that are not
  true."* Neither description needs the other.
- **One stack, one story:** *the platform that serves Pebbles is now in the EU,
  and the policy says so.*

They also differ in PR ceremony, which is a useful smell-test that the seam is
real: Part 1 is infra → **no Lab Note**, add the `no-lab-note` label. Part 2
touches the user-visible Arkaik view nodes `V-docs-privacy` and
`V-docs-legal-notice` (`docs/arkaik/bundle.json`) → **Lab Note required** by
the gate in `CLAUDE.md`. Neither part changes a screen, route, data model or
endpoint, so **no Arkaik bundle edit** is needed in either.

**Fallback, and it is legitimate:** if the maintainer already has the answers in
§7 to hand and wants no legal review, collapsing this to one PR is cheap and
defensible. The stack is the recommendation, not a requirement.

### 3.1 Part 1 — Pin both Vercel projects' functions to an EU region

**Branch:** `chore/<n>-vercel-eu-region-pinning`

| Task | File |
|---|---|
| 1.1 Add the `regions` key to the existing schema-only file | `apps/web/vercel.json` (modify) |
| 1.2 Create the admin equivalent | `apps/admin/vercel.json` (new) |
| 1.3 Document the dashboard-side confirmation step under the existing "Deployment (Vercel)" heading | `apps/admin/README.md` (append one numbered step); root `README.md` § *Web* (append one line) |

**Independent verification (runs with no part above it):**

```bash
npm run build --workspace=apps/web
npm run build --workspace=apps/admin
npm run lint  --workspace=apps/web
npm run lint  --workspace=apps/admin
# vercel.json is not typechecked by Next; validate it as JSON explicitly:
node -e "JSON.parse(require('fs').readFileSync('apps/web/vercel.json','utf8'))"
node -e "JSON.parse(require('fs').readFileSync('apps/admin/vercel.json','utf8'))"
```

Post-deploy, **outside the repo** (this is the only proof that matters and the
repo cannot produce it): open each project's latest deployment → **Functions**
in the Vercel dashboard and confirm the region reads `cdg1 / Paris`.

**Why it stands alone:** it is a strict improvement whether or not the policy is
ever touched. Reverting it is one commit and one redeploy.

> **Deconfliction:** task 1.1 edits **`apps/web/vercel.json`**. A sibling agent
> is speccing a `web.yml` CI workflow and may also touch `apps/web` config. That
> spec should touch `.github/workflows/` and, if anything, `apps/web/package.json`
> — **not** `apps/web/vercel.json`. If both land, `apps/web/vercel.json` is
> this stack's file; rebase rather than merge-resolve.

### 3.2 Part 2 — Name Vercel in the processor inventory and correct the transfer claims

**Branch:** `fix/<n>-privacy-processor-inventory` (stacked on Part 1)

| Task | File |
|---|---|
| 2.1 Add §6.3 Vercel Inc. to the Art. 28 inventory (both locales) | `apps/web/docs/privacy/en.md`, `fr.md` |
| 2.2 Mark §6.2 Google (Gemma) prospective; drop the Privacy Shield reference | same two files |
| 2.3 Rewrite §7.1 to separate storage from delivery | same two files |
| 2.4 Narrow §7.3 to what is actually true of fonts | same two files |
| 2.5 Add §7.4 Hosting and Content Delivery (the edge/Chapter V paragraph) | same two files |
| 2.6 Split the *Hébergeur* block into application host + data host | `apps/web/docs/legal-notice/en.md`, `fr.md` |
| 2.7 Bump `version` → `1.1.0` and `last_updated` → `2026-09-03` in the frontmatter of all four files | same four files |
| 2.8 Lab Note (EN/FR) in the PR body | PR body only |

**Independent verification:**

```bash
npm run build --workspace=apps/web   # generateStaticParams reads every docs/*/{en,fr}.md
                                     # through gray-matter at build; malformed
                                     # frontmatter fails the build. This IS the
                                     # typecheck for legal text.
npm run lint  --workspace=apps/web
npm run test  --workspace=apps/web
npm run dev   --workspace=apps/web   # then read /docs/privacy and /docs/legal-notice
                                     # in BOTH locales and confirm the rendered
                                     # "last updated — v1.1.0" line
                                     # (components/docs/DocsContent.tsx:18-22)
```

**Why it stands alone:** a reviewer reading only this PR is asked one question —
*is this text true and complete?* — and can answer it from the diff.

## 4. Config design

### 4.1 The exact files

`apps/web/vercel.json` — **modified** (the `$schema` key stays first, so the
diff is a one-line addition):

```json
{
  "$schema": "https://openapi.vercel.sh/vercel.json",
  "regions": ["cdg1"]
}
```

`apps/admin/vercel.json` — **new**, byte-identical:

```json
{
  "$schema": "https://openapi.vercel.sh/vercel.json",
  "regions": ["cdg1"]
}
```

Note both projects set **Root Directory** in the dashboard (`apps/web`,
`apps/admin` — root `README.md:112`, `apps/admin/README.md:27`), so each app's
`vercel.json` is the one Vercel reads for that project. No root-level
`vercel.json` is added; adding one would apply to neither project.

### 4.2 `cdg1` (Paris) over `fra1` (Frankfurt)

Both are EU regions, so **Chapter V is not engaged by the function region
either way**. The choice is latency and narrative coherence, not legality:

1. **Co-location with the database.** The Supabase project's servers are in
   Paris (privacy §6.1, legal notice "Hosting Provider"). Admin is *entirely*
   server-rendered and web's four server doors each make at least one RPC, so
   every one of those renders is a function→Postgres round trip. `cdg1` puts
   them in the same metro; `fra1` adds a Frankfurt↔Paris hop to each.
2. **Single-jurisdiction narrative.** The controller is established in France,
   the supervisory authority is the CNIL (policy §9.9), the data is in Paris.
   Naming Paris in both documents keeps the story one country deep and makes
   the mentions légales trivially checkable.
3. `fra1` is the more commonly chosen EU region and is a perfectly good
   fallback if `cdg1` capacity or plan tier ever becomes a problem. It buys
   nothing here.

### 4.3 What region pinning does **not** achieve — read before writing §7.4

The `regions` key pins where **Vercel Functions** (the Node runtime executing
Server Components, route handlers and server actions) run. It does **not**
cover four things, and the policy must not imply otherwise:

1. **The edge / network layer.** TLS termination, routing, WAF and static-asset
   serving happen at the anycast PoP nearest the visitor, which may be outside
   the EU (a user travelling, or an unlucky route). IP, URL and session cookie
   are processed there on **every** request. Not repo-configurable.
2. **The CDN.** The statically generated docs pages — the privacy policy
   included — and all `_next/static` assets are cached and served from that same
   global network. **This is why §7.3's "No transfer to external CDNs" stays
   false no matter what `regions` says**, and why the text fix is not optional.
3. **Build infrastructure.** Builds run in Vercel's build region, configured
   separately from `regions`. Here that is harmless — §1.3(c) establishes the
   build sees no user data — but it is a different setting and should not be
   conflated with it.
4. **Runtime logs.** `console.error` / `console.warn` in
   `apps/web/app/auth/callback/route.ts` (five call sites, on the session
   path), `apps/web/lib/supabase/server.ts:31`, `apps/admin/lib/supabase/admin-guard.ts`
   and `apps/admin/lib/*/fetchers.ts` land in Vercel's log platform. The
   messages themselves carry Supabase `error.message` strings rather than user
   rows, but log *entries* carry request metadata. Retention and location are
   dashboard/plan properties, not repo properties.

Additionally, `regions` in `vercel.json` **can be overridden in the dashboard**,
and function-region choice is plan-gated (single region on Hobby). The repo can
express the intent; it cannot prove the outcome.

**Residual gap → evidence the maintainer must supply from outside the repo:**

| Gap | Evidence needed |
|---|---|
| Is `cdg1` actually in effect for both projects? | Deployment → Functions region, screenshotted per project, after Part 1 deploys |
| Is there a signed/accepted DPA with Vercel? | Vercel's DPA is incorporated by its Terms; record the version + acceptance date for the Art. 30 record |
| Chapter V mechanism for the residual edge/log processing | SCC module + Vercel's DPF certification status, both dated at the time of writing |
| Log retention and storage location | Vercel plan documentation for the account's tier |

## 5. Policy text design — DRAFT, requires human/legal review

**Formatting contract.** These are published, formatting-sensitive documents.
Every block below:

- **preserves existing heading numbering** — nothing is renumbered. Vercel is
  appended as **§6.3** (not inserted as §6.2) and hosting as **§7.4**,
  specifically so that the `#61-supabase-inc`, `#62-google-gemma-llm`,
  `#71-…`, `#72-…`, `#73-…` anchors survive untouched. Order within an Art. 28
  inventory carries no legal weight; broken anchors in four documents do.
- **preserves the list style verbatim**, including the space-before-colon
  (`- **Role :**`) that the EN file inherits from French typography. Do not
  "correct" it.
- **preserves the `---` rules** between top-level sections in `en.md` (note:
  `fr.md` does **not** use them — match each file to itself, not to the other).
- uses **`vous`**, not `tu`. The Lab Note convention's informal "Tu" applies to
  Lab Notes only; these are legal documents and are consistently formal.

**In scope / out of scope.** The roadmap (`2026-07-28-store-launch-roadmap.md`
§M56) already schedules a full bilingual policy rewrite that drops the
fictional sections (Therapist, Decisions, Cairns) and adds marketplace, karma,
connections, public profiles and drafts. **That rewrite is gated behind feature
freeze; a P0 misleading-notice defect is not.** This change is therefore a
*surgical correction* of the specific untrue sentences, and it deliberately
leaves the fictional sections, the dead relative links in §16
(`./legal-notices.md`), and the `Serveurs en Paris` typo in `fr.md:148` alone.
M56 will re-touch §6 and §7; that is expected and fine.

---

### 5.1 §6.2 — mark Gemma prospective, drop Privacy Shield

Rationale for *demoting rather than deleting*: Gemma is described in **four**
document pairs (privacy §2.4/§6.2/§7.2/§12, terms, credits ×2 locales).
Deleting it from §6.2 alone desyncs them. One honest sentence costs two lines
and keeps all four consistent; the full removal is an M56 job.

**EN — `apps/web/docs/privacy/en.md`, §6.2 (lines 170-176)**

```diff
 ### 6.2 Google (Gemma LLM)

 - **Role :** Anonymized language processing to enhance your events (if you enable AI features).
-- **Location :** Google Cloud (United States with Privacy Shield / Data Privacy Framework).
+- **Status :** Not yet activated. AI features are not available in the current version of Pebbles, and no data has been transmitted to Google to date. This entry describes a planned processing operation.
+- **Location :** Google Cloud (United States).
 - **Data Transmitted :** Only anonymized event data (without personal identifiers such as your name, email, or soul names).
 - **Commitment :** Google does not retain your data long-term.
```

**FR — `apps/web/docs/privacy/fr.md`, §6.2 (lines 152-158)**

```diff
 ### 6.2 Google (Gemma LLM)

 - **Fonction :** Traitement de langage anonymisé pour enrichir vos événements (si vous activez les fonctionnalités IA).
-- **Localisation :** Google Cloud (États-Unis avec Privacy Shield / Data Privacy Framework).
+- **Statut :** Non activé à ce jour. Les fonctionnalités d'IA ne sont pas disponibles dans la version actuelle de Pebbles et aucune donnée n'a été transmise à Google. Cette entrée décrit un traitement envisagé.
+- **Localisation :** Google Cloud (États-Unis).
 - **Données transmises :** Seules les données d'événements anonymisées (sans identifiants personnels comme votre nom, email ou noms de souls).
 - **Engagement :** Google ne conserve pas vos données à long terme.
```

*Privacy Shield was invalidated by the CJEU in Case C-311/18 (Schrems II),
16 July 2020, and citing it in a 2026 notice is a factual error independent of
this finding.* The DPF reference already present in §7.2 is left in place there.

---

### 5.2 §6.3 — the new Vercel inventory entry (appended after §6.2)

**EN — new block inserted between §6.2's last bullet and the `---` before §7**

```diff
 - **Commitment :** Google does not retain your data long-term.

+### 6.3 Vercel Inc.
+
+- **Role :** Hosting and delivery of the web application and of the internal back office.
+- **Location :** Server-side page rendering runs in Vercel's Paris region (France, European Union). Requests are first received by Vercel's worldwide delivery network, whose points of presence may be located outside the European Union.
+- **Processing :** Secure connection handling, request routing, delivery of the application's files, server-side rendering of the public profile, shared pebble and invitation pages, sign-in callback handling, and technical platform logs.
+- **Agreement :** Vercel is bound by a data processing agreement compliant with the GDPR. Any processing outside the European Union is covered by the Standard Contractual Clauses adopted by the European Commission and, where applicable, by Vercel's certification under the EU-U.S. Data Privacy Framework.
+
 ---
```

**FR — new block appended after §6.2's last bullet (no `---` in `fr.md`)**

```diff
 - **Engagement :** Google ne conserve pas vos données à long terme.

+### 6.3 Vercel Inc.
+
+- **Fonction :** Hébergement et diffusion de l'application web et du back-office interne.
+- **Localisation :** Le rendu des pages côté serveur s'exécute dans la région Paris de Vercel (France, Union Européenne). Les requêtes sont d'abord reçues par le réseau de diffusion mondial de Vercel, dont les points de présence peuvent se situer hors de l'Union Européenne.
+- **Traitement :** Sécurisation des connexions, acheminement des requêtes, diffusion des fichiers de l'application, rendu côté serveur des pages de profil public, de partage de pebble et d'invitation, traitement du retour de connexion, et journaux techniques de la plateforme.
+- **Contrat :** Vercel est lié par un contrat de traitement des données conforme au RGPD. Tout traitement réalisé hors de l'Union Européenne est couvert par les clauses contractuelles types adoptées par la Commission européenne et, le cas échéant, par la certification de Vercel au titre du cadre de protection des données UE-États-Unis.
+
```

Two deliberate FR choices, neither a literal translation of the EN: *"réseau de
diffusion mondial"* rather than the calque *"réseau edge"*, which means nothing
to a French reader; and *"clauses contractuelles types"*, the term of art used
by the CNIL and by Commission Implementing Decision (EU) 2021/914 — not
*"clauses types"* and never *"SCC"*.

---

### 5.3 §7.1 — separate storage from delivery

**EN (line 183)**

```diff
 ### 7.1 Intra-EU Transfers

-Supabase operates with servers in Paris (France, EU). No transfer outside the EU is involved in primary storage.
+Your data is stored by Supabase on servers in Paris (France, EU), and the pages Pebbles renders on the server run in Vercel's Paris region (France, EU). No transfer outside the EU is involved in the storage of your data. Delivery of the application relies on a worldwide network, described in section 7.4.
```

**FR (line 163)**

```diff
 ### 7.1 Transferts Intracommunautaires

-Supabase opère avec des serveurs en Paris (France, UE). Aucun transfert hors UE n'est impliqué pour le stockage principal.
+Vos données sont stockées par Supabase sur des serveurs situés à Paris (France, UE), et les pages que Pebbles génère côté serveur s'exécutent dans la région Paris de Vercel (France, UE). Aucun transfert hors UE n'est impliqué dans le stockage de vos données. La diffusion de l'application repose sur un réseau mondial, décrit à la section 7.4.
```

The load-bearing edit is `primary storage` → `the storage of your data`: it
keeps the (true) storage claim and stops it being read as a claim about the
whole service.

---

### 5.4 §7.3 — narrow the fonts claim to fonts

The heading stays **Fonts / Polices de Caractères** so the anchor survives; only
the false second sentence goes, replaced by a true and more specific one.

**EN (line 193)**

```diff
 ### 7.3 Fonts

-Our fonts are self-hosted in the EU. No transfer to external CDNs.
+Our fonts are bundled with the application and served from our own domain. No request is made to an external font provider such as Google Fonts.
```

**FR (line 173)**

```diff
 ### 7.3 Polices de Caractères

-Nos polices de caractères sont auto-hébergées en UE. Aucun transfert vers des CDN externes.
+Nos polices de caractères sont intégrées à l'application et servies depuis notre propre domaine. Aucune requête n'est adressée à un fournisseur de polices externe tel que Google Fonts.
```

---

### 5.5 §7.4 — new: Hosting and Content Delivery

This is the section that replaces the blanket "no external CDN" claim with what
actually happens, and it is the paragraph most in need of legal review.

**EN — appended after §7.3, before the `---` preceding §8**

```diff
 Our fonts are bundled with the application and served from our own domain. No request is made to an external font provider such as Google Fonts.

+### 7.4 Hosting and Content Delivery
+
+The web application and the internal back office are hosted by Vercel and delivered through its worldwide network. Every request you make is first received by the Vercel point of presence closest to you, which may be located outside the European Union. At that stage, Vercel processes technical connection data (your IP address, the address of the page requested, and your session cookie) in order to establish a secure connection, route the request, and deliver the application's files.
+
+Server-side rendering, meaning the operations that read your data in order to build a page, runs in Vercel's Paris region (France, European Union).
+
+Any processing carried out outside the European Union is covered by the data processing agreement concluded with Vercel and by the Standard Contractual Clauses adopted by the European Commission, supplemented where applicable by Vercel's certification under the EU-U.S. Data Privacy Framework.
+
 ---
```

**FR — appended after §7.3 (no `---` in `fr.md`)**

```diff
 Nos polices de caractères sont intégrées à l'application et servies depuis notre propre domaine. Aucune requête n'est adressée à un fournisseur de polices externe tel que Google Fonts.

+### 7.4 Hébergement et Diffusion
+
+L'application web et le back-office interne sont hébergés par Vercel et diffusés via son réseau mondial. Chaque requête que vous effectuez est d'abord reçue par le point de présence Vercel le plus proche de vous, lequel peut se situer hors de l'Union Européenne. À ce stade, Vercel traite des données techniques de connexion (votre adresse IP, l'adresse de la page demandée et votre cookie de session) afin d'établir une connexion sécurisée, d'acheminer la requête et de vous transmettre les fichiers de l'application.
+
+Le rendu côté serveur, c'est-à-dire les opérations qui lisent vos données pour construire une page, s'exécute dans la région Paris de Vercel (France, Union Européenne).
+
+Tout traitement réalisé hors de l'Union Européenne est couvert par le contrat de traitement des données conclu avec Vercel et par les clauses contractuelles types adoptées par la Commission européenne, complétés le cas échéant par la certification de Vercel au titre du cadre de protection des données UE-États-Unis.
+
```

---

### 5.6 Legal notice — split the host into application host and data host

The current block names one host for two different things. LCEN art. 6-III asks
who hosts the *service*; that is Vercel.

**EN — `apps/web/docs/legal-notice/en.md`**

```diff
 ## Hosting Provider

-Supabase Inc.
-970 Toa Payoh North #07-04
-Singapore 318992
-
-Servers located in: Paris, France (EU region)
+**Application hosting and delivery**
+
+Vercel Inc.
+[address]
+
+Server-side rendering executed in: Paris, France (EU region)
+
+**Data hosting (database, authentication, file storage)**
+
+Supabase Inc.
+970 Toa Payoh North #07-04
+Singapore 318992
+
+Servers located in: Paris, France (EU region)
```

**FR — `apps/web/docs/legal-notice/fr.md`**

```diff
 ## Hébergeur

-Supabase Inc.
-970 Toa Payoh North #07-04
-Singapore 318992
-
-Serveurs situés en : Paris, France (région EU)
+**Hébergement et diffusion de l'application**
+
+Vercel Inc.
+[adresse]
+
+Rendu côté serveur exécuté à : Paris, France (région EU)
+
+**Hébergement des données (base de données, authentification, stockage de fichiers)**
+
+Supabase Inc.
+970 Toa Payoh North #07-04
+Singapore 318992
+
+Serveurs situés en : Paris, France (région EU)
```

`[address]` / `[adresse]` is left as a **maintainer-filled placeholder**, matching
the file's existing convention for the publisher address. This spec deliberately
does not assert Vercel Inc.'s registered address from memory — it must be copied
from Vercel's own legal page at the time of writing.

---

### 5.7 Frontmatter

All four files carry `version: 1.0.0` / `last_updated: 2026-04-09`, and
`components/docs/DocsContent.tsx:18-22` renders *"{last_updated} — v{version}"*
on the page. A text change that leaves them untouched publishes a corrected
policy that claims to be the April version.

```diff
-version: 1.0.0
+version: 1.1.0
 effective_date: 2026-04-09
-last_updated: 2026-04-09
+last_updated: 2026-09-03
```

`effective_date` is left at `2026-04-09` on the reading that this is a
clarification of existing practice, not a new processing operation — **this is a
controller's call, not an engineer's, and is listed as an open question.**

## 6. Risks

| Risk | Mitigation |
|---|---|
| `cdg1` is not honoured (plan tier, capacity, dashboard override) and §7.1/§7.4 then assert something false | Part 1 must be **deployed and confirmed in the dashboard** before Part 2 merges. This is the stack's single hard gate. |
| Adding a region key changes cold-start/latency behaviour for non-EU users | Accepted and intended. The controller is French, the data is in Paris, the user base is EU-first. |
| Part 2's §7.4 under-states or over-states the Chapter V position | Marked DRAFT; legal review requested; the paragraph deliberately describes the *mechanism* (SCCs + DPF where applicable) rather than asserting adequacy. |
| M56's planned rewrite collides with these edits | Expected. M56 re-touches §6/§7 wholesale; that supersedes this surgical fix rather than conflicting with it. Noted here so the M56 author does not treat §6.3/§7.4 as accidental. |
| Anchor breakage across four documents and three native surfaces (iOS/Android open `https://www.pbbls.app/docs/privacy` in a web sheet) | No heading is renumbered or renamed; only appended. |
| Sibling agent's `web.yml` spec touching `apps/web` config | Called out explicitly in §3.1. |

## 7. Open questions — maintainer only

These are facts the repo cannot hold. **Part 2 must not merge until 1-4 are
answered; Part 1 needs none of them.**

1. **What region are the two Vercel projects on today?** The finding assumes
   the `iad1` default. If the dashboard was already set to an EU region, the
   *config* half of this finding is cosmetic (though still worth committing, so
   the guarantee is in git rather than in a dashboard), while the *text* half
   is untouched and remains a P0.
2. **Is there a DPA of record with Vercel, and what is its version/date?**
   Vercel's DPA is incorporated by its Terms of Service, but GDP-06 asks for a
   *documented* mechanism. The version and acceptance date should go into the
   Art. 30 record, and the §6.3 "Agreement" bullet should be checked against it.
3. **Is Vercel currently certified under the EU-U.S. Data Privacy Framework,
   as of the merge date?** §6.3 and §7.4 both say "where applicable" precisely
   because this must be re-checked at publication rather than asserted from
   training data. If the certification cannot be confirmed, drop the DPF clause
   from both and rely on the SCCs alone.
4. **Does publishing revised policy text require legal review here, and does
   §15 ("we will notify you of any material changes") fire?** Naming a
   previously undisclosed processor is arguably material. The controller
   decides; if it fires, a notification surface is needed, and M55 already
   plans "a re-consent surface for the rewritten policy".
5. **Is `effective_date` bumped, or does it stay 2026-04-09?** §5.7's proposal
   is "stays" (clarification, not new processing) — confirm.
6. **Is Vercel Web Analytics or Speed Insights enabled in either dashboard?**
   The repo has no `@vercel/analytics` / `@vercel/speed-insights` dependency,
   so §13.3 ("No analytics tools... No tracking pixels") is true of the code.
   If either is enabled dashboard-side, §13.3 becomes a *second* misleading
   claim and must be fixed in the same PR.
7. **Vercel Inc.'s registered address** for the mentions légales placeholder.
8. **Plan tier**, which determines whether multi-region functions are even
   available and what the log retention/location is.

Out of scope, noted only so they are not silently lost (per `CLAUDE.md`
"mention it in a comment — don't change it"): the fictional §5/§2.2 sections
(M56 owns them); the dead relative links in privacy §16 and legal-notice
"Related Documents" (`./legal-notices.md`, `./politique-de-confidentialite.md`
resolve to nothing under the `/docs/[slug]` router); and the
*"Serveurs en Paris"* → *"à Paris"* typo at `fr.md:148`.

## 8. Constraints from `docs/decisions/log.md`

Nothing in the ledger constrains the region choice or the policy wording — no
entry mentions hosting regions, CDNs, or the privacy documents. Three entries
shape the *shape* of the work:

- **2026-09-02 — "Privileged profile columns are pinned by trigger" (#739/#740)**
  is the sibling P0 from this same Kritik run. It sets the precedent: one
  finding → one focused change → one appended decision-log entry naming the
  finding id. This stack should follow it, appending **one** entry at the top
  of the stack (Part 2) covering both parts, since one entry describes one
  decision: *Vercel is a named processor and its functions are pinned to the EU*.
- **2026-07-30 — "Cross-user reads are definer-RPC projections" (#654)**
  explains *why* `/u/[handle]` and `/p/[id]` are server-rendered at all, and is
  the reason §2.1's four server doors exist. It is not violated by anything
  here; §6.3's "Processing" bullet describes exactly those projections.
- **2026-07-29 — "Offline is a non-goal on every surface" (#620)** confirms the
  service worker keeps Supabase requests `NetworkOnly` — so no personal data is
  cached at the Vercel edge by the app's own caching policy. Worth knowing
  before writing §7.4; it is the reason §7.4 speaks of *connection metadata* at
  the edge rather than cached responses.

## 9. Proposed issues

Two issues, one per part, both under the same milestone. Label values are taken
from root `CLAUDE.md` § *Issues & labels*: species ∈ {`feat`, `fix`, `bug`,
`chore`, `docs`, `test`, `quality`}; scope ∈ domain {`core`, `ui`, `db`, `api`,
`auth`, `facility`, `legal`} ∪ surface {`web`, `ios`, `android`, `supabase`}.

**Not created. No GitHub API call was made from this session.**

| # | Title | Species | Scope | Milestone |
|---|---|---|---|---|
| 1 | `[Chore] Pin Vercel functions to an EU region for web and admin` | `chore` | `facility`, `web` | M55 · Compliance batch A |
| 2 | `[Fix] Name Vercel in the privacy policy processor inventory and correct the transfer claims` | `fix` | `legal`, `web` | M55 · Compliance batch A |

Notes on the choices:

- **Species for #2 is `fix`, not `docs`.** These files are published product
  content on a user-facing route, rendered with a version stamp, and the change
  corrects statements that are untrue — not documentation of the codebase.
  `docs` would also mis-set the Lab Note expectation.
- **Surface labels:** the taxonomy has no `admin` surface label. Both issues
  carry `web` (the Vercel-hosted Next.js surface) and #1 carries `facility` for
  the deployment-infrastructure half. **Flag for the maintainer:** if an
  `admin` label exists in the repo but is undocumented in `CLAUDE.md`, add it
  to both.
- **Milestone: M55 (Compliance batch A)**, which the roadmap marks as running
  in parallel from M45 and *not* gated on feature freeze. **M56 is the wrong
  home** even though it owns "privacy-policy rewrite (EN/FR)": M56 is
  explicitly *"after feature freeze — the policy must describe M47-M54"*, and a
  P0 misleading-notice defect cannot wait for that. M56 will re-touch §6/§7 as
  planned.
- Per the PR checklist, both issues' labels and milestone should be **proposed
  to and confirmed by the maintainer** before the PRs are opened, and issue #2's
  PR carries the required Lab Note (`platform: webapp`, `species: feature`,
  because a user-visible correction to a legal page is a `feature` in the Lab
  Note taxonomy — a `fix` label on the PR does not change that).
