---
name: icon-concept-wave
description: |
  Produce a wave of Pebbles icon concepts the way the 2026-11 concepts wave was done: concepts split into squads, an Opus and a Sonnet hand drawing four ideas x three variants each, an anonymous in-situation review page (finalists, stars, names, keep/abandon/refine), refinement rounds, and the checker/preview tooling that makes it converge. Use when the maintainer wants new icon concepts illustrated, wants to continue the 2026-11 wave (squads s5 to s8), or asks to review, name or refine drawings from such a wave. Everything here was proven on squads s1 to s4 (126 concepts planned, 65 done, 221 drawings kept). To continue the 2026-11 wave, read "Resuming in a fresh session" below.
---

# Icon concept wave

A repeatable pipeline: **brief -> two hands per squad -> checker -> contact sheet -> blind review page -> refinement rounds -> names**. It lives in `packages/icons/waves/<yyyy-mm-slug>/` (the exploration-wave convention of `packages/icons/CLAUDE.md`). The worked example, with every squad's drawings and the maintainer's verdicts, is `packages/icons/waves/2026-11-concepts/` (uncommitted at the time of writing: check `git status`). The tooling is bundled in `templates/` next to this file.

Read `packages/icons/CLAUDE.md` and `packages/icons/DNA.md` first. Nothing here touches `src/` or `icons.json`: a wave only produces drawings and verdicts. Extraction into `src/` is a separate step (see the end).

## Resuming in a fresh session (read this first when continuing 2026-11)

State when this was written (2026-11-15): squads s1-delivery, s2-system, s3-surface and s4-quality are drawn, reviewed, named and refined. Their result is consolidated in the wave folder: `library.json` (221 kept drawings under their official names), `library.svg`, `verdict.md`, and the saved review document `results/review-final-2026-11-15.json`. Squads **s5-status** (20 icons, two families), **s6-trust**, **s7-value**, **s8-meaning** are not drawn.

Do this, one squad per session (a session that runs several squads gets long and slow, and so does the review artifact):
1. `git status`: the wave folder may still be uncommitted. Read `BRIEF.md` and `check.py` in it; they are final unless a verdict says otherwise.
2. Ask the maintainer which squad, then launch it (step 1 below). Hands are `h41` and `h43` only.
3. After verification, build the page for **that squad only**: `python3 -I review-build.py <squad>`, and publish it as a **new artifact** (no `url`: `capabilities {"db": {}, "user": {}}`, `icon "grid"`, a description naming the squad). Do not republish into the old artifact: with all squads in it (1.2 MB, 650 candidates) the page got slow. A new artifact starts with an empty db, which is what you want.
4. When the maintainer is done reviewing and refining (the same steps 3 to 5), save that artifact's document into `results/` under a dated name, and run `make-library.py` with **all** the saved documents (it merges them) to refresh `library.json`, `library.svg` and `verdict.md`.
5. Keep the earlier artifact untouched; it is the record of s1 to s4.

## Ground rules (the maintainer's, learned the hard way)

- **One squad at a time.** Launch a squad only when the maintainer says go. Never launch several squads, and never start work they did not ask for (the maintainer reviews in between and the briefs get corrected between squads).
- **Two hands per squad: one Opus (medium effort) and one Sonnet (high effort), both given the reference icons (`EXAMPLES.md`).** The pilot compared four hands (both models, with and without references): references mattered most (51 selections vs 28), model mattered little. So the no-reference hands were dropped and there are two hands, `h41` (Opus) and `h43` (Sonnet). With references off and on in the pilot, the unreferenced hands drew pen-width dots and flying marks.
- **Hand identity is hidden from the maintainer**, in the review page and in the report. Hands are only used in the keys (`concept/hand.idea`) and in the analysis you run yourself.
- **The maintainer's note beats my checker.** Three rules were overridden by notes (tail dots, mark size, solid fill count) and then loosened; when a note asks for something the checker rejects, accept the checker error as a deliberate exception and say so.
- **Delegation by size.** A squad = two background agents. A refinement round = two agents (Opus for originals of h41/h42, Sonnet for h43/h44), or do it directly when it is about five small items (a render of the result is cheaper than briefing an agent). Always check an agent's output yourself (`check.py`, the contact sheet): never trust the hand-back.
- Do not edit `check.py` while hands are running it. A broken checker mid-run wastes their time (it happened once).
- No browser is needed or assumed. Rendering is done with macOS Quick Look (`qlmanage`) on SVG files, and the page logic is tested headless with `jsdom` (it is in the repo's `node_modules`).
- Nothing is committed unless asked. When it is time, follow the repo's git rules (branch `chore/<issue>-<slug>`, one commit per logical change).

## Files (copy `templates/*` into the wave folder, then edit the data)

| File | What it is |
|---|---|
| `make-concepts.py` | Data of the squads (name, one-line meaning, optional family note) -> `concepts.json`. Edit the `SQUADS` dict; keep squads to 12-20 concepts; batches of 8. Write meanings in the product context, say what to avoid (no flag, no face). Families (statuses, priorities) get a squad `note`: one shared vocabulary per idea, read apart as a scale. |
| `make-examples.py` | Writes `EXAMPLES.md` from shipped `src/*.svg` (data-* stripped). Pass names to change the set. |
| `BRIEF.md` | The brief both hands read: four ideas (classical, bold, pebbles, weird), three variants (regular, solid, marked, plus skip), the line, canvas, output format. Edit the counts only. |
| `check.py` | The checker hands run after every batch (`python3 -I check.py <hand> <squad>-<batch>`). Canvas rules, the variant rules, lean lines, warnings. |
| `check-refine.py`, `REFINE.md`, `REFINE-LATER-ROUND.md` | Same checks for refinement batches and the instructions the refinement agents read. |
| `review-build.py` + `review-template.html` | Build `gallery/review.html` (the blind review page) from `hands/`, `refine*/` and `concepts.json`. `python3 -I review-build.py [squad ...]`. |
| `contact-sheet.py` | `python3 -I contact-sheet.py <squad> [hand]` -> PNG per hand with carved solids. Look at it before publishing. |
| `make-refine-items.py` | Review results -> `refineN/items.json`. |
| `analyze-hands.py` | Per-hand selection, notes and objective measurements from the results. |
| `make-library.py` | Consolidates finished review documents (one or several) into `library.json`, `library.svg` and `verdict.md`: every kept drawing under its official name, with its latest refinement. |
| `smoke-test.cjs` | Headless run of the built page against a saved review document. |

`gallery/` and `results/` are not committed (gallery is gitignored; results are the maintainer's session data).

## The loop, step by step

### 0. Setup (once per wave)
1. Create `packages/icons/waves/<yyyy-mm-slug>/` with `hands/h41`, `hands/h43`, copy `templates/*`, run `make-concepts.py` (edit data first) and `make-examples.py`.
2. Check that the brief's counts and ids match (hands `h41` and `h43`, the squad list).
3. Smoke-test the checker on a hand-built valid file if you changed it.

### 1. Launch a squad (only on the maintainer's word)
Two `Agent` calls in one message, `run_in_background: true`:

- Opus: `model: "opus"`, `effort: "medium"`. Prompt: *You are hand h41 in an icon-drawing wave for the Pebbles app. Work in <wave folder>. Read BRIEF.md first (revised after review rounds: the rules for solid, marked and straight lines are strict and machine-checked, read the variants section carefully), then EXAMPLES.md (nine shipped drawings: the line you extend). Your squad is `<squad>`: draw every concept whose `squad` is <squad> in concepts.json, batch by batch, exactly as BRIEF.md specifies (hand id h41; files hands/h41/<squad>-<batch>.svg; run check.py after each batch and fix every error in the drawing; read its warnings too). Draw each icon by hand, point by point, rather than generating shapes from a formula: the maintainer wants the uneven hand-drawn line. Finish all batches. Final reply: at most 3 lines, naming the batches that pass and anything left unfinished.*
- Sonnet: `model: "sonnet"`, `effort: "high"`, same prompt with `h43` and the sentence *Hand-draw each path point by point; do not generate drawings with a script or a formula*.

Expect 12-20 minutes and about 150-230k tokens per hand. A hand that writes by script anyway (the pilot's Opus did) is not a failure, but note it: it can look mechanical.

### 2. Verify before the maintainer sees anything
1. `python3 -I check.py h41 <squad>-01 ...` and the same for `h43`, run by you. Read the warnings.
2. `python3 -I contact-sheet.py <squad>` and **Read the two PNGs**. Look for: solids that are plain blobs, marks that float, fused fills, cramped or mechanical drawings, skipped variants that should not be.
3. `python3 -I review-build.py`, then `node --check` on the extracted script and `node smoke-test.cjs <jsdom> gallery/review.html <saved doc>`.
4. Publish with the `Artifact` tool: `file_path: <wave>/gallery/review.html`, `url: <the wave's review artifact>`, `capabilities: {"db": {}, "user": {}}`. First publish for a new wave needs `icon: "grid"` and a `description`; it is private. Re-publishing the same url keeps the maintainer's state (db doc `review/<user id>`).

### 3. The review page (what the maintainer does)
- **Review** tab: concept by concept, 8 anonymous cards each (the pilot had 16). Each card shows the idea as the active tab in a menu bar, in chips, and as the three variants. An inspector shows Paper and Night, sizes, a list row, a button. Pick 1-3 finalists, star the ones to keep for another use, or "No finalist".
- **Names** tab: every finalist and star once; name (unique, lowercase, hyphens, not in `icons.json`, not ending in regular/solid/marked/parts/of), then Keep / Refine / Abandon. Variants are clickable (1 click refine, 2 drop, 3 reset). Done items are hidden by default.
- **Refinements** tab: refinement requests (picked items marked Refine), picked items not judged yet, and redrawn items to judge (Accept / Refine again with a note / Abandon, variants clickable). "Keep as is" closes a request.
- State is the db doc `review/<user id>` with `finals, stars, none, names, dec, note, ref, vf`. Read it back with `ArtifactData` `list` (`collection: review`, `out_dir: <wave>/results`), then use `results.drawings` and `results.refinements` (each carries the note, the variant flags, `round`).
- A skipped variant (`data-skip="true"`) shows faded and struck through; the page shows the regular in its place.
- The solid is previewed **carved**: strokes lying inside a filled shape knock out a gap of stroke width (details stay visible); strokes that reach outside it (edges, stems, beams) merge into the fill. This is a preview of what the icon pipeline does.

### 4. Learn from the verdict, then fix the brief
After every squad, run `analyze-hands.py <results.json>` and read the notes. Fix **causes in the brief and the checker** before the next squad, not symptoms drawing by drawing. The history, which is why the rules are what they are:
- Pilot: solids "too sharp" came from the brief telling hands to carve with `evenodd` and `stroke="none"`; dots "flying around" and smaller than the stroke came from a brief that never anchored a mark. Fixed by v2: solid fills the regular's own closed paths and keeps the stroke; a mark is inside a closed shape, a tail dot, or an existing shape filled; every straight line leans (exact horizontals and verticals are rejected); `skip` for variants that make no sense.
- s2: 27 of 31 refinements vanished when the preview stopped carving connecting lines. The render was the cause. Always suspect the preview before the drawing.
- s2/s3 loosened rules: tail dots, mark size relative to the host shape (error above 75%, warning above 55%), up to 8 filled paths for repeated elements, warning for fused fills.

### 5. Refinement rounds
1. Save the review document (step 3), then `python3 -I make-refine-items.py <results.json> refine` (round 1: everything decided Refine with no round yet). It writes `refine/items.json` with the note, the original drawing, hand/idea, and agent/batch (Opus 8 per batch, Sonnet 7).
2. Launch the two refinement agents (`REFINE.md` is their whole instruction; items in `refine/`, outputs `refine/<agent>-<batch>.svg`, checked with `python3 -I check-refine.py <agent>-<batch>`). Symbols are named after the maintainer's name for the drawing: `<name>-regular|solid|marked`. They may not read other folders; the `original` fields are enough.
3. Verify (checker + contact view), rebuild, publish. Items appear in Refinements as "Redrawn, to judge", each with its own decision state per round (`name@2`, `name@3`).
4. For "Refine again": `make-refine-items.py <results.json> refine2 name1,name2` (it adds the previous round's drawing), write `refine2/README.md` from `REFINE-LATER-ROUND.md`, run `check-refine.py --r2 <agent>-<batch>`; for round 3 the builder reads `refine3/` the same way (it loops rounds 2 and 3; extend the loop for more). When it is only a few items, redraw them yourself from the paths and render a quick sheet instead of briefing an agent.
5. A note that contradicts the checker: keep the drawing, accept the error, tell the maintainer.

### 6. Closing a squad
Everything named and every refinement accepted (`complete: true` in the saved results). Save the artifact's document into `results/<name>-<date>.json` (not committed), run `make-library.py` over every saved document, and update the memory note. Do not launch the next squad until told; the next one goes in a fresh session and a fresh artifact (see "Resuming").

## What the first four squads showed
- References are what matter: with them both models were equal in the end (Opus 95 selections, Sonnet 101, of 260 drawings each); the unreferenced hands of the pilot got 15 and 13 of 64.
- The whole loop converged by s3: s3 and s4 needed almost no refinement rounds once the brief (v2 and its s2 updates) and the carved preview were right. Fix causes in the brief and in the preview first.
- Squad time: a hand takes about 11-24 minutes and 150-230k tokens; the review is the maintainer's time (a squad of 15-18 concepts took a session of reviewing).
- Idea mix kept: classical 20-27%, bold 20-27%, pebbles weak on technical concepts (7 of 53 in s2), weird about a quarter.

## Pitfalls
- One artifact for all squads gets heavy (carved solids are masks): about 1.2 MB and slow at 65 concepts. Use one artifact per squad.
- `python3 -I` removes the script directory from the path: `check-refine.py`, `contact-sheet.py` and `review-build.py` insert it themselves. Run everything from the wave folder.
- Sprite symbols with masks (carved solids) make the page about 1 MB; that is fine.
- The review state key is a per-session random seed plus db rev; a reload keeps the maintainer's data, a republish never wipes it. Do not rename concepts or hands after a review started (keys are `concept/hand.idea`).
- Quick Look renders `<use>` of symbols poorly: build sheets with nested `<svg>` per cell (what `contact-sheet.py` does).
- **What the maintainer approves must be what the package ships.** The review shows each form as drawn, the package renders it from roles; any gap surfaces only at extraction, after the verdict. The 2026-11 wave hit two: the generator notched outlines where a carved detail met the edge (fixed in the generator, carve now inks a carved line outside the fill), and 15 marks drawn as `stroke="none"` dots that the package draws filled and stroked, 1.75 units wider (now a checker error; the 15 were shrunk at extraction, the maintainer kept one as drawn). When the package changes how it renders a form, port it into `review-build.py` and `check.py` before the next squad.
- **Review every form the package ships, not only the drawn ones.** The wave reviews regular, solid and marked; the package also ships solid-marked (worked out from the roles), and it is where most extraction fixes landed in 2026-11 (a lit stone hollow in its row, a mark cut as a disc, a body overlapping a seal). At extraction, publish one "approved and shipped" page per part with regular, solid, marked and solid-marked rendered from the generated `glyphs.json`, and expect a round of drops and overrides per part. Many marks were dropped there: ask in the review whether a drawing needs a marked form at all.
- **Names and meanings come from the drawing.** Meanings drafted from the name alone were wrong where the name misleads (`clap` is a clapperboard, `doctor` a stethoscope): write or check them against the rendered drawing. Check names for typos at naming time; seven were fixed at extraction.

## Not done yet (decide with the maintainer)
- ~~Extraction into the package~~: done (#1653, stack of eight PRs: wave, generator fixes, `extract.py`, then one part per squad group). `extract.py` reads the roles off the solid and marked forms; redrawn solids ship as `<name>.solid.svg` overrides with the outline as body. All 338 drawings are `candidate` rows in `icons.json` with wave `2026-11-concepts`.
- ~~Align the generator's carve with the preview~~: done (branch `fix/1653-carve-ink-outside`): a carved line is cut only inside the fill and inked outside it, a line lying mostly deep inside a fill carves even when it reaches out, a hidden mark may repeat a body path, and a lit mark covers the pen's dot it grows from.
- Follow-up: `abstract-butterfly` and `abstract-flower` want a progress form (step roles) instead of a marked one.
- All eight squads are drawn and reviewed (338 kept drawings).
- The build journal pebble owed by `CLAUDE.md` at the end of each task (one was dropped when s1 to s4 were wrapped up; drop one per squad session that changes something).
