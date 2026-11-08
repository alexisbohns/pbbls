# Pebbles icon set — the everyday wave (2026-11)

124 figures: the 26 the apps still draw with SF Symbols, lucide and hand-traced drawables (the chrome brief, `../2026-11-chrome/brief.md`, absorbed here), and 98 everyday objects, places, animals and actions the set will need. For each figure you draw **five versions**, and each version is a **pair**: the default (outlined) drawing and its filled/active twin. Judged at 24 pt in a column and a list, in ink and in accent, on Paper and on Night.

## Read first (in this order)

1. `../../DNA.md`: the line (Riverbed Reed), the one active rule, the canvas rules. Everything there holds.
2. A dozen shipped drawings in `../../src/` (e.g. `home.svg`, `home.active.svg`, `path.svg`, `people.svg`, `camera.svg`, `hourglass-end.svg`, `trash.svg`, `heart.active.svg`, `pebble-round.svg`, `check-circle.svg`, `dolmen.svg`, `bell-ringing.svg`). That is the hand you extend: its weight, its overshoot, its open corners, the stone that closes by overlap. Some figures here already have a shipped drawing (dolmen, radar, remove, wave, bell): do not copy it, draw past it.

## The five versions are five ideas

v1…v5 of one figure are five different readings, not one drawing jittered five times: change the subject, the viewpoint, the metaphor, the crop. For `delete`: a torn sheet, a stone dropped in water, a crossed-out stroke, a burnt mark, a fist closing. At least one version per figure should be the plain, instantly-read version; at least one should be a reading nobody else would draw. Families that must read apart (alert/warning/danger, rock/boulder/stepping-stone, hill/mountain, bell/notification, minus/remove/delete, sound/audio/waveform/microphone/mute, upload/upvote/download, secret/locked, public/language, house vs the shipped home) are drawn with that contrast in mind: same version number = same family idea where you can.

Sets drawn together: visibility trio (secret, private, public), appearance trio (appearance-light/-dark/-device), error family (load-failed, link-broken, page-gone), alert/warning/danger. Keep a set's vN in one vocabulary.

## The pair: default and filled/active

- **Default** `<name>-vN`: strokes only, no fill, like every base in `src/`.
- **Active** `<name>-vN-active`: the same drawing, unchanged strokes, with something filled solid (`fill="currentColor"`): its stone lit, its flame, its body, the part that carries the meaning. Up to three filled paths. A filled shape that carries a mark (a glyph, a hole, an eye) is carved: one compound path, `fill-rule="evenodd"`, the mark knocked out. A filled path that should have no edge takes `stroke="none"`. Never a different drawing.

## Canvas (strict, machine-checked)

viewBox 0 0 24 24; every coordinate (end and control points, an arc's end point) inside 1.5..22.5; paths only, absolute `M L C Q A Z`; no `H V S T`, no relative commands, no transforms, no dashes, no text. Two decimals at most. A dot is `M x y L x+0.01 y`.

## Output (exact)

Your hand id is given in your prompt (`hNN`). Draw in **13 batches** following `figures.json` (`batch` field, 10 figures per batch, the last has 4). One file per batch: `hands/<hand>/batch-<NN>.svg`, containing only:

```
<svg xmlns="http://www.w3.org/2000/svg">
  <symbol id="again-v1" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="..."/></symbol>
  <symbol id="again-v1-active" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"><path d="..."/><path fill="currentColor" d="..."/></symbol>
  ...
</svg>
```

100 symbols per full batch (10 figures × 5 versions × 2). After **each** batch run `python3 -I check.py <hand> <NN>` from this folder and fix every error in the drawing before starting the next batch. Write each batch file once, in one Write call; do not print drawings anywhere else.

When all 13 pass, write `hands/<hand>/NOTES.md`, at most 15 lines: the name of your hand (two words), what it owns, and the figures you found hardest. Nothing else: no HTML, no preview page.
