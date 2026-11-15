#!/usr/bin/env python3
"""Compare the hands on objective measures and on the maintainer's verdicts.

Usage (from the wave folder):  python3 -I analyze-hands.py <results.json>
Prints, per hand: how many drawings were selected / finalists / kept / sent to refine, how the notes break down (sharp solids,
mark placement and size, geometric look), and measurements taken on every SVG: mark size and position, how solids are built.
Selection counts are the signal; remember a hand is only judged on what the maintainer picked, and four hands is a hint, not a proof.
"""
import collections
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path.cwd()))
import check  # noqa: E402

W = Path.cwd()
res = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))["results"]
D = {d["id"]: d for d in res["drawings"]}
rows = []
for hdir in sorted((W / "hands").iterdir()):
    for f in sorted(hdir.glob("s*-??.svg")):
        syms = {}
        for m in re.finditer(r'<symbol id="([^"]+)"([^>]*)>(.*?)</symbol>', f.read_text(encoding="utf-8"), re.S):
            syms[m.group(1)] = (m.group(2), [(re.search(r'\bd="([^"]*)"', p).group(1), 'fill="currentColor"' in p, 'stroke="none"' in p)
                                           for p in re.findall(r"<path\b[^>]*?/>", m.group(3))])
        for sid in syms:
            if not sid.endswith("-regular"):
                continue
            base = sid[:-len("regular")]
            concept, idea = base[:-1].rsplit("-", 1)
            reg, sol, mar = syms[sid][1], syms.get(base + "solid", ("", []))[1], syms.get(base + "marked", ("", []))[1]
            r = {"hand": hdir.name, "id": f"{concept}/{hdir.name}.{idea}"}
            filled = [p for p in sol if p[1]]
            r["solid_nostroke"] = sum(1 for p in filled if p[2]) / max(1, len(filled))
            regd = {p[0] for p in reg}
            new = [p for p in mar if p[0] not in regd and p[1]]
            if new:
                pts = check.outline(new[0][0])[0]
                xs, ys = [p[0] for p in pts], [p[1] for p in pts]
                r["mark_size"] = round(max(max(xs) - min(xs), max(ys) - min(ys)), 2)
                c = ((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2)
                r["mark_inside"] = any(check.is_closed(d) and check.inside(c, check.outline(d)[0]) for d, _, _ in reg)
            rows.append(r)
by = collections.defaultdict(collections.Counter)
for r in rows:
    c = by[r["hand"]]
    c["drawn"] += 1
    d = D.get(r["id"])
    if d:
        c["selected"] += 1
        c["finalist"] += d["finalist"]
        c["keep"] += d["decision"] == "keep"
        c["refine"] += d["decision"] == "refine"
        t = (d.get("refine") or "").lower()
        c["note:sharp"] += "sharp" in t
        c["note:mark"] += bool(re.search(r"dot|mark|inside|outside|flying|bigger", t))
        c["note:geometric"] += bool(re.search(r"geometric|squared|hand drawn", t))
print(f"{'hand':6}" + "".join(f"{k:>16}" for k in ["drawn", "selected", "finalist", "keep", "refine", "note:sharp", "note:mark", "note:geometric"]))
for h, c in by.items():
    print(f"{h:6}" + "".join(f"{c[k]:>16}" for k in ["drawn", "selected", "finalist", "keep", "refine", "note:sharp", "note:mark", "note:geometric"]))
print("\nobjective, per hand: median mark size | share of marks inside a closed shape | share of solid paths with stroke=none")
for h in by:
    R = [r for r in rows if r["hand"] == h]
    sz = sorted(r["mark_size"] for r in R if "mark_size" in r)
    ins = [r["mark_inside"] for r in R if "mark_inside" in r]
    print(f"{h:6} {sz[len(sz) // 2] if sz else '-':>6} | {sum(ins) / max(1, len(ins)):>5.0%} | {sum(r['solid_nostroke'] for r in R) / max(1, len(R)):>5.0%}")
