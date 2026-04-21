-- Migration: Log Reactions
-- Single upvote per (log, user). Drives "most wanted" sort on the Lab backlog.
-- Public read so counts render for anonymous visitors; writes require auth
-- and can only target published logs (plus admins can react to unpublished).

-- ============================================================
-- TABLE
-- ============================================================

create table public.log_reactions (
  log_id uuid not null references public.logs(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (log_id, user_id)
);

-- ============================================================
-- ROW LEVEL SECURITY
-- ============================================================

alter table public.log_reactions enable row level security;

create policy "log_reactions_select" on public.log_reactions
  for select using (true);

create policy "log_reactions_insert" on public.log_reactions
  for insert with check (
    user_id = auth.uid()
    and exists (
      select 1 from public.logs l
      where l.id = log_id
        and (l.published = true or public.is_admin(auth.uid()))
    )
  );

create policy "log_reactions_delete" on public.log_reactions
  for delete using (user_id = auth.uid());

-- ============================================================
-- INDEXES
-- ============================================================

create index log_reactions_log_id_idx on public.log_reactions (log_id);
create index log_reactions_user_id_idx on public.log_reactions (user_id);
