"""Redraw every stroke-dasharray as short separate strokes (DNA: no dashes).

    python3 -I undash.py            # rewrites library.svg and ../../src/<name>.svg in place

A dash pattern "a b" starts with a dash of length a at the path's start, then a gap of b,
and so on (SVG's default phase). Each dash becomes one subpath on the same curve: a dot
(a <= 0.1) is a 0.1-long `M L` the pen touches, a longer dash is one cubic fitted through
the sampled arc (Hermite ends, so it follows the ring). Coordinates keep two decimals.
"""
import re
from pathlib import Path

HERE = Path(__file__).resolve().parent
SRC = HERE.parent.parent / "src"
NUM = r"-?\d*\.?\d+"


def sample(d, n=64):
    """Dense polyline of the path (absolute M L C Q only), as (x, y, cumulative length)."""
    toks = re.findall(rf"[MLCQZ]|{NUM}", d.replace(",", " "))
    pts, cur, i = [], None, 0
    nums = lambda k: [float(t) for t in toks[i + 1 : i + 1 + k]]
    while i < len(toks):
        c = toks[i]
        if c == "M":
            cur = tuple(nums(2)); pts.append(cur); i += 3
        elif c == "L":
            p = tuple(nums(2)); pts.append(p); cur = p; i += 3
        elif c == "C":
            x1, y1, x2, y2, x3, y3 = nums(6)
            for k in range(1, n + 1):
                t = k / n; u = 1 - t
                pts.append((u*u*u*cur[0] + 3*u*u*t*x1 + 3*u*t*t*x2 + t*t*t*x3,
                            u*u*u*cur[1] + 3*u*u*t*y1 + 3*u*t*t*y2 + t*t*t*y3))
            cur = (x3, y3); i += 7
        elif c == "Q":
            x1, y1, x2, y2 = nums(4)
            for k in range(1, n + 1):
                t = k / n; u = 1 - t
                pts.append((u*u*cur[0] + 2*u*t*x1 + t*t*x2, u*u*cur[1] + 2*u*t*y1 + t*t*y2))
            cur = (x2, y2); i += 5
        else:
            raise SystemExit(f"unsupported command {c}")
    out, s = [], 0.0
    for j, p in enumerate(pts):
        if j:
            q = pts[j - 1]; s += ((p[0] - q[0]) ** 2 + (p[1] - q[1]) ** 2) ** 0.5
        out.append((p[0], p[1], s))
    return out


def at(poly, s):
    """Point and unit tangent at arc length s."""
    for j in range(1, len(poly)):
        if poly[j][2] >= s:
            a, b = poly[j - 1], poly[j]
            span = b[2] - a[2] or 1e-9
            t = (s - a[2]) / span
            tx, ty = b[0] - a[0], b[1] - a[1]
            norm = (tx * tx + ty * ty) ** 0.5 or 1e-9
            return (a[0] + t * tx, a[1] + t * ty), (tx / norm, ty / norm)
    a, b = poly[-2], poly[-1]
    tx, ty = b[0] - a[0], b[1] - a[1]
    norm = (tx * tx + ty * ty) ** 0.5 or 1e-9
    return (b[0], b[1]), (tx / norm, ty / norm)


f2 = lambda v: f"{v:.2f}".rstrip("0").rstrip(".")


def undash(d, pattern):
    dash, gap = (float(x) for x in pattern.split())
    poly = sample(d)
    total = poly[-1][2]
    parts, s = [], 0.0
    while s < total - 0.05:
        end = min(s + dash, total)
        (x0, y0), (tx0, ty0) = at(poly, s)
        if dash <= 0.1:
            parts.append(f"M{f2(x0)} {f2(y0)} L{f2(x0 + 0.1 * tx0)} {f2(y0 + 0.1 * ty0)}")
        else:
            (x1, y1), (tx1, ty1) = at(poly, end)
            k = (end - s) / 3
            parts.append(f"M{f2(x0)} {f2(y0)} C{f2(x0 + k * tx0)} {f2(y0 + k * ty0)} "
                         f"{f2(x1 - k * tx1)} {f2(y1 - k * ty1)} {f2(x1)} {f2(y1)}")
        s += dash + gap
    return " ".join(parts), len(parts)


PATH = re.compile(r'<path\b([^>]*?)/>')


def rewrite(text, label):
    def repl(m):
        attrs = m.group(1)
        dm = re.search(r'stroke-dasharray="([^"]+)"', attrs)
        if not dm:
            return m.group(0)
        d = re.search(r' d="([^"]+)"', attrs).group(1)
        new_d, n = undash(d, dm.group(1))
        print(f"{label}: {dm.group(1)!r} -> {n} strokes")
        attrs = re.sub(r'\s*stroke-dasharray="[^"]+"', "", attrs)
        attrs = attrs.replace(f'd="{d}"', f'd="{new_d}"')
        return f"<path{attrs}/>"
    return PATH.sub(repl, text)


lib = HERE / "library.svg"
text = lib.read_text(encoding="utf-8")
out = []
for m in re.finditer(r'<symbol id="([^"]+)"[^>]*>(.*?)</symbol>', text, re.S):
    if "stroke-dasharray" in m.group(2):
        out.append((m.group(1), rewrite(m.group(2), m.group(1))))
for sid, body in out:
    text = re.sub(r'(<symbol id="%s"[^>]*>).*?(</symbol>)' % re.escape(sid), lambda m: m.group(1) + body + m.group(2), text, count=1, flags=re.S)
lib.write_text(text, encoding="utf-8")
for sid, _ in out:
    f = SRC / f"{sid}.svg"
    if f.exists():
        f.write_text(rewrite(f.read_text(encoding="utf-8"), f.name), encoding="utf-8")
