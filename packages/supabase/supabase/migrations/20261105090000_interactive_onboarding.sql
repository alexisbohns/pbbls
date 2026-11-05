-- Migration: interactive onboarding (#1510 / #1521, spec 2026-11-04 §2)
--
-- 1. promise_letters: the letter step 1 shows, admin-edited. Modelled on
--    settings_links (20261101120000): EN required, FR falls back to EN on the
--    client, public read gated on `published`, writes admin-only. Not user
--    data: no purge_account append. No emotion or domain: no
--    sync_achievement_catalog().
-- 2. profiles.onboarding_reasons / profiles.pace: the two answers every
--    surface reads on launch (D6). Nullable; null = skipped or pre-flow.
-- 3. complete_onboarding: one transaction for everything the flow collected
--    (D5). Consent is NOT in here: step 2 records acts as they happen through
--    record_consent.
-- 4. get_onboarding_answers: the admin's distribution counts, never per user.

-- ---------------------------------------------------------------------------
-- 1. promise_letters
-- ---------------------------------------------------------------------------
create table public.promise_letters (
  id            uuid primary key default gen_random_uuid(),
  body_md_en    text not null check (btrim(body_md_en) <> ''),
  body_md_fr    text,
  signed_by_en  text not null default 'The Pebbles team',
  signed_by_fr  text,
  published     boolean not null default false,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now()
);

comment on table public.promise_letters is
  'The promise every new account reads before consenting (#1510 step 1). Edited in the admin; clients read the most recently updated published row and fall back to EN when FR is null.';

create trigger promise_letters_updated_at
  before update on public.promise_letters
  for each row execute function public.set_updated_at();

alter table public.promise_letters enable row level security;

create policy "promise_letters_select" on public.promise_letters
  for select using (published = true or public.is_admin(auth.uid()));
create policy "promise_letters_insert" on public.promise_letters
  for insert with check (public.is_admin(auth.uid()));
create policy "promise_letters_update" on public.promise_letters
  for update using (public.is_admin(auth.uid()));
create policy "promise_letters_delete" on public.promise_letters
  for delete using (public.is_admin(auth.uid()));

create index promise_letters_published_idx on public.promise_letters (published, updated_at desc);

insert into public.promise_letters (id, body_md_en, body_md_fr, signed_by_en, signed_by_fr, published) values (
  md5('promise_letters:v1')::uuid,
  E'There will never be a feed here.\n\nNothing you keep becomes an ad, a trend, or data to train a machine.\n\nIf you leave, your pebbles leave with you, in a file you can open anywhere.',
  E'Il n''y aura jamais de fil d''actualité ici.\n\nRien de ce que tu gardes ne devient une pub, une tendance ou des données pour entraîner une machine.\n\nSi tu pars, tes galets partent avec toi, dans un fichier que tu peux ouvrir n''importe où.',
  'The Pebbles team',
  'L''équipe Pebbles',
  true
);

-- ---------------------------------------------------------------------------
-- 2. profiles columns
-- ---------------------------------------------------------------------------
alter table public.profiles
  add column onboarding_reasons text[],
  add column pace text;

alter table public.profiles
  add constraint profiles_onboarding_reasons_check check (
    onboarding_reasons is null or (
      onboarding_reasons <@ array['notice-feelings','keep-people','hard-season','good-days','own-patterns','keep-record']::text[]
      and cardinality(onboarding_reasons) between 1 and 6
    )
  ),
  add constraint profiles_pace_check check (
    pace is null or pace in ('daily','few-a-week','when-it-matters','weekly-wrap')
  );

comment on column public.profiles.onboarding_reasons is
  'Step 3 of the onboarding (#1510): the reasons picked, by slug. Null = skipped or pre-flow account.';
comment on column public.profiles.pace is
  'Step 6 of the onboarding (#1510): the pace picked. Read by reminders (M69). Null = skipped or pre-flow account.';

-- ---------------------------------------------------------------------------
-- 3. complete_onboarding
-- ---------------------------------------------------------------------------
-- Owner-checked on auth.uid(). Idempotent: a second call on a completed
-- profile is a no-op, not an error (D5). Errors carry stable names, as
-- record_consent's do, so clients switch on them instead of parsing
-- constraint names. A soul always carries a glyph_id: souls.glyph_id is not
-- null, and D7 says each first person has a glyph.
create function public.complete_onboarding(
  p_display_name text    default null,
  p_glyph_id     uuid    default null,
  p_reasons      text[]  default null,
  p_pace         text    default null,
  p_souls        jsonb   default '[]'::jsonb
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  v_user_id uuid := auth.uid();
  v_done    boolean;
  v_soul    jsonb;
  v_name    text;
  v_glyph   uuid;
  v_names   text[] := '{}';
begin
  if v_user_id is null then
    raise exception 'not_authenticated' using errcode = '42501';
  end if;

  select onboarding_completed into v_done
    from public.profiles
   where user_id = v_user_id
     for update;
  if v_done is null then
    raise exception 'profile_missing';
  end if;
  if v_done then
    return;
  end if;

  if p_reasons is not null and (
    cardinality(p_reasons) = 0
    or not (p_reasons <@ array['notice-feelings','keep-people','hard-season','good-days','own-patterns','keep-record']::text[])
  ) then
    raise exception 'invalid_reason';
  end if;

  if p_pace is not null and p_pace not in ('daily','few-a-week','when-it-matters','weekly-wrap') then
    raise exception 'invalid_pace';
  end if;

  if p_display_name is not null and btrim(p_display_name) = '' then
    raise exception 'invalid_display_name';
  end if;

  if p_glyph_id is not null and not public.can_use_glyph(p_glyph_id, v_user_id) then
    raise exception 'glyph_not_usable' using errcode = '42501';
  end if;

  if p_souls is null or jsonb_typeof(p_souls) <> 'array' then
    raise exception 'invalid_souls';
  end if;
  if jsonb_array_length(p_souls) > 3 then
    raise exception 'too_many_souls';
  end if;

  for v_soul in select value from jsonb_array_elements(p_souls) loop
    if jsonb_typeof(v_soul) <> 'object' then
      raise exception 'invalid_souls';
    end if;
    v_name := btrim(v_soul->>'name');
    if v_name is null or v_name = '' then
      raise exception 'invalid_soul_name';
    end if;
    if lower(v_name) = any(v_names) then
      raise exception 'duplicate_soul';
    end if;
    v_names := v_names || lower(v_name);
    begin
      v_glyph := nullif(v_soul->>'glyph_id', '')::uuid;
    exception when invalid_text_representation then
      raise exception 'invalid_soul_glyph';
    end;
    if v_glyph is null then
      raise exception 'invalid_soul_glyph';
    end if;
    if not public.can_use_glyph(v_glyph, v_user_id) then
      raise exception 'glyph_not_usable' using errcode = '42501';
    end if;
    insert into public.souls (user_id, name, glyph_id) values (v_user_id, v_name, v_glyph);
  end loop;

  -- The profile's name and glyph go through update_profile, so the two share
  -- one write path (spec §2.3).
  if p_display_name is not null or p_glyph_id is not null then
    perform public.update_profile(nullif(btrim(p_display_name), ''), p_glyph_id);
  end if;

  update public.profiles
     set onboarding_reasons   = p_reasons,
         pace                 = p_pace,
         onboarding_completed = true,
         updated_at           = now()
   where user_id = v_user_id;
end;
$$;

revoke all on function public.complete_onboarding(text, uuid, text[], text, jsonb) from public, anon;
grant execute on function public.complete_onboarding(text, uuid, text[], text, jsonb) to authenticated;

-- ---------------------------------------------------------------------------
-- 4. get_onboarding_answers (admin): distribution only, never per user.
-- ---------------------------------------------------------------------------
create function public.get_onboarding_answers()
returns table (kind text, value text, users bigint)
language plpgsql
security definer
stable
set search_path = public
as $$
begin
  if not public.is_admin(auth.uid()) then
    raise exception 'insufficient_privilege' using errcode = '42501';
  end if;
  return query
    select * from (
      select 'reason'::text as kind, r.reason as value, count(*)::bigint as users
        from public.profiles p, unnest(p.onboarding_reasons) as r(reason)
       group by r.reason
      union all
      select 'pace'::text, p.pace, count(*)::bigint
        from public.profiles p
       where p.pace is not null
       group by p.pace
      union all
      select 'completed'::text, case when p.onboarding_completed then 'yes' else 'no' end, count(*)::bigint
        from public.profiles p
       group by p.onboarding_completed
    ) answers
    order by answers.kind, answers.value;
end;
$$;

revoke all on function public.get_onboarding_answers() from public, anon;
grant execute on function public.get_onboarding_answers() to authenticated;
