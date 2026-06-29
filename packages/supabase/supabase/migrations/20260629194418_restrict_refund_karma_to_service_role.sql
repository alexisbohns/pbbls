-- Security fix: refund_karma must not be a client-callable karma mint.
-- As written it has no validation against an original purchase, so granting it
-- to `authenticated` lets any user mint karma via refund_karma(1_000_000, …).
-- Refunds are issued by trusted server/admin logic only. The buy flow
-- (sub-project C) needs no client refund: a failed grant rolls back the spend
-- in the same transaction. Restrict execution to service_role.
revoke execute on function public.refund_karma(integer,uuid) from public, anon, authenticated;
grant  execute on function public.refund_karma(integer,uuid) to service_role;
