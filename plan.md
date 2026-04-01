# Plan: Issue #68 — Celebration screen: karma increment & bounce level

## Context

The celebration screen (`RecordCelebration.tsx`) currently shows only "Pebble #N!" after saving. Issue #68 enhances it to display:
1. **Karma earned** (`+N karma`) with a float-up animation
2. **Current bounce level** displayed subtly below
3. **Level-up indicator** if the bounce level increased after saving

### Dependencies
- **#59** (celebration screen) — completed, merged via PR #62
- **#66** (karma increments on pebble enrichment) — completed via PR #77, but `computeKarmaDelta` does **not exist yet** in the codebase. Currently `createPebble` hardcodes `delta: 1`. The issue description says to use `computeKarmaDelta` — we need to create it.

### Current state
- `createPebble` in `local-provider.ts:226` always awards `delta: 1` karma and calls `refreshBounce()` after saving.
- `RecordCelebration` receives only `pebbleId` as a prop.
- `useKarma()` and `useBounce()` hooks exist but are not used in the celebration screen.
- Framer Motion and haptics patterns are established in the codebase.

---

## Implementation steps

### Step 1: Create `computeKarmaDelta` utility

**File:** `lib/data/karma.ts` (new file)

Create a pure function that calculates karma earned from a pebble based on enrichment depth, per issue #66's spec:
- Base: +1 (pebble created)
- +1 if description is non-empty (pearl)
- +1 per card with non-empty `value`
- +1 if at least one soul attached
- +1 if at least one domain attached

```typescript
import type { CreatePebbleInput } from "@/lib/data/data-provider"

export function computeKarmaDelta(pebble: CreatePebbleInput): number {
  let delta = 1 // base: pebble_created

  if (pebble.description?.trim()) delta += 1                    // pearl
  delta += pebble.cards.filter((c) => c.value.trim()).length     // cards
  if (pebble.soul_ids.length > 0) delta += 1                    // soul
  if (pebble.domain_ids.length > 0) delta += 1                  // domain

  return delta
}
```

Max karma per pebble: 1 (base) + 1 (pearl) + 4 (cards) + 1 (soul) + 1 (domain) = **8** (assuming 4 card types max).

### Step 2: Wire `computeKarmaDelta` into `createPebble`

**File:** `lib/data/local-provider.ts`

Replace the hardcoded `delta: 1` with the computed delta:

```typescript
import { computeKarmaDelta } from "@/lib/data/karma"

// In createPebble():
const karmaDelta = computeKarmaDelta(input)
const karmaEvent: KarmaEvent = {
  delta: karmaDelta,
  reason: "pebble_created",
  ref_id: pebble.id,
  created_at: now,
}
// ...
karma: this.store.karma + karmaDelta,
```

### Step 3: Capture pre-save bounce & karma, pass to celebration

**File:** `lib/hooks/useRecordForm.ts`

Before calling `addPebble`, snapshot the current bounce level and karma so we can compute deltas:

- Add `useBounce()` and `useKarma()` hooks
- Capture `bounceBefore` and `karmaBefore` before save
- Change `onSaveSuccess` callback signature to pass `{ pebbleId, karmaDelta, bounceBefore, bounceAfter }`

**File:** `components/record/RecordStepper.tsx`

- Update `savedPebbleId` state to store the full celebration data object
- Pass `karmaDelta`, `bounceBefore`, `bounceAfter` as props to `RecordCelebration`

### Step 4: Enhance `RecordCelebration` component

**File:** `components/record/RecordCelebration.tsx`

#### New props:
```typescript
interface RecordCelebrationProps {
  pebbleId: string
  karmaDelta: number
  bounceBefore: number
  bounceAfter: number
}
```

#### UI additions:

1. **Karma display** — `+N karma` text that floats upward with spring easing:
   - Appears below "Pebble #N!" heading
   - Animated with `motion.p` using `initial={{ opacity: 0, y: 10 }}` → `animate={{ opacity: 1, y: 0 }}`
   - Staggered delay (0.3s after main heading)

2. **Bounce level** — subtle display below karma:
   - Shows "Bounce level N" in `text-muted-foreground`
   - Small, understated text
   - `motion.p` with staggered delay (0.5s)

3. **Level-up indicator** — conditional, only when `bounceAfter > bounceBefore`:
   - Shows a celebratory message like "Level up!" 
   - Animated with a slightly bouncier spring
   - Staggered delay (0.7s)
   - Uses `font-semibold` or similar visual emphasis

All animations respect `prefersReducedMotion`. Copy follows the "would I say this to a friend?" rule — no generic motivational phrases.

### Step 5: Build & lint verification

- Run `npm run build` and `npm run lint` to confirm everything passes.

---

## Files changed

| File | Change |
|------|--------|
| `lib/data/karma.ts` | **New** — `computeKarmaDelta` pure function |
| `lib/data/local-provider.ts` | Wire computed karma delta into `createPebble` |
| `lib/hooks/useRecordForm.ts` | Snapshot bounce/karma before save, pass deltas to callback |
| `components/record/RecordStepper.tsx` | Pass celebration data to `RecordCelebration` |
| `components/record/RecordCelebration.tsx` | Display karma delta, bounce level, level-up indicator |

## Risks & considerations

- **`computeKarmaDelta` must be pure** — it will be ported to PostgreSQL later (per #66). No side effects, no provider access.
- **Bounce snapshot timing** — we read bounce before `addPebble` and after, since `createPebble` calls `refreshBounce()` internally. We need to read the updated store after save completes.
- **Copy tone** — all text must pass the authenticity test. No "Great job!" or "Keep it up!" — use matter-of-fact language.
