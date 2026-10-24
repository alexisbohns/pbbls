-- gem_allocate: the emotions gem's facet allocation (#1207, part 1).
--
-- A plpgsql port of GEM.allocate in scripts/oracles/path-wrap/gem-method.js,
-- step for step and in float8, so the same inputs give the same facets on
-- every client. The JS file is the oracle: scripts/verify-path-wraps.ts
-- compares this function with it facet by facet (fixed months and random ones).
-- Method: scripts/oracles/path-wrap/METHOD.md.
--
-- Input: the month's worth and pebble count per emotion family (category slug),
--   {"joy": {"worth": 14, "count": 7}, "peace": {"worth": 8, "count": 6}, ...}
-- Worth is intensity × charge (bright 1, heavy 1, even ½), computed by the
-- caller (path_month_wrap, part 4). Unknown keys are ignored; a missing or
-- non-positive worth means the family is absent.
--
-- Output:
--   {"main": "joy" | null, "rotation": 0..6,
--    "shares": {"joy": 0.341, ...}            -- all seven families
--    "facets": {"kite_0": "joy", "star_3": null, ...}}  -- the 28 data facets
-- The table facet carries the count and is never data, so it is not listed.
--
-- The stone is fixed: 7 kites, 7 stars, 14 girdle facets around a table, laid
-- end to end clockwise from the top as one ring weighted by nominal area. Each
-- present family gets a wedge of the ring proportional to its share; the main
-- family's wedge is centred on kite_0 (the wheel rotates so the main family
-- takes the crown), the others follow clockwise in the fixed order
-- joy · pride · anger · fear · shame · sadness · peace.

create or replace function public.gem_allocate(p_families jsonb)
returns jsonb
language plpgsql
immutable
set search_path = ''
as $$
declare
  ord constant text[] := array['joy', 'pride', 'anger', 'fear', 'shame', 'sadness', 'peace'];
  -- nominal facet areas of the round stone (GEM.AREA), copied from the oracle
  a_kite   constant float8 := 1034.8127177953763;
  a_girdle constant float8 := 516.2394149640708;
  a_star   constant float8 := 325.98255305140214;
  nf constant int := 28;

  ids  text[]   := '{}';
  area float8[] := '{}';
  f0   float8[] := '{}';
  f1   float8[] := '{}';
  ring_a float8;
  eps    float8;
  u      float8 := 0;

  w   float8[] := array[0, 0, 0, 0, 0, 0, 0];
  n   float8[] := array[0, 0, 0, 0, 0, 0, 0];
  s   float8[] := array[0, 0, 0, 0, 0, 0, 0];
  tot float8 := 0;
  top int;
  rot int := 0;

  walk int[] := '{}';
  w0  float8[] := array[0, 0, 0, 0, 0, 0, 0];
  w1  float8[] := array[0, 0, 0, 0, 0, 0, 0];
  tgt float8[] := array[0, 0, 0, 0, 0, 0, 0];
  held float8[] := array[0, 0, 0, 0, 0, 0, 0];
  owner int[] := array_fill(null::int, array[28]);
  cnt int[] := array[0, 0, 0, 0, 0, 0, 0];

  p int; i int; j int; k int; g int; guard int;
  best int; bc float8; c float8; x float8; y float8;
  moved boolean;
  pick int; pick_area float8; pick_cover float8;
  before_miss float8; after_miss float8;
  out_facets jsonb := '{}';
  out_shares jsonb := '{}';
begin
  -- the ring: kite_p, girdle_(2p+1), star_p, girdle_(2p+2 mod 14), clockwise from the top (GEM.CYCLE)
  for p in 0..6 loop
    ids  := ids  || array['kite_' || p, 'girdle_' || (2 * p + 1), 'star_' || p, 'girdle_' || ((2 * p + 2) % 14)];
    area := area || array[a_kite, a_girdle, a_star, a_girdle];
  end loop;
  for i in 1..nf loop
    f0 := f0 || u;
    u := u + area[i];
    f1 := f1 || u;
  end loop;
  ring_a := u;
  eps := 1e-6 * ring_a;

  -- worths, counts, shares
  for i in 1..7 loop
    w[i] := greatest(coalesce((p_families -> ord[i] ->> 'worth')::float8, 0), 0);
    n[i] := coalesce((p_families -> ord[i] ->> 'count')::float8, 0);
    tot := tot + w[i];
  end loop;
  for i in 1..7 loop
    if tot > 0 then s[i] := w[i] / tot; end if;
    out_shares := out_shares || jsonb_build_object(ord[i], s[i]);
  end loop;

  -- the main family: greatest worth; a tie goes to more pebbles, then to the earlier family in the order
  for i in 1..7 loop
    if w[i] > 0 and (top is null
                     or w[i] > w[top] + 1e-9
                     or (abs(w[i] - w[top]) <= 1e-9 and n[i] > n[top])) then
      top := i;
    end if;
  end loop;

  if top is null then
    for i in 1..nf loop
      out_facets := out_facets || jsonb_build_object(ids[i], null);
    end loop;
    return jsonb_build_object('main', null, 'rotation', 0, 'shares', out_shares, 'facets', out_facets);
  end if;

  -- rotate: the walk starts at the main family; absent families have no wedge
  rot := top - 1;
  for i in 0..6 loop
    k := ((i + rot) % 7) + 1;
    if w[k] > 0 then walk := walk || k; end if;
  end loop;

  -- wedges in ring coordinates: the first centred on kite_0's middle, then clockwise
  u := a_kite / 2 - s[walk[1]] * ring_a / 2;
  foreach k in array walk loop
    w0[k] := u;
    w1[k] := u + s[k] * ring_a;
    tgt[k] := s[k] * ring_a;
    u := u + s[k] * ring_a;
  end loop;

  -- each facet goes to the wedge covering most of it; a dead heat goes to the
  -- wedge that holds the facet's leading edge (the counter-clockwise one)
  for i in 1..nf loop
    best := null;
    bc := -1;
    foreach k in array walk loop
      c := greatest(0, least(f1[i], w1[k]) - greatest(f0[i], w0[k]))
         + greatest(0, least(f1[i], w1[k] - ring_a) - greatest(f0[i], w0[k] - ring_a))
         + greatest(0, least(f1[i], w1[k] + ring_a) - greatest(f0[i], w0[k] + ring_a));
      -- inside(f0 + eps, wedge k): ((x % A) + A) % A with JS fmod semantics, exact on |x| < 2A
      x := (f0[i] + eps) - w0[k];
      if abs(x) >= ring_a then x := x - sign(x) * ring_a; end if;
      y := x + ring_a;
      if y >= ring_a then y := y - ring_a; end if;
      if c > bc + eps or (abs(c - bc) <= eps and y >= 0 and y < w1[k] - w0[k]) then
        best := k;
        bc := c;
      end if;
    end loop;
    owner[i] := best;
  end loop;

  for i in 1..nf loop cnt[owner[i]] := cnt[owner[i]] + 1; end loop;

  -- minimum presence: a present family left with nothing takes the smallest facet its wedge touches
  -- (then the one it covers more, then the earlier one on the ring), from an owner that keeps one
  foreach k in array walk loop
    continue when cnt[k] > 0;
    pick := null;
    for i in 1..nf loop
      c := greatest(0, least(f1[i], w1[k]) - greatest(f0[i], w0[k]))
         + greatest(0, least(f1[i], w1[k] - ring_a) - greatest(f0[i], w0[k] - ring_a))
         + greatest(0, least(f1[i], w1[k] + ring_a) - greatest(f0[i], w0[k] + ring_a));
      continue when c <= eps or cnt[owner[i]] <= 1;
      if pick is null
         or area[i] < pick_area
         or (area[i] = pick_area and c > pick_cover) then
        pick := i; pick_area := area[i]; pick_cover := c;
      end if;
    end loop;
    if pick is not null then
      cnt[owner[pick]] := cnt[owner[pick]] - 1;
      owner[pick] := k;
      cnt[k] := cnt[k] + 1;
    end if;
  end loop;

  -- settle: move an end facet of a run to the neighbouring run when that lowers the total miss
  -- (first improving move along the ring, repeat until none; a family is never emptied)
  for i in 1..nf loop held[owner[i]] := held[owner[i]] + area[i]; end loop;
  for guard in 1..64 loop
    moved := false;
    for i in 1..nf loop
      exit when moved;
      k := owner[i];
      continue when cnt[k] < 2;
      foreach j in array array[case when i = 1 then nf else i - 1 end, case when i = nf then 1 else i + 1 end] loop
        g := owner[j];
        continue when g = k;
        before_miss := abs(held[k] - tgt[k]) + abs(held[g] - tgt[g]);
        after_miss := abs(held[k] - area[i] - tgt[k]) + abs(held[g] + area[i] - tgt[g]);
        if after_miss < before_miss - eps then
          owner[i] := g;
          held[k] := held[k] - area[i];
          held[g] := held[g] + area[i];
          cnt[k] := cnt[k] - 1;
          cnt[g] := cnt[g] + 1;
          moved := true;
          exit;
        end if;
      end loop;
    end loop;
    exit when not moved;
  end loop;

  for i in 1..nf loop
    out_facets := out_facets || jsonb_build_object(ids[i], ord[owner[i]]);
  end loop;
  return jsonb_build_object('main', ord[top], 'rotation', rot, 'shares', out_shares, 'facets', out_facets);
end;
$$;

comment on function public.gem_allocate(jsonb) is
  'The emotions gem (#1207): shares 28 fixed facets between emotion families by worth, rotated so the main family takes the crown. A port of scripts/oracles/path-wrap/gem-method.js, verified against it by verify-path-wraps.ts.';

revoke all on function public.gem_allocate(jsonb) from public, anon;
grant execute on function public.gem_allocate(jsonb) to authenticated;
