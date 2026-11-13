-- log_reactions: each reader sees only their own rows (#1530).
--
-- The select policy was `true`, so anyone, anon included, read every reaction
-- with its user_id: who upvoted what in the Lab. The clients only ever read
-- their own (`myReactions` on web, iOS and Android all filter on user_id);
-- everyone else needs only a count per log, which v_logs_with_counts carries.
--
-- That view is security_invoker, so once the policy narrows, its count(*)
-- would count the caller's own reaction and nothing else. The count moves into
-- a definer helper that returns a number and nothing more, and only for a log
-- the caller may read (logs_select's predicate), so a draft's count stays dark.

-- ---------------------------------------------------------------------------
-- 1. The read policy: own rows, signed in. Writes are unchanged.
-- ---------------------------------------------------------------------------
drop policy "log_reactions_select" on public.log_reactions;
create policy "log_reactions_select" on public.log_reactions
  for select to authenticated
  using (user_id = (select auth.uid()));

-- ---------------------------------------------------------------------------
-- 2. The count, without the rows. Anon executes it through the view: the Lab
-- renders counts for signed-out visitors.
-- ---------------------------------------------------------------------------
create function public.log_reaction_count(p_log_id uuid)
returns integer
language sql
stable
security definer
set search_path = public
as $$
  select count(*)::int
    from public.log_reactions lr
   where lr.log_id = p_log_id
     and exists (
       select 1 from public.logs l
        where l.id = p_log_id
          and (l.published = true or public.is_admin(auth.uid()))
     );
$$;

revoke all on function public.log_reaction_count(uuid) from public;
grant execute on function public.log_reaction_count(uuid) to anon, authenticated;

-- ---------------------------------------------------------------------------
-- 3. The view, same columns (base: 20260514000001). Still security_invoker, so
-- logs RLS keeps deciding which logs appear.
-- ---------------------------------------------------------------------------
create or replace view public.v_logs_with_counts
with (security_invoker = true) as
select
  l.*,
  public.log_reaction_count(l.id) as reaction_count
from public.logs l;
