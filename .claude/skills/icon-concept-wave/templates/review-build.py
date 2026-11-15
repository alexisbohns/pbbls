#!/usr/bin/env python3
"""Build gallery/review.html: the blind in-situation review of the concepts wave.

Usage: python3 -I review-build.py [squad ...]    (default: every squad that has drawings)

Reads concepts.json and hands/hNN/<squad>-<batch>.svg. Hands are never shown: a
candidate is (hand, idea) for one concept and carries its three variants (regular,
solid, marked). Phase 1: review concept by concept in situation, pick 1 to 3
finalists and star the ones to keep for another use. Phase 2: name every finalist
and star, then keep, abandon or ask a refinement. The page saves to the artifact db.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
import check  # noqa: E402
SRC = ROOT.parent.parent / "src"
DATA = json.loads((ROOT / "concepts.json").read_text(encoding="utf-8"))
IDEAS = ("classical", "bold", "pebbles", "weird")
VARIANTS = ("regular", "solid", "marked")
SYMBOL = re.compile(r"<symbol\b.*?</symbol>", re.S)
SID = re.compile(r'(<symbol\b[^>]*?\bid=")([^"]+)(")')
FILLERS = [("home", "Home"), ("path", "Path"), ("collection", "Collection"), ("people", "Souls"), ("user", "Profile")]


def filler_symbols():
    out = []
    for n, _ in FILLERS:
        s = (SRC / f"{n}.svg").read_text(encoding="utf-8")
        body = re.sub(r'\s+data-[a-z-]+="[^"]*"', "", re.search(r"<svg[^>]*>(.*)</svg>", s, re.S).group(1))
        out.append(f'<symbol id="f-{n}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">{body.strip()}</symbol>')
    return out


def skipped_as_regular(syms):
    """A skipped solid or marked (data-skip) is shown as the idea's regular drawing."""
    inner = {}
    for sym in syms:
        m = SID.search(sym)
        if m and m.group(2).endswith("-regular"):
            inner[m.group(2)[:-len("regular")]] = re.search(r"<symbol\b[^>]*>(.*)</symbol>", sym, re.S).group(1)
    out = []
    for sym in syms:
        if 'data-skip="true"' in sym:
            m = SID.search(sym)
            base = m.group(2).rsplit("-", 1)[0] + "-"
            head = re.match(r"<symbol\b[^>]*>", sym).group(0)
            sym = head.replace(' data-skip="true"', "") + inner.get(base, "") + "</symbol>"
        out.append(sym)
    return out


def _dense(d, step=0.5):
    pts = check.outline(d)[0]
    out = []
    for a, b2 in zip(pts, pts[1:]):
        n = max(1, int(((a[0] - b2[0]) ** 2 + (a[1] - b2[1]) ** 2) ** 0.5 / step))
        out += [(a[0] + (b2[0] - a[0]) * k / n, a[1] + (b2[1] - a[1]) * k / n) for k in range(n)]
    return out or pts


def is_detail(d, polys):
    """A stroke is a detail (carved) when it lies inside a filled body; a stroke that reaches outside it is a
    connector (an edge, a stem, a beam) and simply merges into the fill."""
    pts = _dense(d)
    inside = sum(1 for p in pts if any(check.inside(p, poly) for poly in polys))
    return inside / len(pts) >= 0.85


def carve_solids(syms):
    """Preview of the pipeline's carved solid: detail strokes lying in a filled body knock out a gap of the stroke
    width and are inked only outside the fill; connectors that reach out of the body merge into it."""
    out = []
    for sym in syms:
        m = SID.search(sym)
        if not m or not m.group(2).endswith("-solid"):
            out.append(sym)
            continue
        sid = m.group(2)
        head = re.match(r"<symbol\b[^>]*>", sym).group(0)
        paths = re.findall(r"<path\b[^>]*?/>", sym)
        filled = [p for p in paths if 'fill="currentColor"' in p]
        dd = lambda p: re.search(r'\bd="([^"]*)"', p).group(1)
        polys = [check.outline(dd(p))[0] for p in filled]
        others = [p for p in paths if p not in filled and is_detail(dd(p), polys)] if filled else []
        plain = [p for p in paths if p not in filled and p not in others]
        if not filled or not others:
            out.append(sym)
            continue
        rect = '<rect x="0" y="0" width="24" height="24" fill="#fff" stroke="none"/>'
        ma = (f'<mask id="a-{sid}" maskUnits="userSpaceOnUse" x="0" y="0" width="24" height="24">{rect}'
              + "".join(f'<path d="{dd(p)}" fill="none" stroke="#000" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/>' for p in others) + "</mask>")
        mb = (f'<mask id="b-{sid}" maskUnits="userSpaceOnUse" x="0" y="0" width="24" height="24">{rect}'
              + "".join(f'<path d="{dd(p)}" fill="#000" stroke="none"/>' for p in filled) + "</mask>")
        body = f'<g mask="url(#a-{sid})">{"".join(filled)}</g><g mask="url(#b-{sid})">{"".join(others)}</g>{"".join(plain)}'
        out.append(head + ma + mb + body + "</symbol>")
    return out


def skip_keys(syms, refine=False):
    """Keys of the dropped (data-skip) variants: concept/hand.idea/form, or R/name/form (R2/ for round 2)."""
    keys = []
    for sym in syms:
        if 'data-skip="true"' not in sym:
            continue
        sid = SID.search(sym).group(2)
        if refine:
            tag, _, rest = sid.partition("-")
            keys.append(("R" + tag[1:] if tag in ("r2", "r3") else "R") + "/" + rest.rsplit("-", 1)[0] + "/" + rest.rsplit("-", 1)[1])
        else:
            hand, _, rest = sid.partition("-")
            concept, idea, form = rest.rsplit("-", 2)
            keys.append(f"{concept}/{hand}.{idea}/{form}")
    return keys


def main():
    want = set(sys.argv[1:])
    squads = {c["squad"] for c in DATA["concepts"]}
    sprites, have, skips = [], {}, []
    for hand_dir in sorted((ROOT / "hands").iterdir()):
        if not hand_dir.is_dir():
            continue
        hand = hand_dir.name
        syms = []
        for f in sorted(hand_dir.glob("s*-??.svg")):
            if want and f.stem.rpartition("-")[0] not in want:
                continue
            for sym in SYMBOL.findall(f.read_text(encoding="utf-8")):
                m = SID.search(sym)
                if m:
                    have.setdefault(hand, set()).add(m.group(2))
                    syms.append(SID.sub(lambda m: f"{m.group(1)}{hand}-{m.group(2)}{m.group(3)}", sym, count=1))
        skips += skip_keys(syms)
        syms = carve_solids(skipped_as_regular(syms))
        if syms:
            sprites.append(f'<svg xmlns="http://www.w3.org/2000/svg" class="sprite" aria-hidden="true" focusable="false">{"".join(syms)}</svg>')
    concepts = []
    for c in DATA["concepts"]:
        if want and c["squad"] not in want:
            continue
        cands = [f"{h}.{i}" for h in sorted(have) for i in IDEAS
                 if all(f"{c['name']}-{i}-{v}" in have[h] for v in VARIANTS)]
        if cands:
            concepts.append({"n": c["name"], "m": c["meaning"], "s": c["squad"], "c": cands})
    refine, rsprites = [], []
    items_f = ROOT / "refine" / "items.json"
    if items_f.exists():
        items = json.loads(items_f.read_text(encoding="utf-8"))
        rsyms = []
        for f in sorted((ROOT / "refine").glob("*-??.svg")):
            for sym in SYMBOL.findall(f.read_text(encoding="utf-8")):
                m = SID.search(sym)
                if m:
                    rsyms.append(SID.sub(lambda m: f"{m.group(1)}r-{m.group(2)}{m.group(3)}", sym, count=1))
        skips += skip_keys(rsyms, refine=True)
        rsyms = carve_solids(skipped_as_regular(rsyms))
        ids = {SID.search(s).group(2) for s in rsyms}
        for it in items:
            if all(f"r-{it['name']}-{v}" in ids for v in VARIANTS) and any(c["n"] == it["concept"] for c in concepts):
                refine.append({"name": it["name"], "concept": it["concept"], "cand": f"{it['hand']}.{it['idea']}", "note": it["note"], "rc": "R", "key": it["name"]})
        for rnd in (2, 3):
            d = ROOT / f"refine{rnd}"
            if not (d / "items.json").exists():
                continue
            items_n = json.loads((d / "items.json").read_text(encoding="utf-8"))
            rn_syms = []
            for f in sorted(d.glob("*-??.svg")):
                for sym in SYMBOL.findall(f.read_text(encoding="utf-8")):
                    m = SID.search(sym)
                    if m:
                        rn_syms.append(SID.sub(lambda m: f"{m.group(1)}r{rnd}-{m.group(2)}{m.group(3)}", sym, count=1))
            skips += skip_keys(rn_syms, refine=True)
            rn_syms = carve_solids(skipped_as_regular(rn_syms))
            ids_n = {SID.search(s).group(2) for s in rn_syms}
            notes = {it["name"]: it["note"] for it in items_n if all(f"r{rnd}-{it['name']}-{v}" in ids_n for v in VARIANTS)}
            rsyms += rn_syms
            have_names = {e["name"] for e in refine}
            for it in items_n:
                if it["name"] in notes and it["name"] not in have_names and it.get("hand") and any(c["n"] == it["concept"] for c in concepts):
                    refine.append({"name": it["name"], "concept": it["concept"], "cand": f"{it['hand']}.{it['idea']}", "note": it["note"], "rc": "R", "key": it["name"]})
            for e in refine:
                if e["name"] in notes:
                    e.update({"rc": f"R{rnd}", "key": f"{e['name']}@{rnd}", "note": notes[e["name"]]})
        if rsyms:
            rsprites.append(f'<svg xmlns="http://www.w3.org/2000/svg" class="sprite" aria-hidden="true" focusable="false">{"".join(rsyms)}</svg>')
    reserved = sorted(r["name"] for r in json.loads((ROOT.parent.parent / "icons.json").read_text(encoding="utf-8"))["icons"])
    data = {"concepts": concepts, "reserved": reserved, "fillers": [{"n": n, "l": l} for n, l in FILLERS], "refine": refine, "skips": skips}
    page = (TEMPLATE.replace("<!--SPRITES-->", "\n".join(filler_symbols() and
            ['<svg xmlns="http://www.w3.org/2000/svg" class="sprite" aria-hidden="true" focusable="false">' + "".join(filler_symbols()) + "</svg>"] + sprites + rsprites), 1)
            .replace("/*__DATA__*/null", json.dumps(data, separators=(",", ":")), 1))
    out = ROOT / "gallery" / "review.html"
    out.parent.mkdir(exist_ok=True)
    out.write_text(page, encoding="utf-8")
    n = sum(len(c["c"]) for c in concepts)
    print(f"wrote {out} ({out.stat().st_size // 1024} KiB): {len(concepts)} concepts, {n} candidates")


TEMPLATE = open(ROOT / "review-template.html", encoding="utf-8").read()

if __name__ == "__main__":
    main()
