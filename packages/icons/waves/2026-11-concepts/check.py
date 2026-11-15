#!/usr/bin/env python3
"""Check a hand's batch sprites against the wave brief.

Usage: python3 -I check.py <hand> [<squad>-<batch> ...]   e.g. python3 -I check.py h41 s1-delivery-01
With no batch, checks every batch file present for that hand. Exit 1 on any error.
"""
import json
import math
import re
import sys
import xml.dom.minidom as md
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DATA = json.loads((ROOT / "concepts.json").read_text(encoding="utf-8"))
CONCEPTS = DATA["concepts"]
IDEAS = ("classical", "bold", "pebbles", "weird")
VARIANTS = ("regular", "solid", "marked")
ROOT_ATTRS = {"viewBox": "0 0 24 24", "fill": "none", "stroke": "currentColor", "stroke-width": "1.75",
              "stroke-linecap": "round", "stroke-linejoin": "round"}
TOKEN = re.compile(r"[A-Za-z]|-?(?:\d+\.?\d*|\.\d+)(?:e-?\d+)?")
WARNINGS = []
ARGS = {"M": 2, "L": 2, "C": 6, "Q": 4, "A": 7, "Z": 0}


def points(d, where, errs):
    """Validate the grammar and range of d; return the list of (x, y) it names."""
    toks = TOKEN.findall(d)
    i, cmd, pts = 0, None, []
    while i < len(toks):
        t = toks[i]
        if t.isalpha():
            if t not in ARGS:
                errs.append(f"{where}: command {t} not allowed (absolute M L C Q A Z only)")
                return pts
            cmd = t
            i += 1
            if cmd == "Z":
                continue
        if cmd is None:
            errs.append(f"{where}: path starts without a command")
            return pts
        n = ARGS[cmd]
        nums = toks[i:i + n]
        if len(nums) < n or any(x.isalpha() for x in nums):
            errs.append(f"{where}: {cmd} has the wrong number of arguments")
            return pts
        vals = [float(x) for x in nums]
        xy = vals[5:7] if cmd == "A" else vals
        for v in xy:
            if not 1.5 <= v <= 22.5:
                errs.append(f"{where}: coordinate {v} outside 1.5..22.5")
        for k in range(0, len(xy) - 1, 2):
            pts.append((xy[k], xy[k + 1]))
        i += n
    return pts


def arc_pts(p0, a, p1, n=16):
    rx, ry, rot, fa, fs = a
    x1, y1 = p0
    x2, y2 = p1
    if rx == 0 or ry == 0:
        return [p0, p1]
    phi = math.radians(rot)
    c, s = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p, y1p = c * dx + s * dy, -s * dx + c * dy
    lam = x1p ** 2 / rx ** 2 + y1p ** 2 / ry ** 2
    if lam > 1:
        rx *= math.sqrt(lam)
        ry *= math.sqrt(lam)
    num = rx * rx * ry * ry - rx * rx * y1p ** 2 - ry * ry * x1p ** 2
    den = rx * rx * y1p ** 2 + ry * ry * x1p ** 2
    co = math.sqrt(max(0, num / den)) if den else 0
    if fa == fs:
        co = -co
    cxp, cyp = co * rx * y1p / ry, -co * ry * x1p / rx
    cx, cy = c * cxp - s * cyp + (x1 + x2) / 2, s * cxp + c * cyp + (y1 + y2) / 2
    ang = lambda ux, uy, vx, vy: math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
    th1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dth = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not fs and dth > 0:
        dth -= 2 * math.pi
    if fs and dth < 0:
        dth += 2 * math.pi
    return [(c * rx * math.cos(th1 + dth * k / n) - s * ry * math.sin(th1 + dth * k / n) + cx,
             s * rx * math.cos(th1 + dth * k / n) + c * ry * math.sin(th1 + dth * k / n) + cy) for k in range(n + 1)]


def outline(d):
    """Sample a path into (points, nsegments, has_arc, has_z)."""
    toks = TOKEN.findall(d)
    i, cmd, cur, start, pts, nseg, arc = 0, None, (0.0, 0.0), (0.0, 0.0), [], 0, False
    while i < len(toks):
        if toks[i].isalpha():
            cmd = toks[i]
            i += 1
        if cmd == "Z":
            pts.append(start)
            cur = start
            nseg += 1
            continue
        n = ARGS.get(cmd, 0)
        v = [float(x) for x in toks[i:i + n]]
        i += n
        if len(v) < n:
            break
        if cmd == "M":
            cur = start = (v[0], v[1])
            pts.append(cur)
            cmd = "L"
            continue
        nseg += 1
        if cmd == "L":
            p = (v[0], v[1])
            pts += [cur, p]
        elif cmd == "C":
            p = (v[4], v[5])
            for k in range(13):
                u = k / 12
                a, b, c2, e = (1 - u) ** 3, 3 * u * (1 - u) ** 2, 3 * u * u * (1 - u), u ** 3
                pts.append((a * cur[0] + b * v[0] + c2 * v[2] + e * p[0], a * cur[1] + b * v[1] + c2 * v[3] + e * p[1]))
        elif cmd == "Q":
            p = (v[2], v[3])
            for k in range(9):
                u = k / 8
                a, b, c2 = (1 - u) ** 2, 2 * u * (1 - u), u * u
                pts.append((a * cur[0] + b * v[0] + c2 * p[0], a * cur[1] + b * v[1] + c2 * p[1]))
        else:
            p = (v[5], v[6])
            arc = True
            pts += arc_pts(cur, v[:5], p)
        cur = p
    return pts, nseg, arc, "Z" in d


def is_closed(d):
    pts, nseg, arc, z = outline(d)
    if len(pts) < 3:
        return False
    return z or (arc and nseg >= 2) or (nseg >= 3 and math.hypot(pts[0][0] - pts[-1][0], pts[0][1] - pts[-1][1]) < 2.6)


def inside(pt, poly):
    x, y = pt
    ins = False
    for i in range(len(poly)):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % len(poly)]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1 + 1e-12) + x1:
            ins = not ins
    return ins


def axis_lines(d):
    """Count straight L segments longer than 2 units that are exactly horizontal or vertical."""
    toks = TOKEN.findall(d)
    i, cmd, cur, n = 0, None, (0.0, 0.0), 0
    while i < len(toks):
        if toks[i].isalpha():
            cmd = toks[i]
            i += 1
            if cmd == "Z":
                continue
        k = ARGS.get(cmd, 0)
        v = [float(x) for x in toks[i:i + k]]
        i += k
        if len(v) < k:
            break
        p = (v[-2], v[-1]) if k else cur
        if cmd == "L" and (p[0] == cur[0] or p[1] == cur[1]) and math.hypot(p[0] - cur[0], p[1] - cur[1]) > 2:
            n += 1
        cur = p
        if cmd == "M":
            cmd = "L"
    return n


def check_symbol(sym, sid, variant, errs):
    """Return the symbol's list of (d, filled) so variants can be compared."""
    for k, v in ROOT_ATTRS.items():
        if sym.getAttribute(k) != v:
            errs.append(f"{sid}: symbol {k} must be {v!r}")
    skipped = sym.getAttribute("data-skip") == "true"
    if skipped:
        if variant == "regular":
            errs.append(f"{sid}: a regular drawing cannot be skipped")
        if any(ch.nodeType == ch.ELEMENT_NODE for ch in sym.childNodes):
            errs.append(f"{sid}: a skipped variant has no paths")
        return "skip"
    extra = set(sym.attributes.keys()) - set(ROOT_ATTRS) - {"id", "data-skip"}
    if extra:
        errs.append(f"{sid}: extra symbol attributes {sorted(extra)}")
    out, filled = [], 0
    for ch in sym.childNodes:
        if ch.nodeType == ch.TEXT_NODE and not ch.data.strip():
            continue
        if ch.nodeType != ch.ELEMENT_NODE or ch.tagName != "path":
            errs.append(f"{sid}: only <path> children allowed")
            continue
        bad = set(ch.attributes.keys()) - {"d", "fill", "fill-rule", "stroke"}
        if bad:
            errs.append(f"{sid}: path attributes {sorted(bad)} not allowed")
        fill = ch.getAttribute("fill")
        if fill:
            if fill != "currentColor":
                errs.append(f"{sid}: fill must be currentColor")
            elif variant == "regular":
                errs.append(f"{sid}: a regular drawing is never filled")
            filled += 1
        if ch.getAttribute("fill-rule") and (not fill or ch.getAttribute("fill-rule") != "evenodd"):
            errs.append(f"{sid}: fill-rule only as evenodd on a filled path")
        if variant == "solid" and (ch.getAttribute("stroke") == "none" or ch.getAttribute("fill-rule")):
            errs.append(f"{sid}: a solid keeps its stroke and its own path: no stroke=\"none\", no evenodd carving")
        if ch.getAttribute("stroke") and (ch.getAttribute("stroke") != "none" or not fill):
            errs.append(f"{sid}: stroke=\"none\" only on a filled path")
        d = ch.getAttribute("d")
        pts = points(d, sid, errs)
        if axis_lines(d):
            errs.append(f"{sid}: a straight line is exactly horizontal or vertical: lines lean a little (move one end by 0.2 to 0.5)")
        if variant == "marked" and fill and pts:
            xs, ys = [p[0] for p in pts], [p[1] for p in pts]
            if max(xs) - min(xs) > 6.01 or max(ys) - min(ys) > 6.01:
                errs.append(f"{sid}: a filled mark must fit in 6x6 units")
        out.append((d, bool(fill)))
    if not out:
        errs.append(f"{sid}: no paths")
    if variant == "solid" and filled > 4:
        sizes = []
        for ch in sym.childNodes:
            if ch.nodeType == ch.ELEMENT_NODE and ch.getAttribute("fill"):
                pts = outline(ch.getAttribute("d"))[0]
                xs, ys = [p[0] for p in pts], [p[1] for p in pts]
                sizes.append(max(max(xs) - min(xs), max(ys) - min(ys)))
        repeated = filled <= 8 and sizes and max(sizes) <= 1.6 * min(sizes)
        if not repeated:
            errs.append(f"{sid}: a solid fills 1 to 4 paths, or up to 8 when they are repeats of one similar element (found {filled})")
    elif variant == "solid" and filled < 1:
        errs.append(f"{sid}: a solid fills nothing")
    if variant == "marked" and filled > 2:
        errs.append(f"{sid}: a marked drawing fills at most 2 paths (found {filled})")
    return out


def check_variants(tag, reg, sol, mar, errs):
    """The v2 rules tying solid and marked to their regular."""
    reg_d = {d for d, _ in reg}
    closed = [outline(d)[0] for d, _ in reg if is_closed(d)]
    if sol != "skip":
        sol_d = {d for d, _ in sol}
        if not reg_d <= sol_d:
            errs.append(f"{tag}: the solid must keep every path of the regular (fill some of them, change none)")
        for d, fl in sol:
            if fl and d not in reg_d:
                errs.append(f"{tag}: the solid fills a path that is not one of the regular's own: fill the regular's closed path as it is, keep its stroke")
                break
    if mar != "skip":
        mar_d = {d for d, _ in mar}
        if not reg_d <= mar_d:
            errs.append(f"{tag}: the marked must keep every path of the regular")
        if not any(fl for _, fl in mar):
            errs.append(f"{tag}: the marked fills nothing: fill an existing closed shape, or add a filled dot inside one")
        for d, fl in mar:
            if d in reg_d:
                continue
            pts = outline(d)[0]
            xs, ys = [p[0] for p in pts], [p[1] for p in pts]
            w, h = max(xs) - min(xs), max(ys) - min(ys)
            size = max(w, h)
            centre = ((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2)
            if not fl:
                errs.append(f"{tag}: the marked adds an unfilled path: a mark is a filled dot inside a closed shape, a tail dot after a line's end, or an existing shape filled")
                continue
            hosts = []
            for rd, _ in reg:
                if is_closed(rd):
                    poly = outline(rd)[0]
                    if inside(centre, poly):
                        px, py = [p[0] for p in poly], [p[1] for p in poly]
                        hosts.append(min(max(px) - min(px), max(py) - min(py)))
            if hosts:
                host = min(hosts)
                if size < 2.0 or size > 6.01:
                    errs.append(f"{tag}: the mark is {size:.1f} units wide, it must be 2 to 6")
                elif size / host > 0.75:
                    errs.append(f"{tag}: the mark ({size:.1f}) fills almost all of its shape ({host:.1f}): it cannot be told from the shape. Make it under three quarters of it, or fill the shape itself")
                elif size / host > 0.55:
                    WARNINGS.append(f"{tag}: the mark ({size:.1f}) is more than half of its shape ({host:.1f}): check it still reads as a mark")
            else:
                ends = []
                for rd, _ in reg:
                    if not is_closed(rd):
                        p = outline(rd)[0]
                        if p:
                            ends += [p[0], p[-1]]
                near = [e for e in ends if math.hypot(e[0] - centre[0], e[1] - centre[1]) <= 3.4]
                if not near:
                    errs.append(f"{tag}: the mark's centre is neither inside a closed shape nor just after the end of a line (a dot never flies around)")
                elif not 2.5 <= size <= 5.0:
                    errs.append(f"{tag}: a tail dot is {size:.1f} units wide, it must be 2.5 to 5")
    # warning: filled shapes of a solid fused together (closer than one stroke width)
    if sol != "skip":
        fills = [outline(d)[0] for d, fl in sol if fl]
        for i in range(len(fills)):
            for j in range(i + 1, len(fills)):
                dmin = min(math.hypot(a[0] - b[0], a[1] - b[1]) for a in fills[i][::2] for b in fills[j][::2])
                if 0.3 < dmin < 1.75 and not any(inside(p, fills[j]) for p in fills[i][::3]) and not any(inside(p, fills[i]) for p in fills[j][::3]):
                    WARNINGS.append(f"{tag}: two filled shapes of the solid sit {dmin:.1f} apart, less than the stroke: they fuse into one blob (move them 2 apart or merge them on purpose)")
                    break


def check_batch(hand, batch):
    errs = []
    f = ROOT / "hands" / hand / f"{batch}.svg"
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
    squad, _, num = batch.rpartition("-")
    names = [c["name"] for c in CONCEPTS if c["squad"] == squad and c["batch"] == num]
    if not names:
        return [f"{f.name}: no concepts for batch {batch}"]
    expected = {f"{n}-{i}-{v}" for n in names for i in IDEAS for v in VARIANTS}
    for sid in sorted(expected - set(syms)):
        errs.append(f"{f.name}: missing symbol {sid}")
    for sid in sorted(set(syms) - expected):
        errs.append(f"{f.name}: unexpected symbol {sid}")
    got = {}
    for sid in sorted(expected & set(syms)):
        got[sid] = check_symbol(syms[sid], sid, sid.rsplit("-", 1)[1], errs)
    for n in names:
        regs = {}
        for i in IDEAS:
            reg, sol, mar = (got.get(f"{n}-{i}-{v}") for v in VARIANTS)
            if reg is None or sol is None or mar is None:
                continue
            key = tuple(reg)
            if key in regs:
                errs.append(f"{n}: ideas {regs[key]} and {i} have the identical regular drawing")
            regs[key] = i
            check_variants(f"{n}-{i}", reg, sol, mar, errs)
    return errs


def main():
    hand = sys.argv[1]
    batches = sys.argv[2:] or sorted(p.stem for p in (ROOT / "hands" / hand).glob("s*-??.svg"))
    total = []
    for b in batches:
        e = check_batch(hand, b)
        print(f"{hand} {b}: {'ok' if not e else str(len(e)) + ' errors'}")
        total += e
    for e in total[:80]:
        print("  " + e)
    for w in WARNINGS[:40]:
        print("  warning: " + w)
    sys.exit(1 if total else 0)


if __name__ == "__main__":
    main()
