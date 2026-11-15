#!/usr/bin/env python3
"""Write EXAMPLES.md in the wave folder: shipped drawings the hands read, with data-* attributes stripped.

Usage (from the wave folder, packages/icons/waves/<wave>/):  python3 -I make-examples.py [name ...]
Default names are the nine used in 2026-11. `dolmen` stood in for a tent that the set does not have.
"""
import re
import sys
from pathlib import Path

SRC = Path.cwd().parent.parent / "src"
NAMES = sys.argv[1:] or ["people", "house", "dolmen", "pebble-round", "pearl", "pouch", "karma", "knot-sm", "memo-text-stone"]
out = ["# Examples: shipped Pebbles drawings (the line you extend)\n",
       ("Nine" if len(NAMES) == 9 else str(len(NAMES))) + f" shipped drawings of the set, as sources (24-unit square, pen width 1.75). Look at the weight, the open corners, the stones that close by overlap, the single dot of the pen. Do not copy them; draw past them. Their attributes are already stripped to what you write.\n"]
for n in NAMES:
    s = (SRC / f"{n}.svg").read_text(encoding="utf-8")
    s = re.sub(r'\s+data-[a-z-]+="[^"]*"', "", s)
    out.append(f"## {n}\n\n```svg\n{s.strip()}\n```\n")
Path("EXAMPLES.md").write_text("\n".join(out), encoding="utf-8")
print("wrote EXAMPLES.md")
