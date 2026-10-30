-- Achievements page, the model (#1305, spec 2026-10-29 D3–D10).
--
-- * first_stone: the six firsts (small/medium/large intensity, lowlight/neutral/
--   highlight positiveness), seeded only; stone_kind says which.
-- * set_complete: one catalog row per completion stack, unlocked only by
--   pull_ceremony (part 3), so a completion's karma is an admin-set catalog
--   value and is paid exactly once by the unlocks PK. Seeded is_active = false:
--   part 3's migration activates them with pull_ceremony.
-- * the nine new rows carry seeded titles (EN/FR): the shipped grids fall back
--   to the raw slug when a row has no title and no built-in copy.
-- * the pebble ladder continues every 500 up to 10 000 (D3).
-- * achievement_unlocks.pebble_id: the pebble that earned the row (D4), with
--   achievement_completions.pebble_id indexed beside it, so on delete set null
--   stays cheap on a pebble delete and in purge_account.
-- * achievement_views: "new since you last looked", per card (D10).
-- * achievement_completions: a set made whole, offered later, pulled (D9).
-- * contributions never pay karma (D7): zeroed, then constrained.

-- 1. families ---------------------------------------------------------------------
alter table public.achievements drop constraint achievements_family_check;
alter table public.achievements add constraint achievements_family_check check (family in (
  'pebble_count','emotion_first','domain_first','first_collection',
  'first_glyph','first_soul','glyph_count','glyph_sales',
  'first_moment','first_pebble','first_five',
  'first_stone','set_complete'));

alter table public.achievements add column stone_kind text
  check (stone_kind in ('small','medium','large','lowlight','neutral','highlight'));
alter table public.achievements add constraint achievements_stone_kind_family
  check ((family = 'first_stone') = (stone_kind is not null));

-- 2. seeds ---------------------------------------------------------------------------
insert into public.achievements (id, slug, family, stone_kind, title_en, title_fr, sort_order) values
  (md5('achievements:first-stone-small')::uuid,     'first-stone-small',     'first_stone', 'small',     'First small pebble',   'Premier petit galet', 460),
  (md5('achievements:first-stone-medium')::uuid,    'first-stone-medium',    'first_stone', 'medium',    'First medium pebble',  'Premier galet moyen', 461),
  (md5('achievements:first-stone-large')::uuid,     'first-stone-large',     'first_stone', 'large',     'First large pebble',   'Premier grand galet', 462),
  (md5('achievements:first-stone-lowlight')::uuid,  'first-stone-lowlight',  'first_stone', 'lowlight',  'First lowlight',       'Premier creux',       463),
  (md5('achievements:first-stone-neutral')::uuid,   'first-stone-neutral',   'first_stone', 'neutral',   'First neutral pebble', 'Premier galet neutre', 464),
  (md5('achievements:first-stone-highlight')::uuid, 'first-stone-highlight', 'first_stone', 'highlight', 'First highlight',      'Premier temps fort',  465);

-- Inactive until part 3 ships pull_ceremony, their only way to unlock.
insert into public.achievements (id, slug, family, title_en, title_fr, is_active, sort_order) values
  (md5('achievements:complete-emotions')::uuid,   'complete-emotions',   'set_complete', 'Every emotion',  'Toutes les émotions',    false, 700),
  (md5('achievements:complete-domains')::uuid,    'complete-domains',    'set_complete', 'Every domain',   'Tous les domaines',      false, 710),
  (md5('achievements:complete-six-firsts')::uuid, 'complete-six-firsts', 'set_complete', 'The six firsts', 'Les six premières fois', false, 720);

insert into public.achievements (id, slug, family, threshold, sort_order)
select md5('achievements:pebble-count-' || t)::uuid, 'pebble-count-' || t, 'pebble_count', t, 170 + t / 500
from generate_series(1500, 10000, 500) t;

-- 3. silent karma (D7) ----------------------------------------------------------------
update public.achievements set karma_reward = 0
 where family in ('emotion_first','domain_first','first_stone');
alter table public.achievements add constraint achievements_silent_karma
  check (family not in ('emotion_first','domain_first','first_stone') or karma_reward = 0);

-- 4. the crossing pebble (D4) ------------------------------------------------------------
alter table public.achievement_unlocks
  add column pebble_id uuid references public.pebbles(id) on delete set null;
-- on delete set null scans for referencing rows: index them (partial, since
-- an unlock outside the pebble-backed families carries none).
create index achievement_unlocks_pebble_id_idx on public.achievement_unlocks (pebble_id) where pebble_id is not null;

-- 5. seen, per card (D10) ---------------------------------------------------------------
create table public.achievement_views (
  user_id uuid not null references auth.users(id) on delete cascade,
  card    text not null check (card in ('explorer','emotions','domains','pebbles','year')),
  seen_at timestamptz not null default now(),
  primary key (user_id, card)
);
alter table public.achievement_views enable row level security;
create policy achievement_views_select_self on public.achievement_views
  for select to authenticated using (user_id = auth.uid());
-- No write policies: mark_achievements_seen (part 3) is the only writer.

-- 6. completions (D9) --------------------------------------------------------------------
create table public.achievement_completions (
  user_id      uuid not null references auth.users(id) on delete cascade,
  stack        text not null check (stack in ('emotions','domains','six_firsts')),
  completed_at timestamptz not null default now(),
  pebble_id    uuid references public.pebbles(id) on delete set null,
  pulled_at    timestamptz,
  primary key (user_id, stack)
);
alter table public.achievement_completions enable row level security;
create policy achievement_completions_select_self on public.achievement_completions
  for select to authenticated using (user_id = auth.uid());
create index achievement_completions_pebble_id_idx on public.achievement_completions (pebble_id) where pebble_id is not null;
-- No write policies: check_achievements records, pull_ceremony (part 3) pulls.

-- 7. helpers ------------------------------------------------------------------------------
create or replace function public.achievement_stone_match(p_kind text, p_intensity int, p_positiveness int)
returns boolean
language sql
immutable
set search_path = ''
as $$
  select case p_kind
    when 'small'     then p_intensity = 1
    when 'medium'    then p_intensity = 2
    when 'large'     then p_intensity = 3
    when 'lowlight'  then p_positiveness = -1
    when 'neutral'   then p_positiveness = 0
    when 'highlight' then p_positiveness = 1
    else false
  end
$$;

-- The pebble that earned a row (D4). Counting: the threshold-th pebble by
-- completion time. Firsts: the earliest pebble of that kind by happened_at.
-- Called only from definer code (check_achievements, the backfill), so it reads
-- the user's rows regardless of RLS; it is not granted to clients.
create or replace function public.achievement_crossing_pebble(
  p_user uuid, p_family text, p_threshold int, p_emotion uuid, p_domain uuid, p_stone text)
returns uuid
language sql
stable
set search_path = ''
as $$
  select case p_family
    when 'pebble_count' then (
      select p.id from public.pebbles p where p.user_id = p_user
       order by p.created_at, p.id offset greatest(p_threshold, 1) - 1 limit 1)
    when 'first_pebble' then (
      select p.id from public.pebbles p where p.user_id = p_user
       order by p.created_at, p.id limit 1)
    when 'emotion_first' then (
      select p.id from public.pebbles p join public.moments m on m.id = p.moment_id
       where p.user_id = p_user and p.emotion_id = p_emotion
       order by m.happened_at, p.created_at, p.id limit 1)
    when 'domain_first' then (
      select p.id from public.pebbles p join public.moments m on m.id = p.moment_id
        join public.pebble_domains pd on pd.pebble_id = p.id
       where p.user_id = p_user and pd.domain_id = p_domain
       order by m.happened_at, p.created_at, p.id limit 1)
    when 'first_stone' then (
      select p.id from public.pebbles p join public.moments m on m.id = p.moment_id
       where p.user_id = p_user and public.achievement_stone_match(p_stone, p.intensity, p.positiveness)
       order by m.happened_at, p.created_at, p.id limit 1)
  end
$$;

revoke all on function public.achievement_crossing_pebble(uuid, text, int, uuid, uuid, text) from public, anon, authenticated;
grant execute on function public.achievement_stone_match(text, int, int) to authenticated;
