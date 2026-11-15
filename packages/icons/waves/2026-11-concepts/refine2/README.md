# Refinement round 2

Seven drawings that round 1 got wrong in shape (not just in how the solid hides detail: the preview now carves inner strokes out of a filled body automatically, so detail strokes drawn inside a filled path stay visible in the solid; read BRIEF.md, solid).

`refine2/items.json`: for each item, `original_round0` (the first-round drawing the maintainer reviewed), `previous_round1` (your colleagues' round-1 refinement, which the maintainer rejected with the `note`) and the `note`. Redraw the three variants of each item so the note is satisfied. Start from whichever of the two drawings the note favours (a note may say to keep the first round's regular, or its marked). Same v2 rules as BRIEF.md. Do not read other folders.

Output `refine2/opus-01.svg`, symbols `<name>-regular`, `<name>-solid`, `<name>-marked`; check with `python3 -I check-refine.py --r2 opus-01`. Then `refine2/NOTES.md` (at most 6 lines).

Extra hints from the notes: square-check: the dot goes after the tail of the tick as in the shipped `src/checkmark-dot.svg` (you may read that one file); flask: the solid fills only the liquid inside the flask, so the regular needs a closed liquid shape; logs-stack: the marked is a skip; scale-balance: the solid fills the triangle base.
