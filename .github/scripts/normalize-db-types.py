#!/usr/bin/env python3
"""
Normalises a generated types/database.ts for comparison (#794).

The `schema` job asserts that the committed types still describe the migrations.
Comparing the two files byte-for-byte does not work, for one structural reason:

    __InternalSupabase: { PostgrestVersion: "14.5" }

That block is emitted only when the types are generated against a LINKED project,
because the version is read from that project's running API. A file generated from
a local stack can never carry it. The committed file has it (it came from the
dashboard / the `generate_typescript_types` MCP tool named as a fallback in
packages/supabase/CLAUDE.md); CI generates with `--local` and cannot reproduce it.

It describes the server, not the schema, so it is removed from BOTH sides rather
than tolerated on one — a one-sided allowance is how a real drift hides. Trailing
blank lines are normalised for the same reason: `gen types` is inconsistent about
the final newline across versions, and that is not a schema fact either.

Everything else is compared exactly. A renamed column, a dropped function or a new
table survives this normalisation untouched, which is the whole point.

Usage: normalize-db-types.py <in> <out> [<in> <out> ...]
"""
import sys


def strip_internal_block(text: str) -> str:
    lines = text.split("\n")
    out: list[str] = []
    i = 0
    while i < len(lines):
        if lines[i].strip().startswith("__InternalSupabase:"):
            # The block is preceded by explanatory comments that are part of it.
            while out and out[-1].strip().startswith("//"):
                out.pop()
            depth = lines[i].count("{") - lines[i].count("}")
            i += 1
            while i < len(lines) and depth > 0:
                depth += lines[i].count("{") - lines[i].count("}")
                i += 1
            continue
        out.append(lines[i])
        i += 1
    return "\n".join(out)


def main() -> int:
    args = sys.argv[1:]
    if not args or len(args) % 2:
        print("usage: normalize-db-types.py <in> <out> [<in> <out> ...]", file=sys.stderr)
        return 2
    for src, dst in zip(args[::2], args[1::2]):
        with open(src, encoding="utf-8") as fh:
            text = fh.read()
        with open(dst, "w", encoding="utf-8") as fh:
            fh.write(strip_internal_block(text).rstrip() + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
