#!/usr/bin/env python3
"""Consolidate a finished review into the wave's library: every kept or accepted drawing under its official name.

Usage (from the wave folder):  python3 -I make-library.py <results.json> [<more results.json> ...]
Several files are merged: one per review artifact (a wave reviewed over several fresh artifacts has one saved document each).
Writes library.json (name -> concept, squad, hand, idea, finalist, starred, round, regular/solid/marked inner SVG, skipped forms),
library.svg (one sprite, ids <name>-regular|solid|marked, skipped variants left out) and verdict.md (tallies and the per-concept picks).
The drawing used is the latest round that redrew it (refine3 > refine2 > refine), else the first-round drawing. Abandoned drawings are listed, not kept.
"""
import collections
import json
import re
import sys
from pathlib import Path

W = Path.cwd()
docs = [json.loads(Path(a).read_text(encoding="utf-8"))["results"] for a in sys.argv[1:]]
res = {"drawings": [d for r in docs for d in r["drawings"]], "concepts": [c for r in docs for c in r["concepts"]]}
concepts = json.loads((W / "concepts.json").read_text(encoding="utf-8"))
squad = {c["name"]: c["squad"] for c in concepts["concepts"]}
VARIANTS = ("regular", "solid", "marked")
SYM = re.compile(r'<symbol id="([^"]+)"([^>]*)>(.*?)</symbol>', re.S)


def read(folder):
    out = {}
    for f in sorted(folder.glob("*.svg")):
        for m in SYM.finditer(f.read_text(encoding="utf-8")):
            out[m.group(1)] = (m.group(3).strip(), "data-skip" in m.group(2))
    return out


hands = {h.name: read(h) for h in (W / "hands").iterdir() if h.is_dir()}
rounds = [(n, read(W / d)) for n, d in ((3, "refine3"), (2, "refine2"), (1, "refine")) if (W / d).exists()]
lib, dropped = {}, []
for d in res["drawings"]:
    if d["decision"] == "abandon" or not d["name"]:
        dropped.append(d["id"])
        continue
    name, hand, idea = d["name"].strip(), *d["candidate"].split(".")
    src, rnd = None, 0
    for n, syms in rounds:
        if f"{name}-regular" in syms:
            src, rnd = {v: syms[f"{name}-{v}"] for v in VARIANTS}, n
            break
    if src is None:
        src = {v: hands[hand][f'{d["concept"]}-{idea}-{v}'] for v in VARIANTS}
    lib[name] = {"concept": d["concept"], "squad": squad[d["concept"]], "hand": hand, "idea": idea, "finalist": d["finalist"], "starred": d["starred"],
                 "round": rnd, "skipped": [v for v in VARIANTS if src[v][1]], **{v: ("" if src[v][1] else src[v][0]) for v in VARIANTS}}
lib = dict(sorted(lib.items(), key=lambda kv: (kv[1]["squad"], kv[1]["concept"], kv[0])))
(W / "library.json").write_text(json.dumps(lib, indent=1, ensure_ascii=False), encoding="utf-8")
A = 'viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"'
syms = [f'<symbol id="{n}-{v}" {A}>{e[v]}</symbol>' for n, e in lib.items() for v in VARIANTS if e[v]]
(W / "library.svg").write_text('<svg xmlns="http://www.w3.org/2000/svg">\n' + "\n".join(syms) + "\n</svg>\n", encoding="utf-8")

by = collections.defaultdict(list)
for n, e in lib.items():
    by[(e["squad"], e["concept"])].append((n, e))
lines = ["# Verdict", "", f"{len(lib)} drawings kept under {len(set(e['concept'] for e in lib.values()))} concepts; {len(dropped)} abandoned.",
         f"{sum(e['finalist'] for e in lib.values())} finalists, {sum(e['starred'] for e in lib.values())} starred, "
         f"{sum(1 for e in lib.values() if e['round'])} redrawn in a refinement round, {sum(len(e['skipped']) for e in lib.values())} variants dropped.", ""]
sq_now = None
for (s, c), items in by.items():
    if s != sq_now:
        lines += [f"## {s}", ""]
        sq_now = s
    lines.append(f"- **{c}**: " + ", ".join(f"`{n}`" + (" (finalist)" if e["finalist"] else "") + (" ★" if e["starred"] else "") for n, e in items))
none = [c["concept"] for c in res["concepts"] if c["none"]]
lines += ["", "Concepts with no finalist: " + (", ".join(none) or "none") + ".", "", "Abandoned: " + (", ".join(dropped) or "none") + "."]
(W / "verdict.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"library.json: {len(lib)} drawings; library.svg: {len(syms)} symbols; verdict.md written")
