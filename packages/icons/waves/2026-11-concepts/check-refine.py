#!/usr/bin/env python3
"""Check a refinement batch: python3 -I check-refine.py <agent>-<batch>   e.g. opus-01

Every item of the batch needs <name>-regular, <name>-solid and <name>-marked in
refine/<agent>-<batch>.svg, and passes the same v2 rules as a first-round drawing.
"""
import json
import sys
import xml.dom.minidom as md
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))
import check  # noqa: E402
DIR = "refine2" if "--r2" in sys.argv else "refine"
sys.argv = [a for a in sys.argv if a != "--r2"]
ITEMS = json.loads((ROOT / DIR / "items.json").read_text(encoding="utf-8"))


def main():
    errs_all = []
    for b in sys.argv[1:]:
        agent, _, num = b.rpartition("-")
        f = ROOT / DIR / f"{b}.svg"
        errs = []
        if not f.exists():
            errs.append(f"{f.name}: missing")
        else:
            try:
                doc = md.parseString(f.read_text(encoding="utf-8"))
            except Exception as e:  # noqa: BLE001
                errs.append(f"{f.name}: does not parse: {e}")
                doc = None
            if doc:
                syms = {s.getAttribute("id"): s for s in doc.getElementsByTagName("symbol")}
                names = [i["name"] for i in ITEMS if i["agent"] == agent and i["batch"] == num]
                expected = {f"{n}-{v}" for n in names for v in check.VARIANTS}
                errs += [f"{f.name}: missing symbol {s}" for s in sorted(expected - set(syms))]
                errs += [f"{f.name}: unexpected symbol {s}" for s in sorted(set(syms) - expected)]
                got = {s: check.check_symbol(syms[s], s, s.rsplit("-", 1)[1], errs) for s in sorted(expected & set(syms))}
                for n in names:
                    reg, sol, mar = (got.get(f"{n}-{v}") for v in check.VARIANTS)
                    if reg is not None and sol is not None and mar is not None:
                        check.check_variants(n, reg, sol, mar, errs)
        print(f"{b}: {'ok' if not errs else str(len(errs)) + ' errors'}")
        errs_all += errs
    for e in errs_all[:80]:
        print("  " + e)
    sys.exit(1 if errs_all else 0)


if __name__ == "__main__":
    main()
