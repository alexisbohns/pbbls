-- Migration: Logs Feed View
-- Convenience view that joins reaction counts onto logs so the iOS Lab
-- tab can fetch feed data in a single call. RLS on the underlying tables
-- still applies (security_invoker) — non-admins only see published rows.

create view public.v_logs_with_counts
with (security_invoker = true) as
select
  l.*,
  coalesce(rc.reaction_count, 0)::int as reaction_count
from public.logs l
left join lateral (
  select count(*)::int as reaction_count
  from public.log_reactions lr
  where lr.log_id = l.id
) rc on true;
