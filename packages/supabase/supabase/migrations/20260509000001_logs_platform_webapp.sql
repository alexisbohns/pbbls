-- Migration: Rename `web` platform to `webapp` on logs
-- Aligns the Lab platform enum with how the webapp surface is referred
-- to elsewhere in the codebase. Updates the check constraint and any
-- existing rows tagged `web`.

alter table public.logs
  drop constraint if exists logs_platform_check;

update public.logs
  set platform = 'webapp'
  where platform = 'web';

alter table public.logs
  add constraint logs_platform_check
  check (platform in ('webapp','ios','android','all','project','infra'));
