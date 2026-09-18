-- ---------------------------------------------------------------------------
-- #870 — give `glyphs.strokes` the shape all four clients already require.
--
-- Symptom: the Android glyph picker and Profile → Glyphs showed "Couldn't load
-- glyphs." for every user, including brand-new accounts.
--
-- Cause: `verify-account-purge.ts` seeded its fixture glyphs with
-- `[{"points": [[20,20],[180,180]]}]` — a stroke shape no client model accepts.
-- `purge_account` then anonymized the sold one (`set user_id = null`, which is
-- deliberate: a sold glyph must outlive its creator because buyers hold
-- entitlements), and `user_id is null` *is* a system glyph. Android is the only
-- surface that lists system glyphs (a named deviation, M43 design D7) and it
-- decodes the whole result set before filtering, so one undecodable row took
-- the entire picker down. Six had accumulated, one per manual harness run.
--
-- `glyphs.strokes` is jsonb with no constraint, written straight through
-- PostgREST, so nothing stopped the shape from entering. The clients all
-- declare `d: string` and `width: number` as required (android
-- `GlyphStroke.kt`, ios `GlyphStroke.swift`, web `MarkStroke`), and the
-- server-side compositor re-emits against the same pair (#829/#830). That
-- agreement is the contract; this makes it a constraint instead of a
-- convention.
-- ---------------------------------------------------------------------------

-- ---------------------------------------------------------------------------
-- 1. Clear the residue.
--
-- Scoped to ownerless rows: a malformed glyph that still has an owner is a
-- user's drawing, and a migration has no business deleting one silently — it
-- would fail loudly at step 2 instead, which is the right way to find out.
-- Referenced rows are equally safe: `souls.glyph_id` is ON DELETE RESTRICT and
-- `pebbles`/`domains`/`achievements` are NO ACTION, so a glyph anyone actually
-- uses raises an FK error here rather than disappearing. Verified against the
-- linked project: 133 glyph rows, these 6 the only offenders, referenced by
-- nothing but their own (already delisted) `glyph_submissions` rows, which
-- cascade.
-- ---------------------------------------------------------------------------
delete from public.glyphs g
 where g.user_id is null
   and not (
     jsonb_typeof(g.strokes) = 'array'
     and not jsonb_path_exists(
       g.strokes,
       '$[*] ? (!exists(@.d) || !exists(@.width) || @.d.type() != "string" || @.width.type() != "number")'
     )
   );

-- ---------------------------------------------------------------------------
-- 2. The contract: every stroke carries a string `d` and a numeric `width`.
--
-- Written as an inline jsonpath rather than a helper function on purpose. A
-- CHECK that calls a function is evaluated with the *inserting* role's
-- privileges, so the day someone revokes EXECUTE on that function as routine
-- hardening, every glyph insert starts failing with "permission denied" —
-- verified, and exactly the kind of tripwire a constraint should not carry.
-- Built-ins have no such dependency.
--
-- An empty array stays valid (a blank glyph is a real thing), and extra keys
-- are tolerated so a future stroke attribute does not need a migration to land.
-- ---------------------------------------------------------------------------
alter table public.glyphs
  add constraint glyphs_strokes_shape check (
    jsonb_typeof(strokes) = 'array'
    and not jsonb_path_exists(
      strokes,
      '$[*] ? (!exists(@.d) || !exists(@.width) || @.d.type() != "string" || @.width.type() != "number")'
    )
  );

comment on constraint glyphs_strokes_shape on public.glyphs is
  'Every stroke must decode as {d: string, width: number} — the shape android '
  'GlyphStroke.kt, ios GlyphStroke.swift and web MarkStroke all declare '
  'required. Empty array = blank glyph (valid); extra keys tolerated. #870.';
