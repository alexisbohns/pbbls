# pbbls

> Pebbles — collect meaningful moments, one pebble at a time.

A local-first MVP for **Pebbles**, an app where you record life moments as "pebbles" — small, tangible memory records enriched with emotions, people (souls), life domains, and reflective cards.

This is a concept-validation prototype. All data lives in `localStorage`. There is no backend, no auth, no server — just a fast iteration loop to refine the experience and data model before migrating to Supabase.

## Quick start

```
npm install
npm run dev
```

Open http://localhost:3000. On first launch, seed data is loaded automatically.

## Concepts

| Concept     | Description |
|-------------|-------------|
| **Pebble**  | A moment you record. Has a time, intensity (1–3), positiveness (-2 to +2), an emotion, related souls, life domains, and reflective cards. |
| **Emotion** | A feeling attached to a pebble (joy, sadness, anger, etc.). One per pebble in V1. |
| **Soul**    | A person, pet, or entity related to a pebble. Not a user — a private contact in your world. |
| **Domain**  | A life dimension based on Maslow: Zoē (health), Asphaleia (security), Philía (relationships), Timē (recognition), Eudaimonia (self-actualization). |
| **Card**    | A reflective note attached to a pebble, optionally framed by type: free, feelings, thoughts, behaviour. |
| **Collection** | A group of pebbles with an optional mode: Stack (goal), Pack (time-bound), Track (recurring frequency). |

## Architecture

to-do

## Data flow

```
localStorage (JSON)
↕
LocalProvider (implements DataProvider)
↕
React hooks (usePebbles, useSouls, useCollections)
↕
Page components → UI
```

The DataProvider interface is designed for a future Supabase migration — swap the provider, keep hooks and UI unchanged.

## Tech stack

| Layer       | Choice |
|-------------|--------|
| Framework   | Next.js (App Router) |
| UI          | React + Tailwind CSS + shadcn/ui |
| Storage     | localStorage (Supabase planned) |
| Theming     | next-themes |

## Seed data

The app ships with 10 realistic pebbles, 5 souls, and 3 collections. Seed data loads on first launch when localStorage is empty. You can reset by clearing localStorage.

## What this MVP validates

1. Can a user record a pebble in under 60 seconds?
2. Does the emotion picker feel intuitive?
3. Is importance × positiveness understandable without explanation?
4. Do framed cards (feelings/thoughts/behaviour) produce richer entries than freeform?
5. Does the data model support timeline browsing and collection grouping?
6. Is the schema ready for Supabase migration?

## License

Private — not open source yet.