# Collections with domains on iOS — design

- **Issues:** #1705 (back-end), #1706 (iOS), #1707 (Android mirror). #1706 absorbs #1275 and the iOS half of #1299; #1707 absorbs the Android half of #1299 and replaces the Collections half of #1301. The web is out of scope.
- **Date:** 2026-11-26
- **Surface:** `apps/ios` (lead), `packages/supabase` (one read RPC).
- **Size:** Medium–large. Three parts (§8).
- **Exploration:** private Design canvas `https://claude.ai/artifact/HKzSGNd6PHXrSp6dF237M4`. The maintainer kept 07's three shape tiles, 09's contents table (for domains only) and 05's centred empty state. Boards **12** (no collection yet), **13** (with collections) and **14** (nothing at all) are the approved screens; **P11** is the domain page they open.
- **Supersedes:** §3 of `2026-10-26-home-tab-and-empty-states-design.md` (E2, the explainer card) and its D11 for Collections.

## Summary

The Collections tab stops explaining itself and starts showing the person's own life. Every life domain that holds at least one of their pebbles appears as a default collection, in a contents-style table: icon, name, dotted leader, count. Above it, three drawn tiles (Stack, Pack, Track) start a collection with its mode already chosen. Once the person has a collection, the tiles step aside for their collections as rows. An account with no pebble in any domain and no collection sees one centred empty state. A domain opens a read-only page of its pebbles by month. The emoji mode badges (🎯📦🔄) are replaced everywhere by three small drawn marks.

## Goal

- A new user never lands on a page of instructions: they see what they already have, or one sentence and one button.
- Domains become browsable as collections without the user doing anything.
- Starting a collection takes one tap on the shape it will have.
- The page keeps its look after the first collection: nothing disappears for good, nothing needs dismissing.

## Non-goals

- **Editing a domain collection.** No rename, no glyph, no mode, no delete, no adding or removing pebbles from the domain page. Domains change only when a pebble's domains change.
- **Hiding or reordering domains.** The order is the server's (§4).
- **Suggested collections** (board 06). Not in this round.
- **Domains with no pebble.** They never show, not even greyed out.
- **The web.** Android follows in its own issue (§9).

## Decisions

**D1. Three page states, one pure function.** `CollectionsPageState.make(collections:domains:)` returns:

| State | When | Board |
|---|---|---|
| `.blank` | no collection and no domain row | 14 |
| `.start` | no collection, at least one domain row | 12 |
| `.filled` | at least one collection | 13 |

It replaces `CollectionsNoteState`. The state follows the data alone: nothing is stored on the device, so the `collections.note.dismissed` key is no longer read (a stale value is harmless and is not migrated).

**D2. Domains are a server projection.** A new RPC, `collection_domains()`, returns one row per domain carrying at least one of the caller's pebbles, with its count and the latest `happened_at`, already ordered. Clients never count `pebble_domains` themselves (brain in the back-end). Names, labels and glyphs come from the reference data the app already holds (`ReferenceDataService.domains`, read from `v_domains_with_glyph`), keyed by `domain_id`.

**D3. The count is what the domain page lists.** A domain's count is the number of the caller's pebbles tagged with it, with no moderation filter, because the domain page reads the Path (`path_moments`), which shows hidden moments to their owner. This differs on purpose from `people_prompts`, which skips hidden moments.

**D4. The shape tiles keep the modes' names.** The tiles read **Stack · Pack · Track**, with their drawn figure and a short italic line. The mock's "Pile · Set · Line" were working names. Confirmed by the maintainer on 2026-11-26: the modes keep their names everywhere, so no row, sheet or other client changes.

**D5. A tile opens the create sheet with its mode selected.** `CreateCollectionSheet` gains `initialMode: CollectionMode?` (default `nil`). The mode picker stays in the sheet, so the choice can still change. The blank state's button and the "New collection" row open the sheet with no mode. The Home checklist intent (`.createCollection`) keeps opening the sheet as it does today.

**D6. Section titles are Home's.** Every section head on the page is `PebblesSectionHead` (`sectionTitle`, sentence case), with a `.count` figure where there is a number: "Yours 2", "By domain 7". "Start a collection" has no figure; its italic line sits under it.

**D7. Drawn mode marks replace the emoji badges.** A new `CollectionModeMark` draws the pile, the set with one empty place, and the stones along a line. It is drawn in SwiftUI shapes in `onSurface`, `onSurfaceVariant` and `outlineVariant`, with no emoji and no literal colours. It comes in two sizes: **tile** (about 76 × 64 pt) and **row** (about 36 × 30 pt). It replaces `CollectionModeBadge` in `CollectionRow`, in the `CollectionDetailView` header and in the mode picker's labels where it fits. `CollectionModeBadge` is then deleted. A collection with no mode shows no mark.

**D8. A domain opens a read-only page.** `DomainCollectionView` reuses the collection detail's list: pebbles grouped by month (`groupPebblesByMonth`), `PebbleRow`, and a tap opens the held card. Its header is the domain glyph, the localized name, the localized label and the count. It has no Edit button. A row's swipe still offers the pebble's own delete, as in a collection.

**D9. The explainer card and the footnote are removed.** `CollectionsNote`, `CollectionsFootnote`, `CollectionsNoteCopy`, `CollectionsNoteState` and their string keys go. The guidance moves into the tiles' captions and the blank state's sentence.

## 1. The page, state by state

The toolbar keeps the "Collections" title and the `+` button (it opens the sheet with no mode). Loading and errors behave as today: a spinner on the first load, `ContentUnavailableView` on failure. The two reads (collections and domains) run in parallel. If only the domains read fails, the page still draws its collections section and logs the error.

### 1.1 `.blank` (board 14)

This state is centred and vertically placed like an empty page, not a list:

- The drawn line of stones, with its last place dashed (decorative, hidden from VoiceOver).
- **Nothing gathered yet** (`bareTitle`).
- *Gather pebbles along a thread: a trip, a project, a year with someone.* (`lead`, `onSurfaceVariant`)
- A filled **Start a collection** button (`PebblesPrimaryButtonStyle`).
- *As you record, your pebbles also gather here by domain, on their own.* (`tileCaptionItalic`, `onSurfaceVariant`)

### 1.2 `.start` (board 12)

1. `PebblesSectionHead("Start a collection")`, then *Pick the shape it takes.* (`lead`).
2. Three equal tiles in one row (grid, 8 pt gap). Each tile is a plain button on `surfaceContainerLow` with a `rule` hairline and a 20 pt radius. It holds the tile-size mark, the mode name (`rowTitle`) and its line (`tileCaptionItalic`, centred):
   - **Stack**: *grows as long as it lasts*
   - **Pack**: *one place still empty*
   - **Track**: *keeps coming back*
3. `PebblesSectionHead("By domain", figure: .count(n))`, then the domain table (§2).

### 1.3 `.filled` (board 13)

1. `PebblesSectionHead("Yours", figure: .count(collections.count))`.
2. An inset grouped list of the collections, ordered by name as today. Each row has the row-size mark (or nothing), the name (`rowTitle`) and a second line *Track · 6 pebbles* (`rowLine`, `onSurfaceVariant`). For a collection with no mode, the line is just the count. Swipe to delete and the push to `CollectionDetailView` stay as they are.
3. A last row, **New collection**, in `primary` with a circled `+`, which opens the sheet.
4. `PebblesSectionHead("By domain", figure: .count(n))` and the table, or nothing if there is no domain row.

## 2. The domain table

One row per domain row from `collection_domains()`, in server order, each a `NavigationLink` to `CollectionsRoute.domain(id)`:

- The domain glyph at 28 pt, drawn the way `DomainPickerContent` draws it (system glyph from `v_domains_with_glyph`).
- `domain.localizedName`, in a new rank `SpineType.contentsEntry` (Ysabeau 19, weight 400, `.text(.body)`).
- A dotted leader filling the gap: a `Shape` with a round-capped 1.5 pt dash in `outline` at 35 %, on the name's baseline.
- The count, in a new rank `SpineType.contentsCount` (Ysabeau 17, weight 600, lining, tabular), right-aligned.

The table has no card background and no separators. Rows are 46 pt high and grow with Dynamic Type. At accessibility sizes the leader is hidden and the count wraps under the name. VoiceOver reads each row as one element, "Friends, 23 pebbles", with the button trait; the leader and the glyph are hidden.

The two new ranks follow the rank model: views never name a size. Android adds the same two to `SpineTypography`.

## 3. The domain page (board P11)

- Toolbar title "Domain" (`pebblesToolbarTitle`), with the standard back button. There is no Edit button.
- Header (centred): the glyph at 96 pt, the name (`bareTitle`), *label · gathered for you* (`lead`), then "23 pebbles · since April" (`sectionLabel`, `onSurfaceVariant`). "Since" is the month of the oldest pebble listed, in the user's locale.
- The month sections and rows of `CollectionDetailView`, unchanged.
- Data: `MomentsService.moments(inDomain:)` reads `pebble_domains` for the domain id and intersects the link ids with `pathMoments()`, the pattern the soul and collection pages already use. These are two single-table reads with no write, so no RPC is needed.
- It reloads on `MomentsRevision` changes from outside, as the collection detail does. When the last pebble leaves the domain, the page shows the collection detail's empty state, and the list drops the row on return.

## 4. Server: `collection_domains()`

```
collection_domains() returns jsonb  -- an array, ordered
  [{ "domain_id": uuid, "slug": text, "pebble_count": int, "last_happened_at": "YYYY-MM-DDTHH:MM:SSZ" }]
```

- `language plpgsql` (or `sql`), `stable`, **`security invoker`**, `set search_path = ''`, every relation schema-qualified. RLS on `pebbles` and `pebble_domains` stays the backstop (the `people_prompts` invoker template, migration `20261027090210`).
- `not_authenticated` (42501) when `auth.uid()` is null. `execute` is granted to `authenticated` only and revoked from `public` and `anon`.
- It counts the caller's pebbles per domain through `pebbles` (`user_id = auth.uid()`) joined to `moments` for `happened_at` and to `pebble_domains`. Domains with zero pebbles are absent.
- The order is `pebble_count desc`, then `last_happened_at desc`, then `slug`. It is the only order clients use.
- `last_happened_at` is emitted in UTC at whole seconds (standing rule). iOS parses it tolerantly.
- No new table, so `purge_account` is unchanged. No emotion or domain insert, so there is no `sync_achievement_catalog()` call. Regenerate `database.ts` with `db:types:remote` after the push.
- **Harness** `packages/supabase/scripts/verify-collection-domains.ts` (signed-in, like `verify-home.ts`) seeds pebbles across three domains, one moment hidden by moderation and one pebble with two domains. It checks the payload against a JS oracle: counts, order, the hidden moment still counted, the two-domain pebble counted in both, an empty array for a fresh account, and the anon refusal. It joins the CI set.

## 5. iOS structure

| File | Change |
|---|---|
| `Features/Collections/CollectionsPageState.swift` | new: D1's state and `make` |
| `Features/Collections/CollectionsShapeTiles.swift` | new: the three tiles |
| `Features/Collections/CollectionsBlankState.swift` | new: board 14 |
| `Features/Collections/DomainContentsTable.swift` | new: §2, with its `DottedLeader` shape |
| `Features/Collections/DomainCollectionView.swift` | new: §3 |
| `Features/Collections/CollectionDomainRow.swift` | new: the RPC payload type (custom tolerant date decoding) |
| `Features/Profile/Components/CollectionModeMark.swift` | new: D7; replaces `CollectionModeBadge.swift` (deleted) |
| `Features/Profile/Lists/CollectionsListView.swift` | the three states, the parallel load, `.domain` route |
| `Features/Profile/Sheets/CreateCollectionSheet.swift` | `initialMode` |
| `Features/Profile/Views/CollectionDetailView.swift` | the header uses the mark |
| `Features/Tabs/TabRoutes.swift` | `CollectionsRoute.domain(UUID)` |
| `Services/MomentsService.swift` | `moments(inDomain:)`, `collectionDomains()` |
| `Theme/SpineType.swift` | `contentsEntry`, `contentsCount` |
| `Features/Collections/CollectionsNote*.swift`, `CollectionsNoteState.swift` | deleted (D9) |
| `Resources/Localizable.xcstrings` | new keys below; `collections.note.*` and `collections.footnote*` removed |

### Copy (EN · FR)

| Key | EN | FR |
|---|---|---|
| `collections.blank.title` | Nothing gathered yet | Rien de rassemblé pour l'instant |
| `collections.blank.line` | Gather pebbles along a thread: a trip, a project, a year with someone. | Rassemble des galets le long d'un fil : un voyage, un projet, une année avec quelqu'un. |
| `collections.blank.domains` | As you record, your pebbles also gather here by domain, on their own. | Au fil de tes galets, ils se rassemblent aussi ici par domaine, tout seuls. |
| `collections.start` | Start a collection | Commencer une collection |
| `collections.start.line` | Pick the shape it takes. | Choisis la forme qu'elle prend. |
| `collections.shape.stack` | grows as long as it lasts | grandit tant qu'elle dure |
| `collections.shape.pack` | one place still empty | une place encore vide |
| `collections.shape.track` | keeps coming back | revient encore et encore |
| `collections.section.yours` | Yours | Les tiennes |
| `collections.section.domains` | By domain | Par domaine |
| `collections.new` | New collection | Nouvelle collection |
| `collections.domain.title` | Domain | Domaine |
| `collections.domain.gathered` | %@ · gathered for you | %@ · rassemblés pour toi |
| `collections.domain.since` | %lld pebbles · since %@ (plural variants) | %lld galets · depuis %@ |

The mode names (Stack, Pack, Track) keep their existing keys. The FR lines are a first pass for the maintainer to review.

## 6. Accessibility

- Tiles are buttons labelled "Start a stack collection, grows as long as it lasts", and so on. The marks are hidden.
- The blank state's drawing is hidden. Its title has the header trait.
- Section heads are already single header elements (`PebblesSectionHead`).
- Text contrast uses the existing roles in both schemes. The dotted leader is decoration and needs no contrast.

## 7. Testing

- Swift Testing for `CollectionsPageState.make`: all three states, plus "collections but no domain rows" → `.filled`.
- Decoder tests for `CollectionDomainRow` on a payload **copied verbatim from the harness run**, with explicit `Z` seconds, a fractional-seconds variant and an empty array (cross-surface rule).
- `MomentsService.moments(_:carrying:)` already has coverage. Add one case for a pebble in two domains.
- Rendered references for the three states and the domain page in light, dark, French and the largest Dynamic Type size, gated against a baseline run (the iOS references rule).
- Device pass against boards 12, 13, 14 and P11, side by side.

## 8. Parts

One stack, three parts, each linting and building alone:

1. **Back-end (#1705):** the `collection_domains()` migration, types and harness.
2. **iOS mode marks (#1706):** `CollectionModeMark` replaces the badge in rows, the detail header and the sheet. This part is independent of 1 and could merge first.
3. **iOS page (#1706):** the three states, the domain table, the domain page, `initialMode`, the removal of the note, and the copy. It depends on 1 and 2.

Arkaik: move the Collections view to `development` when Part 3 starts. Add a **Domain collection** view (`metadata.product: app`) with an edge from Collections, and the `collection_domains` endpoint, through `arkaik-mcp`.

## 9. Follow-ups

- **Android mirror (#1707):** the same three states, the marks, the domain page and the two type ranks, reading the same RPC.
- Data safety: no change. Nothing new is collected or sent.
