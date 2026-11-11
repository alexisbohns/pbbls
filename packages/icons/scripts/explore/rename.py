#!/usr/bin/env python3
"""Rename glyphs everywhere: python3 -I rename.py <repo root> <renames.json>

Moves src/<old>.svg and its dotted drawing and overrides, renames the icons.json
row, and rewrites hand-written call sites under apps/ in each surface's
spelling (React PascalCase, Swift camelCase, Kotlin CONSTANT, the kebab name in
strings). Generated files are left alone: run generate after.
"""
import json
import re
import sys
from pathlib import Path

GENERATED = (
    "packages/icons/react/",
    "apps/ios/Pebbles/Resources/pbbls-glyphs.json",
    "apps/ios/Pebbles/Theme/PbblsGlyph.swift",
    "apps/ios/Pebbles/Theme/PbblsGlyphState.swift",
    "apps/android/app/src/main/res/raw/pbbls_glyphs.json",
    "apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PbblsGlyph.kt",
    "apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PbblsGlyphState.kt",
)


def words(name):
    return [w for w in re.split(r"[.-]", name) if w]


def spellings(name):
    w = words(name)
    return {
        "pascal": "".join(x[:1].upper() + x[1:] for x in w),
        "camel": w[0] + "".join(x[:1].upper() + x[1:] for x in w[1:]),
        "constant": "_".join(x.upper() for x in w),
        "kebab": name,
    }


def main():
    repo = Path(sys.argv[1]).resolve()
    renames = json.loads(Path(sys.argv[2]).read_text(encoding="utf-8"))
    src = repo / "packages/icons/src"
    manifest_file = repo / "packages/icons/icons.json"
    manifest = manifest_file.read_text(encoding="utf-8")
    pairs = []
    for old, new in renames.items():
        sources = [src / f"{old}.svg"] + sorted(src.glob(f"{old}.*.svg"))
        for f in sources:
            if f.exists():
                suffix = f.name[len(old) : -len(".svg")]
                f.rename(src / f"{new}{suffix}.svg")
        pairs.append((old, new))
        manifest = manifest.replace(f'"name": "{old}"', f'"name": "{new}"')
    manifest_file.write_text(manifest, encoding="utf-8")
    subs = []
    for old, new in sorted(pairs, key=lambda p: -len(p[0])):
        o, n = spellings(old), spellings(new)
        for key in ("pascal", "camel", "constant"):
            subs.append((re.compile(rf"\b{re.escape(o[key])}\b"), n[key]))
        subs.append((re.compile(rf'(["\']){re.escape(old)}(["\'])'), rf"\g<1>{new}\g<2>"))
    changed = 0
    for path in (repo / "apps").rglob("*"):
        rel = path.relative_to(repo).as_posix()
        if not path.is_file() or path.suffix not in (".swift", ".kt", ".ts", ".tsx") or rel.startswith(GENERATED) or "/node_modules/" in rel or "/build/" in rel:
            continue
        text = path.read_text(encoding="utf-8")
        out = text
        for pattern, repl in subs:
            out = pattern.sub(repl, out)
        if out != text:
            path.write_text(out, encoding="utf-8")
            changed += 1
    print(f"{len(renames)} glyphs renamed, {changed} app files rewritten")


main()
