-- Migration: Art. 9 explicit consent as a version-bound ledger
--
-- The privacy policy qualifies moods, emotion labels and CBT reflections as
-- Art. 9 special-category health data processed only on explicit consent. No
-- consent step existed on any path (Kritik F-2026-08-GDP-web-01). This is the
-- storage half: the table, its invariant, and the only two ways to write it.

-- ============================================================
-- 1. The ledger
-- ============================================================
-- A row is a consent ACT, not a flag. Granting inserts; withdrawing stamps
-- withdrawn_at; a policy-version bump stamps superseded_at and inserts a new
-- row. withdrawn_at and superseded_at are kept distinct on purpose: "the user
-- withdrew" and "a newer document replaced this" are different facts, and an
-- accountability record that conflates them is a record that lies.

create table public.user_consents (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null references auth.users(id),
  kind             text not null check (kind in ('health_data', 'public_profile')),
  document_version text not null,
  source           text not null check (source in ('web_register', 'web_oauth', 'web_settings')),
  granted_at       timestamptz not null default now(),
  withdrawn_at     timestamptz,
  superseded_at    timestamptz,
  created_at       timestamptz not null default now()
);

-- No `on delete cascade`, matching profiles: the purge stays explicit, so the
-- zero-row assertion in verify-account-purge.ts stays meaningful.

-- At most one ACTIVE consent per (user, kind) — a database invariant, not a
-- convention the application is trusted to keep.
create unique index user_consents_active_kind
  on public.user_consents (user_id, kind)
  where withdrawn_at is null and superseded_at is null;

create index user_consents_user_id on public.user_consents (user_id);

-- ============================================================
-- 2. RLS — owner select only
-- ============================================================
-- There are deliberately NO insert/update/delete policies. A consent record
-- must never land through a client write path: every write goes through the
-- two definer RPCs below, which stamp auth.uid() themselves. The absence of a
-- policy is the guard.

alter table public.user_consents enable row level security;

create policy user_consents_select on public.user_consents for select
  using (auth.uid() = user_id);

-- ============================================================
-- 3. record_consent
-- ============================================================

create or replace function public.record_consent(
  p_kind text,
  p_document_version text,
  p_source text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
begin
  if v_user_id is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;

  -- Idempotent. A replayed OAuth callback and a second Save on an unchanged
  -- settings toggle must both be no-ops: the partial unique index would reject
  -- the duplicate insert anyway, and an error there would surface to the user
  -- as a failed signup.
  if exists (
    select 1 from public.user_consents
     where user_id = v_user_id
       and kind = p_kind
       and document_version = p_document_version
       and withdrawn_at is null
       and superseded_at is null
  ) then
    return;
  end if;

  -- An active grant at a DIFFERENT version is superseded, not duplicated.
  update public.user_consents
     set superseded_at = now()
   where user_id = v_user_id
     and kind = p_kind
     and withdrawn_at is null
     and superseded_at is null;

  insert into public.user_consents (user_id, kind, document_version, source)
  values (v_user_id, p_kind, p_document_version, p_source);
end;
$$;

-- ============================================================
-- 4. withdraw_consent
-- ============================================================

create or replace function public.withdraw_consent(p_kind text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_n       int;
begin
  if v_user_id is null then
    raise exception 'not_authenticated' using errcode = '28000';
  end if;

  update public.user_consents
     set withdrawn_at = now()
   where user_id = v_user_id
     and kind = p_kind
     and withdrawn_at is null
     and superseded_at is null;
  get diagnostics v_n = row_count;

  if v_n = 0 then
    raise exception 'no_active_consent' using errcode = 'P0002';
  end if;

  -- Multi-table, therefore in this transaction and not stitched client-side:
  -- withdrawing public-profile consent must actually unpublish. A consent
  -- record the backend does not honour is precisely the defect this change
  -- exists to fix.
  if p_kind = 'public_profile' then
    update public.profiles set public_profile = false where user_id = v_user_id;
  end if;
end;
$$;

grant execute on function public.record_consent(text, text, text) to authenticated;
grant execute on function public.withdraw_consent(text)            to authenticated;

-- ============================================================
-- 5. handle_new_user — record the email-path consent at signup
-- ============================================================
-- Body carried forward verbatim from 20260729120000 (profiles insert with the
-- two NULL-safe consent timestamps), with the Art. 9 insert appended.
--
-- Why metadata rather than a client RPC: at signUp() time there is no session
-- yet if email confirmations are enabled (M55 plans to enable them), so a
-- client-side record_consent call would simply be lost. The OAuth path cannot
-- use metadata at all — no signup metadata survives an OAuth round trip — and
-- records from the callback route instead.

create or replace function public.handle_new_user()
returns trigger as $$
declare
  v_consent_at      timestamptz := (new.raw_user_meta_data->>'health_data_consent_at')::timestamptz;
  v_consent_version text        := new.raw_user_meta_data->>'health_data_consent_version';
begin
  insert into public.profiles (
    user_id,
    display_name,
    terms_accepted_at,
    privacy_accepted_at
  )
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'full_name', 'Pebbler'),
    (new.raw_user_meta_data->>'terms_accepted_at')::timestamptz,
    (new.raw_user_meta_data->>'privacy_accepted_at')::timestamptz
  );

  -- >>> APPEND later signup-metadata side effects HERE. <<<
  -- `->>` yields NULL for an absent key, so the OAuth path (which carries no
  -- metadata) skips this silently and records from the callback instead.
  if v_consent_at is not null and v_consent_version is not null then
    insert into public.user_consents (user_id, kind, document_version, source, granted_at)
    values (new.id, 'health_data', v_consent_version, 'web_register', v_consent_at);
  end if;

  return new;
end;
$$ language plpgsql security definer set search_path = public;
