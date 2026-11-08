#!/usr/bin/env python3
"""Guess each glyph's roles from its old twins: python3 -I roles.py <package root>

For every base `src/<name>.svg` with twins, each filled twin path F (fill="currentColor")
is matched to the base:
- F's d equals a base path's d (whitespace-normalised): that path is the match;
- else the base path P with the smallest fill area whose implicit-closed polygon (curves
  sampled, an open path closed by a straight segment, as SVG fills) contains F's
  sample-point centroid is the container. If area(F) / area(P) >= FILL_RATIO the twin
  fills P and P is the match;
- else F is an added lit dot (a small filled shape the base does not draw): it is
  appended to the base file as a new path with only d, data-role, data-mark (when a
  mark) and data-rest="hidden", and flagged `added-dot`. An even-odd F is never
  appended: it is flagged `redrawn`;
- no container and no equal path: flagged `redrawn` (keep as an override or drop).
Roles from matches. The lit series first, lit-1..lit-N in order: what is newly filled
at step k (compared with step k-1, by match) becomes mark k. Then `.active`: a matched
P becomes `body` when its fill area is at least FOCUS_MAX square units, else `focus`;
an added dot becomes mark 1 (hidden) only when the glyph has no lit series, else it
is flagged `conflict` and left out. A path already a mark keeps it; `conflict` is also
flagged when `.active` wanted it as body or focus. Writes data-role/data-mark/data-rest
into the base files and waves/2026-11-forms/review.json. Run
`npm run format --workspace=@pbbls/icons` after.
"""
import json
import math
import re
import sys
from pathlib import Path

FOCUS_MAX = 24.0
FILL_RATIO = 0.5
PATH = re.compile(r"<path\b([^>]*?)/>")
ATTR = re.compile(r'([\w:-]+)="([^"]*)"')
TOKEN = re.compile(r"([MLCQAZ])|(-?(?:\d+\.?\d*|\.\d+))")


def paths(text):
    return [dict(ATTR.findall(m.group(1))) for m in PATH.finditer(text)]


def norm(d):
    return " ".join(d.split())


def arc_points(p0, rx, ry, phi, fa, fs, p1, steps=12):
    """SVG elliptical arc from p0 to p1, sampled (the endpoint-to-centre conversion)."""
    (x1, y1), (x2, y2) = p0, p1
    if (x1, y1) == (x2, y2) or rx == 0 or ry == 0:
        return [p1]
    rx, ry = abs(rx), abs(ry)
    cp, sp = math.cos(math.radians(phi)), math.sin(math.radians(phi))
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p, y1p = cp * dx + sp * dy, -sp * dx + cp * dy
    lam = x1p**2 / rx**2 + y1p**2 / ry**2
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx**2 * ry**2 - rx**2 * y1p**2 - ry**2 * x1p**2
    den = rx**2 * y1p**2 + ry**2 * x1p**2
    co = math.sqrt(max(0.0, num / den)) * (-1 if fa == fs else 1)
    cxp, cyp = co * rx * y1p / ry, -co * ry * x1p / rx
    cx, cy = cp * cxp - sp * cyp + (x1 + x2) / 2, sp * cxp + cp * cyp + (y1 + y2) / 2

    def ang(ux, uy, vx, vy):
        a = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return a

    t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not fs and dt > 0:
        dt -= 2 * math.pi
    elif fs and dt < 0:
        dt += 2 * math.pi
    out = []
    for k in range(1, steps + 1):
        t = t1 + dt * k / steps
        out.append((cp * rx * math.cos(t) - sp * ry * math.sin(t) + cx, sp * rx * math.cos(t) + cp * ry * math.sin(t) + cy))
    out[-1] = p1
    return out


def rings(d):
    """The path's subpaths as point rings, curves sampled; each is implicitly closed."""
    out, ring, cur, start, cmd, nums = [], [], (0.0, 0.0), (0.0, 0.0), None, []

    def end_ring():
        nonlocal ring
        if len(ring) >= 3:
            out.append(ring)
        ring = []

    def flush():
        nonlocal cur, start, ring
        if cmd == "M":
            for i in range(0, len(nums) - 1, 2):
                cur = (nums[i], nums[i + 1])
                if i == 0:
                    end_ring()
                    start = cur
                ring.append(cur)
        elif cmd == "L":
            for i in range(0, len(nums) - 1, 2):
                cur = (nums[i], nums[i + 1])
                ring.append(cur)
        elif cmd == "C":
            for i in range(0, len(nums) - 5, 6):
                p0, p1, p2, p3 = cur, (nums[i], nums[i + 1]), (nums[i + 2], nums[i + 3]), (nums[i + 4], nums[i + 5])
                for k in range(1, 11):
                    t = k / 10
                    u = 1 - t
                    ring.append(tuple(u**3 * a + 3 * u * u * t * b + 3 * u * t * t * c + t**3 * e for a, b, c, e in zip(p0, p1, p2, p3)))
                cur = p3
        elif cmd == "Q":
            for i in range(0, len(nums) - 3, 4):
                p0, p1, p2 = cur, (nums[i], nums[i + 1]), (nums[i + 2], nums[i + 3])
                for k in range(1, 11):
                    t = k / 10
                    u = 1 - t
                    ring.append(tuple(u * u * a + 2 * u * t * b + t * t * c for a, b, c in zip(p0, p1, p2)))
                cur = p2
        elif cmd == "A":
            for i in range(0, len(nums) - 6, 7):
                end = (nums[i + 5], nums[i + 6])
                ring.extend(arc_points(cur, nums[i], nums[i + 1], nums[i + 2], int(nums[i + 3]), int(nums[i + 4]), end))
                cur = end
        elif cmd == "Z":
            cur = start

    for m in TOKEN.finditer(d):
        if m.group(1):
            flush()
            cmd, nums = m.group(1), []
            if cmd == "Z":
                flush()
                end_ring()
                cmd = None
        else:
            nums.append(float(m.group(2)))
    flush()
    end_ring()
    return out


def signed(ring):
    return sum(a[0] * b[1] - b[0] * a[1] for a, b in zip(ring, ring[1:] + ring[:1])) / 2


def winding(pt, ring):
    x, y = pt
    w = 0
    for a, b in zip(ring, ring[1:] + ring[:1]):
        if a[1] <= y:
            if b[1] > y and (b[0] - a[0]) * (y - a[1]) - (x - a[0]) * (b[1] - a[1]) > 0:
                w += 1
        elif b[1] <= y and (b[0] - a[0]) * (y - a[1]) - (x - a[0]) * (b[1] - a[1]) < 0:
            w -= 1
    return w


def fill_area(rs):
    """Area of the rings filled even-odd: nested rings subtract."""
    total = 0.0
    for i, r in enumerate(rs):
        depth = sum(1 for j, o in enumerate(rs) if j != i and winding(r[0], o) != 0)
        total += abs(signed(r)) * (1 if depth % 2 == 0 else -1)
    return max(total, 0.0)


def contains(rs, pt):
    return sum(winding(pt, r) for r in rs) != 0


def centroid(rs):
    pts = [p for r in rs for p in r]
    return (sum(p[0] for p in pts) / len(pts), sum(p[1] for p in pts) / len(pts))


def filled(ps):
    return [p for p in ps if p.get("fill") == "currentColor"]


class Glyph:
    """One base glyph: its paths, their fill geometry, and the paths appended to it."""

    def __init__(self, text):
        self.text = text
        self.base = paths(text)
        self.geo = [rings(p["d"]) for p in self.base]
        self.areas = [fill_area(g) for g in self.geo]
        self.index = {norm(p["d"]): i for i, p in enumerate(self.base)}
        self.added = []  # appended dots: {"d": d}

    def resolve(self, f):
        """('base', i) | ('dot', d) | ('redrawn', None) for one filled twin path."""
        d = norm(f["d"])
        if d in self.index:
            return ("base", self.index[d])
        for k, a in enumerate(self.added):
            if norm(a["d"]) == d:
                return ("dot", len(self.base) + k)
        fr = rings(f["d"])
        if fr:
            c = centroid(fr)
            fa = fill_area(fr)
            best = None
            for i, g in enumerate(self.geo):
                if self.areas[i] > 0 and contains(g, c) and (best is None or self.areas[i] < self.areas[best]):
                    best = i
            if best is not None and fa / self.areas[best] >= FILL_RATIO:
                return ("base", best)
            if best is None:
                return ("redrawn", None)
            if f.get("fill-rule") == "evenodd":
                return ("redrawn", None)
            return ("newdot", fa)
        return ("redrawn", None)


def main():
    root = Path(sys.argv[1]).resolve()
    src = root / "src"
    manifest = json.loads((root / "icons.json").read_text(encoding="utf-8"))
    review = []
    for row in manifest["icons"]:
        name = row["name"]
        base_file = src / f"{name}.svg"
        text = base_file.read_text(encoding="utf-8")
        g = Glyph(text)
        roles = {}  # path index -> [role, mark | None, hidden]
        flags = []

        def target(f, variant):
            """Resolve F, appending an added dot to the glyph when it is one."""
            kind, v = g.resolve(f)
            if kind in ("base", "dot"):
                return v
            if kind == "redrawn":
                flags.append({"kind": "redrawn", "from": variant, "area": round(fill_area(rings(f["d"])), 1)})
                return None
            return ("new", f, v)

        def add_dot(f, variant, mark):
            g.added.append({"d": f["d"]})
            i = len(g.base) + len(g.added) - 1
            roles[i] = ["mark", mark, True]
            flags.append({"kind": "added-dot", "from": variant, "path": i + 1, "area": round(fill_area(rings(f["d"])), 1)})
            return i

        lit = sorted((v for v in row["variants"] if v.startswith("lit-")), key=lambda v: int(v[4:]))
        before = set()
        for v in lit:
            n = int(v[4:])
            now = set()
            for f in filled(paths((src / f"{name}.{v}.svg").read_text(encoding="utf-8"))):
                t = target(f, v)
                if t is None:
                    continue
                if isinstance(t, tuple):
                    if f.get("fill-rule") == "evenodd":
                        flags.append({"kind": "redrawn", "from": v, "area": round(t[2], 1)})
                        continue
                    t = add_dot(f, v, n)
                    now.add(t)
                    continue
                now.add(t)
                if t not in before and t not in roles:
                    roles[t] = ["mark", n, False]
            before = now

        if "active" in row["variants"]:
            for f in filled(paths((src / f"{name}.active.svg").read_text(encoding="utf-8"))):
                t = target(f, "active")
                if t is None:
                    continue
                if isinstance(t, tuple):
                    if f.get("fill-rule") == "evenodd":
                        flags.append({"kind": "redrawn", "from": "active", "area": round(t[2], 1)})
                    elif lit:
                        flags.append({"kind": "conflict", "from": "active", "was": "added-dot", "area": round(t[2], 1)})
                    else:
                        add_dot(f, "active", 1)
                    continue
                wanted = "body" if g.areas[t] >= FOCUS_MAX else "focus" if t < len(g.base) else None
                if t in roles:
                    if roles[t][0] == "mark" and wanted:
                        flags.append({"kind": "conflict", "path": t + 1, "was": f"mark {roles[t][1]}", "wanted": wanted})
                    continue
                if t >= len(g.base):
                    continue
                roles[t] = [wanted, None, False]

        if roles:
            count = [0]

            def tag(m):
                i = count[0]
                count[0] += 1
                if i not in roles:
                    return m.group(0)
                role, mark, hidden = roles[i]
                extra = f' data-role="{role}"' + (f' data-mark="{mark}"' if mark else "") + (' data-rest="hidden"' if hidden else "")
                return f"<path{m.group(1)}{extra}/>"

            out = PATH.sub(tag, text)
            appended = ""
            for k, a in enumerate(g.added):
                role, mark, hidden = roles[len(g.base) + k]
                appended += f'  <path d="{a["d"]}" data-role="{role}" data-mark="{mark}" data-rest="hidden"/>\n'
            if appended:
                out = out.replace("</svg>", appended + "</svg>", 1)
            base_file.write_text(out, encoding="utf-8")
        review.append({
            "name": name,
            "wave": row["wave"],
            "roles": {str(i + 1): (r if m is None else f"{r} {m}") + (" hidden" if h else "") for i, (r, m, h) in sorted(roles.items())},
            "flags": flags,
        })
    out = root / "waves" / "2026-11-forms" / "review.json"
    out.write_text(json.dumps(review, indent=1) + "\n", encoding="utf-8")
    guessed = sum(1 for r in review if r["roles"])
    flagged = sum(1 for r in review if r["flags"])
    print(f"{len(review)} glyphs, {guessed} with roles, {flagged} flagged")


main()
