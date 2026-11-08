#!/usr/bin/env python3
"""Guess each glyph's roles from its old twins: python3 -I roles.py <package root>

For every base `src/<name>.svg` with twins:
- a path filled in `<name>.active.svg` whose d equals a base path: `body` if its
  area is at least FOCUS_MAX square units, else `focus`;
- the path newly filled in `<name>.lit-N.svg` (compared with lit-(N-1)) whose d
  equals a base path: `mark` N;
- a filled twin path with no equal base path: flagged `redrawn` (keep as an
  override or drop);
- an `.active` whose filled area is under DUPLICATE_MAX: flagged `near-duplicate`.
A path guessed both as a mark and as body/focus keeps the mark and is flagged
`conflict`. Writes data-role/data-mark into the base files and
waves/2026-11-forms/review.json. Run `npm run format --workspace=@pbbls/icons` after.
"""
import json
import re
import sys
from pathlib import Path

FOCUS_MAX = 24.0
DUPLICATE_MAX = 3.0
PATH = re.compile(r"<path\b([^>]*?)/>")
ATTR = re.compile(r'([\w:-]+)="([^"]*)"')
TOKEN = re.compile(r"([MLCQAZ])|(-?(?:\d+\.?\d*|\.\d+))")


def paths(text):
    return [dict(ATTR.findall(m.group(1))) for m in PATH.finditer(text)]


def norm(d):
    return " ".join(d.split())


def area(d):
    """Shoelace over the path's points, curves sampled at 8 steps: a rough filled area."""
    pts, cur, cmd, nums = [], (0.0, 0.0), None, []

    def flush():
        nonlocal cur
        if cmd in ("M", "L"):
            for i in range(0, len(nums) - 1, 2):
                cur = (nums[i], nums[i + 1])
                pts.append(cur)
        elif cmd == "C":
            for i in range(0, len(nums) - 5, 6):
                p0, p1, p2, p3 = cur, (nums[i], nums[i + 1]), (nums[i + 2], nums[i + 3]), (nums[i + 4], nums[i + 5])
                for k in range(1, 9):
                    t = k / 8
                    u = 1 - t
                    pts.append(tuple(u**3 * a + 3 * u * u * t * b + 3 * u * t * t * c + t**3 * e for a, b, c, e in zip(p0, p1, p2, p3)))
                cur = p3
        elif cmd == "Q":
            for i in range(0, len(nums) - 3, 4):
                cur = (nums[i + 2], nums[i + 3])
                pts.append(cur)
        elif cmd == "A":
            for i in range(0, len(nums) - 6, 7):
                cur = (nums[i + 5], nums[i + 6])
                pts.append(cur)

    for m in TOKEN.finditer(d):
        if m.group(1):
            flush()
            cmd, nums = m.group(1), []
        else:
            nums.append(float(m.group(2)))
    flush()
    s = sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(pts, pts[1:] + pts[:1]))
    return abs(s) / 2


def filled(ps):
    return [p for p in ps if p.get("fill") == "currentColor"]


def main():
    root = Path(sys.argv[1]).resolve()
    src = root / "src"
    manifest = json.loads((root / "icons.json").read_text(encoding="utf-8"))
    review = []
    for row in manifest["icons"]:
        name = row["name"]
        base_file = src / f"{name}.svg"
        text = base_file.read_text(encoding="utf-8")
        base = paths(text)
        index = {norm(p["d"]): i for i, p in enumerate(base)}
        roles, flags = {}, []
        if "active" in row["variants"]:
            twin = filled(paths((src / f"{name}.active.svg").read_text(encoding="utf-8")))
            total = sum(area(p["d"]) for p in twin)
            if total < DUPLICATE_MAX:
                flags.append({"kind": "near-duplicate", "area": round(total, 1)})
            for p in twin:
                i = index.get(norm(p["d"]))
                if i is None:
                    flags.append({"kind": "redrawn", "from": "active", "area": round(area(p["d"]), 1)})
                else:
                    roles[i] = ("body" if area(p["d"]) >= FOCUS_MAX else "focus", None)
        lit = sorted((v for v in row["variants"] if v.startswith("lit-")), key=lambda v: int(v[4:]))
        before = set()
        for v in lit:
            n = int(v[4:])
            now = {norm(p["d"]) for p in filled(paths((src / f"{name}.{v}.svg").read_text(encoding="utf-8")))}
            for d in now - before:
                i = index.get(d)
                if i is None:
                    flags.append({"kind": "redrawn", "from": v})
                    continue
                if i in roles and roles[i][0] != "mark":
                    flags.append({"kind": "conflict", "path": i + 1, "was": roles[i][0]})
                roles[i] = ("mark", n)
            before = now
        if roles:
            def tag(m):
                attrs = m.group(1)
                d = dict(ATTR.findall(attrs)).get("d", "")
                i = index.get(norm(d))
                if i is None or i not in roles:
                    return m.group(0)
                role, mark = roles[i]
                extra = f' data-role="{role}"' + (f' data-mark="{mark}"' if mark else "")
                return f"<path{attrs}{extra}/>"
            base_file.write_text(PATH.sub(tag, text), encoding="utf-8")
        review.append({
            "name": name,
            "wave": row["wave"],
            "roles": {str(i + 1): (r if m is None else f"{r} {m}") for i, (r, m) in sorted(roles.items())},
            "flags": flags,
        })
    out = root / "waves" / "2026-11-forms" / "review.json"
    out.write_text(json.dumps(review, indent=1) + "\n", encoding="utf-8")
    guessed = sum(1 for r in review if r["roles"])
    flagged = sum(1 for r in review if r["flags"])
    print(f"{len(review)} glyphs, {guessed} with roles, {flagged} flagged")


main()
