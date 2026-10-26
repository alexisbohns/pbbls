# iOS Home tab, People and Collections onboarding, and the guides table — design

- **Issues:** [#1254](https://github.com/alexisbohns/pbbls/issues/1254) (back-end and admin), [#1255](https://github.com/alexisbohns/pbbls/issues/1255) (iOS Home), [#1256](https://github.com/alexisbohns/pbbls/issues/1256) (iOS empty states); copy follow-ups #1257 (Android) and #1258 (web). Milestone M64 · New Path.
- **Date:** 2026-10-26
- **Surface:** `apps/ios` (lead), `packages/supabase` and `apps/admin` (the guides content and the Home RPCs). Android mirrors in a follow-up; the web is out of scope.
- **Size:** Large. Three stacks (§Stacks).
- **Depends on:** the four-tab IA (#903), the iOS Path wraps (#1226) and the month intro (#1241), the moments split (#1107–#1113).
- **Exploration:** six boards, private hub `https://claude.ai/artifact/HWtGYsDbj7pejcYSTN2p61`. The maintainer chose **H3 "Native, quietly"** for Home, **E3 "Start from what you have"** for People and **E2 "The explainer card"** for Collections.

## Summary

Home becomes the fifth tab, in first position, and the cold-start landing. It greets the user by name according to the hour and the weekday, offers three shortcuts (a camera moment, a quick moment, a full pebble), shows the finished months as a row of square cards that stay folded until the user unfolds them, walks a beginner through five firsts, and ends on short readings about Pebbles that the admin edits in a new `guides` table. The People tab, when it has no souls, reads the user's own pebbles and asks who was there. The Collections tab, when empty, carries one editorial note that explains collections with the real row and the three mode marks.

## Goal

- A new user who opens the app for the first time lands on a page that tells them what to do first, and can record a moment in one tap.
- A returning user lands on a page that greets them, lets them record in one tap, and shows the months they have not yet read.
- The People and Collections tabs never show a mute "No X yet" again.
- The About topics are editable from the admin without a release.

## Non-goals

- **Android and web.** The Android mirror is a follow-up issue; the web keeps its own landing.
- **A "This week" section on Sunday evening.** Cut. The greeting asks the question, the Path holds the answer.
- **Deep links into Home.** The invite link stays the only URL entry point.
- **Replaying wraps, sharing wraps, notifications when a wrap folds.**
- **A pack suggestion on Collections.** E2 explains the modes; nothing is inferred.
- **A claim step for any other achievement.** Only the blue pebble is claimed; everything else keeps unlocking by itself.
- **The web admin's own Markdown preview beyond what `LogForm` has.**

## Decisions

**D1. Home is a fifth tab, first position: Home · Path · People · Collections · You.** `AppTab` gains `case home` as its first case; `TabNavigation` gains a `home` path. The authed root opens on Home. Symbol `house`, EN "Home", FR "Accueil".
*Rejected:* replacing Path (the timeline is the product) and folding You into Home (profile and settings are not a landing).

**D2. Home is built from system parts wearing the Spine's type and paper.** Large-title greeting, three equal buttons, a horizontal row of square cards, inset grouped lists, medium and large sheet detents. Every list row carries an italic second line and a colourless drawn mark, never a coloured icon square. The only colour on the page comes from the pebbles in the wheels. This is the whole difference between Home and Settings, and it is a build rule, not a flourish.

**D3. The shortcuts never reorder.** Camera · Quick moment · New pebble, always in that order and size. Muscle memory beats a guess about the hour.

**D4. The gallery never hides.** With no finished month, it shows one dashed card with an empty wheel and the sentence "<Month> folds into your first wrap on the <last day>."

**D5. A folded month shows its name and a turned corner, and no wheel.** Its sheet shows only the pebble count and one filled "Unfold" button, with dashed outlines of the cards to come. Unfolding is recorded server-side once (`wrap_reveals`) and the card gets its wheel. Revealed state is per account, not per device, so iOS and Android agree.

**D6. The five firsts are achievements, and the checklist ends with a claim.** Each task is one one-shot achievement: `first-moment` and `first-pebble` are new families, `first-collection`, `first-soul` and `first-glyph` exist. They unlock by themselves through `check_achievements`, which Home fires on appear, and they stay unlocked whatever is deleted later. A sixth achievement, **the blue pebble** (`blue-pebble`, family `first_five`), is never unlocked by the check: it is claimed. Once the five are done the list shows one filled row, "Claim the blue pebble"; the claim calls `claim_blue_pebble()`, the usual unlock moment plays, and the whole section leaves Home for good. Until the claim, the finished list stays. The state is the unlocks, so iOS and Android agree and nothing lives in a device preference.
*Rejected:* counting live rows for the two firsts without an achievement. A count unticks when the only moment or pebble is deleted, while the three achievement-backed tasks would stay ticked; the checklist would lean on two different truths. Also rejected: hiding the list the day it completes, with the reread under You. The claim is the ceremony, and the achievement page already keeps the record.

**D7. About topics push full pages.** A chevron promises a push, and a Markdown body of any length reads better with a back button than on a sheet that can be swiped away. Fixed layout: large title, italic summary, optional illustration, Markdown body rendered through the same `AttributedString(markdown:)` path as the Lab's announcements.

**D8. The guides content lives in a `guides` table modelled on `logs`.** Published rows are readable by everyone, writes are admin-only, bilingual with EN required and FR falling back to EN. The illustration is an enum the clients draw from real components; the admin picks it from a list. No image upload.

**D9. The wraps gallery is a server projection; the checklist is the unlocks.** `home_wraps` returns the finished months with their cover data and their revealed flag; clients render. The checklist needs no RPC of its own: Home reads the catalog and the unlocks through the existing `AchievementsService` and keys the six rows by slug, in a fixed order that Android copies.

**D10. The People prompt is a server heuristic.** `people_prompts` returns the user's latest pebbles that have no soul, at least one outward domain and a positiveness of high or neutral. The outward set is a constant in the function body: `family, friends, partner, community, currentevents, dating, hobbies, fitness, travel`. A low-positiveness pebble is never asked about, whatever its domain. The client never re-implements the rule.

**D11. The People onboarding leaves at three souls or on "Not now"; the Collections note folds after the first collection and is gone after the second or on dismissal.** Both dismissals are device preferences; they are not data.

**D12. Camera moment = system camera, shutter time, no name.** The moment is saved by `save_moment` with `happened_at` = the shutter time, no name, and the photo as its one snap. A toast above the tab bar shows the photo, the time and "Name it"; it leaves after a few seconds and the moment waits on the Path.

**D13. Quick moment = a medium sheet; Save & open hands the moment to the Complete flow.** Name first, date and time prefilled to now. Save & close returns to Home. Save & open presents `CompleteFlowView(momentId:)` for that moment.

**D14. New pebble = Capture then Complete, uninterrupted.** `CaptureFlowView` saves the moment and, instead of dismissing, hands its id to `CompleteFlowView` in the same cover. Leaving mid-way keeps the moment (a moment is already a thing).

## 1. Home

### 1.1 Greeting

Two lines. The first is the large title, the second an italic line. Small caps above carry the date ("Monday 2 November"). The hour bands are morning (before 12), afternoon (12 to 18) and evening (from 18). The weekday lines override the band's default on Monday morning and Sunday evening.

| When | Title | Italic line |
|---|---|---|
| Monday morning | Morning, Alexis | Hope you'll have a pleasant week! |
| Sunday evening | Good evening, Alexis | How was your week? |
| morning | Morning, Alexis | A new day to keep something from. |
| afternoon | Good afternoon, Alexis | Halfway through the day. Anything worth keeping? |
| evening | Good evening, Alexis | What stays with you from today? |

The name is `profiles.display_name`. Lines live in `Localizable.xcstrings` (EN and FR); the greeting is computed in a pure `HomeGreeting.lines(date:name:calendar:)` with a test per band and per override. Nothing about the greeting touches the server.

### 1.2 Shortcuts

Three equal buttons under the greeting, 44 pt minimum, label under a drawn mark: Camera (`camera`), Quick moment (`clock`), New pebble (`plus.circle`). Behaviour: D12, D13, D14.

### 1.3 Monthly wraps

Section header "Monthly wraps" in small caps, with the count on the right. A horizontal `ScrollView` of square cards (about 128 pt), newest first. A revealed card holds the garland miniature (`MonthCoverGarland` drawn small, the same drawing as the month page's cover) with the pebble count inside, and the month name and year under it. A folded card holds "folded" in small caps, a rule where the wheel would be, a turned corner at the top right, and the name. With no finished month: D4.

Tapping a card opens `HomeWrapSheet` at the large detent: a title, the count and the system close button, then the deck only (`MonthDeck`, the same cards as the month page, with its intro off: no `IntroClock` in the environment, D2 of the month intro spec). The deck's data comes from `PathWrapService.monthWrap`, already cached by `PathModel`. A folded month: D5. Unfold calls `reveal_wrap`, then loads the deck in place.

### 1.4 Your first five

Section header "Your first five" with five dots and "n of 5". An inset list, one row per task, in this order: Record your first moment · Complete your first pebble · Start your first collection · Add your first soul · Carve your first glyph. Each row: a circle mark (dashed, or filled with a check when done), the task, an italic line, a chevron. Done rows: grey, no chevron, no tap.

Tapping a row opens a medium sheet with three parts: an illustration built from real components, two sentences, one filled button.

| Task | Illustration | Button → |
|---|---|---|
| moment | a contents row (day, seal, name, time) | the Quick moment sheet |
| pebble | a moment row becoming a lit stone | the Complete flow on the latest moment without a pebble, or the Capture flow when there is none |
| collection | three seals and one `CollectionRow` | the Collections tab, with its add sheet open |
| soul | three glyph faces above a pebble row carrying one of them | the People tab, with its add sheet open |
| glyph | a dashed ring with a carved stroke | the You tab, pushed to Glyphs, carve cover open |

Cross-tab navigation goes through `TabNavigation` (select the tab, append the route) plus a one-shot "present the add sheet" flag on the destination's navigation state.

Home fires `AchievementsService.fireCheck()` on appear and after each shortcut completes, so a task ticks as soon as the data exists. When the five are unlocked the list gains its sixth row, "Claim the blue pebble", filled, with a drawn blue pebble mark. Tapping it calls `claim_blue_pebble()`; the result feeds `presentUnlockMoment` like any unlock, and the section is removed from Home (D6). The blue pebble then appears on the Achievements page and in the public profile like any other.

### 1.5 About Pebbles

Section header "About Pebbles". An inset list of the published guides in `sort_order`, each row: a drawn mark for its illustration, the title, the summary in italics, a chevron. Pushes `GuidePage` (D7). The nine seeded topics: About Pebbles, About the stones, About emotions, About valence, About domains, About collections, About souls, About glyphs, About karma. The seed carries EN and FR summaries and a short EN body for each; the maintainer rewrites them in the admin.

### 1.6 Loading and errors

Home renders its static parts (greeting, shortcuts, About header) immediately. The three server projections load in one `.task`, stale-while-revalidate like the Path wraps: each section keeps its last value while reloading, and shows a quiet italic line on error ("Couldn't load your months.") with a retry. A guide list that fails to load shows nothing under its header rather than an error; the page must never be mostly error text.

## 2. People, empty (E3)

States, decided by `people_prompts`'s totals:

1. **No pebbles.** Kicker "Nobody named yet", three lines (a soul is a person or a being in your life; you name them on a pebble; every moment you share gathers on their page), one link "Record a first moment" (the Quick moment sheet), one dashed `SoulItem` placeholder.
2. **Pebbles, no souls.** Kicker, the italic question "Who was there?", the three-line definition, then a card "Your latest pebbles" listing up to three prompted pebbles as the Spine's contents rows (day, seal, name) each with a "who?" affordance that opens `EditPebbleSheet` on that pebble with the souls field focused. Under the card, when the rule skipped a recent pebble, one italic line "Not asked: <name>. Nothing in it points outward." Below, the dashed `SoulItem` showing the cell a tag would create, with two sentences.
3. **One or two souls.** The real grid, and under it a smaller card "Was someone there too?" with up to two prompted pebbles and a "Not now" link. Mutual connection is mentioned once here, as optional.
4. **Three souls, or Not now.** The real grid only. "Not now" is `@AppStorage "people.prompts.dismissed"`.

The `+` stays in the toolbar in every state.

## 3. Collections, empty (E2)

One card centred on the page, the rest bare paper. Kicker "collections · a note". One italic sentence: "A collection gathers pebbles along a thread: a trip, a project, a year with someone." Three numbered steps drawn with real parts: (1) three seals, "Pick pebbles that belong together."; (2) the three mode marks with their names (stack = a pile, pack = a set with one place still empty, track = stones along a line); (3) one real `CollectionRow`. An aside: "A pebble can be in several collections. Deleting a collection never deletes its pebbles." One text link "Start a collection" that opens `CreateCollectionSheet`.

After the first collection: the card goes; one line stays at the foot of the list ("Try a track for something that unfolds in time.") with a dismiss. After the second collection, or a dismissal (`@AppStorage "collections.note.dismissed"`), nothing.

The card has a word budget (the sentence, three step captions of one line, the aside of two sentences) and scrolls with the page at large Dynamic Type.

## 4. Server

All in `packages/supabase`, one migration per part, each with its harness (§Testing).

### 4.1 `guides`

```sql
create table public.guides (
  id uuid primary key default gen_random_uuid(),
  slug text not null unique,            -- 'pebbles', 'stones', 'emotions', 'valence', 'domains', 'collections', 'souls', 'glyphs', 'karma'
  sort_order smallint not null default 0,
  illustration text check (illustration in ('pebbles','stones','emotions','valence','domains','collections','souls','glyphs','karma')),
  title_en text not null, title_fr text,
  summary_en text not null, summary_fr text,
  body_md_en text not null, body_md_fr text,
  published boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
```

RLS copies `logs`: select where `published or is_admin(auth.uid())`; insert, update, delete `is_admin`. Trigger `set_updated_at`. Index on `(published, sort_order)`. The seed inserts the nine rows, published. Clients read with a direct select (`published` rows, ordered), which is a single-table read; no RPC.

### 4.2 Three achievements and `claim_blue_pebble()`

The `achievements.family` check gains `first_moment`, `first_pebble` and `first_five`; `admin_create_achievement`'s one-shot family list (`20260730150000`, the `p_family in (...)` branch) gains the same three, so the admin can rename them. Three rows are seeded, `is_active`, with a karma reward the maintainer sets in the admin afterwards:

| slug | family | unlocks |
|---|---|---|
| `first-moment` | `first_moment` | by `check_achievements`: `exists (select 1 from moments where user_id = v_uid)` |
| `first-pebble` | `first_pebble` | by `check_achievements`: `st.pebble_count >= 1` |
| `blue-pebble` | `first_five` | only by `claim_blue_pebble()` |

`check_achievements` gains the two new cases; `first_five` falls to `else false`, so the check can never hand it out. `claim_blue_pebble()` is `security definer`, `returns table (slug text, karma_granted integer)` like the check: it requires the five one-shot unlocks (`first-moment`, `first-pebble`, `first-collection`, `first-soul`, `first-glyph`) for `auth.uid()`, otherwise raises; then inserts the `blue-pebble` unlock with `on conflict do nothing` and pays its karma in the same statement, the check's exactly-once mechanics. A second call returns no row.

Clients compose achievement copy by family (`Achievement+Localized.swift` on iOS, `AchievementCopy.kt` on Android, `useAchievementCopy` on the web, `lib/achievements/types.ts` in the admin). The three new families need copy on every surface, or they render as their slug on the Achievements page and the public profile. iOS ships it in stack 2; Android (#1257) and web (#1258) copy are filed as follow-ups, since the public profile exposes the unlock as soon as it exists.

### 4.3 `wrap_reveals`, `reveal_wrap(p_month date)`, `home_wraps(p_tz text)`

```sql
create table public.wrap_reveals (
  user_id uuid not null references auth.users(id) on delete cascade,
  month date not null check (month = date_trunc('month', month)::date),
  revealed_at timestamptz not null default now(),
  primary key (user_id, month)
);
```

Owner-only RLS on select; writes only through `reveal_wrap`, which upserts for `auth.uid()` and ignores a month that is not finished in `p_tz` (it returns the row either way). `home_wraps(p_tz)` returns, newest first, every finished month in the user's zone that holds at least one pebble: `{"month": "2026-09-01", "count": 18, "revealed_at": timestamptz|null, "cover": <the same object path_month_wrap returns as cover>}`. The cover comes from `path_month_cover(p_month, p_tz)`, the cover CTE of `path_month_wrap` lifted into its own light function; `path_month_wrap` adopts it at its next re-emission, and until then the harness asserts per month that `home_wraps`' cover equals `path_month_wrap(...)->'cover'`, so drift fails CI. The list is capped at the twelve most recent finished months with a pebble.

`purge_account` gains `wrap_reveals` in the same migration, appended at its in-body marker, and `verify-account-purge.ts` gains its seed and zero-row assertion. The batch is applied after a pairwise diff of any other pending `purge_account` re-emission.

### 4.4 `people_prompts(p_limit int default 3)`

`security invoker` like the Path wraps (RLS stays the backstop; every predicate is also scoped to the caller), `stable`, `returns jsonb`: `{"pebbles": n, "souls": n, "prompts": [{"pebble_id", "moment_id", "name", "happened_at", "intensity", "positiveness", "emotion_id"}], "skipped": [{"name"}]}` (`positiveness` is the stored integer, 1 or 0 here, the same convention as the month cover's `polarity`; `happened_at` at whole seconds UTC). Prompts are the user's latest pebbles (by `happened_at desc`) with no row in `pebble_souls`, at least one domain in the outward set (D10), `valence in ('high','neutral')` and a moment that is not hidden, limited to `p_limit`. `skipped` holds the names of up to two more recent soul-less pebbles that the rule left out, so the client can say so. The outward set is a constant array in the body with a comment pointing here.

## 5. Admin

`apps/admin/app/(authed)/guides/`: a list page (slug, title EN, published switch, sort order, drag or numeric order), `new/` and `[id]/` with a `GuideForm` built the way `LogForm` is (EN and FR columns, `MarkdownField` for the bodies, a `Select` for the illustration, a `Switch` for published). Server actions mirror `logs/actions.ts`. A sidebar entry "Guides" in the Logs group. The playground gets a `guides` fixture section with dense and empty variants.

## 6. iOS structure

```
Features/Home/
  HomeView.swift              the tab root: greeting, shortcuts, wraps, checklist, about
  HomeModel.swift             @Observable: loads checklist, wraps, guides; stale-while-revalidate
  HomeGreeting.swift          pure greeting rules + tests
  Shortcuts/                  HomeShortcuts, QuickMomentSheet, CameraMomentCoordinator, MomentToast
  Wraps/                      HomeWrapCard, HomeWrapsRow, HomeWrapSheet
  Checklist/                  HomeChecklist, ChecklistTask, ChecklistTaskSheet (+ the five illustrations)
  Guides/                     GuidesList, GuidePage, GuideIllustration
Features/Profile/Lists/       SoulsListView gains PeopleOnboarding (E3); CollectionsListView gains CollectionsNote (E2)
Services/HomeService.swift    the three RPC calls + the guides select, behind a protocol for the fixtures
```

The Spine Lab gets a Home tab with the fixture states (new user, returning, 5/5, folded sheet) so the references pin them.

## 7. Stacks

**Stack 1 · backend and admin** (`packages/supabase`, `apps/admin`), four parts: (1) `guides` table, RLS, seed, types, `verify-guides.ts`; (2) the three achievements, `claim_blue_pebble` and `people_prompts`, `verify-home.ts`; (3) `wrap_reveals`, `reveal_wrap`, `home_wraps`, the shared cover function, purge, harnesses; (4) admin `/guides`.

**Stack 2 · iOS Home**, six parts: (1) the fifth tab and an empty `HomeView` with the greeting; (2) shortcuts: quick moment, new pebble chaining, camera moment and toast; (3) the wraps row and sheet; (4) the checklist, task sheets, cross-tab navigation, the claim and the three families' copy; (5) guides list and page; (6) Spine Lab Home tab and references.

**Stack 3 · iOS empty states**, two parts: (1) People (E3); (2) Collections (E2).

Stack 2 depends on stack 1 parts 1–3; stack 3 part 1 depends on stack 1 part 2. Each part is one PR, each PR lints, typechecks and tests alone. Arkaik: a `V-home` view node with its acceptances,  `API-rpc-claim-blue-pebble`, `API-rpc-home-wraps`, `API-rpc-reveal-wrap`, `API-rpc-people-prompts`, a `guides` data model, and the People and Collections views gain an acceptance each.

## 8. Testing

- **Harnesses** (`npm run db:verify --workspace=packages/supabase`): `verify-guides.ts` (anon reads published only, admin writes, FR falls back), `verify-home.ts` (the two new families unlock on the first moment and the first pebble and survive their deletion; `claim_blue_pebble` raises before the five, unlocks once, pays once, and the check never grants it; prompts honour the outward set, the valence guard, the no-soul filter and hidden moments; `home_wraps` equals `path_month_wrap.cover` per month; `reveal_wrap` ignores an unfinished month). `verify-account-purge.ts` runs manually after part 3.
- **iOS** (Swift Testing): `HomeGreeting` per band and override, in EN and FR; the checklist rows from catalog and unlock fixtures, including the claim row and the removed section; the prompt states from fixture payloads written verbatim by the harness (`HOME_DUMP=1`), including explicit nulls; references for Home's four states and both empty states in Paper and Night.
- **Admin**: the playground section; ESLint and Vitest as on every PR.

## 9. Risks

- **Settings look.** Dropped italic lines or coloured icons turn Home into Settings. The references pin the look; the review checks it.
- **Cover drift.** If `home_wraps` built its own cover, the card and the month page would disagree. The shared function and the equality assertion are the guard.
- **Prompt on a painful pebble.** The valence guard is deliberately crude. The harness pins it; a real-account pass before TestFlight.
- **Camera and the snap coordinator.** The shutter path reuses `SnapUploadCoordinator`; a failed upload keeps the moment and shows the existing retry.
- **`save_moment` without a name.** `moments.name` is nullable and the writer trims to null. Part 2 of stack 2 asserts it against the live project before building the toast.
