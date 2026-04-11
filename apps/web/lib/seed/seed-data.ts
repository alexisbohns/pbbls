import type { Pebble, Soul, Collection } from "@/lib/types"
import { EMOTIONS } from "@/lib/config/emotions"
import { DOMAINS } from "@/lib/config/domains"
import { CARD_TYPES } from "@/lib/config/card-types"

// ---------------------------------------------------------------------------
// Seed types — timestamps are injected at hydration time,
// so they are intentionally omitted from these constants.
// ---------------------------------------------------------------------------

type SeedSoul = Omit<Soul, "created_at" | "updated_at">
type SeedPebble = Omit<Pebble, "created_at" | "updated_at">
type SeedCollection = Omit<Collection, "created_at" | "updated_at">

// ---------------------------------------------------------------------------
// Approved seed dataset (issue #10)
// ---------------------------------------------------------------------------

export const SEED_SOULS: SeedSoul[] = [
  { id: "soul-mia",     name: "Mia" },
  { id: "soul-lucas",   name: "Lucas" },
  { id: "soul-papa",    name: "Papa" },
  { id: "soul-luna",    name: "Luna" },
  { id: "soul-dr-roux", name: "Dr. Roux" },
]

export const SEED_COLLECTIONS: SeedCollection[] = [
  {
    id: "col-gratitudes",
    name: "Morning gratitudes",
    mode: "track",
    pebble_ids: ["pbl-sunrise", "pbl-luna-purr", "pbl-bread"],
  },
  {
    id: "col-career",
    name: "Career milestones",
    mode: "stack",
    pebble_ids: ["pbl-promo", "pbl-launch"],
  },
  {
    id: "col-march",
    name: "March 2026",
    mode: "pack",
    pebble_ids: [
      "pbl-sunrise", "pbl-argument", "pbl-luna-purr",
      "pbl-promo", "pbl-anxiety", "pbl-bread",
      "pbl-hike", "pbl-launch", "pbl-nostalgia", "pbl-dr-session",
    ],
  },
]

export const SEED_PEBBLES: SeedPebble[] = [
  {
    id: "pbl-sunrise",
    name: "Sunrise from the balcony",
    description: "Woke up early, caught the pink sky over the rooftops. Coffee in hand, no phone.",
    happened_at: "2026-03-02T06:45:00+01:00",
    intensity: 1,
    positiveness: 1,
    visibility: "private",
    emotion_id: "serenity",
    soul_ids: [],
    domain_ids: ["zoe", "eudaimonia"],
    instants: [],
    cards: [
      { species_id: "feelings", value: "Calm and grounded. A rare quiet start." },
      { species_id: "free", value: "I should do this more often." },
    ],
  },
  {
    id: "pbl-argument",
    name: "Argument with Mia about money",
    description: "Tense conversation about rent increase. We both got frustrated.",
    happened_at: "2026-03-05T20:30:00+01:00",
    intensity: 2,
    positiveness: -1,
    visibility: "private",
    emotion_id: "anger",
    soul_ids: ["soul-mia"],
    domain_ids: ["philia", "asphaleia"],
    instants: [],
    cards: [
      { species_id: "feelings", value: "Frustrated and defensive. Then guilty for raising my voice." },
      { species_id: "thoughts", value: "She's right that we need a plan. I was reacting, not listening." },
      { species_id: "behaviour", value: "Apologized before bed. We agreed to revisit with numbers." },
    ],
  },
  {
    id: "pbl-luna-purr",
    name: "Luna fell asleep on my lap",
    description: "Working from home, Luna jumped up and fell asleep purring. Instant calm.",
    happened_at: "2026-03-07T14:20:00+01:00",
    intensity: 1,
    positiveness: 1,
    visibility: "private",
    emotion_id: "gratitude",
    soul_ids: ["soul-luna"],
    domain_ids: ["philia"],
    instants: [],
    cards: [
      { species_id: "free", value: "Small moments like this are everything." },
    ],
  },
  {
    id: "pbl-promo",
    name: "Got the senior title",
    description: "Manager confirmed the promotion. Effective April 1st.",
    happened_at: "2026-03-10T11:00:00+01:00",
    intensity: 3,
    positiveness: 1,
    visibility: "private",
    emotion_id: "pride",
    soul_ids: ["soul-lucas", "soul-papa"],
    domain_ids: ["time", "eudaimonia", "asphaleia"],
    instants: [],
    cards: [
      { species_id: "feelings", value: "Proud but also nervous. Now expectations are higher." },
      { species_id: "thoughts", value: "Two years of work led here. Lucas was the first I told." },
    ],
  },
  {
    id: "pbl-anxiety",
    name: "3 AM spiral",
    description: "Couldn't sleep. Brain looping on what-ifs about the new role.",
    happened_at: "2026-03-12T03:00:00+01:00",
    intensity: 2,
    positiveness: -1,
    visibility: "private",
    emotion_id: "anxiety",
    soul_ids: [],
    domain_ids: ["zoe", "eudaimonia"],
    instants: [],
    cards: [
      { species_id: "feelings", value: "Chest tight, mind racing. Classic anxiety spiral." },
      { species_id: "thoughts", value: "What if I'm not good enough? What if they find out?" },
      { species_id: "behaviour", value: "Got up, drank water, wrote down the worst-case scenario. Felt slightly better." },
    ],
  },
  {
    id: "pbl-bread",
    name: "First successful sourdough",
    description: "After three failed attempts, the crumb was finally open and airy.",
    happened_at: "2026-03-14T09:30:00+01:00",
    intensity: 1,
    positiveness: 1,
    visibility: "private",
    emotion_id: "joy",
    soul_ids: ["soul-mia"],
    domain_ids: ["eudaimonia"],
    instants: [],
    cards: [
      { species_id: "free", value: "Patience pays off. Mia said it's the best bread she's had." },
    ],
  },
  {
    id: "pbl-hike",
    name: "Calanques hike with Lucas",
    description: "Full day hike to En-Vau. Crystal water, sore legs, great conversations.",
    happened_at: "2026-03-16T08:00:00+01:00",
    intensity: 2,
    positiveness: 1,
    visibility: "private",
    emotion_id: "excitement",
    soul_ids: ["soul-lucas"],
    domain_ids: ["zoe", "philia", "eudaimonia"],
    instants: [],
    cards: [
      { species_id: "feelings", value: "Alive. Exhausted but deeply happy." },
      { species_id: "thoughts", value: "We talked about starting something together. Felt like old times." },
    ],
  },
  {
    id: "pbl-launch",
    name: "Shipped the new dashboard",
    description: "Months of work, finally live. 200 users on day one.",
    happened_at: "2026-03-19T17:00:00+01:00",
    intensity: 3,
    positiveness: 1,
    visibility: "private",
    emotion_id: "pride",
    soul_ids: [],
    domain_ids: ["time", "eudaimonia"],
    instants: [],
    cards: [
      { species_id: "feelings", value: "Relief more than joy. We cut so many corners." },
      { species_id: "behaviour", value: "Celebrated with the team. Wrote a retro doc the next morning." },
    ],
  },
  {
    id: "pbl-nostalgia",
    name: "Found a photo of Mamie's garden",
    description: "Scrolling old photos, found one from summer 2008. The roses, the stone wall.",
    happened_at: "2026-03-21T22:15:00+01:00",
    intensity: 2,
    positiveness: 1,
    visibility: "private",
    emotion_id: "nostalgia",
    soul_ids: ["soul-papa"],
    domain_ids: ["philia"],
    instants: [],
    cards: [
      { species_id: "feelings", value: "Warm and melancholic. Miss her but grateful for those summers." },
      { species_id: "free", value: "Sent the photo to Papa. He replied with a heart emoji. That's a lot from him." },
    ],
  },
  {
    id: "pbl-dr-session",
    name: "Breakthrough session with Dr. Roux",
    description: "Connected the anxiety spiral to the impostor pattern. First time it clicked.",
    happened_at: "2026-03-24T10:00:00+01:00",
    intensity: 2,
    positiveness: 1,
    visibility: "private",
    emotion_id: "awe",
    soul_ids: ["soul-dr-roux"],
    domain_ids: ["zoe", "eudaimonia"],
    instants: [],
    cards: [
      { species_id: "thoughts", value: "The anxiety isn't about competence — it's about belonging. I've felt this since school." },
      { species_id: "behaviour", value: "Homework: notice when the pattern activates and name it. 'There's the visitor again.'" },
    ],
  },
]

// ---------------------------------------------------------------------------
// Dev-mode seed integrity validation.
// Logs a warning if any ID references in the seed data don't match config.
// Fail-soft: logs warnings only, never throws — keeps the app alive.
// ---------------------------------------------------------------------------

if (process.env.NODE_ENV === "development") {
  const emotionIds = new Set(EMOTIONS.map((e) => e.id))
  const domainIds = new Set(DOMAINS.map((d) => d.id))
  const cardTypeIds = new Set(CARD_TYPES.map((ct) => ct.id))

  for (const pebble of SEED_PEBBLES) {
    if (!emotionIds.has(pebble.emotion_id)) {
      console.warn(`[seed] Invalid emotion_id "${pebble.emotion_id}" on pebble ${pebble.id}`)
    }
    for (const domainId of pebble.domain_ids) {
      if (!domainIds.has(domainId)) {
        console.warn(`[seed] Invalid domain_id "${domainId}" on pebble ${pebble.id}`)
      }
    }
    for (const card of pebble.cards) {
      if (!cardTypeIds.has(card.species_id)) {
        console.warn(`[seed] Invalid species_id "${card.species_id}" on pebble ${pebble.id}`)
      }
    }
  }
}
