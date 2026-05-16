# iOS Profile redesign + Settings sheet — design

**Date:** 2026-05-16
**Status:** Approved (scope), pending implementation
**Closes:** #445 (via Issue 2)
**Replaces:** the legacy `ProfileView.swift` layout (Bounce/Karma stat rows + List sections)

## Goal

Transform the iOS Profile screen from a utilitarian `List` of stat/nav rows into a "comfy, beautiful" surface organised around the user's identity (banner), their engagement (Ripples + assiduity + counters), and their content (Collections, Lab). Extract the legal/account-management items into a dedicated Settings sheet that mirrors the Edit-mode pattern used elsewhere in the app.

## Non-goals

- **Stats page** — the destination behind the Stats card chevron is deferred to its own future issue. The chevron is suppressed in this batch.
- **Account deletion**, **notification preferences**, **language override** — not in the Settings sheet.
- **User-level glyphs** — the new glyph FK lives on `profiles`, not `auth.users`.
- **Removing `bounce` from the database or admin analytics** — only the iOS surface is retired.

## Scope decomposition

This work ships as **three sequential issues**, each a vertical slice that leaves the app in a consistent state:

1. **Profile data foundations** — schema + RPCs only, no UI.
2. **Profile screen redesign** — full `ProfileView` rewrite, consumes Issue 1. Closes #445.
3. **Settings sheet** — new sheet wired to the gear button stubbed in Issue 2.

Each issue has its own branch, PR, and implementation plan. Issue 2 cannot start until Issue 1 is merged and types regenerated. Issue 3 can technically begin in parallel with Issue 2 but is cleaner sequentially.

---

## Issue 1 — Profile data foundations

**Branch:** `feat/<n>-profile-data-foundations`
**Surface:** SQL + generated types only. No Swift.

### Migration

New file under `packages/supabase/supabase/migrations/`:

```sql
-- Add the profile glyph FK (nullable; existing users start without one).
alter table public.profiles
  add column glyph_id uuid references public.glyphs(id) on delete set null;

create index profiles_glyph_id_idx on public.profiles(glyph_id);
```

### `update_profile` RPC (new)

No such RPC exists today. Profile name is currently set only via the auth trigger. We need a single multi-field updater so we can keep `display_name` and `glyph_id` writes atomic and consistent.

```sql
create or replace function public.update_profile(
  p_display_name text default null,
  p_glyph_id     uuid default null
) returns public.profiles
language plpgsql
security definer
set search_path = public
as $$
declare
  updated public.profiles;
begin
  update public.profiles
    set display_name = coalesce(p_display_name, display_name),
        glyph_id     = case
                         when p_glyph_id is not null then p_glyph_id
                         else glyph_id
                       end,
        updated_at   = now()
    where user_id = auth.uid()
    returning * into updated;

  if updated is null then
    raise exception 'profile_not_found' using errcode = 'P0002';
  end if;

  return updated;
end;
$$;

grant execute on function public.update_profile(text, uuid) to authenticated;
```

**Caveat about `glyph_id`:** `coalesce(p_glyph_id, glyph_id)` is wrong here — the caller may want to unset the glyph in the future (pass null intentionally). Because we currently have no "remove glyph" UX, we accept the asymmetry: a null arg means "don't change". If a future issue needs to clear the glyph, add a separate `p_clear_glyph boolean` parameter rather than re-interpreting null.

### `get_profile_engagement` RPC (new)

```sql
create or replace function public.get_profile_engagement(p_tz text)
returns table (
  days_practiced int,
  assiduity      boolean[]
)
language plpgsql
security definer
set search_path = public
as $$
declare
  v_today     date;
  v_user_id   uuid := auth.uid();
  v_practiced int;
  v_grid      boolean[];
begin
  -- Resolve "today" in the caller's timezone.
  v_today := (now() at time zone p_tz)::date;

  -- Distinct day-buckets in the user's tz, across all-time pebbles.
  select count(distinct (created_at at time zone p_tz)::date)
    into v_practiced
    from public.pebbles
    where user_id = v_user_id;

  -- 28-element bool array: index 1 = 27 days ago, index 28 = today.
  -- (Postgres arrays are 1-indexed; clients consume in order.)
  with active_days as (
    select distinct (created_at at time zone p_tz)::date as d
      from public.pebbles
      where user_id = v_user_id
        and created_at >= (v_today - interval '27 days') at time zone p_tz
  ),
  window as (
    select generate_series(v_today - interval '27 days', v_today, interval '1 day')::date as d
  )
  select array_agg(active_days.d is not null order by window.d)
    into v_grid
    from window
    left join active_days using (d);

  return query select v_practiced, v_grid;
end;
$$;

grant execute on function public.get_profile_engagement(text) to authenticated;
```

**Timezone contract:** caller passes an IANA tz string (e.g. `"Europe/Paris"`). All bucketing happens server-side. The client must pass `TimeZone.current.identifier`; the SQL never assumes UTC for display.

### Regenerate types

```bash
npm run db:types --workspace=packages/supabase
git add packages/supabase/types/database.ts
```

### Done when

- Migration applied to remote Supabase (no local Docker — see project memory).
- Both RPCs callable from `@pbbls/supabase` client with correct types.
- `database.ts` regenerated and committed.

---

## Issue 2 — Profile screen redesign (closes #445)

**Branch:** `feat/445-profile-screen-redesign` (rename #445 if the title no longer fits)
**Surface:** iOS only. Depends on Issue 1.

### Ripples primitives relocation

Move from `Features/Path/` to a new shared folder so Profile can consume them without depending on Path's internal layout:

- `Features/Path/Components/RippleBadge.swift`        → `Features/Shared/Ripples/RippleBadge.swift`
- `Features/Path/Components/RippleStrokes.swift`      → `Features/Shared/Ripples/RippleStrokes.swift`
- `Features/Path/Components/RippleStrokeColor.swift`  → `Features/Shared/Ripples/RippleStrokeColor.swift`
- `Features/Path/Models/RippleSummary.swift`          → `Features/Shared/Ripples/RippleSummary.swift`

Asset colors (`RippleDefault`, `RippleInactive`, `RippleActive`) stay in `Resources/Assets.xcassets/` (already shared). Update `project.yml` if it pins the old paths, then `xcodegen generate`. `PathView`'s import path updates; no behavior change.

This is a relocation, not a refactor — it's the minimum required to give Profile a clean consumer path. No other refactoring of Path is in scope.

### New `ProfileView.swift`

Replace the entire body. Top-level structure (top → bottom):

| Section            | Component                  | Notes                                                                |
|--------------------|----------------------------|----------------------------------------------------------------------|
| Toolbar            | inline                     | back chevron · "PROFILE" title · gear button → opens Settings sheet  |
| Banner             | `ProfileBanner`            | glyph thumbnail · `display_name` · "MEMBER SINCE <date>"             |
| Shortcuts          | `ProfileShortcutTile` × 3  | Collections · Souls · Glyphs (each → existing list view)             |
| Stats card         | `ProfileStatsCard`         | wraps `RipplesRow` + divider + `ProfileCountersRow`                  |
| Collections card   | `ProfileCollectionsCard`   | horizontal scroll of `ProfileCollectionCard`, or "new" placeholder   |
| Lab card           | `ProfileLabCard`           | nav to existing `LabView`                                            |
| Logout             | pill button                | calls `supabase.signOut()`                                           |

#### Settings button (gear)

Stubbed in this issue: opens a placeholder sheet (`Text("Settings — coming in #<issue-3>")`) so the navigation entry exists and the visual is testable. Issue 3 swaps the sheet body.

#### Banner

- Uses `GlyphThumbnail` (existing, from `Features/Glyph/Views/`) for the profile glyph.
- Empty state (no `glyph_id`): renders a neutral placeholder (existing `GlyphThumbnail` empty mode if one exists, otherwise a tinted rounded rect with a `scribble` SF Symbol). Confirm during implementation.
- "Member since" formats `profiles.created_at` with `.formatted(date: .abbreviated, time: .omitted)` — `Locale.current` handles localization.

#### Stats card

- Header: "STATS" (label only). **No chevron** until the stats page exists. When Issue 4 (stats page) lands, the chevron + `NavigationLink` is added back.
- **Ripples row:** shared `RippleBadge(level:)` on the left, "Ripples Level <N>" + engagement copy on the right of the badge, 4×7 `AssiduityGrid` on the far right.
  - Engagement copy uses the same level-derivation as the Path nav-bar badge (single source of truth in `RippleSummary`). If the "days to reach level X" calculation isn't already exposed by `RippleSummary`, extend `RippleSummary` to provide it — do not duplicate the formula in Profile.
- **Counters row:** three columns — Days practiced, Pebbles, Karma. Each: number (large) · icon · label (small). `daysPracticed` comes from Issue 1's RPC; `pebbles` count and `karma` come from the existing service.

#### Collections card

- Header: "COLLECTIONS" + chevron → existing `CollectionsListView`.
- **Engaged state:** horizontal `ScrollView(.horizontal, showsIndicators: false)` of `ProfileCollectionCard` (icon · name · "<N> pebbles"). Tapping a card navigates to `CollectionDetailView` for that collection.
- **New-user state:** single dashed-border `ProfileCollectionCard` showing "New / Create collection" → opens existing `CreateCollectionSheet`.
- Data source: reuse whatever `CollectionsListView` uses. If that fetch isn't extractable into a small service, leave it inline in the view for now — don't preemptively abstract.

#### Lab card

- Single card visual: egg/lab glyph · "Lab" title · "News & Community" subtitle · chevron.
- Destination: existing `LabView`.

### New components (under `Features/Profile/Components/`)

- `ProfileBanner.swift`
- `ProfileShortcutTile.swift`
- `ProfileStatsCard.swift`
- `RipplesRow.swift`
- `AssiduityGrid.swift` — props: `data: [Bool]`, `columns: Int = 7`. Renders the ripple/squiggle motif from Image 5. Same component used later in the stats view with different `columns`.
- `ProfileCountersRow.swift`
- `ProfileCollectionsCard.swift`
- `ProfileCollectionCard.swift`
- `ProfileLabCard.swift`

### Deletions (no longer referenced)

- `Features/Profile/Components/ProfileStatRow.swift`
- `Features/Profile/Components/ProfileNavRow.swift`
- `Features/Profile/Sheets/BounceExplainerSheet.swift`
- `Features/Profile/Sheets/KarmaExplainerSheet.swift`
- `Features/Profile/Models/BounceSummary.swift`

`BounceSummary`'s service-layer fetch (if any) also goes. The DB column and admin analytics are untouched.

### Service changes

Extend `PathStatsService` (which already owns Ripples + Karma) with:

- `daysPracticed: Int?`
- `assiduity: [Bool]?`
- A single new method that calls `get_profile_engagement(p_tz: TimeZone.current.identifier)` and populates both, alongside the existing Ripples load.

The service name "PathStatsService" becomes a slight misnomer once it serves Profile too — leave the rename for a follow-up issue if it bothers anyone. Do **not** rename as part of this work (scope creep).

### Localization

All new user-facing strings added to `Pebbles/Resources/Localizable.xcstrings` with `en` + `fr` values. Confirm zero `New` / `Stale` entries before opening the PR (per `apps/ios/CLAUDE.md`).

### Arkaik

Profile is an existing screen; the visual rewrite alone doesn't change the product graph. However, **the Settings sheet entry point is new** — add it under the Profile node when Issue 3 ships, not here. No Arkaik update needed for Issue 2.

### Done when

- Profile screen matches the engaged-user and new-user mockups.
- `RippleBadge` in the Path nav bar renders identically (no regression).
- Bounce row + sheets + explainers fully removed from iOS surface; #445 closes.
- `daysPracticed` and `assiduity` populate correctly across timezone changes (manual test: device tz set to `Pacific/Auckland`, verify "today" lights up).
- New strings present in both locales.

---

## Issue 3 — Settings sheet

**Branch:** `feat/<n>-profile-settings-sheet`
**Surface:** iOS only. Depends on Issue 2 (the gear button + stub sheet exist).

### Presentation

Replaces the stub from Issue 2. Sheet presented from `ProfileView`'s gear button. Uses the Edit-mode toolbar pattern (`Cancel` left, `Save` right, "SETTINGS" title centred). `Save` is disabled until dirty; dismisses on success.

### Sections

| Section        | Visibility                          | Fields                                                              |
|----------------|-------------------------------------|---------------------------------------------------------------------|
| Header         | always                              | large profile glyph, tappable → opens `GlyphPickerSheet`            |
| Informations   | always                              | `display_name` (editable TextField), email (read-only, dimmed)      |
| Providers      | SSO-only (≥1 non-email identity)    | one row per linked provider: icon + label (Apple ID / Google …)     |
| Password       | email-only (no SSO identity)        | "Current password" + "New password" (secure fields)                 |
| Legal          | always                              | Terms · Privacy (open existing `LegalDocumentSheet`)                |

### Glyph picker entry point

Tapping the header glyph opens the existing `GlyphPickerSheet`. The user picks an existing glyph; if they want a new one, the picker already has a "create" affordance leading to `GlyphCarveSheet`. The new selection is held in local state and only persisted on `Save`.

### Provider detection

```swift
let identities = supabase.auth.currentSession?.user.identities ?? []
let nonEmailProviders = identities.filter { $0.provider != "email" }
let isSSO = !nonEmailProviders.isEmpty
```

Provider icons: use existing Apple/Google logos from the Welcome/Auth flow (`Features/Auth/`). If they aren't extracted yet, copy the SF Symbol or asset name from the sign-in buttons — don't introduce new artwork.

### Save action

In order, inside one `Task`:

1. If `display_name` or `glyph_id` changed: call `update_profile(p_display_name:, p_glyph_id:)`.
2. If password fields are non-empty: call `supabase.auth.update(user: .init(password: newPassword))` (Swift SDK syntax — confirm exact API during implementation).
3. On any error: log via `os.Logger`, surface inline (red text under the relevant section), keep sheet open.
4. On full success: dismiss.

The password change does **not** require re-authentication in the Supabase Swift SDK as long as the session is fresh — but if the API surfaces a "needs recent login" error, surface it inline and let the user re-auth from the Welcome flow (out of scope here).

### Localization

All new strings → `Localizable.xcstrings` with `en` + `fr`.

### Arkaik

Update `docs/arkaik/bundle.json`: add a `SettingsSheet` node under the Profile screen, edge `opens_settings` from Profile.

### Done when

- Sheet renders the SSO variant (mockup 3) for accounts with linked providers.
- Sheet renders the email variant (mockup 4) for password-only accounts.
- Save persists name + glyph + (optional) password atomically per the success/error rules above.
- Legal sheets work identically to today.

---

## Open questions

These do not block the spec but should be answered in each issue's implementation plan:

1. **Banner glyph placeholder visual** — does `GlyphThumbnail` already have an empty state, or do we add one? (Issue 2)
2. **Engagement copy localization** — "3 days to reach level 6" pluralization in French requires Stringsdict-style handling. Confirm format strings cover singular/plural in both locales. (Issue 2)
3. **Password change API** — exact Supabase Swift SDK signature for `auth.update(user:)` and its error shape for "needs recent login". (Issue 3)
4. **"Current password" verification** — Supabase doesn't require it server-side for `auth.update(user:)`; the mockup shows the field anyway. Decide: (a) cosmetic only (ignore the value), (b) client-side re-verify via a no-op `signIn` before calling `update`, or (c) drop the field from the form. Default: (b) — most user-trustworthy. (Issue 3)
5. **`update_profile` parameter naming** — settling on `p_display_name` / `p_glyph_id` to match the project's existing RPC convention (verify by reading a sibling RPC like `create_pebble` before writing the migration). (Issue 1)
6. **"Replay onboarding" affordance** — currently sits under Profile's Legal section. The Settings mockup omits it. Decide: (a) drop entirely (devs/internal can still trigger via debug builds), (b) keep in Profile as a small footer link, or (c) add it back to Settings → Legal. Default: (a) — matches the mockup; if anyone needs it, restore in a tiny follow-up. (Issue 2 or 3)

## Files to touch (summary)

**Issue 1:** new migration, `packages/supabase/types/database.ts` (regenerated).

**Issue 2:**
- Rewritten: `apps/ios/Pebbles/Features/Profile/ProfileView.swift`
- Moved: 4 files from `Features/Path/{Components,Models}/Ripple*` → `Features/Shared/Ripples/`
- New: 9 components under `Features/Profile/Components/`
- Deleted: `ProfileStatRow.swift`, `ProfileNavRow.swift`, `BounceExplainerSheet.swift`, `KarmaExplainerSheet.swift`, `BounceSummary.swift`
- Modified: `Features/Path/Services/PathStatsService.swift`, `project.yml` (then `xcodegen generate`), `Localizable.xcstrings`. `PathView.swift` only changes if its imports become qualified — most likely no change since everything is in the same module.

**Issue 3:**
- New: `apps/ios/Pebbles/Features/Profile/Sheets/SettingsSheet.swift` (+ optional helper components)
- Modified: `ProfileView.swift` (swap stub sheet for real one), `Localizable.xcstrings`, `docs/arkaik/bundle.json`
