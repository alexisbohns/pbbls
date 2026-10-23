# The emotions gem — the method

One method. Reference code: `gem-method.js` (`window.GEM`, also a node module). `node test.js` checks the invariants; `node test.js --md` prints the tables in §8.

## 1 · Pebble worth

```
worth(p) = intensity(p) × charge(valence(p))      charge: bright 1 · heavy 1 · even ½
```

A pebble weighs its intensity; an even one weighs half. Bright and heavy count the same: the gem is a mirror of the month's emotional weight, not a diagnosis, and the hardness of a moment is already carried by its family (colour and place in the order). Weighting heavy above bright would make every month look darker than it was lived and teach users to under-record hard moments. An even pebble carried less charge by the user's own choice, so it moves the stone less; without this an October of calm coffees would outweigh its rooftop birthday. Worth runs ½ … 3: `1 even → ½ · 1 charged → 1 · 2 even → 1 · 2 charged → 2 · 3 even → 1½ · 3 charged → 3`. A missing valence counts as even. (`GEM.CHARGE.charge`; `low` is the one constant to raise if heavy moments should weigh more. `flat` = intensity only, the previous boards, kept for comparison.)

## 2 · Family share

`share(f) = Σ worth of f's pebbles ÷ Σ worth of the month`. A family with no pebble has share 0 and never appears. **Main family** = greatest worth; a tie goes to the family with more pebbles, then to the earlier one in the clockwise order (a dead heat tips the stone toward the pleasant side).

## 3 · The cut: a fixed stone, 29 named facets

Same construction as the brilliant board: table corners at T = 35, star points at RS = 53 on the bisectors, kite tips on the girdle. Position p (0…6) is the direction −90° + p·51.43°, clockwise from the top.

| facet | id | polygon (board names) | θ | nominal area | of the 28 |
|---|---|---|---|---|---|
| table | `table` | Tc[0..6] | — | 3352 | not allocated |
| star | `star_p` | Tc[p], Tc[p+1], Sk[p] — between kites p and p+1 | p + 0.5 | 326 | 1.95 % |
| kite | `kite_p` | Tc[p], Sk[p], Gt[p], Sk[p−1] | p | 1035 | 6.18 % |
| girdle, counter-clockwise of tip p | `girdle_2p` | Gt[p], Sk[p−1], Ge[p−1], rim arc back to Gt[p] | p − 0.25 | 516 | 3.08 % |
| girdle, clockwise of tip p | `girdle_2p+1` | Gt[p], rim arc to Ge[p], Sk[p] | p + 0.25 | 516 | 3.08 % |

Edge adjacency (`GEM.ADJ`): `kite_p` — `star_p−1`, `star_p`, `girdle_2p`, `girdle_2p+1`; `star_p` — `kite_p`, `kite_p+1`, `table`; `girdle_2p+1` — `kite_p`, `girdle_2p+2` (across the valley Sk[p]–Ge[p]); `girdle_2p` — `kite_p`, `girdle_2p−1`; plus `girdle_2p` – `girdle_2p+1` at the tip, so the rim is a cycle.

**Areas are nominal**: projected areas of the round stone (every tip at R0 = 80; table 3.5·T²·sin 51.43°, star ½·2T sin 25.7°·(RS − T cos 25.7°), kite ½·(R0 − T)·2RS sin 25.7°, girdle ½R0²·0.449 − ½R0·RS·sin 25.7°). The drawn outline is a free, **fixed** design parameter (`GEM.SHAPE.reach`, reference 88 · 84 · 79 · 76 · 76 · 79 · 84, a gentle pear, top tip longest); it never carries data and allocation ignores it, so an identical month gives an identical allocation whatever outline is chosen. **Allocation balances area, not count**: kite = 2 girdles = 3.2 stars.

**The table** is never allocated: it stays the neutral window with the count. The 28 facets around it are the data.

**The cycle** (`GEM.CYCLE`): the 28 facets clockwise from the top, `kite_0, girdle_1, star_0, girdle_2, kite_1, …, kite_6, girdle_13, star_6, girdle_0`. Laid end to end by nominal area they form a ring of length A = 16 753.

**Seats** (headquarters): position p's seat is `kite_p, girdle_2p, girdle_2p+1, star_p` = 2393, exactly one seventh of the ring. In an equal month every family sits in its seat.

## 4 · Allocation: wedges in a fixed order, centred on the crown

1. Worths, shares, main family.
2. **Rotate**: the walk is ORDER started at the main family, absent families skipped (main = anger → `anger · fear · shame · sadness · peace · joy · pride`).
3. **Wedges**: each present family gets a wedge of the ring `share × A` long. The main family's wedge is centred on the middle of `kite_0` (the top); each next wedge starts where the previous ends, clockwise. In ring units: `u = kite/2 − share(main)·A/2`, then `u += share(f)·A` per family.
4. **Coverage**: each facet goes to the wedge covering most of its interval. A dead heat (within 10⁻⁶·A) goes to the counter-clockwise wedge, the one holding the facet's leading edge — this is what lands the equal month on the seats.
5. **Minimum presence**: a present family left with nothing takes the smallest facet its wedge touches (ties: the one it covers more, then the clockwise one), from an owner keeping at least one facet.
6. **Settle**: walk the cycle; an end facet of a run moves to the neighbouring run when that lowers `|held − target|` summed over the two families; apply the first improving move, repeat until none; a family is never emptied. Runs stay contiguous.

Properties: deterministic; honest — every family lands within one kite of its target, usually within a girdle (October: largest miss 1 % of the ring); stable — dropping one October pebble changes 1.5 facets on average, at most 6; territories are contiguous runs, so "conquest" reads as the main wedge widening from the top and pushing its neighbours round the stone.

`GEM.allocate(P)` returns `{ shares, worths, main, rotation, walk, wedges: {f: {start, end, centre (turns clockwise from the top), target, held}}, facets: {id: family | null} }`. `GEM.summary(P)` adds per-family facet lists, areas and a centroid of the drawn polygons, for labels.

## 5 · The crown: the wheel turns

Rule (maintainer's decision): **the month's main family takes the top; the clockwise order of the seven families never changes.** Rotation = the index of the main family in ORDER; position p then belongs to `ORDER[(p + rotation) mod 7]`. All three rings turn together — kites, girdles and stars are allocated on the rotated walk — and the table does not turn. A tie for main is broken by pebble count, then by ORDER.

Learnable: the sequence `joy → pride → anger → fear → shame → sadness → peaceful → joy` read clockwise, so the user always knows a colour's neighbours and which way the stone darkens; the shape; the table. Moving: the compass — joy is no longer always at twelve, peaceful no longer always at the left. Gained: at every size, including the unlabelled 32 pt seal, the top of the stone is the month's answer, and a month that turns from joy to anger visibly turns the stone.

## 6 · The order

`joy · pride · anger · fear · shame · sadness · peaceful`, clockwise: the circumplex laid on the stone. The pleasant families stand together as the crown; from pride the aroused side runs down the right (anger, fear) and the calm side climbs back up the left (shame, sadness, peaceful). Sadness, the lowest-arousal hard family, sits beside peaceful, the lowest-arousal pleasant one, so the join between the halves is the calm one; fear and shame, the two anxious self-directed families, share the bottom; anger and sadness mirror each other as the two big hard families. Because wedges are contiguous in this order, every border between two colours is a border between emotionally adjacent families. (`… fear · sadness · shame · peaceful` would put shame against peaceful, a harsher join, and part sadness from peaceful.)

## 7 · Edge cases

- **One family** / **one pebble**: it is main, its wedge is the whole ring: 28 facets; the table keeps the count.
- **Two families tied** (anger 2 / sadness 2): main by pebble count, then ORDER (anger); anger on top, sadness opposite; 15 / 13 facets, 51 / 49 %.
- **All heavy**: nothing special — valence only shifts relative worth when charges differ; the stone turns to the leading hard family.
- **One small even pebble**: worth ½; its wedge is thinner than any facet, so step 5 gives it the smallest facet its wedge touches — a star or a girdle — a chip of its colour in the right place in the order. Minimum presence is one facet, never a kite by right.
- **Empty month**: no main, every facet `null` (glass), count 0.

## 8 · Worked results

From `node test.js --md`, charge method. Wedge = the family's arc of the ring in turns clockwise from the top; area share = nominal area held ÷ the 28-facet ring.

#### M3.OCT · October — 24 pebbles, main: joy (on top), walk: joy · pride · anger · fear · sadness · peaceful

| family | worth | share | wedge (turns cw from top) | facets (n) | nominal area | area share | facets |
|---|---|---|---|---|---|---|---|
| joy | 14 | 34.1% | 0.829 → 0.171 | 9 | 5821 | 34.7% | star_0 star_6 kite_0 kite_1 kite_6 girdle_0 girdle_1 girdle_2 girdle_13 |
| pride | 7 | 17.1% | 0.171 → 0.341 | 5 | 2910 | 17.4% | star_1 kite_2 girdle_3 girdle_4 girdle_5 |
| anger | 5 | 12.2% | 0.341 → 0.463 | 3 | 1877 | 11.2% | star_2 kite_3 girdle_6 |
| fear | 2 | 4.9% | 0.463 → 0.512 | 2 | 842 | 5.0% | star_3 girdle_7 |
| shame | 0 | — | — | 0 | 0 | — | — |
| sadness | 5 | 12.2% | 0.512 → 0.634 | 3 | 2067 | 12.3% | kite_4 girdle_8 girdle_9 |
| peaceful | 8 | 19.5% | 0.634 → 0.829 | 6 | 3235 | 19.3% | star_4 star_5 kite_5 girdle_10 girdle_11 girdle_12 |

Table: neutral, carries the count (24).

#### M3.THIN — 7 pebbles, main: joy (on top), walk: joy · pride · anger · fear · peaceful

| family | worth | share | wedge (turns cw from top) | facets (n) | nominal area | area share | facets |
|---|---|---|---|---|---|---|---|
| joy | 3 | 33.3% | 0.833 → 0.167 | 9 | 5821 | 34.7% | star_0 star_6 kite_0 kite_1 kite_6 girdle_0 girdle_1 girdle_2 girdle_13 |
| pride | 2 | 22.2% | 0.167 → 0.389 | 7 | 3752 | 22.4% | star_1 star_2 kite_2 girdle_3 girdle_4 girdle_5 girdle_6 |
| anger | 2 | 22.2% | 0.389 → 0.611 | 6 | 3944 | 23.5% | star_3 kite_3 kite_4 girdle_7 girdle_8 girdle_9 |
| fear | 1 | 11.1% | 0.611 → 0.722 | 3 | 1877 | 11.2% | star_4 kite_5 girdle_10 |
| shame | 0 | — | — | 0 | 0 | — | — |
| sadness | 0 | — | — | 0 | 0 | — | — |
| peaceful | 1 | 11.1% | 0.722 → 0.833 | 3 | 1358 | 8.1% | star_5 girdle_11 girdle_12 |

Table: neutral, carries the count (7).

#### M3.QUIET — 2 pebbles, main: pride (on top), walk: pride · peaceful

| family | worth | share | wedge (turns cw from top) | facets (n) | nominal area | area share | facets |
|---|---|---|---|---|---|---|---|
| joy | 0 | — | — | 0 | 0 | — | — |
| pride | 2 | 80.0% | 0.600 → 0.400 | 23 | 13325 | 79.5% | star_0 star_1 star_2 star_4 star_5 star_6 kite_0 kite_1 kite_2 kite_5 kite_6 girdle_0 girdle_1 girdle_2 girdle_3 girdle_4 girdle_5 girdle_6 girdle_9 girdle_10 girdle_11 girdle_12 girdle_13 |
| anger | 0 | — | — | 0 | 0 | — | — |
| fear | 0 | — | — | 0 | 0 | — | — |
| shame | 0 | — | — | 0 | 0 | — | — |
| sadness | 0 | — | — | 0 | 0 | — | — |
| peaceful | 0.5 | 20.0% | 0.400 → 0.600 | 5 | 3428 | 20.5% | star_3 kite_3 kite_4 girdle_7 girdle_8 |

Table: neutral, carries the count (2).

#### Heavy (October without its bright pebbles) — 13 pebbles, main: anger (on top), walk: anger · fear · sadness · peaceful · pride

| family | worth | share | wedge (turns cw from top) | facets (n) | nominal area | area share | facets |
|---|---|---|---|---|---|---|---|
| joy | 0 | — | — | 0 | 0 | — | — |
| pride | 2 | 12.5% | 0.719 → 0.844 | 3 | 1358 | 8.1% | star_5 girdle_11 girdle_12 |
| anger | 5 | 31.3% | 0.844 → 0.156 | 8 | 4787 | 28.6% | star_0 star_6 kite_0 kite_6 girdle_0 girdle_1 girdle_2 girdle_13 |
| fear | 2 | 12.5% | 0.156 → 0.281 | 4 | 2393 | 14.3% | star_1 kite_1 girdle_3 girdle_4 |
| shame | 0 | — | — | 0 | 0 | — | — |
| sadness | 5 | 31.3% | 0.281 → 0.594 | 9 | 5821 | 34.7% | star_2 star_3 kite_2 kite_3 kite_4 girdle_5 girdle_6 girdle_7 girdle_8 |
| peaceful | 2 | 12.5% | 0.594 → 0.719 | 4 | 2393 | 14.3% | star_4 kite_5 girdle_9 girdle_10 |

Table: neutral, carries the count (13).

#### One family (October's peace pebbles) — 6 pebbles, main: peaceful (on top), walk: peaceful

| family | worth | share | wedge (turns cw from top) | facets (n) | nominal area | area share | facets |
|---|---|---|---|---|---|---|---|
| joy | 0 | — | — | 0 | 0 | — | — |
| pride | 0 | — | — | 0 | 0 | — | — |
| anger | 0 | — | — | 0 | 0 | — | — |
| fear | 0 | — | — | 0 | 0 | — | — |
| shame | 0 | — | — | 0 | 0 | — | — |
| sadness | 0 | — | — | 0 | 0 | — | — |
| peaceful | 8 | 100.0% | 0.500 → 0.500 | 28 | 16753 | 100.0% | star_0 star_1 star_2 star_3 star_4 star_5 star_6 kite_0 kite_1 kite_2 kite_3 kite_4 kite_5 kite_6 girdle_0 girdle_1 girdle_2 girdle_3 girdle_4 girdle_5 girdle_6 girdle_7 girdle_8 girdle_9 girdle_10 girdle_11 girdle_12 girdle_13 |

Table: neutral, carries the count (6).

#### Equal (one intensity-2 bright pebble per family) — 7 pebbles, main: joy (on top), walk: joy · pride · anger · fear · shame · sadness · peaceful

| family | worth | share | wedge (turns cw from top) | facets (n) | nominal area | area share | facets |
|---|---|---|---|---|---|---|---|
| joy | 2 | 14.3% | 0.929 → 0.071 | 4 | 2393 | 14.3% | star_0 kite_0 girdle_0 girdle_1 |
| pride | 2 | 14.3% | 0.071 → 0.214 | 4 | 2393 | 14.3% | star_1 kite_1 girdle_2 girdle_3 |
| anger | 2 | 14.3% | 0.214 → 0.357 | 4 | 2393 | 14.3% | star_2 kite_2 girdle_4 girdle_5 |
| fear | 2 | 14.3% | 0.357 → 0.500 | 4 | 2393 | 14.3% | star_3 kite_3 girdle_6 girdle_7 |
| shame | 2 | 14.3% | 0.500 → 0.643 | 4 | 2393 | 14.3% | star_4 kite_4 girdle_8 girdle_9 |
| sadness | 2 | 14.3% | 0.643 → 0.786 | 4 | 2393 | 14.3% | star_5 kite_5 girdle_10 girdle_11 |
| peaceful | 2 | 14.3% | 0.786 → 0.929 | 4 | 2393 | 14.3% | star_6 kite_6 girdle_12 girdle_13 |

Table: neutral, carries the count (7).

#### One pebble (Rooftop birthday) — 1 pebble, main: joy (on top), walk: joy

| family | worth | share | wedge (turns cw from top) | facets (n) | nominal area | area share | facets |
|---|---|---|---|---|---|---|---|
| joy | 3 | 100.0% | 0.500 → 0.500 | 28 | 16753 | 100.0% | star_0 star_1 star_2 star_3 star_4 star_5 star_6 kite_0 kite_1 kite_2 kite_3 kite_4 kite_5 kite_6 girdle_0 girdle_1 girdle_2 girdle_3 girdle_4 girdle_5 girdle_6 girdle_7 girdle_8 girdle_9 girdle_10 girdle_11 girdle_12 girdle_13 |
| pride | 0 | — | — | 0 | 0 | — | — |
| anger | 0 | — | — | 0 | 0 | — | — |
| fear | 0 | — | — | 0 | 0 | — | — |
| shame | 0 | — | — | 0 | 0 | — | — |
| sadness | 0 | — | — | 0 | 0 | — | — |
| peaceful | 0 | — | — | 0 | 0 | — | — |

Table: neutral, carries the count (1).

#### Two families tied — 2 pebbles, main: anger (on top), walk: anger · sadness

| family | worth | share | wedge (turns cw from top) | facets (n) | nominal area | area share | facets |
|---|---|---|---|---|---|---|---|
| joy | 0 | — | — | 0 | 0 | — | — |
| pride | 0 | — | — | 0 | 0 | — | — |
| anger | 2 | 50.0% | 0.750 → 0.250 | 15 | 8538 | 51.0% | star_0 star_1 star_5 star_6 kite_0 kite_1 kite_6 girdle_0 girdle_1 girdle_2 girdle_3 girdle_4 girdle_11 girdle_12 girdle_13 |
| fear | 0 | — | — | 0 | 0 | — | — |
| shame | 0 | — | — | 0 | 0 | — | — |
| sadness | 2 | 50.0% | 0.250 → 0.750 | 13 | 8215 | 49.0% | star_2 star_3 star_4 kite_2 kite_3 kite_4 kite_5 girdle_5 girdle_6 girdle_7 girdle_8 girdle_9 girdle_10 |
| peaceful | 0 | — | — | 0 | 0 | — | — |

Table: neutral, carries the count (2).

#### October + one small even shame pebble — 25 pebbles, main: joy (on top), walk: joy · pride · anger · fear · shame · sadness · peaceful

| family | worth | share | wedge (turns cw from top) | facets (n) | nominal area | area share | facets |
|---|---|---|---|---|---|---|---|
| joy | 14 | 33.7% | 0.831 → 0.169 | 9 | 5821 | 34.7% | star_0 star_6 kite_0 kite_1 kite_6 girdle_0 girdle_1 girdle_2 girdle_13 |
| pride | 7 | 16.9% | 0.169 → 0.337 | 5 | 2910 | 17.4% | star_1 kite_2 girdle_3 girdle_4 girdle_5 |
| anger | 5 | 12.0% | 0.337 → 0.458 | 3 | 1877 | 11.2% | star_2 kite_3 girdle_6 |
| fear | 2 | 4.8% | 0.458 → 0.506 | 1 | 516 | 3.1% | girdle_7 |
| shame | 0.5 | 1.2% | 0.506 → 0.518 | 1 | 326 | 1.9% | star_3 |
| sadness | 5 | 12.0% | 0.518 → 0.639 | 3 | 2067 | 12.3% | kite_4 girdle_8 girdle_9 |
| peaceful | 8 | 19.3% | 0.639 → 0.831 | 6 | 3235 | 19.3% | star_4 star_5 kite_5 girdle_10 girdle_11 girdle_12 |

Table: neutral, carries the count (25).


Worth table (charge): 1 high → 1 · 1 neutral → 0.5 · 1 low → 1 · 2 high → 2 · 2 neutral → 1 · 2 low → 2 · 3 high → 3 · 3 neutral → 1.5 · 3 low → 3

Stability, October minus one pebble, facets changed per dropped pebble: 5 6 0 1 6 1 0 2 1 0 3 0 0 1 1 0 0 1 0 0 0 0 1 6 (max 6, mean 1.5)

October under flat (intensity only): joy 31.1% → 8, pride 20.0% → 6, anger 11.1% → 3, fear 4.4% → 1, shame 0.0% → 0, sadness 11.1% → 3, peaceful 22.2% → 7

Largest miss per set (nominal area; girdle = 516, kite = 1035): M3.OCT 166 · M3.THIN 503 · M3.QUIET 78 · Heavy 736 · One 0 · Equal 0 · One 0 · Two 162 · October 291

## 9 · How to explain it to a user

"Your month, cut as a stone. Every moment weighs what you gave it — its intensity, half of that if the moment was even — and each emotion's colour takes a slice of the stone the size of its weight. The emotion that weighed most is on top; around it, clockwise, the others always come in the same order: joy, pride, anger, fear, shame, sadness, peaceful. The window in the middle is how many moments you kept."

## 10 · Rejected on the way

- **Fixed headquarters + flood fill over adjacency**: in October joy at 34 % ends with 16 %, land-locked by pride and peaceful who need only their own girdles; a strong family cannot conquer through a present neighbour, so honesty fails in the common case.
- **Table to the main family**: doubles the emphasis and the count loses its window; superseded by rotation.
- **Heavy > bright** (× 1.5): defensible as negativity bias, rejected for a mirror; one constant away.
- **Count-balanced allocation**: three girdles would read as equal to a kite and two girdles.

## 11 · Files

- `gem-method.js` — `GEM.ORDER`, `GEM.FACETS` (id, kind, home position, θ, nominal `area`, drawn `points`, `drawnArea`), `GEM.ADJ`, `GEM.CYCLE`, `GEM.AREA`, `GEM.SHAPE`, `GEM.worth`, `GEM.shares`, `GEM.main`, `GEM.allocate`, `GEM.summary`. `method` = `"charge"` (default) | `"flat"`, or `{ worth, crown: "rotate" | "fixed" }` (`fixed` anchors the walk on joy's seat; comparison only).
- `test.js` — invariants and the tables above.
