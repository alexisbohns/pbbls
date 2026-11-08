#!/usr/bin/env python3
"""Check a hand's batch sprites against the wave brief.

Usage: python3 -I check.py <hand> [<batch> ...]   e.g. python3 -I check.py h31 01
With no batch, checks every batch file present. Exit 1 on any error.
"""
import json
import re
import sys
import xml.dom.minidom as md
from pathlib import Path

ROOT = Path(__file__).resolve().parent
FIGS = json.loads((ROOT / "figures.json").read_text(encoding="utf-8"))
ROOT_ATTRS = {"viewBox": "0 0 24 24", "fill": "none", "stroke": "currentColor", "stroke-width": "1.75",
              "stroke-linecap": "round", "stroke-linejoin": "round"}
TOKEN = re.compile(r"[A-Za-z]|-?(?:\d+\.?\d*|\.\d+)(?:e-?\d+)?")
ARGS = {"M": 2, "L": 2, "C": 6, "Q": 4, "A": 7, "Z": 0}


def check_d(d, where, errs):
    toks = TOKEN.findall(d)
    i, cmd = 0, None
    while i < len(toks):
        t = toks[i]
        if t.isalpha():
            if t not in ARGS:
                errs.append(f"{where}: command {t} not allowed (absolute M L C Q A Z only)")
                return
            cmd = t
            i += 1
            if cmd == "Z":
                continue
        if cmd is None:
            errs.append(f"{where}: path starts without a command")
            return
        n = ARGS[cmd]
        nums = toks[i:i + n]
        if len(nums) < n or any(x.isalpha() for x in nums):
            errs.append(f"{where}: {cmd} has the wrong number of arguments")
            return
        vals = [float(x) for x in nums]
        pts = vals[5:7] if cmd == "A" else vals
        for v in pts:
            if not 1.5 <= v <= 22.5:
                errs.append(f"{where}: coordinate {v} outside 1.5..22.5")
        i += n


def check_symbol(sym, sid, active, errs):
    for k, v in ROOT_ATTRS.items():
        if sym.getAttribute(k) != v:
            errs.append(f"{sid}: symbol {k} must be {v!r}")
    extra = set(sym.attributes.keys()) - set(ROOT_ATTRS) - {"id"}
    if extra:
        errs.append(f"{sid}: extra symbol attributes {sorted(extra)}")
    paths = 0
    filled = 0
    for ch in sym.childNodes:
        if ch.nodeType == ch.TEXT_NODE and not ch.data.strip():
            continue
        if ch.nodeType != ch.ELEMENT_NODE or ch.tagName != "path":
            errs.append(f"{sid}: only <path> children allowed")
            continue
        paths += 1
        attrs = set(ch.attributes.keys())
        bad = attrs - {"d", "fill", "fill-rule", "stroke"}
        if bad:
            errs.append(f"{sid}: path attributes {sorted(bad)} not allowed")
        fill = ch.getAttribute("fill")
        if fill:
            if fill != "currentColor":
                errs.append(f"{sid}: fill must be currentColor")
            elif not active:
                errs.append(f"{sid}: a base drawing is never filled")
            filled += 1
        if ch.getAttribute("fill-rule") and (not fill or ch.getAttribute("fill-rule") != "evenodd"):
            errs.append(f"{sid}: fill-rule only as evenodd on a filled path")
        if ch.getAttribute("stroke") and (ch.getAttribute("stroke") != "none" or not fill):
            errs.append(f"{sid}: stroke=\"none\" only on a filled path")
        check_d(ch.getAttribute("d"), sid, errs)
    if paths == 0:
        errs.append(f"{sid}: no paths")
    if active and filled == 0:
        errs.append(f"{sid}: the active twin fills nothing")
    if filled > 3:
        errs.append(f"{sid}: more than three filled paths")


def check_batch(hand, batch):
    errs = []
    f = ROOT / "hands" / hand / f"batch-{batch}.svg"
    if not f.exists():
        return [f"{f.name}: missing"]
    text = f.read_text(encoding="utf-8")
    if "dasharray" in text:
        errs.append(f"{f.name}: stroke-dasharray is not allowed")
    try:
        doc = md.parseString(text)
    except Exception as e:  # noqa: BLE001
        return [f"{f.name}: does not parse: {e}"]
    syms = {s.getAttribute("id"): s for s in doc.getElementsByTagName("symbol")}
    names = [r["name"] for r in FIGS if r["batch"] == batch]
    expected = {f"{n}-v{v}{a}" for n in names for v in range(1, 6) for a in ("", "-active")}
    for sid in sorted(expected - set(syms)):
        errs.append(f"{f.name}: missing symbol {sid}")
    for sid in sorted(set(syms) - expected):
        errs.append(f"{f.name}: unexpected symbol {sid}")
    for sid in sorted(expected & set(syms)):
        check_symbol(syms[sid], sid, sid.endswith("-active"), errs)
    return errs


def main():
    hand = sys.argv[1]
    batches = sys.argv[2:] or sorted({r["batch"] for r in FIGS
                                      if (ROOT / "hands" / hand / f"batch-{r['batch']}.svg").exists()})
    total = []
    for b in batches:
        e = check_batch(hand, b)
        print(f"{hand} batch-{b}: {'ok' if not e else str(len(e)) + ' errors'}")
        total += e
    for e in total[:80]:
        print("  " + e)
    sys.exit(1 if total else 0)


if __name__ == "__main__":
    main()
