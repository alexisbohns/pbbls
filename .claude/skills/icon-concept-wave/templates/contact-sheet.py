#!/usr/bin/env python3
"""Render a squad's drawings as a contact sheet, with the carved solids as the review page shows them.

Usage: python3 -I contact-sheet.py <squad> [hand ...]     (default: every hand that has the squad)
Writes gallery/sheets/<squad>-<hand>.svg and, on macOS, a .png beside it (Quick Look). Look at the PNGs
before publishing a squad: rows are concepts, columns are classical/bold/pebbles/weird x regular/solid/marked.
"""
import importlib.util
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
spec = importlib.util.spec_from_file_location("rb", ROOT / "review-build.py")
rb = importlib.util.module_from_spec(spec)
spec.loader.exec_module(rb)
CONCEPTS = json.loads((ROOT / "concepts.json").read_text(encoding="utf-8"))["concepts"]
IDEAS = ("classical", "bold", "pebbles", "weird")
VARIANTS = ("regular", "solid", "marked")
CELL, LABEL = 54, 150


def sheet(squad, hand):
    syms = {}
    for f in sorted((ROOT / "hands" / hand).glob(f"{squad}-??.svg")):
        for s in rb.SYMBOL.findall(f.read_text(encoding="utf-8")):
            syms[rb.SID.search(s).group(2)] = s
    if not syms:
        return None
    ordered = rb.carve_solids(rb.skipped_as_regular(list(syms.values())))
    syms = {rb.SID.search(s).group(2): s for s in ordered}
    names = [c["name"] for c in CONCEPTS if c["squad"] == squad]
    out = []
    for r, n in enumerate(names):
        y = r * (CELL + 6) + 6
        out.append(f'<text x="6" y="{y + CELL / 2 + 4}" font-family="Helvetica" font-size="13" fill="#1D1B16">{n}</text>')
        for k, (idea, var) in enumerate((i, v) for i in IDEAS for v in VARIANTS):
            s = syms.get(f"{n}-{idea}-{var}")
            if not s:
                continue
            m = re.match(r"<symbol([^>]*)>(.*)</symbol>", s, re.S)
            x = LABEL + k * (CELL + 2) + (k // 3) * 10
            out.append(f'<svg x="{x}" y="{y}" width="{CELL}" height="{CELL}" color="#1D1B16" {m.group(1)}>{m.group(2)}</svg>')
    w, h = LABEL + 12 * (CELL + 2) + 40, len(names) * (CELL + 6) + 12
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}"><rect width="100%" height="100%" fill="#FEF9EF"/>{"".join(out)}</svg>', w, h


def main():
    squad = sys.argv[1]
    hands = sys.argv[2:] or sorted(p.name for p in (ROOT / "hands").iterdir() if p.is_dir())
    outdir = ROOT / "gallery" / "sheets"
    outdir.mkdir(parents=True, exist_ok=True)
    for hand in hands:
        res = sheet(squad, hand)
        if not res:
            continue
        svg, w, h = res
        f = outdir / f"{squad}-{hand}.svg"
        f.write_text(svg, encoding="utf-8")
        subprocess.run(["qlmanage", "-t", "-s", str(max(w, h)), "-o", str(outdir), str(f)], capture_output=True)
        print(f"wrote {f.with_suffix('.svg.png')}")


if __name__ == "__main__":
    main()
