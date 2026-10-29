#!/usr/bin/env python3
"""Write the package sources from a wave's library: python3 -I extract.py <wave> [--only name,...]

For every entry of <wave>/library.json, copies the <name> symbol of <wave>/library.svg to
src/<name>.svg, the <name>-active symbol to src/<name>.active.svg when hasActive, and each
<name>-lit-N symbol to src/<name>.lit-N.svg, under the canonical root. Path attributes are
written in the canonical order (d, fill, fill-rule, stroke, then anything else verbatim)
and path data passes through untouched; run `npm run format --workspace=@pbbls/icons` and
`npm run lint` afterwards. A symbol holding anything but <path> children is refused.
"""
import json
import re
import sys
from pathlib import Path

ROOT_ATTRS = ('xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" '
              'stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"')
FIRST = ("d", "fill", "fill-rule", "stroke")
SYMBOL = re.compile(r'<symbol\b[^>]*\bid="([^"]+)"[^>]*>(.*?)</symbol>', re.S)
ELEMENT = re.compile(r"<([a-zA-Z][\w:-]*)\b([^>]*?)/?>")
ATTR = re.compile(r'([\w:-]+)="([^"]*)"')


def path_line(attrs: str) -> str:
    pairs = ATTR.findall(attrs)
    names = [k for k, _ in pairs]
    if "d" not in names:
        raise SystemExit("a <path> without d")
    ordered = [(k, v) for key in FIRST for k, v in pairs if k == key]
    ordered += [(k, v) for k, v in pairs if k not in FIRST]
    return "  <path " + " ".join(f'{k}="{v}"' for k, v in ordered) + "/>"


def symbol_file(sid: str, body: str) -> str:
    lines = []
    for m in ELEMENT.finditer(body):
        tag = m.group(1)
        if tag == "path":
            lines.append(path_line(m.group(2)))
        elif tag.startswith("/"):
            continue
        else:
            raise SystemExit(f"{sid}: refused, holds <{tag}> (paths only)")
    stray = ELEMENT.sub("", body).strip()
    if stray:
        raise SystemExit(f"{sid}: refused, stray content {stray[:40]!r}")
    if not lines:
        raise SystemExit(f"{sid}: refused, no <path>")
    return f"<svg {ROOT_ATTRS}>\n" + "\n".join(lines) + "\n</svg>\n"


def main() -> None:
    args = sys.argv[1:]
    only: set[str] | None = None
    if "--only" in args:
        i = args.index("--only")
        only = set(args[i + 1].split(","))
        del args[i : i + 2]
    wave = Path(args[0]).resolve()
    src = wave.parent.parent / "src"
    src.mkdir(exist_ok=True)
    library = json.loads((wave / "library.json").read_text(encoding="utf-8"))
    symbols = {m.group(1): m.group(2) for m in SYMBOL.finditer((wave / "library.svg").read_text(encoding="utf-8"))}
    counts = {"base": 0, "active": 0, "lit": 0}
    for entry in library:
        name = entry["name"]
        if only is not None and name not in only:
            continue
        wanted = [(name, f"{name}.svg", "base")]
        if entry.get("hasActive"):
            wanted.append((f"{name}-active", f"{name}.active.svg", "active"))
        for lit in entry.get("lit", []):
            n = lit.rsplit("-lit-", 1)[1]
            wanted.append((lit, f"{name}.lit-{n}.svg", "lit"))
        for sid, filename, kind in wanted:
            if sid not in symbols:
                raise SystemExit(f"{name}: no symbol {sid} in library.svg")
            (src / filename).write_text(symbol_file(sid, symbols[sid]), encoding="utf-8")
            counts[kind] += 1
    print(f"wrote {counts['base']} bases, {counts['active']} active twins, {counts['lit']} lit twins to {src}")


if __name__ == "__main__":
    main()
