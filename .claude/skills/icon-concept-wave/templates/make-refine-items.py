#!/usr/bin/env python3
"""Build <dir>/items.json for a refinement round from the review results.

Usage (from the wave folder):
  python3 -I make-refine-items.py <results.json> refine                 round 1: every drawing decided "refine" that has no round yet
  python3 -I make-refine-items.py <results.json> refine2 name1,name2    a later round: those named items (the ones judged "again"),
                                                                        with their previous round's drawing
<results.json> is the review page's db document saved by ArtifactData (it holds results.drawings / results.refinements).
Items carry the maintainer's note, the original drawing, hand/idea (so the page can pair before and after) and the agent/batch
that will draw them (Opus for the originals of h41/h42, Sonnet for h43/h44, 8 and 7 items per batch).
"""
import json
import re
import sys
from pathlib import Path

W = Path.cwd()
res = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))["results"]
outdir = W / sys.argv[2]
only = set(sys.argv[3].split(",")) if len(sys.argv) > 3 else None
concepts = {c["name"]: c["meaning"] for c in json.loads((W / "concepts.json").read_text(encoding="utf-8"))["concepts"]}


def symbols(folder):
    d = {}
    for f in folder.rglob("*.svg"):
        for m in re.finditer(r'<symbol id="([^"]+)"([^>]*)>(.*?)</symbol>', f.read_text(encoding="utf-8"), re.S):
            d[(f.parent.name, m.group(1))] = "skip" if "data-skip" in m.group(2) else m.group(3).strip()
    return d


hands = symbols(W / "hands")
prev = {}
for p in sorted(W.glob("refine*")):
    if p.is_dir() and p != outdir:
        for (_, i), v in symbols(p).items():
            prev[i] = v  # later rounds overwrite earlier ones
decided = {(d["concept"], d["candidate"]): d for d in res["drawings"]}
refined = set()
for p in W.glob("refine*/items.json"):
    if p.parent != outdir:
        refined |= {i["name"] for i in json.loads(p.read_text(encoding="utf-8"))}
items = []
for d in res["drawings"]:
    name = (d["name"] or f'{d["concept"]}-{d["candidate"].replace(".", "-")}').strip()
    if only is not None and name not in only:
        continue
    if only is None and (d["decision"] != "refine" or name in refined):
        continue
    hand, idea = d["candidate"].split(".")
    note = d["refine"] or ""
    for r in res.get("refinements", []):
        if r["name"] == name and r.get("note"):
            note = r["note"]
    orig = {v: hands[(hand, f'{d["concept"]}-{idea}-{v}')] for v in ("regular", "solid", "marked")}
    it = {"name": name, "concept": d["concept"], "meaning": concepts[d["concept"]], "hand": hand, "idea": idea, "note": note, "original": orig}
    if only is not None:
        it["previous"] = {v: prev.get(f"{name}-{v}") for v in ("regular", "solid", "marked")}
    items.append(it)
cnt = {"opus": 0, "sonnet": 0}
for it in items:
    a = "opus" if it["hand"] in ("h41", "h42") else "sonnet"
    per = 8 if a == "opus" else 7
    it["agent"], it["batch"] = a, f"{cnt[a] // per + 1:02d}"
    cnt[a] += 1
outdir.mkdir(exist_ok=True)
(outdir / "items.json").write_text(json.dumps(items, indent=1, ensure_ascii=False), encoding="utf-8")
print(f"wrote {outdir / 'items.json'}: {len(items)} items, {cnt}")
