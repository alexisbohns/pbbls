#!/usr/bin/env python3
"""Build library.json + library.svg from the maintainer's names: python3 -I library-build.py

Reads results/names-2026-11-08.json ({"<fig>/<hand>.vN": name}); a name of "tbr" (any case)
drops the drawing. Each kept drawing becomes <name> and <name>-active in library.svg, the
format extract.py reads. Refuses invalid, duplicate or already-shipped names.
"""
import json
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE.parent.parent / "src"
KEBAB = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
SYMBOL = re.compile(r'<symbol\b[^>]*\bid="([^"]+)"[^>]*>(.*?)</symbol>', re.S)
ROOT = ('viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" '
        'stroke-linecap="round" stroke-linejoin="round"')


def main() -> None:
    names = json.loads((HERE / "results/names-2026-11-08.json").read_text(encoding="utf-8"))["names"]
    figures = {f["name"]: f for f in json.loads((HERE / "figures.json").read_text(encoding="utf-8"))}
    shipped = {p.name[:-4] for p in SRC.glob("*.svg") if p.name.count(".") == 1}
    symbols: dict[str, dict[str, str]] = {}
    for hand_dir in sorted((HERE / "hands").iterdir()):
        if hand_dir.is_dir():
            sym = symbols.setdefault(hand_dir.name, {})
            for batch in sorted(hand_dir.glob("batch-*.svg")):
                for m in SYMBOL.finditer(batch.read_text(encoding="utf-8")):
                    sym[m.group(1)] = m.group(2)
    library, out, seen, dropped = [], [], set(), 0
    for key in sorted(names, key=lambda k: (list(figures).index(k.split("/")[0]), k)):
        name = names[key].strip()
        if name.lower() == "tbr":
            dropped += 1
            continue
        fig, cand = key.split("/")
        hand, v = cand.split(".")
        if not KEBAB.match(name) or name in seen or name in shipped:
            raise SystemExit(f"{key}: refused name {name!r}")
        seen.add(name)
        base, active = symbols[hand][f"{fig}-{v}"], symbols[hand][f"{fig}-{v}-active"]
        out.append(f'<symbol id="{name}" {ROOT}>{base.strip()}</symbol>')
        out.append(f'<symbol id="{name}-active" {ROOT}>{active.strip()}</symbol>')
        library.append({"name": name, "hand": hand, "source": f"{fig}-{v}", "figure": fig,
                        "meaning": figures[fig]["meaning"], "hasActive": True})
    (HERE / "library.json").write_text(json.dumps(library, indent=1) + "\n", encoding="utf-8")
    (HERE / "library.svg").write_text('<svg xmlns="http://www.w3.org/2000/svg">\n' + "\n".join(out) + "\n</svg>\n",
                                      encoding="utf-8")
    print(f"library: {len(library)} drawings kept, {dropped} dropped (tbr)")


main()
