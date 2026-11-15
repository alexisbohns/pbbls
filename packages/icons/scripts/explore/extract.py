#!/usr/bin/env python3
"""Write the package sources from a wave's library: python3 -I extract.py <wave> [--only name,...] [--keep-dots name,...]

For every entry of <wave>/library.json (a list of entries with a "name", or an object keyed by
name), copies its drawing from <wave>/library.svg to src/<name>.svg under the canonical root.
The drawing is the <name> symbol, or <name>-regular when the library keeps forms
(<name>-regular, -solid, -marked). Path attributes are written in the canonical order
(d, fill, fill-rule, stroke, then anything else verbatim) and path data passes through
untouched; run `npm run format --workspace=@pbbls/icons` and `npm run lint` afterwards.
A symbol holding anything but <path> children is refused.

With forms, the roles are read off them (DNA.md, Roles and forms):
- body: the regular's paths the solid fills. A solid that fills a path the regular does not
  have (redrawn) gets no body: the name is listed under "needs a call".
- mark: the regular's paths the marked fills; a path only the marked has becomes a hidden mark
  (data-rest="hidden"), written unfilled. The package draws a lit mark filled and stroked, so a
  mark drawn filled with stroke="none" is shrunk by half a stroke to keep its drawn size, unless
  its name is in --keep-dots.
A skipped form (the entry's "skipped") gives no role.
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
NUMBER = re.compile(r"-?\d+(?:\.\d+)?")
HALF_STROKE = 0.875


def path_line(pairs: list[tuple[str, str]]) -> str:
    names = [k for k, _ in pairs]
    if "d" not in names:
        raise SystemExit("a <path> without d")
    ordered = [(k, v) for key in FIRST for k, v in pairs if k == key]
    ordered += [(k, v) for k, v in pairs if k not in FIRST]
    return "  <path " + " ".join(f'{k}="{v}"' for k, v in ordered) + "/>"


def paths_of(sid: str, body: str) -> list[list[tuple[str, str]]]:
    out = []
    for m in ELEMENT.finditer(body):
        tag = m.group(1)
        if tag == "path":
            out.append(ATTR.findall(m.group(2)))
        elif tag.startswith("/"):
            continue
        else:
            raise SystemExit(f"{sid}: refused, holds <{tag}> (paths only)")
    stray = ELEMENT.sub("", body).strip()
    if stray:
        raise SystemExit(f"{sid}: refused, stray content {stray[:40]!r}")
    if not out:
        raise SystemExit(f"{sid}: refused, no <path>")
    return out


def svg_file(paths: list[list[tuple[str, str]]]) -> str:
    return f"<svg {ROOT_ATTRS}>\n" + "\n".join(path_line(p) for p in paths) + "\n</svg>\n"


def shrink(d: str, name: str) -> str:
    """The dot `d` drawn half a stroke smaller all round, about the centre of its box."""
    if set(re.findall(r"[A-Za-z]", d)) - set("MLCZ"):
        raise SystemExit(f"{name}: cannot shrink a mark with commands other than M L C Z")
    nums = [float(n) for n in NUMBER.findall(d)]
    xs, ys = nums[0::2], nums[1::2]
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2
    r = (max(xs) - min(xs) + max(ys) - min(ys)) / 4
    if r <= HALF_STROKE:
        raise SystemExit(f"{name}: the mark is too small to shrink by half a stroke")
    k = (r - HALF_STROKE) / r
    i = iter(range(len(nums)))

    def scaled(m: re.Match[str]) -> str:
        c = cx if next(i) % 2 == 0 else cy
        return f"{round(c + (float(m.group(0)) - c) * k, 2):g}"

    return NUMBER.sub(scaled, d)


def with_roles(name: str, entry: dict, symbols: dict[str, str], keep_dots: set[str], calls: list[str]) -> list[list[tuple[str, str]]]:
    regular = paths_of(f"{name}-regular", symbols[f"{name}-regular"])
    skipped = set(entry.get("skipped", []))
    reg_d = [dict(p)["d"] for p in regular]
    roles: dict[int, list[tuple[str, str]]] = {}
    extra: list[list[tuple[str, str]]] = []
    if "solid" not in skipped and f"{name}-solid" in symbols:
        filled = [dict(p)["d"] for p in paths_of(f"{name}-solid", symbols[f"{name}-solid"]) if dict(p).get("fill")]
        if any(d not in reg_d for d in filled):
            calls.append(f"{name}: the solid is redrawn, no body written")
        else:
            for i, d in enumerate(reg_d):
                if d in filled:
                    roles[i] = [("data-role", "body")]
    if "marked" not in skipped and f"{name}-marked" in symbols:
        for p in paths_of(f"{name}-marked", symbols[f"{name}-marked"]):
            a = dict(p)
            if a["d"] in reg_d:
                if a.get("fill"):
                    i = reg_d.index(a["d"])
                    if i in roles:
                        # One stone of a filled row, lit: the body keeps it, a hidden copy is the mark.
                        extra.append([("d", a["d"]), ("data-role", "mark"), ("data-rest", "hidden")])
                    else:
                        roles[i] = [("data-role", "mark")]
                continue
            d = a["d"]
            if a.get("fill") and a.get("stroke") == "none" and name not in keep_dots:
                d = shrink(d, name)
            extra.append([("d", d), ("data-role", "mark"), ("data-rest", "hidden")])
    return [[*p, *roles.get(i, [])] for i, p in enumerate(regular)] + extra


def main() -> None:
    args = sys.argv[1:]
    opts: dict[str, set[str]] = {}
    for flag in ("--only", "--keep-dots"):
        if flag in args:
            i = args.index(flag)
            opts[flag] = set(args[i + 1].split(","))
            del args[i : i + 2]
    only = opts.get("--only")
    wave = Path(args[0]).resolve()
    src = wave.parent.parent / "src"
    src.mkdir(exist_ok=True)
    library = json.loads((wave / "library.json").read_text(encoding="utf-8"))
    entries = [{"name": k, **v} for k, v in library.items()] if isinstance(library, dict) else library
    symbols = {m.group(1): m.group(2) for m in SYMBOL.finditer((wave / "library.svg").read_text(encoding="utf-8"))}
    written, calls = 0, []
    for entry in entries:
        name = entry["name"]
        if only is not None and name not in only:
            continue
        if f"{name}-regular" in symbols:
            paths = with_roles(name, entry, symbols, opts.get("--keep-dots", set()), calls)
        elif name in symbols:
            paths = paths_of(name, symbols[name])
        else:
            raise SystemExit(f"{name}: no symbol {name} or {name}-regular in library.svg")
        (src / f"{name}.svg").write_text(svg_file(paths), encoding="utf-8")
        written += 1
    print(f"wrote {written} drawings to {src}")
    if calls:
        print("needs a call:")
        for c in calls:
            print("  " + c)


if __name__ == "__main__":
    main()
