# Icon forms, part 3b: the drawings from the review — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the maintainer's review of the guessed roles: retag marks and steps, redraw bodies so ≈90 glyphs get a solid, split bodies into parts, add real steps, add dotted forms to the pebble glyphs, drop the twins judged useless, then hand the touched glyphs back for a second review on the design page.

**Architecture:** Every change is in the SVG sources (`packages/icons/src/<name>.svg`) under the amended model (spec D2–D4): `data-role="body"` (optionally `data-part`), `data-role="mark"`, `data-role="step" data-step="k"`, `data-rest="hidden"`; a dotted form is `src/<name>.dotted.svg`. The generator works out the forms; a contact-sheet script renders every form of a list of glyphs so each batch is checked by eye. The design page gets a "Round 2" list with fresh verdicts.

**Tech Stack:** SVG by hand (the DNA), TypeScript (tsx) for the sheet, headless Chrome for the PNG, Next.js design page.

**Input:** `packages/icons/waves/2026-11-forms/verdicts.json` — the maintainer's verdicts (`ok`, `fix`, `drop-twin`) and a note per fix. **The note is the requirement**; this plan only groups the glyphs and says how to read the notes. Spec: `docs/superpowers/specs/2026-11-08-icon-forms-design.md` (amended 2026-11-09). Style: `packages/icons/DNA.md`.

**Branch:** `chore/1607-icon-drawings`, stacked on `chore/1607-icon-roles` (PR #1611). Never touch the untracked `apps/web/AGENTS.md`.

**Commit trailer:** every commit message ends with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

**Commands** (repo root):

| What | Command |
|---|---|
| format sources | `npm run format --workspace=@pbbls/icons` |
| lint sources | `npm run lint --workspace=@pbbls/icons` |
| generate / freshness | `npm run generate --workspace=@pbbls/icons` / `npm run check --workspace=@pbbls/icons` |
| package tests / types | `npm run test --workspace=@pbbls/icons` / `npm run build --workspace=@pbbls/icons` |
| contact sheet | `npm run sheet --workspace=@pbbls/icons -- <out.html> <name> [<name>…]` (Task 1) |
| sheet to PNG | `"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --headless=new --disable-gpu --hide-scrollbars --screenshot=<out.png> --window-size=1400,<rows × 150 + 80> file://<out.html>` |

Write sheets and PNGs to the scratchpad `/private/tmp/claude-503/-Users-alexis-code-pbbls/d5cce965-85cd-4a54-952e-f04224f61d58/scratchpad/sheets/`, never into the repo.

---

## How to fix one glyph (every batch follows this)

1. Read its note in `verdicts.json`. Look at `src/<name>.svg`, its twins (`src/<name>.active.svg`, `src/<name>.lit-N.svg`: "ex active", "ancient active", "ex lit-1" in a note point at these) and its current forms on the sheet.
2. Edit `src/<name>.svg` only (plus `src/<name>.dotted.svg` in Task 8). Keep the regular drawing looking the same: same lines, same places. Rules:
   - **DNA:** paths only, absolute `M L C Q A Z`, every point inside 1.5–22.5, no fill, no transforms. Attributes allowed: `d`, `data-role`, `data-part`, `data-step`, `data-rest`.
   - **Body (for a solid):** the outer silhouette, `data-role="body"`. It is filled the way SVG fills a path: each subpath closes with a straight line from its end to its start. So the body must be **one continuous outline** around the shape. A silhouette drawn as several open strokes (e.g. `home`'s tent: two strokes from the apex) is redrawn as one stroke that runs around it, and the pieces that are not outline (poles, antennas) move to their own untagged path. Several closed shapes (two circles) may be several body paths.
   - **Details that must show in the solid** (a fold, a door, eyes, the `i` of info) stay separate untagged paths inside the body: the generator cuts them out at the stroke width. A detail drawn *on* the outline stays stroked.
   - **Mark:** the one accent, `data-role="mark"`; add `data-rest="hidden"` when the regular drawing does not show it (a lit dot). In a solid, the mark inside the body is cut out (`solid · marked`).
   - **A dot that lights with the mark** (a lock's keyhole dot inside the lit blob): tag the dot `data-role="mark"` too. Paths of one role never carve each other, so marked shows one solid dot, and the solid still carves the dot.
   - **Steps:** two or more (the lint refuses a single step), a real sequence only, `data-role="step" data-step="1…n"` in fill order; hidden when the regular drawing does not show them.
   - **Parts:** `data-role="body" data-part="<word>"` on every body path (one lowercase word: `left`, `middle`, `right`, `top`, `moon`, `cloud`, `candle`, `flame`). Every combination is worked out.
   - Remove a role the note calls irrelevant ("progress-1 is not relevant" → that path loses its step role; a hidden step that only existed for that form is deleted).
   - An override (`src/<name>.<form>.svg`) is the last resort, when a redraw cannot make the worked-out form right. Say so in the report.
3. `npm run format --workspace=@pbbls/icons` then `npm run lint --workspace=@pbbls/icons`: fix every error **in the drawing**.
4. After the batch: render the sheet of the batch's glyphs, look at the PNG yourself (Read it), fix what looks wrong (a body that fills a sliver, a detail lost, a regular that moved), and only then `npm run generate --workspace=@pbbls/icons`, `npm run check --workspace=@pbbls/icons`, `npm run test --workspace=@pbbls/icons`, commit (`git add packages/icons/src packages/icons/glyphs packages/icons/react apps/ios/Pebbles/Resources/Icons.xcassets apps/android/app/src/main/res/drawable`: a redrawn source also moves the old emitters' outputs; `git status --short` must show nothing else but `apps/web/AGENTS.md` afterwards).
5. Report: the PNG path, one line per glyph (what you changed, which forms it now has), anything you could not do and why.

---

### Task 0: Branch (controller)

- [ ] `git switch chore/1607-icon-roles && git pull --ff-only && git switch -c chore/1607-icon-drawings`
- [ ] Commit `packages/icons/waves/2026-11-forms/verdicts.json` and this plan: `docs(ui): record the glyph review verdicts and plan the drawings`.

---

### Task 1: The contact sheet

**Files:**
- Create: `packages/icons/scripts/sheet.ts`
- Modify: `packages/icons/package.json` (scripts)

- [ ] **Step 1: Write `scripts/sheet.ts`**

```ts
/**
 * A contact sheet for reviewing drawings by eye: each named glyph in every
 * form it has, plus its roles (body ink, parts in hues, mark orange, steps
 * blue, hidden paths dashed). Reads the sources directly, no generate needed.
 *
 *   npm run sheet --workspace=@pbbls/icons -- <out.html> <name> [<name>…]
 */
import { writeFileSync } from "node:fs"
import { fileURLToPath } from "node:url"
import { loadGlyphs } from "./lib/emit-glyphs"
import { formsOf, parseFormKey, renderForm, type GlyphData, type GlyphPath, type RenderedPath } from "./lib/forms"

const ROOT = fileURLToPath(new URL("..", import.meta.url))
const PART_HUES = ["#2f7d6d", "#7a4fb0", "#b0602f", "#2f5fb0"]

const esc = (s: string): string => s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/"/g, "&quot;")

function svg(inner: string): string {
  return `<svg viewBox="0 0 24 24" width="72" height="72" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`
}

function drawn(paths: readonly RenderedPath[]): string {
  return paths.map((p) => `<path d="${esc(p.d)}" fill="${p.fill ? "currentColor" : "none"}"${p.evenOdd ? ' fill-rule="evenodd"' : ""}${p.stroke ? "" : ' stroke="none"'}/>`).join("")
}

function roleColour(p: GlyphPath, parts: readonly string[]): string {
  if (p.role === "mark") return "#d0461e"
  if (p.role === "step") return "#2563eb"
  if (p.role === "body" && p.part !== undefined) return PART_HUES[Math.max(0, parts.indexOf(p.part)) % PART_HUES.length] ?? "#1d1b18"
  if (p.role === "body") return "#1d1b18"
  return "#9a948a"
}

function roles(g: GlyphData): string {
  return g.paths
    .map((p) => `<path d="${esc(p.d)}" stroke="${roleColour(p, g.parts)}"${p.hidden ? ' stroke-dasharray="1 1.4"' : ""}/>`)
    .join("")
}

function row(g: GlyphData): string {
  const cells = formsOf(g).flatMap((key) => {
    const form = parseFormKey(key)
    return form ? [`<figure>${svg(drawn(renderForm(g, form)))}<figcaption>${esc(key)}${key in g.overrides ? " ·override" : ""}</figcaption></figure>`] : []
  })
  return `<section><h2>${esc(g.name)}</h2><div><figure class="roles">${svg(roles(g))}<figcaption>roles</figcaption></figure>${cells.join("")}</div></section>`
}

const [out, ...names] = process.argv.slice(2)
if (!out || names.length === 0) {
  console.error("usage: npm run sheet --workspace=@pbbls/icons -- <out.html> <name> [<name>…]")
  process.exit(2)
}
const glyphs = new Map(loadGlyphs(ROOT).map((g) => [g.name, g]))
const missing = names.filter((n) => !glyphs.has(n))
if (missing.length > 0) {
  console.error(`no such glyph: ${missing.join(", ")}`)
  process.exit(1)
}
const rows = names.flatMap((n) => {
  const g = glyphs.get(n)
  return g ? [row(g)] : []
})
writeFileSync(
  out,
  `<!doctype html><meta charset="utf-8"><style>
body{font:12px system-ui;background:#f6f2ea;color:#1d1b18;margin:16px}
section{display:flex;align-items:center;gap:12px;margin:0 0 10px;background:#fff;border-radius:12px;padding:8px 12px}
h2{font:600 12px ui-monospace,monospace;width:150px;margin:0;flex:none}
section>div{display:flex;flex-wrap:wrap;gap:6px}
figure{margin:0;display:flex;flex-direction:column;align-items:center;gap:2px}
figure.roles{padding-right:8px;border-right:1px solid #e6dfd2}
figcaption{font:10px ui-monospace,monospace;color:#6b655c}
</style>${rows.join("")}`,
)
console.log(`${rows.length} glyphs → ${out}`)
```

- [ ] **Step 2: Add the script** — in `packages/icons/package.json` `scripts`, add `"sheet": "tsx scripts/sheet.ts"` after `"format"`.

- [ ] **Step 3: Try it**

```bash
mkdir -p /private/tmp/claude-503/-Users-alexis-code-pbbls/d5cce965-85cd-4a54-952e-f04224f61d58/scratchpad/sheets
npm run sheet --workspace=@pbbls/icons -- /private/tmp/claude-503/-Users-alexis-code-pbbls/d5cce965-85cd-4a54-952e-f04224f61d58/scratchpad/sheets/try.html home pouch cromlech stack
npm run build --workspace=@pbbls/icons
```

Expected: `4 glyphs → …/try.html`; `build` clean. Screenshot it (Commands table, height 680) and Read the PNG: four rows, a roles cell and one cell per form.

- [ ] **Step 4: Commit** — `chore(ui): render a contact sheet of glyph forms` (`git add packages/icons/scripts/sheet.ts packages/icons/package.json`).

---

### Task 2: The mark, not a step (20 glyphs)

The old guess made these accents into step 1 (or missed them). Each gets its mark; most also get a solid.

`home`, `home-hive`, `abstract-home`, `pouch-dot`, `barbed-wire`, `user-heart`, `node`, `heart-heart`, `hand-dot`, `lock`, `keyhole-dot`, `hour`, `circle-info`, `music-treble-clef`, `chart-grow`, `pyramid`, `karma-center`, `bookmark`, `ripples`, `people`

How to read the notes:
- "progress-1 → should be half / half / marked": that path becomes `data-role="mark"` (keep `data-rest="hidden"` if it had it).
- "ex active", "ancient active", "better before with solid dot": the mark is the shape the `.active` twin fills; copy that geometry in as the mark if the base lacks it.
- `home`: the reference result was approved: the tent redrawn as one outline (`M3.6 20 C6.4 14.6 9.2 9.6 12.1 4.9 C14.9 9.8 17.6 14.6 20.4 19.8` as body), the poles `M12.1 4.9 L10.6 2.6 M12.1 4.9 L13.7 2.5` as their own path, ground and door untagged, the door dot as a hidden mark. The solid shows the door carved.
- `home-hive`: move the dot from the roof to over the door.
- `barbed-wire`: the mark dot sits within the ribbon.
- `user-heart`: forms regular, marked, solid; the solid looks like today's progress-2.
- `people`: keep steps 1 and 2; drop step 3; the solid paints both figures; add the mark from the `.active` twin.
- `pyramid`, `karma-center`, `ripples`: the mark is the filled accent of the old `.active` (centre circle for `ripples`).

Follow "How to fix one glyph"; commit `chore(ui): give the accent glyphs their mark and a solid`.

### Task 3: A solid instead of a wrong step (17 glyphs)

`orange`, `file`, `chat-dots`, `circle-a`, `lightbulb-moon`, `lightbulb-mark`, `magnifying-sm`, `circle-minus`, `qr-code`, `christmas-ball`, `poo-smell`, `apple-seeds`, `exclamation-double-lakshmi`, `robot-head`, `purse`, `yarn`, `exchange`

Drop the step role the note calls irrelevant, then give the glyph a body. Specifics: `magnifying-sm`: the lens is the body, its reflection a carved line. `purse`: the fold stays visible (a carved line). `yarn`: the ball filled with one band left empty (one band line carved). `exchange`: both circles are body paths. `robot-head`: today's three forms look the same: one body, the face details carved.

Commit `chore(ui): give glyphs a solid where a step did not mean anything`.

### Task 4: Solids, part 1 (25 glyphs)

`bubble-info`, `sign-info`, `circle-karma`, `circle-fossil`, `gem-stone-1`, `feather-write`, `obelisk`, `dashboard`, `library`, `copy-squares`, `cursor-pointer`, `cursor-pointer-click`, `book`, `bookbinding`, `memo-text-clip`, `paperplane`, `folder`, `toolbox`, `metal-box`, `moon-crescent`, `star-dot`, `download-picture`, `filter-box`, `stone-layers-2`, `mountain-path`

Each note says "need a solid": tag (or redraw as one outline) the body, keep the inner details as carved lines. Keep any role that is already right. Commit `chore(ui): give everyday glyphs a solid (1 of 2)`.

### Task 5: Solids, part 2 (31 glyphs)

`cat-2`, `cat-back`, `piglet`, `chick-profile`, `guitar-neck`, `bike`, `question-circle`, `question-bubble`, `exclamation-circle`, `exclamation-triangle`, `exclamation-double`, `exclamation-triple`, `hand-palm`, `circles-dot`, `speaker-dot`, `pound`, `hand-wave`, `stone-12`, `window`, `search-bar`, `bridge-2`, `dot-circle-line`, `basketball`, `smartphone`, `phone-ring`, `android-head-2`, `robot-stones-arms-down`, `robot-fun`, `globe-spin`, `map-fold`, `map-fold-path`

As Task 4. `bike`: the solid fills the wheels (two body circles), the frame stays a line. `exclamation-double`, `exclamation-triple`: each bar and dot a body path. Commit `chore(ui): give everyday glyphs a solid (2 of 2)`.

### Task 6: Parts (7 glyphs)

| Glyph | Parts | Also |
|---|---|---|
| `dolmen` | `left`, `middle`, `right` (the three ellipses) | |
| `dolmen-2` | `left`, `right`, `top` | a mark (from the `.active` twin) |
| `moon-cloud` | `moon`, `cloud` | |
| `circles-joined` | `left`, `center`, `right` | |
| `binoculars-row` | `left`, `right` | |
| `candle-simple` | `candle`, `flame` | |
| `users` | `left`, `right` (each figure's head and body, the head as a second path of the same part) | |

Every part needs an outline that fills well on its own; check every `parts-…` cell on the sheet. Commit `chore(ui): split glyph bodies into parts`.

### Task 7: Steps (14 glyphs)

| Glyph | Steps | Also |
|---|---|---|
| `collection` | the 3 stones, 1→3 | solid |
| `cocoon` | the layers, inside out | |
| `ziggurat` | 3 tiers, bottom up | solid |
| `pyramid-layers` | 3 layers, bottom up | solid |
| `feeding-bottle`, `milk-bottle`, `bottle` | liquid levels (hidden fills, bottom up) | solid |
| `umbrella` | the canopy panels, left to right | solid |
| `umbrella-above` | 7 steps, the last one the filled canopy | |
| `pine-cone`, `pine` | tiers, bottom up | solid |
| `signal-dots` | regular has no dots: the dots become hidden steps 1, 2 | |
| `stones-link` | step 1 the left dot, step 2 the right dot | |
| `palette-3` | one step per well, then solid | (`palette-1` is left as it is: the merge into one `palette` renames call sites, part 7) |

Commit `chore(ui): give sequence glyphs real steps`.

### Task 8: Dotted forms and dropped twins

- `pebble-glyph`: `src/pebble-glyph.dotted.svg` = the drawing of today's `pebble-glyph-dotted.svg` (no roles, no fill); the outline becomes the body, so its solid matches today's `pebble-glyph.lit-1`. Same for `pebble-glyph-open` with `pebble-glyph-dotted-open.svg`. The `pebble-glyph-dotted` rows stay until part 7 moves their call sites (web uses them).
- `drop-twin` glyphs (`abstract-crown`, `abstract-fence`, `abstract-loki`, `dna`, `pearl`, `watermelon`, `abstract-key`, `translation-a`, `stone-facet`, `square-paste`, `hills`, `signal`, `unset-input`, `tree-branches`): the twin adds nothing, so remove the roles the guess took from it (they are listed under the glyph in `waves/2026-11-forms/review.json`). Then, for each twin file: if no app names its generated symbol (search `apps/` for the PascalCase name with the twin suffix, e.g. `AbstractCrownActive`, `abstractCrownActive`, `ic_pbbls_abstract_crown_active`), delete `src/<name>.<twin>.svg` and remove the variant from the row in `icons.json`; otherwise leave it and list it for part 7.

Commit `chore(ui): draw the pebble glyphs dotted and drop the twins that added nothing`.

---

### Task 9: Round 2 on the design page

**Files:**
- Create: `packages/icons/waves/2026-11-forms/round2.json`
- Modify: `packages/icons/package.json` (`exports`)
- Modify: `apps/web/lib/design/useReviewVerdicts.ts`, `apps/web/lib/design/glyph-forms.ts`, `apps/web/components/sandbox/design/DesignTools.tsx`, `apps/web/components/sandbox/design/GlyphReviewDialog.tsx`, `apps/web/components/sandbox/design/pages/IconsPage.tsx`
- Test: `apps/web/lib/design/glyph-forms.test.ts`

- [ ] **Step 1: The list.** Write every glyph whose source changed on this branch:

```bash
git diff --name-only chore/1607-icon-roles...HEAD -- packages/icons/src | sed -E 's#packages/icons/src/([^.]+)\..*#\1#' | sort -u | node -e 'let s="";process.stdin.on("data",d=>s+=d).on("end",()=>console.log(JSON.stringify({names:s.trim().split("\n")},null,1)))' > packages/icons/waves/2026-11-forms/round2.json
```

and add to `packages/icons/package.json` `exports`: `"./verdicts.json": "./waves/2026-11-forms/verdicts.json"` and `"./round2.json": "./waves/2026-11-forms/round2.json"`.

- [ ] **Step 2: Failing test** — append to `glyph-forms.test.ts`:

```ts
describe("round 2", () => {
  it("lists the glyphs touched since the first review and keeps their first verdict and note", () => {
    const touched = GLYPHS.find((g) => showsGlyph(g.name, "round2", {}))
    if (!touched) throw new Error("no glyph in round2.json")
    expect(showsGlyph(touched.name, "round2", {})).toBe(true)
    expect(firstReview("home")).toEqual({ verdict: "fix", note: expect.stringContaining("solid") })
    expect(firstReview("path")).toEqual({ verdict: "ok", note: undefined })
  })
})
```

(import `firstReview` with the others). Run `npm run test --workspace=@pbbls/web -- lib/design/glyph-forms.test.ts`: FAIL (`firstReview` missing).

- [ ] **Step 3: Implement**

In `glyph-forms.ts`, add the imports `import round2 from "@pbbls/icons/round2.json"` and `import round1 from "@pbbls/icons/verdicts.json"`, then:

- `GlyphShow` becomes `"all" | "review" | "round2" | "fix"`; in `showsGlyph` add `if (show === "round2") return ROUND2.has(name)` with `const ROUND2: ReadonlySet<string> = new Set(round2.names)`.
- add:

```ts
const ROUND1_VERDICTS: Readonly<Record<string, string>> = round1.verdicts
const ROUND1_NOTES: Readonly<Record<string, string>> = round1.notes

/** The first review (2026-11-09): its verdict and, for a fix, what the maintainer asked for. */
export function firstReview(name: string): { verdict: string | undefined; note: string | undefined } {
  return { verdict: ROUND1_VERDICTS[name], note: ROUND1_NOTES[name] }
}
```

In `useReviewVerdicts.ts`, the two keys become `"pbbls-glyph-review-v2"` and `"pbbls-glyph-review-notes-v2"` (round 1 lives in `verdicts.json` now), and `copy` writes `{ round: 2, verdicts, notes: fixNotes, copiedAt }`.

In `DesignTools.tsx`, `SHOW_OPTIONS` gains `{ value: "round2", label: "Round 2" }` after "Needs review".

In `GlyphReviewDialog.tsx`, under the flags list, when `firstReview(glyph.name).verdict` exists render:

```tsx
<p className="rounded-2xl bg-muted px-4 py-3 text-sm">
  <span className="font-medium">Round 1 · {first.verdict}</span>
  {first.note && <> · {first.note}</>}
</p>
```

with `const first = glyph ? firstReview(glyph.name) : null` beside `flags`.

In `IconsPage.tsx`, the review header shows when `glyphShow !== "all"` as today; no change.

`needsReview` stays (the role pass's flags); the tile pill shows for `needsReview(name) || ROUND2` glyphs: export `inRound2(name)` from `glyph-forms.ts` and use `needsReview(glyph.name) || inRound2(glyph.name)` for the pill.

- [ ] **Step 4: Check** — web tests, `tsc`, lint (Commands table of the amendment plan); open `/sandbox/design/icons`, Show → Round 2: the touched glyphs, pills reading "review", the dialog showing the round 1 note.

- [ ] **Step 5: Commit** — `feat(web): review the redrawn glyphs as round 2`.

---

### Task 10: Verdict and PR (controller)

- [ ] `packages/icons/waves/2026-11-forms/verdict.md`: what round 1 decided (counts by verdict), the model change it led to, what part 3b drew (per task, the glyph count), what is left for part 7 (`pebble-glyph-dotted*` rows, `palette-1`, twins kept for call sites), and that round 2 is pending.
- [ ] Full checks: icons `lint`, `build`, `test`, `check`, `lint:replaces`; web `lint`, `tsc`, `test`.
- [ ] Push and open the PR against `chore/1607-icon-roles` (stacked): title `chore(ui): draw the forms the glyph review asked for`, body `Part of #1607 (part 3b of 7)`, labels `chore`, `ui`, `no-lab-note`, milestone `M66 · Brand icons`, `Data safety: no change`. Before #1611 merges, retarget this PR to `main`.
- [ ] Watch the Android and iOS checks (generated outputs move with redrawn sources); re-baseline Android with the `rebaseline-screenshots` label if the screenshot gate turns red.

---

## Round 2 fixes (partial review, 2026-11-09)

**Input:** `packages/icons/waves/2026-11-forms/verdicts-round2.json` — the maintainer's round 2 so far (`partial: true`; more may follow). Each `fix` has a note: **the note is the requirement.** Same rules as "How to fix one glyph", plus:

- **A hole** (a door, a window, a keyhole that should be empty in the solid, not a carved line): make it a subpath of the body path, drawn in the opposite direction to the outline. SVG's non-zero fill then leaves it open, the regular drawing is unchanged (the hole is still stroked), and a mark inside it is drawn filled in black in `solid · marked`. `home` is the worked example (its door).
- **"Marked is actually the solid"**: the path becomes the body (or the body grows to include it); **"this is marked, not solid"**: the filled thing becomes the mark and a real body is drawn.
- **"An alternative version I like"** (`thumbs-up`, `clipboard`): keep today's partial fill as the mark, and add a real solid (the whole silhouette filled).
- **Parts named in the note** (left/right/top/bottom/center/up): use those part names. Combinations the note lists (e.g. `dots-cross`: start = both left, end = both right, first = top-left + bottom-right, second = bottom-left + top-right) come from four parts named as one lowercase word each (`topleft`, `topright`, `bottomleft`, `bottomright`); every combination is worked out, the components pick the named ones.
- **drop-twin** in round 2 means the same as round 1: remove the roles the twin gave, delete the twin if no app uses it.

| Task | Glyphs |
|---|---|
| R1 retags and fixes | `home` (done in the plan commit: door is a hole), `home-hive`, `abstract-home`, `users`, `dolmen`, `collection`, `case`, `cycle-link`, `stone-bounce`, `people-3`, `picture-speed`, `thumbs-up`, `clipboard`, `scroll`, `bubble-a`, `shop-showcase`, `cycle-stone`, `exchange`, `heart`, `abstract-key` |
| R2 parts | `stones-back`, `swing-double`, `people-send`, `stones-swap`, `dots-cross`, `cycle-stones`, `sprout`, `stones-double-circle-open`, `coffee-2`, `signs-soil`, `signs`, `stones-plus`, `scale-balanced`, `disk-angle-up`, `abstract-crown`, `watermelon` |
| R3 steps | `nechlace`, `slider`, `shelve`, `connection`, `timeline`, `hourglass-shake` (also correct the arrow's movement), `cairn-angle-up`, `stones-triple-circle`, `circle-quarters`, `stones-stack` |
| R4 marks | `dna`, `baluchon`, `bag`, `karma-dot`, `calendar-dot`, `sunshine`, `rectangle-dashed`, `keyhole` |
| R5 solids (a) | `abstract-sign`, `radar`, `abstract-umbrella`, `abstract-sign-2`, `calendar-date`, `game`, `calendar-day`, `trash`, `trash-2`, `broom`, `bell-ringing`, `sunrise`, `sparkle`, `sparkles`, `petroglyph`, `whatsapp`, `ellipse-forward`, `paste-square`, `picture-upload`, `picture-kite`, `arrow-up-lg`, `circle-angle-up`, `stones-fall`, `paper-plane-trail`, `memorial`, `brochure` |
| R6 solids (b) and drops | `shield`, `user-circle-lg`, `user-circle-sm`, `users-circle`, `fruit-basket`, `eye-open`, `arche-cairn`, `megaphone-2`, `user-shine`, `jail`, `gift-circle`, `lock-rounded`, `keyhole-circle`, `envelope`, `ticket`, `user-plus-curve`, `compass-vertical`, `egg-fried`, `compass`, `hexagon-text`, `label`, `label-stone`, `stopwatch`, `user-forbidden`, `users-communication`, `flower-daisy`, `toggle-off-label`; drop-twin: `exchange-md`, `upload-box`, `cloud-upload`, `hook-fish`, `x-half-circle`, `arrow-down-cut`, `eye-closed` |

Each task commits on `chore/1607-icon-drawings` (`chore(ui): round 2 — <task>`), regenerated outputs included. The maintainer re-reviews through Show → Marked fix: the glyphs keep their round 2 `fix` verdict until he changes it.

### Round 2, second export (2026-11-10)

`verdicts-round2.json` now holds both exports (`exports[1]` is the second). 98 of the first export's 107 fixes came back ok. `stones-back`, `user-shine` and `toggle-off-label` kept their first notes, which the current drawings already answer: left as they are, reported to the maintainer.

| Task | Glyphs |
|---|---|
| R7 re-fixes | `home-hive` (widen the door so its hole reads empty in the solid; the mark just above the door, in marked and solid · marked), `baluchon` (mark at the centre of the circle), `broom` (solid fully filled, no white line), `paper-plane-trail` (no mark; today's marked becomes progress 1 of 2 steps, the whole plane step 2; a solid that reads), `megaphone-2` (solid · marked broken), `jail` (regular without the mark: hide it; redo the solids), `binoculars` (solid incorrect), `circle-leaf`, `leaf` (solid not ok), `obelisk` (solid · marked incorrect), `gem-stone-1` (no mark), `metronome`, `abstract-radar` (drop the mark, add a solid); drop-twin `slide` |
| R8 solids and steps | solids: `stone-star`, `toggle-off-shine`, `screwdriver`, `stone-debounce`, `user-standing`, `book-stone`, `bookmark-stone`, `check-circle`, `plus-circle`, `hourglass-end`, `pendulum`, `abstract-pen`, `camera`, `camera-analogic`; steps: `circles-jointure`, `circles-jointure-2`, `chart-stones` |
