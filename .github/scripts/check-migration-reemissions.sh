#!/usr/bin/env bash
# Fails when the migrations added by this PR (a) create or replace the same
# public/private function more than once, or (b) re-emit purge_account /
# remove_connection / handle_new_user without their APPEND marker.
# `create or replace` has no merge semantics; see
# 20260731090000_purge_account_union.sql.
# Only `create [or replace] function public.<name>` / `private.<name>` on one
# line is recognised: unqualified or quoted names are not.
# Escape hatch for (c): a line `-- reemission-guard: drop <table>` in the new
# migration exempts exactly that table.
# Usage: check-migration-reemissions.sh <base-ref> [migrations-dir]
set -euo pipefail
base="${1:?base ref}"
dir="${2:-packages/supabase/supabase/migrations}"

git rev-parse --verify -q "$base^{commit}" >/dev/null || { echo "::error::base ref $base not found"; exit 2; }

tmp=$(mktemp)
trap 'rm -f "$tmp" "$tmp.base" "$tmp.new"' EXIT

git diff --name-only --diff-filter=A "$base...HEAD" -- "$dir" | { grep '\.sql$' || true; } > "$tmp"
if [ ! -s "$tmp" ]; then
  echo "no new migrations"
  exit 0
fi

fail=0
fn_re='create (or replace )?function (public|private)\.[a-z_0-9]+'

# (a) the same function created in two new files, or twice in one
dups=$(while read -r f; do grep -hoiE "$fn_re" "$f"; done < "$tmp" \
  | sed -E 's/.*function //' | tr '[:upper:]' '[:lower:]' | sort | uniq -d)
for fn in $dups; do
  echo "::error::$fn is re-emitted more than once by this PR's new migrations: union the bodies in one file"
  fail=1
done

# (b) marker presence
check_marker() {
  local fn="$1" marker="$2" f
  while read -r f; do
    if grep -qiE "create (or replace )?function $fn\b" "$f" && ! grep -qF -- "$marker" "$f"; then
      echo "::error file=$f::$fn re-emitted without its append marker ($marker)"
      fail=1
    fi
  done < "$tmp"
}
check_marker public.purge_account '>>> APPEND new per-user tables'
check_marker public.remove_connection '>>> M52/M53: sever seams'
check_marker public.handle_new_user '>>> APPEND later signup-metadata'

# (c) the new body still touches every table the latest base body touches
# body_of <fn>: the function's body from stdin, up to its closing $$;
body_of() {
  awk -v fn="$1" '
    { l = tolower($0) }
    on && l ~ /^[[:space:]]*(end[[:space:]]*;?[[:space:]]*)?\$[a-z_]*\$[[:space:]]*(;|language)/ { print; on = 0; next }
    l ~ /create (or replace )?function / { on = (index(l, "function " fn "(") > 0 || index(l, "function " fn " ") > 0) }
    on { print }
  '
}
# tables_of <verb-regex>: sorted unique target tables of matching lines from stdin
tables_of() {
  tr '[:upper:]' '[:lower:]' | { grep -E "^[[:space:]]*($1) (public\.)?[a-z_0-9]+" || true; } \
    | sed -E "s/^[[:space:]]*($1) (public\.)?([a-z_0-9]+).*/\3/" | sort -u
}
check_keeps() {
  local fn="$1" verbs="$2" short f latest bf table ls_base base_body
  short="${fn#public.}"
  ls_base=$(git ls-tree -r --name-only "$base" -- "$dir" | { grep '\.sql$' || true; } | sort)
  while read -r f; do
    grep -qiE "create (or replace )?function $fn\b" "$f" || continue
    latest=""
    while read -r bf; do
      [ -n "$bf" ] || continue
      base_body=$(git show "$base:$bf")
      if printf '%s\n' "$base_body" | grep -qiE "create (or replace )?function $fn\b"; then latest="$bf"; fi
    done <<EOT
$ls_base
EOT
    [ -n "$latest" ] || continue
    git show "$base:$latest" | body_of "$fn" | tables_of "$verbs" > "$tmp.base"
    body_of "$fn" < "$f" | tables_of "$verbs" > "$tmp.new"
    while read -r table; do
      [ -n "$table" ] || continue
      grep -qxF -- "$table" "$tmp.new" && continue
      grep -qE "^[[:space:]]*-- reemission-guard: drop ${table}[[:space:]]*$" "$f" && continue
      echo "::error file=$f::$short re-emitted without touching $table that $latest carries (or add: -- reemission-guard: drop $table)"
      fail=1
    done < "$tmp.base"
  done < "$tmp"
  rm -f "$tmp.base" "$tmp.new"
}
check_keeps public.purge_account 'delete from'
check_keeps public.remove_connection 'delete from'
check_keeps public.handle_new_user 'insert into|update'

exit "$fail"
