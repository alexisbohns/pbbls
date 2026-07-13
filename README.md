# pbbls

> Pebbles — collect meaningful moments, one pebble at a time.

A Turborepo monorepo for **Pebbles**, an app where you record life moments as "pebbles" — small, tangible memory records enriched with emotions, people (souls), life domains, and reflective cards.

## Monorepo structure

```
pbbls/
  apps/
    web/         ← Next.js PWA (the main application)
    ios/         ← Native iOS app (SwiftUI, iOS 17+)
    android/     ← Native Android app (Kotlin + Jetpack Compose, minSdk 33)
    admin/       ← Next.js back-office (analytics, Lab logs, glyph moderation)
  packages/
    shared/      ← Shared types & utilities (placeholder)
    supabase/    ← Supabase types, migrations & edge functions
    rive/        ← Shared .riv animation assets (consumed by copy per surface)
  turbo.json     ← Turborepo task configuration
  package.json   ← Workspace root
```

## Quick start

```
npm install
npm run dev
```

Open http://localhost:3000.

## Commands

| Command | Description |
|---------|-------------|
| `npm run dev` | Start all dev servers via Turborepo |
| `npm run build` | Build all packages via Turborepo |
| `npm run lint` | Lint all packages via Turborepo |

## Web app (`apps/web/`)

The web app is a PWA built with Next.js (App Router), React, Tailwind CSS, and shadcn/ui. Data lives in Supabase (Postgres + RLS); business logic that spans tables lives in Postgres RPCs shared by all client surfaces. The `DataProvider` interface abstracts the data layer — `SupabaseProvider` is its sole implementation.

See [`apps/web/`](apps/web/) for the full web app documentation. The native apps (`apps/ios`, `apps/android`) are independent codebases that mirror each other's architecture and share only the database contract — see the 2026-07-10 entry in `docs/decisions/log.md`.

### Concepts

| Concept     | Description |
|-------------|-------------|
| **Pebble**  | A moment you record. Has a time, intensity (1–3), positiveness (-2 to +2), an emotion, related souls, life domains, and reflective cards. |
| **Emotion** | A feeling attached to a pebble (joy, sadness, anger, etc.). One per pebble in V1. |
| **Soul**    | A person, pet, or entity related to a pebble. Not a user — a private contact in your world. |
| **Domain**  | A life dimension based on Maslow: Zoe (health), Asphaleia (security), Philia (relationships), Time (recognition), Eudaimonia (self-actualization). |
| **Card**    | A reflective note attached to a pebble, optionally framed by type: free, feelings, thoughts, behaviour. |
| **Collection** | A group of pebbles with an optional mode: Stack (goal), Pack (time-bound), Track (recurring frequency). |

### Architecture

```
apps/web/
  app/                    → Routes (thin page shells)
    layout.tsx            → Root layout: DataProvider + ThemeProvider + nav
    path/                 → Timeline view
    record/               → Multi-step pebble creation
    pebble/[id]/          → Pebble detail
    collections/          → Collection list + detail

  components/
    layout/               → Sidebar, BottomNav, EmptyState, providers
    record/               → RecordStepper, TimePicker, EmotionPicker, SoulPicker, etc.
    pebble/               → PebbleDetail, PebbleIndicators
    path/                 → PebbleTimeline, PebbleCard, PathEmptyState
    collections/          → CollectionList, CollectionCard, ModeBadge, etc.
    ui/                   → shadcn/ui primitives (Button, Badge, Input, Card)

  lib/
    types.ts              → Domain entity types (Pebble, Soul, Collection)
    config/               → Static configs (emotions, domains, card types, navigation)
    data/                 → DataProvider interface, SupabaseProvider, hooks
    hooks/                → Reusable hooks (useRecordForm, useStepNavigation)
    utils/                → Utilities (formatters, group-pebbles-by-date)
    seed/                 → Seed data with dev-mode validation
```

### Data flow

```
in-memory Store (React context)
↕
SupabaseProvider (PostgREST reads + RPCs for multi-table writes)
↕
React hooks (usePebbles, etc.)
↕
Page components → UI
```

### Tech stack

| Layer       | Choice |
|-------------|--------|
| Framework   | Next.js (App Router) |
| UI          | React + Tailwind CSS + shadcn/ui |
| Storage     | Supabase (Postgres + RLS + RPCs) |
| Theming     | next-themes |
| Monorepo    | Turborepo + npm workspaces |

## Deployment

### Web (`apps/web/`)

The web app deploys to **Vercel**. After the monorepo migration, the Vercel project's **Root Directory** must be set to `apps/web` in the dashboard. This makes Vercel:

1. Install dependencies at the repo root (respecting npm workspaces)
2. Build from within `apps/web/`
3. Serve output from `apps/web/.next`

Preview URLs remain unchanged.

### Android (`apps/android/`)

The native Android app ships to **Google Play internal testing** from CI: merges to `main` touching `apps/android/**` build a signed release AAB and publish it via `.github/workflows/android-release.yml`, and a PR labelled `deploy-beta` deploys that branch on demand. The pipeline and its one-time console setup are documented in [`docs/android-play-deploy.md`](docs/android-play-deploy.md).

## License

Private — not open source yet.

## Engineering Paradigm

**Thesis:** specification-driven, agentic execution. Match ceremony to blast radius. Architecture lives as code-adjacent artifacts, not folklore.

### 1. Conception
- GitHub issue: `[Type] Description` + species label (`feat`/`fix`/...) + scope label (`core`/`ui`/`db`/`api`/`auth`/`facility`/`android`) + milestone.
- Living product graph (`docs/arkaik/bundle.json`) — screens, flows, models, APIs as nodes/edges. Updated whenever architecture moves.

### 2. Spec — `docs/superpowers/specs/<date>-<slug>-design.md`
Pre-flight checklist. Flattens ambiguity *before* code.
- Problem, key decisions + rationale, architecture per layer
- File-by-file create/modify/delete with pseudocode/SQL
- Data flow, error modes, **out-of-scope**, manual acceptance, PR metadata

### 3. Plan — `docs/superpowers/plans/<date>-<slug>.md`
Operationalizes the spec. Checkboxable, copy-pasteable.
- 8–15 numbered tasks: files touched, exact bash/SQL/Xcode steps, expected output
- Spec-drift section, self-review checklist (placeholders, type consistency, spec coverage)
- Post-ship: "Lessons learned" + PR link annotated back in

### 4. Execution — triaged by size
| Size | Ceremony |
|---|---|
| Small (≤150 LOC) | Skip plan/agents; workspace-scoped lint |
| Medium (≤500 LOC) | 2–3 sentence sketch; workspace lint + build |
| Large (cross-app / schema / new surface) | Full Superpowers loop; root lint + build; update Arkaik |

**Safety rails (non-negotiable):**
- Strict TS, no `any`, no type assertions
- Multi-table Supabase writes → RPC in migration (atomicity); single-row → direct client
- Never `await` Supabase inside `onAuthStateChange` (deadlock)
- Migration → regen types → commit `database.ts`
- Topical guides loaded on demand: `docs/agents/ui-and-styling.md`, `docs/agents/data-and-async.md`

### 5. Review
- Branch `type/issue-N-slug` created *before* first commit
- Conventional commits, one logical change each: `feat(ui): ...`
- PR body: `Resolves #N` + key files + notes; inherit issue labels/milestone
- Reviewed against spec acceptance criteria + plan self-review

### 6. QA
- Lint/build scoped to change size (per triage)
- Manual acceptance checklist from the spec
- Arkaik diff = architectural regression signal

### 7. Release
- Conventional commits feed the changelog
- Milestones group shipped work; "Lessons learned" feeds back into the next spec template

---

### Operating principles (the 5 things to say out loud)
1. **Spec before code, plan before keys.** Ambiguity gets resolved on paper.
2. **Ceremony scales with blast radius.** Small change = small process.
3. **Architecture is a graph, not a vibe.** Arkaik bundle is the source of truth.
4. **Atomicity is a primitive.** RPCs over client-stitching. Strict types over runtime guards.
5. **Every ship teaches the next one.** Post-ship lessons annotate the plan, not a separate retro.

