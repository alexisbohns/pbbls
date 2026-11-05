-- RLS denial harness inventory (#1523): the live catalog the registry in
-- scripts/rls-expectations.json must cover, one CSV on stdout.
--
--   psql "$DATABASE_URL" -X -q -v ON_ERROR_STOP=1 -f scripts/rls-inventory.sql
--
-- Read by scripts/verify-rls-denial.ts (through scripts/rls-inventory.ts). It
-- is the source of truth, not a grep of the migrations: an object a migration
-- dropped is gone, one created in Studio is here.
--
-- Three object kinds:
--   table     every ordinary/partitioned table in `public` (RLS on or off: an
--             RLS-off table is the loudest finding, so it must be listed)
--   view      every view / materialised view in `public` (PostgREST serves them,
--             and a view without security_invoker reads as its owner)
--   function  every SECURITY DEFINER function in `public` and `private`
--
-- Columns (one header row, then one row per object):
--   kind, schema, name, identity_args, rls_enabled, rls_forced, policy_count,
--   security_invoker, anon_select, authenticated_select, returns_trigger,
--   anon_execute, authenticated_execute, service_role_execute
-- Booleans are `t`/`f`; a column that does not apply to the kind is empty.

copy (
  select 'table'                                   as kind,
         n.nspname                                 as schema,
         c.relname                                 as name,
         ''                                        as identity_args,
         case when c.relrowsecurity then 't' else 'f' end      as rls_enabled,
         case when c.relforcerowsecurity then 't' else 'f' end as rls_forced,
         (select count(*) from pg_policies p
           where p.schemaname = n.nspname and p.tablename = c.relname)::text as policy_count,
         ''                                        as security_invoker,
         case when has_table_privilege('anon', c.oid, 'select') then 't' else 'f' end          as anon_select,
         case when has_table_privilege('authenticated', c.oid, 'select') then 't' else 'f' end as authenticated_select,
         ''                                        as returns_trigger,
         ''                                        as anon_execute,
         ''                                        as authenticated_execute,
         ''                                        as service_role_execute
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'public'
     and c.relkind in ('r', 'p')
  union all
  select 'view', n.nspname, c.relname, '',
         '', '', '',
         case when coalesce(c.reloptions, '{}') && array['security_invoker=true', 'security_invoker=on'] then 't' else 'f' end,
         case when has_table_privilege('anon', c.oid, 'select') then 't' else 'f' end,
         case when has_table_privilege('authenticated', c.oid, 'select') then 't' else 'f' end,
         '', '', '', ''
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
   where n.nspname = 'public'
     and c.relkind in ('v', 'm')
  union all
  select 'function', n.nspname, p.proname, pg_get_function_identity_arguments(p.oid),
         '', '', '', '', '', '',
         case when p.prorettype = 'trigger'::regtype then 't' else 'f' end,
         case when has_function_privilege('anon', p.oid, 'execute') then 't' else 'f' end,
         case when has_function_privilege('authenticated', p.oid, 'execute') then 't' else 'f' end,
         case when has_function_privilege('service_role', p.oid, 'execute') then 't' else 'f' end
    from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
   where p.prosecdef
     and n.nspname in ('public', 'private')
   order by 1, 2, 3, 4
) to stdout with (format csv, header true);
