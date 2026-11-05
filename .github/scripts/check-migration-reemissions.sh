#!/usr/bin/env bash
# Fails when the migrations added by this PR (a) create or replace the same
# public/private function more than once, or (b) re-emit purge_account /
# remove_connection / handle_new_user without their APPEND marker.
# `create or replace` has no merge semantics; see
# 20260731090000_purge_account_union.sql.
# Usage: check-migration-reemissions.sh <base-ref> [migrations-dir]
set -euo pipefail
base="${1:?base ref}"
dir="${2:-packages/supabase/supabase/migrations}"

tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT

git diff --name-only --diff-filter=A "$base...HEAD" -- "$dir" | grep '\.sql$' > "$tmp" || true
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

# (c) the new body keeps every append line the latest base body carries
# body_lines <fn> <patterns-regex>: trimmed, lowercased matching lines of fn's body (stdin)
body_lines() {
  awk -v fn="$1" '
    BEGIN { IGNORECASE = 1 }
    { l = tolower($0) }
    l ~ /create (or replace )?function / { on = (index(l, "function " fn "(") > 0 || index(l, "function " fn " ") > 0) }
    on { print }
  ' | tr '[:upper:]' '[:lower:]' | grep -E "$2" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//' || true
}
check_keeps() {
  local fn="$1" pat="$2" short f latest bf line
  short="${fn#public.}"
  ls_base=$(git ls-tree -r --name-only "$base" -- "$dir" | grep '\.sql$' | sort || true)
  while read -r f; do
    grep -qiE "create (or replace )?function $fn\b" "$f" || continue
    latest=""
    while read -r bf; do
      if git show "$base:$bf" | grep -qiE "create (or replace )?function $fn\b"; then latest="$bf"; fi
    done <<EOT
$ls_base
EOT
    [ -n "$latest" ] || continue
    git show "$base:$latest" | body_lines "$fn" "$pat" | sort -u > "$tmp.base"
    tr '[:upper:]' '[:lower:]' < "$f" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//' > "$tmp.new"
    while read -r line; do
      [ -n "$line" ] || continue
      if ! grep -qxF -- "$line" "$tmp.new"; then
        echo "::error file=$f::$short re-emitted without \`$line\` that $latest carries"
        fail=1
      fi
    done < "$tmp.base"
  done < "$tmp"
  rm -f "$tmp.base" "$tmp.new"
}
del_re='^[[:space:]]*delete from (public\.)?[a-z_]+'
ins_re='^[[:space:]]*(insert into|update) (public\.)?[a-z_]+'
check_keeps public.purge_account "$del_re"
check_keeps public.remove_connection "$del_re"
check_keeps public.handle_new_user "$ins_re"

exit "$fail"
