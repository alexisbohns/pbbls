# L5 — silhouette lights
- A lit stone is one solid path, no ring, no cut-outs: the stone's own strokes (outline, glint, dashed ring, scribble) are dropped and replaced by the outer edge of the stroke (centreline outset 0.875, ray-cast from the centroid, 144 points).
- Every other stroke is byte-identical to lit-sources.svg.
- people: lit-1 = front (left) head, lit-2 = both heads; bodies stay outlined.
- New bases pebble-outlined-glyph / -2: dotted ring with stroke-dasharray removed, scribble verbatim. The -2 ring was one open path with a 1.2 gap between its ends; one closing curve (C4 10.2 4.1 9.9 4.3 9.5) was appended so it reads closed.
- Pebbles are single stones: -lit-1 only. Contact sheet: sheet.png (source | L4 | L5).
