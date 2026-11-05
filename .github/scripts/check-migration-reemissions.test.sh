#!/usr/bin/env bash
# Builds throwaway git repos and asserts the guard's exit codes.
set -uo pipefail
script="$(cd "$(dirname "$0")" && pwd)/check-migration-reemissions.sh"
root=$(mktemp -d)
trap 'rm -rf "$root"' EXIT
failures=0

# new_repo <name>: repo with an empty base commit on main, then a feature branch
new_repo() {
  local d="$root/$1"
  mkdir -p "$d/m"
  git -C "$d" init -q -b main
  git -C "$d" config user.email t@t
  git -C "$d" config user.name t
  : > "$d/m/.keep"
  git -C "$d" add -A && git -C "$d" commit -qm base
  git -C "$d" checkout -qb feature
  echo "$d"
}
commit_all() { git -C "$1" add -A && git -C "$1" commit -qm change; }
expect() { # name want got
  if [ "$2" -eq "$3" ]; then echo "ok   $1 (exit $3)"; else echo "FAIL $1: wanted $2, got $3"; failures=$((failures+1)); fi
}
run() { (cd "$1" && bash "$script" main m >/dev/null 2>&1); echo $?; }

d=$(new_repo dup)
echo 'create or replace function public.foo() returns int language sql as $$ select 1 $$;' > "$d/m/1_a.sql"
echo 'create or replace function public.foo() returns int language sql as $$ select 2 $$;' > "$d/m/2_b.sql"
commit_all "$d"; expect "duplicate function across files" 1 "$(run "$d")"

d=$(new_repo marker)
echo 'create or replace function public.purge_account() returns void language sql as $$ select 1 $$;' > "$d/m/1_a.sql"
commit_all "$d"; expect "missing append marker" 1 "$(run "$d")"

d=$(new_repo clean)
echo 'create or replace function public.foo() returns int language sql as $$ select 1 $$;' > "$d/m/1_a.sql"
printf '%s\n' 'create or replace function public.purge_account() returns void language sql as $$ select 1 $$;' '-- >>> APPEND new per-user tables' > "$d/m/2_b.sql"
commit_all "$d"; expect "clean (one foo, purge_account with marker)" 0 "$(run "$d")"

# base-append fixtures: base already carries delete a + b in purge_account
new_base_repo() {
  local d="$root/$1"
  mkdir -p "$d/m"
  git -C "$d" init -q -b main
  git -C "$d" config user.email t@t
  git -C "$d" config user.name t
  printf '%s\n' 'create or replace function public.purge_account() returns void language plpgsql as $$' 'begin' '  delete from public.a;' '  delete from public.b;' '  -- >>> APPEND new per-user tables' 'end $$;' > "$d/m/1_base.sql"
  git -C "$d" add -A && git -C "$d" commit -qm base
  git -C "$d" checkout -qb feature
  echo "$d"
}
d=$(new_base_repo dropped)
printf '%s\n' 'create or replace function public.purge_account() returns void language plpgsql as $$' 'begin' '  delete from public.a;' '  -- >>> APPEND new per-user tables' 'end $$;' > "$d/m/2_new.sql"
commit_all "$d"; expect "new body drops a base append line" 1 "$(run "$d")"

d=$(new_base_repo kept)
printf '%s\n' 'create or replace function public.purge_account() returns void language plpgsql as $$' 'begin' '  delete from public.a;' '  delete from public.b;' '  delete from public.c;' '  -- >>> APPEND new per-user tables' 'end $$;' > "$d/m/2_new.sql"
commit_all "$d"; expect "new body keeps all base append lines" 0 "$(run "$d")"

d=$(new_base_repo hatch)
printf '%s\n' 'create or replace function public.purge_account() returns void language plpgsql as $$' 'begin' '  delete from public.a;' '  -- reemission-guard: drop b' '  -- >>> APPEND new per-user tables' 'end $$;' > "$d/m/2_new.sql"
commit_all "$d"; expect "hatch for the dropped table" 0 "$(run "$d")"

d=$(new_base_repo hatchwrong)
printf '%s\n' 'create or replace function public.purge_account() returns void language plpgsql as $$' 'begin' '  delete from public.a;' '  -- reemission-guard: drop c' '  -- >>> APPEND new per-user tables' 'end $$;' > "$d/m/2_new.sql"
commit_all "$d"; expect "hatch for a different table" 1 "$(run "$d")"

d=$(new_repo badref)
echo x > "$d/m/1_a.sql"; commit_all "$d"
expect "bad base ref fails closed" 2 "$(cd "$d" && bash "$script" nope m >/dev/null 2>&1; echo $?)"

d=$(new_repo none)
echo x > "$d/README"; commit_all "$d"; expect "no new migrations" 0 "$(run "$d")"

if [ "$failures" -eq 0 ]; then echo "all passed"; else echo "$failures failed"; exit 1; fi
