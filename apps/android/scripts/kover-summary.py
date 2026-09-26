#!/usr/bin/env python3
"""Render a Kover XML report as Markdown for a GitHub step summary (#857).

Usage: kover-summary.py <reportDebug.xml>

Prints the line / branch / instruction totals and a per-package table, lowest
line coverage first, so the thinnest areas lead. Report only: it never fails
the build, because coverage is not a gate yet (see app/build.gradle.kts).
"""

import sys
import xml.etree.ElementTree as ET

ROOT_PACKAGE = "app/pbbls/android/"


def ratio(counters, kind):
    counter = counters.get(kind)
    if counter is None:
        return None
    covered, missed = counter
    total = covered + missed
    return (covered, total, 100.0 * covered / total if total else 0.0)


def counters_of(element):
    return {
        c.attrib["type"]: (int(c.attrib["covered"]), int(c.attrib["missed"]))
        for c in element.findall("counter")
    }


def cell(value):
    if value is None or value[1] == 0:
        return "–"
    covered, total, pct = value
    return f"{pct:.1f}% ({covered}/{total})"


def main(path):
    root = ET.parse(path).getroot()
    totals = counters_of(root)

    print("### Android coverage (debug unit + Robolectric UI tests)")
    print()
    print("| | Covered |")
    print("|---|---|")
    for kind, label in (("LINE", "Lines"), ("BRANCH", "Branches"), ("INSTRUCTION", "Instructions")):
        print(f"| {label} | {cell(ratio(totals, kind))} |")
    print()

    rows = []
    for package in root.findall("package"):
        name = package.attrib["name"]
        short = name[len(ROOT_PACKAGE):] if name.startswith(ROOT_PACKAGE) else name
        line = ratio(counters_of(package), "LINE")
        if line is None or line[1] == 0:
            continue
        rows.append((line[2], short.replace("/", ".") or "(root)", line, ratio(counters_of(package), "BRANCH")))
    rows.sort()

    print("<details><summary>By package, lowest line coverage first</summary>")
    print()
    print("| Package | Lines | Branches |")
    print("|---|---|---|")
    for _, short, line, branch in rows:
        print(f"| `{short}` | {cell(line)} | {cell(branch)} |")
    print()
    print("</details>")
    print()
    print("Not a gate — reported so a floor can be set from real numbers later.")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("usage: kover-summary.py <kover xml report>")
    main(sys.argv[1])
