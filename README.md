# pbbls

> Pebbles — collect meaningful moments, one pebble at a time.

A Turborepo monorepo for **Pebbles**, an app where you record life moments as "pebbles" — small, tangible memory records enriched with emotions, people (souls), life domains, and reflective cards.

## Monorepo structure

```
pbbls/
  apps/
    web/         ← Next.js PWA (the main application)
    ios/         ← iOS app (placeholder)
  packages/
    shared/      ← Shared types & utilities (placeholder)
    supabase/    ← Supabase client & types (placeholder)
  turbo.json     ← Turborepo task configuration
  package.json   ← Workspace root
```

## Quick start

```
npm install
npm run dev
```

Open https://localhost:3000. On first launch, seed data is loaded automatically.

## Commands

| Command | Description |
|---------|-------------|
| `npm run dev` | Start all dev servers via Turborepo |
| `npm run build` | Build all packages via Turborepo |
| `npm run lint` | Lint all packages via Turborepo |

## Web app (`apps/web/`)

The web app is a local-first MVP built with Next.js (App Router), React, Tailwind CSS, and shadcn/ui. All data lives in `localStorage` — there is no backend yet. The DataProvider interface is designed for a future Supabase migration.

See [`apps/web/`](apps/web/) for the full web app documentation.

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
    data/                 → DataProvider interface, LocalProvider, hooks
    hooks/                → Reusable hooks (useRecordForm, useStepNavigation)
    utils/                → Utilities (formatters, group-pebbles-by-date)
    seed/                 → Seed data with dev-mode validation
```

### Data flow

```
localStorage (JSON)
↕
LocalProvider (implements DataProvider)
↕
React hooks (usePebbles, useSouls, useCollections)
↕
Page components → UI
```

### Tech stack

| Layer       | Choice |
|-------------|--------|
| Framework   | Next.js (App Router) |
| UI          | React + Tailwind CSS + shadcn/ui |
| Storage     | localStorage (Supabase planned) |
| Theming     | next-themes |
| Monorepo    | Turborepo + npm workspaces |

## Deployment

The web app deploys to **Vercel**. After the monorepo migration, the Vercel project's **Root Directory** must be set to `apps/web` in the dashboard. This makes Vercel:

1. Install dependencies at the repo root (respecting npm workspaces)
2. Build from within `apps/web/`
3. Serve output from `apps/web/.next`

Preview URLs remain unchanged.

## License

Private — not open source yet.
