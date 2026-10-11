# Migration rehearsal (no Docker)

`run.sh <version>` restores the linked project's `public` schema and rows plus
`auth.users` and `storage.objects` into a throwaway native cluster, then applies
every migration whose version is >= `<version>`. Run it before any `db push`
that drops or backfills columns. The DB URL is in the dashboard (Project
settings → Database → connection string, direct, not pooled).

Prerequisites: Homebrew `postgresql@17` (`brew install postgresql@17`). The
project is on Postgres 17 and pg_dump refuses a newer server; `run.sh` checks
that the remote major equals the local pg_dump major (override the binaries
with `PGBIN`). Scratch dir override: `REHEARSAL_SCRATCH`.

```bash
SUPABASE_DB_URL=postgresql://postgres:<pw>@db.<ref>.supabase.co:5432/postgres \
  scripts/rehearsal/run.sh 20261011000000          # from packages/supabase
REHEARSAL_KEEP=1 ...                                # keep the cluster up to poke at it
```

The cluster listens on 127.0.0.1:55432 and is stopped on exit unless
`REHEARSAL_KEEP=1`.

## How the restore works

`pg_dump --schema=public --table=...` does not do what it looks like: `-t`
makes `-n` have no effect. So the restore is two steps:

1. `bootstrap.sql` creates minimal shells of `auth.users`, `storage.buckets` and
   `storage.objects` (only the columns the rehearsal needs), and `run.sh` copies
   those columns from the live tables with `\copy`. The live tables carry many
   more columns and platform-owned types, so a real `--schema-only` dump of them
   would not restore on a bare Postgres.
2. `pg_dump --schema=public` (schema and data) is restored afterwards, so its
   foreign keys to `auth.users` find their rows.

Data at rest: the scratch dir holds production rows (public data, `auth.users`
emails). It is created with umask 077 and removed on exit. With
`REHEARSAL_KEEP=1` it stays on disk; clean up with
`/opt/homebrew/opt/postgresql@17/bin/pg_ctl -D /tmp/pbbls-rehearsal/pgdata stop; rm -rf /tmp/pbbls-rehearsal`.

Known gaps:
- `auth.uid()` is a stub reading `request.jwt.claim.sub`; `storage.foldername`
  is a faithful reimplementation. `auth.jwt()` is genuinely missing.
- Policies are restored, but nothing runs as `authenticated` unless you
  `set role authenticated; set request.jwt.claim.sub = '<uuid>'`. That works
  out of the box: `bootstrap.sql` grants `usage on schema auth` to the three
  API roles so `auth.uid()` resolves under `set role`.
- The `extensions` schema is absent, so `extensions.gen_random_bytes` and
  friends fail at runtime, not at restore.
- Migrations run as a superuser here; `db push` runs as Supabase's
  non-superuser `postgres`, so a rehearsal-green migration can still fail on
  push for permission reasons.
- If a new migration touches another `auth.*` or `storage.*` column, add it to
  `bootstrap.sql` and to the `copy_table` calls.
