#!/usr/bin/env bash
# packages/supabase/scripts/rehearsal/run.sh
# Usage: SUPABASE_DB_URL=postgresql://postgres:<pw>@db.<ref>.supabase.co:5432/postgres \
#        scripts/rehearsal/run.sh <first-new-migration-version>
# Restores the linked project's public schema + data into a throwaway native
# cluster on 127.0.0.1:55432, then applies every migration >= $1 in order.
# Needs postgresql@17 (matches the project); override with PGBIN.
# Scratch dir: REHEARSAL_SCRATCH. Set REHEARSAL_KEEP=1 to leave the cluster running after the replay.
set -euo pipefail
umask 077  # the scratch dir holds production rows
PGBIN="${PGBIN:-/opt/homebrew/opt/postgresql@17/bin}"
SCRATCH="${REHEARSAL_SCRATCH:-/tmp/pbbls-rehearsal}"
FROM="${1:?usage: run.sh <first-new-migration-version>}"
[[ "$FROM" =~ ^[0-9]{14}$ ]] || { echo "version must be 14 digits"; exit 1; }
: "${SUPABASE_DB_URL:?set SUPABASE_DB_URL to the direct (not pooled) connection string}"
HERE="$(cd "$(dirname "$0")/../.." && pwd)"

# Preflight: pg_dump refuses a server newer than itself, and the major must match.
[[ -x "$PGBIN/pg_dump" ]] || { echo "$PGBIN/pg_dump not found: install postgresql@17 or set PGBIN"; exit 1; }
remote_major="$("$PGBIN/psql" "$SUPABASE_DB_URL" -Atqc "show server_version_num" | cut -c1-2)"
local_major="$("$PGBIN/pg_dump" --version | sed -E 's/[^0-9]*([0-9]+).*/\1/')"
[[ "$remote_major" == "$local_major" ]] || { echo "server major $remote_major != local pg_dump major $local_major: set PGBIN to a matching install"; exit 1; }

if "$PGBIN/pg_ctl" -D "$SCRATCH/pgdata" status >/dev/null 2>&1; then "$PGBIN/pg_ctl" -D "$SCRATCH/pgdata" stop >/dev/null; fi
rm -rf "$SCRATCH"; mkdir -p "$SCRATCH"
"$PGBIN/initdb" -D "$SCRATCH/pgdata" -U postgres --auth=trust --locale=C -E UTF8 >/dev/null
"$PGBIN/pg_ctl" -D "$SCRATCH/pgdata" -o "-p 55432 -k /tmp -c listen_addresses=127.0.0.1" -l "$SCRATCH/pg.log" start >/dev/null
if [[ "${REHEARSAL_KEEP:-0}" != "1" ]]; then
  trap '"$PGBIN/pg_ctl" -D "$SCRATCH/pgdata" stop >/dev/null; rm -rf "$SCRATCH"' EXIT
fi
PSQL="$PGBIN/psql -h 127.0.0.1 -p 55432 -U postgres -v ON_ERROR_STOP=1 -q"

$PSQL -f "$HERE/scripts/rehearsal/bootstrap.sql"

# 1. Platform rows. pg_dump ignores --schema when --table is given, so the
#    three platform tables are copied column-by-column with \copy instead.
copy_table() { # <table> <column list>
  "$PGBIN/psql" "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -q -c "\\copy (select $2 from $1) to stdout" \
    | $PSQL -c "\\copy $1 ($2) from stdin"
}
copy_table auth.users "id, email, raw_user_meta_data, created_at"
copy_table storage.buckets "id, name"
copy_table storage.objects "id, bucket_id, name, metadata, created_at"

# 2. The public schema and its rows.
"$PGBIN/pg_dump" "$SUPABASE_DB_URL" --no-owner --no-privileges --no-publications --no-subscriptions \
  --schema=public > "$SCRATCH/dump.sql"
$PSQL -f "$SCRATCH/dump.sql" 2> "$SCRATCH/restore.err" || { echo "restore failed, see $SCRATCH/restore.err"; head -n 5 "$SCRATCH/restore.err"; exit 1; }

# 3. Only the new migrations.
shopt -s nullglob
files=("$HERE"/supabase/migrations/*.sql)
if [[ ${#files[@]} -eq 0 ]]; then echo "no migrations found in $HERE/supabase/migrations"; exit 1; fi
applied=0
for path in "${files[@]}"; do
  f="$(basename "$path")"
  v="${f%%_*}"
  if [[ "$v" -ge "$FROM" ]]; then
    echo "== applying $f"
    $PSQL -1 -f "$path"
    applied=$((applied + 1))
  fi
done
if [[ "$applied" -eq 0 ]]; then echo "no migration with version >= $FROM"; exit 1; fi
echo "== rehearsal ok ($applied applied)."
if [[ "${REHEARSAL_KEEP:-0}" == "1" ]]; then
  echo "cluster left running on 127.0.0.1:55432; psql: $PSQL"
  echo "production rows remain on disk; clean up: $PGBIN/pg_ctl -D $SCRATCH/pgdata stop; rm -rf $SCRATCH"
fi
