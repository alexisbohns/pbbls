#!/usr/bin/env python3
"""Borda count of a wave's blind pick-three game: python3 -I tally.py <wave>

Reads <wave>/results-picks.json (one entry per step: ctx, icon, ranked[3], special[])
and prints, per icon id, every hand's score (3/2/1 for ranks 1-3) with its
per-context breakdown and the contexts that marked it special. An input to the
verdict, never a substitute for it.
"""
import json
import sys
from collections import defaultdict
from pathlib import Path

POINTS = (3, 2, 1)


def main() -> None:
    wave = Path(sys.argv[1]).resolve()
    picks = json.loads((wave / "results-picks.json").read_text(encoding="utf-8"))
    icons: list[str] = []
    score: dict[str, dict[str, int]] = defaultdict(lambda: defaultdict(int))
    per_ctx: dict[str, dict[str, dict[str, int]]] = defaultdict(lambda: defaultdict(dict))
    special: dict[str, dict[str, list[str]]] = defaultdict(lambda: defaultdict(list))
    for step in picks:
        icon, ctx = step["icon"], step["ctx"]
        if icon not in icons:
            icons.append(icon)
        for rank, hand in enumerate(step["ranked"][:3]):
            score[icon][hand] += POINTS[rank]
            per_ctx[icon][hand][ctx] = POINTS[rank]
        for hand in step.get("special", []):
            special[icon][hand].append(ctx)
    for icon in icons:
        print(icon)
        hands = set(score[icon]) | set(special[icon])
        rows = sorted(hands, key=lambda h: (-score[icon][h], h))
        for hand in rows:
            ctxs = " ".join(f"{c}:{per_ctx[icon][hand].get(c, '-')}" for c in ("A", "B", "C"))
            flag = f"  special {''.join(sorted(special[icon][hand]))}" if special[icon][hand] else ""
            print(f"  {hand}  {score[icon][hand]:>2}  {ctxs}{flag}")
    print(f"{len(icons)} ids, {len(picks)} steps")


if __name__ == "__main__":
    main()
