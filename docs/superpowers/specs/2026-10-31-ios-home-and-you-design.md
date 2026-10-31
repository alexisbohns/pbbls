# iOS Home and You — design

**Date:** 2026-10-31
**Issue:** #1382 (siblings: #1383 Android, #1384 web, #1385 blog post)
**Milestone:** M67 · Home & You
**Design:** the 2026-10-31 Type Bench exploration, direction "Cut Stones, carded" (c3). Render: Home Paper, You Paper, Home Night, You Night. Private hub: https://claude.ai/artifact/7AjH6xYjeupn4SnFQEZV9u
**Inventory:** counted on `main` at `05cf4926c` (2026-10-31): 77 `pebblesFont` call sites on the ten SF Pro Rounded tokens in 40 files, 3 inline `design: .rounded` fonts, 18 Home reference names (55 PNGs), no You reference.

## Summary

Home and You get one type voice and one surface vocabulary. Ysabeau carries the editorial voice (greeting, section heads, figures, dates); Inclusive Sans, bundled, replaces SF Pro Rounded in every token that used it; the system rounded design leaves the app. Home opens on the current month drawn as the wrap's week columns, then the date and greeting, the quick actions as one stone cut in three, the first five in one rounded box, the month wraps as cards and About Pebbles as a plain list. You opens on a glyph in a squircle pentagon, the name in the hand font, the Ripples level, the three counters as one split stone, then Glyphs, Achievements and the Lab as sections. Stones are for actions and side-by-side tiles, cards for content you browse, one box for the list. The work is one stack of seven iOS parts and one back-end PR.

## Goal

After the stack merges:

- no `PebblesFont` token, and no view under `Features/` or `Components/`, draws SF Pro Rounded or SF Compact Rounded, and `ThemeLiteralsTests` fails on a new one;
- Inclusive Sans is bundled with its OFL licence and every former SF Pro Rounded token resolves to it, scaling with Dynamic Type;
- Home and You draw as c3 in both schemes and both languages, with VoiceOver and Dynamic Type covered;
- no number on either screen is counted by the client: every figure comes from an RPC or a view the server owns.

## Non-goals

- Android (#1383) and web (#1384). They build from the same brief; the two contract points they must mirror are D9 (a column opens its week) and D19 (the glyph counts in `get_profile_engagement`).
- The plain SF text styles (`.font(.caption)`, `.font(.body)` …, 55 files). They are SF Pro, not SF Pro Rounded; moving them is a follow-up issue, not this stack (D5).
- The Lab page itself (`LabView`), the glyph pages, the achievements page. You links to them; they keep their look.
- A slim month projection for Home (D8 says why not now).
- The other files of the uncommitted tonal-fill fix in the main checkout (`CompleteCollectionStep`, `CompletePrivacyStep`, `AchievementMomentView`). Only its `HomeShortcuts` change belongs here (D11); `ProfileAchievementsCard` is replaced (D20).

## Decisions

**D1. Inclusive Sans takes every SF Pro Rounded token, as a Spine rank.** `SpineType.Face` gains `.inclusive` (Bold Text ceiling 700, the file's axis maximum), and `SpineType.resolve` builds it through a new `InclusiveFace`. The ten SF tokens of `PebblesFont` become `SpineType.Style` values on that face, and `.pebblesFont(_:)` applies them through the same resolver as `.spineType(_:)`. Consequence, accepted: the tokens now scale with Dynamic Type (to the `.accessibility2` ceiling) and thicken under Bold Text. Today they do not scale at all: `UIFont.systemFont(ofSize:)` is a fixed size, so this is an accessibility fix riding on the face swap. The Ysabeau tokens (`title`, `buttonLabel`) and the hand tokens keep their current path and look; `largeTitleHand` is retuned in D17. *Rejected:* a second modifier beside `pebblesFont` (two ways to set the same text), and keeping fixed sizes (it would carry the old non-scaling into the new face).

**D2. The files, the licence, the registration.** The maintainer's files ship as they are, under their own names, in `Pebbles/Resources/`: `InclusiveSans-VariableFont_wght.ttf` (112 064 bytes) and `InclusiveSans-Italic-VariableFont_wght.ttf` (114 308 bytes), with `OFL.txt` copied to `Pebbles/Resources/Licenses/InclusiveSans-OFL.txt` (the OFL requires the licence to travel with the font, and an app bundle is a distribution). Measured with CoreText on 2026-10-31: one `wght` axis, 300 to 700, default 400; `UIFont(name:)` resolves `InclusiveSans-Regular` and `InclusiveSans-Italic` (the files also carry named instances such as `InclusiveSans-Regular_SemiBold`, which nothing uses). `Info.plist` `UIAppFonts` gains the two file names. `project.yml` needs no edit: the target's `- path: Pebbles` source already copies `Resources/*.ttf` and `.txt` as resources, which is how Ysabeau ships; the font test (Part 1) proves the bundle instead of a YAML line. Android ships a different build of the same family (`res/font/inclusive_sans.ttf`, 112 280 bytes, same PostScript names and axis); #1383 decides whether to align. Until both sides carry the same bytes, `ios.yml` gets no `cmp` step for Inclusive Sans; once they do, it gets one beside Ysabeau's. Ysabeau, Caveat and Reenie Beanie ship today without their OFL texts in the bundle; that gap is noted for a follow-up, not fixed here.

**D3. The token table.** Sizes are kept one for one; weights map SF to the axis (regular 400, medium 500, semibold 600, bold 700); the SF tokens' +2 % tracking goes (Inclusive Sans is already open), the capitals keep a tighter 0.08 em than SF's 10 %. Each rank scales with the text style `SpineType.textStyle(forSize:)` gives its size.

| Token | Today | After | Sites |
|---|---|---|---|
| `body` | SF Pro Rounded 17 regular, +0.34 pt | Inclusive 17 / 400 | 1 (`CompleteCollectionStep`) |
| `bodyEmphasized` | SF Pro Rounded 17 semibold | Inclusive 17 / 600 | 3 (`CompletePrivacyStep`, `DomainPickerContent`, `RecordStepScaffold`) |
| `subhead` | SF Pro Rounded 15 regular, +0.30 pt | Inclusive 15 / 400 | 19 (Record steps, Complete, Capture, `DomainPickerContent`, `AchievementMomentView`, Profile cards until Part 6) |
| `subheadEmphasized` | SF Pro Rounded 15 semibold | Inclusive 15 / 600 | 0 |
| `headline` | SF Pro Rounded 17 semibold | Inclusive 17 / 600 | 6 (`CompleteSuccessStep`, `GuidesList`, `AchievementMomentView`, Profile cards) |
| `headlineEmphasized` | SF Pro Rounded 17 bold | Inclusive 17 / 700 | 1 (`AchievementMomentView`) |
| `callout` | SF Pro Rounded 16 medium, +0.32 pt | Inclusive 16 / 500 | 23 (Home, Connections, Collections note, People onboarding, pickers, `SurfaceTile`, `QuickMomentSheet`, `MomentToast`, `HomeWrapSheet`, `AchievementsNotice`, Capture) |
| `calloutEmphasized` | SF Pro Rounded 16 semibold | Inclusive 16 / 600 | 0 |
| `meta` | "SF Compact Rounded" 12 medium, caps, +1.2 pt | Inclusive 12 / 500, caps, 0.08 em | 13 (every navigation title through `PebblesToolbarTitle`, `GlyphBanner`, `SoulItem`, Connections, `MomentToast`) |
| `metaEmphasized` | "SF Compact Rounded" 12 bold, caps | Inclusive 12 / 700, caps, 0.08 em | 0 |
| `cardHeading` | "SF Compact Rounded" 15 semibold, caps, +1.5 pt | Inclusive 15 / 600, caps, 0.08 em | 9 (every list section header through `PebblesList`, Valence headline and roll, `SoulPickerContent`, `InviteAcceptSheet`, `ProfileStatsCard`) |
| `cardHeadingEmphasized` | "SF Compact Rounded" 15 bold, caps | Inclusive 15 / 700, caps, 0.08 em | 0 |
| `counterLg` | SF Pro Rounded 17 semibold | Inclusive 17 / 600 in Part 1; deleted in Part 6 with `DataTile`, its only caller | 1 |
| `captionEmphasized` | SF Pro Rounded 12 semibold, +0.24 pt | Inclusive 12 / 600 | 1 (`RippleBadge`'s digit) |
| `title`, `buttonLabel`, `bodyLeadHand`, `largeTitleHand`, `nameInputHand`, `valenceWord*` | Ysabeau / Reenie Beanie / Caveat | unchanged (`largeTitleHand`: D17) | — |

"SF Compact Rounded" is in quotes because it never shipped: no OTF is in `Resources/` and none is in `UIAppFonts`, so the four caps tokens have always fallen back to SF Pro Rounded (the doc comment naming `Resources/Fonts/` is stale and goes with the helper). The visible changes are therefore app-wide, not Home-only: every navigation title, every list section header, the record and complete flows, Connections, the pickers.

**D4. The rounded design is retired by a rule, not by hope.** `sfProRounded` and `sfCompactRounded` are deleted from `Font+Pebbles.swift`. The three inline sites move to plain SF, the chrome face (icon spec D1): `SheetParts` (a chevron sized by `.subheadline`), `ProfileAchievementsCard` and `ProfileLabCard` (two chevrons at 15 medium, deleted later in Part 7 anyway). `ThemeLiteralsTests` gains a rule, `design:\s*\.rounded|fontDesign\(\s*\.rounded|withDesign\(\s*\.rounded`, reason "SF Pro Rounded is retired: use pebblesFont or spineType", with its positive and negative cases in the rule table; no allowlist entry. A new `FontTokensTests` resolves every `PebblesFont` case and asserts its family is one of Inclusive Sans, Ysabeau, Reenie Beanie, Caveat (Theme files are outside the literal scan, so this test covers the token file itself).

**D5. Plain SF stays where it is.** 55 feature files use system text styles (`.font(.caption)` ×28, `.footnote` ×21, `.body` ×11 …): SF Pro, not rounded, and outside the brief. Moving them to ranks is a follow-up issue opened at Part 1, not a silent widening of this stack. The two screens this stack rebuilds use no system text style when it is done.

**D6. Stones are shared geometry, moved first, then generalised.** The rock that `ShortcutStones` cuts lives in `Features/Home/Shortcuts/`. Part 2 moves the rock outline, the distance-field cutter, `resample`, `path(_:)` and `StoneRandom` to `Features/Shared/Stones/StoneGeometry.swift` unchanged (a pure move: same outputs, no reference may change). Part 3 generalises the row split to the kit's `split(w, h, n, seed, opt)`: `StoneGeometry.split(size:count:seed:options:)` with `options` = corner (34), bow (1), lean (drawn 0.11 ± 0.03 when nil), gutter (8), fillet (20); `ShortcutStones.row` becomes `split(count: 3, seed: "camera|fixed|rock|q11")` and a golden test pins its three outlines to the pre-move points to 0.001 pt. It adds `StoneGeometry.single(size:seed:options:)` (the kit's `d(w, h, seed, opt)`) and `StoneShape: Shape` / `SplitStone: View` wrappers, and `SquirclePentagon: Shape` (c3's `pentagon()`: R 64 in a 128 × 122 box, centre y 66, corner cubic over 28 % of each side with handles at 4 %, sides bowed 2 pt). The bento split stays where it is: nothing else cuts a bento.

**D7. The vocabulary in code.** *Stones* (actions and side-by-side tiles): `SplitStone` for the quick actions, the counters and the first-five stepper; `StoneShape` for the About and Glyphs chips (`surfaceContainer`, 48 × 46, corner 16), the settings button (`surfaceContainerHigh`, 46 × 44, corner 16) and the community callout (`tertiaryContainer`, corner 30). Stones are flat: no border, no shadow. *Cards* (content you browse): `.pebblesCard()`, `stock` on an 18 pt continuous radius; on Paper two shadow layers in `SpineColors.shadowInk` (0 / 1 / 2 at 5 %, 0 / 8 / 22 at 7 %), at Night a `rule` hairline and one layer (0 / 8 / 22 at 35 %), built as `SpineCardShell` builds its pool so `ThemeLiteralsTests` sees only roles. Used by the month wrap cards, the achievement badges and the announcement. *The box* (one list): `.pebblesBox()`, `surfaceContainer` on a 26 pt continuous radius, padding 24 / 16 / 10 / 18, for the first five only. `.profileCard()` loses its last caller in Part 7 and is deleted.

**D8. Home's month comes from `path_month_wrap`, no slim projection now.** The grid needs `days_in_month`, `first_weekday`, `cover.days[].pebbles[]` and `weeks[]`, all in `path_month_wrap(p_month, p_tz)`, which `PathWrapService.monthWrap` already calls, decodes and keeps on disk per owner. Home reads the kept copy first (`cachedMonthWrap`), then fetches, so the second launch paints at once and a Path visit warms Home for free. The RPC also computes the gem, tone, hours and persons Home ignores; that cost is paid on a background executor and does not block the greeting. Recommendation: ship on it, log the call's duration under `home.month` in the TestFlight build, and file the slim `home_month` projection as a back-end issue only if it shows up on Home's first paint. The grid keeps a fixed height while it loads (label 34 + 8 + 7 × 28 + 8 + cairn 46 = 292 pt), so nothing jumps; a failed read logs, keeps the last month and draws nothing new. It reloads on `MomentsRevision` (a pebble saved anywhere) and when the clock crosses into a new month (`.task(id: liveMonth)`).

**D9. A tap on a week's column opens that week on the Path** (the brief's open question; recommended answer, yes, as in the wrap). Home is a launch pad: the grid shows where the month's pebbles are, and the Path is where a week is read. `TabIntent` gains `.focusWeek(CalendarDay)`; Home calls `nav.go(.path, intent: .focusWeek(monday))`; `PathView` takes the intent when it appears and once its pages have loaded, and calls `PathModel.focus(weekStarting:)`, which converts the Monday in `SpineCalendar.current` and falls back to the current week when the page does not exist. A week that starts after today is not a button (the Path has no page for it); its column draws, and VoiceOver reads it as text. The Home wrap sheet's `onWeek` goes through the same intent: today it calls `nav.go(.path)` and drops the week. #1383 and #1384 should mirror the tap.

**D10. The greeting.** Three new Ysabeau ranks: `homeDateline` 17 / 540 in `primary` (Ysabeau's default figures are old-style, which is what the mock asks for), `greeting` 40 / 270, −0.024 em, line height 41.6, and `greetingLine` 21 / 360 italic in `onSurfaceVariant`. The greeting holds one line with `minimumScaleFactor(0.75)` through `.xxxLarge`; at accessibility sizes it may wrap to two lines rather than shrink further. The mock's check (both "Good evening, Alexis" and "Bonsoir, Alexandrine" in 350 pt at 40 pt) is repeated as a reference in French with the long name. The dateline leaves the small-caps kicker for the c3 line.

**D11. The quick actions.** The shipping split stone stays, and this spec owns its colour change: fill `primaryContainer`, ink `onPrimaryContainer`, in place of `primary` at 13 / 20 % (the opaque roles follow Increase Contrast; the `colorScheme` read goes). The row cell becomes c3's: icon (28, `.tile`) top-left, label bottom-left, padding 15 / 12 / 14 / 16 (the second and third cells 20 on the left, clear of the cut), minimum height 100. The label takes a new Inclusive rank `actionLabel` 15 / 600, line height 17.25, two lines. The bento and the plain-tile options in Settings keep their layout and take the same colours and rank; the Settings choice is unchanged.

**D12. The first five.** One box (D7). Head: `sectionTitle` "Your first five" and a figure "2 of 5" built as three texts: the count in `sectionFigure` (Ysabeau 30 / 300, lining, `primary`), the word in `sectionFigureWord` (19 / 380 italic, `onSurfaceVariant`), the total in `sectionFigure`. Under it the stepper: one stone 6 pt tall cut in five (`split(count: 5, seed: "first-five", corner 3, gutter 5, fillet 2, lean 0.9)`), done pieces `primary`, the rest `surfaceContainerHighest`, hidden from VoiceOver. Rows lose their dividers; the title takes `rowTitle` (Inclusive 17 / 600, `onSurface`) or, done, `rowTitleDone` (17 / 500, `onSurfaceVariant`); the line takes `rowLine` (Inclusive 15 / 400, `onSurfaceVariant`, `outline` when done) in place of the italic Ysabeau summary. The marks stay the icons (`check-circle` done in `primary`, `pebble-round` to do in `outline`): the mock's blob is a stand-in. Done rows already carry no chevron; the claim row stays the sixth row, inside the box. The section still draws nothing once the blue pebble is claimed or the catalog is unavailable.

**D13. The month wraps.** No box. Head: `sectionTitle` "Monthly wraps" and the month count in `sectionFigure`. Each month is a `.pebblesCard()` 152 wide, padding 18 / 16 / 18 / 18: the garland (today scaled to 92) drawn at 116 with the count inside in `wrapCount` (Ysabeau 34 / 280, lining), then the month in `wrapMonth` (23 / 400) and the year in `wrapYear` (16 / 400 italic). The folded month keeps its turned corner, on the card. The empty promise stays a dashed outline (it invites, it is not content). The row scrolls sideways as today, with 22 pt below it so the shadow is not clipped.

**D14. About Pebbles.** No box, no dividers. Each guide is a row: a chip stone holding the guide's own mark (`GuideIllustrationView` `.mark`, the data's choice, as today), the title in `rowTitle`, the summary in `rowLine`, an SF chevron in `outline`. The row is the shared `PebblesLinkRow` (Part 3), also used by You's Glyphs.

**D15. One section head.** `PebblesSectionHead(title:figure:)`: the title in `sectionTitle` (Ysabeau 30 / 300, −0.018 em, sentence case, no caps, no tracking, `.isHeader`), an optional trailing figure on the same baseline. Home and You use it for every section; `SpineSectionHeader` stays for the Path, the wraps and People.

**D16. You is a shell around a testable page.** `ProfileView` keeps the routes, the Settings sheet and the intent hand-off; its body becomes `YouContent(model:)`, a pure view fed by `@Observable YouModel` (the profile row, the stats from `PathStatsService`, the achievements shelf, the Lab feeds), so references draw it from a fixture as `HomeContent` is drawn. The navigation bar is hidden at the root, as on Home; the "Profile" title goes. Settings becomes a stone button in the content's top-right (`slider` at `.toolbar`, 44 pt hit target, label "Settings"). "Log out", today a button at the foot of You and the app's only sign-out, moves to the top of Settings' Account section, above "Delete account"; c3 has no room for it and Settings is where iOS users look.

**D17. The header.** The pentagon (D6) in `primaryContainer`, 128 × 122, holding the profile glyph through `GlyphThumbnail` at 58 in `onPrimaryContainer`, or the `pebble-dotted-glyph` mark when there is none; decorative, hidden from VoiceOver. The name in Reenie Beanie at 60, tracking −0.075 em (`largeTitleHand` is retuned from 41 / −2 pt: its only caller is this header), one line, `minimumScaleFactor(0.5)`, header trait. "Member since March 2026" in `memberSince` (Ysabeau 19 / 380 italic). Then the Ripples line, and only it: `RippleBadge` at 22 beside "Ripples Level 3" in `rowLabel` (Inclusive 15 / 600, `onSurface`), one VoiceOver element reading "Ripples Level 3, active today". `RippleBadge` gains `side: CGFloat = 44` (every other caller is unchanged): its strokes already scale from the 44 viewBox; below 32 its digit goes (the label says the level) and its stroke scales to `max(1.25, 2 × side / 44)`. The progress subline and the 28-day `AssiduityGrid` go; `AssiduityGrid`, `chunkAssiduity` and their test are deleted. `PathStatsService` keeps loading `assiduity` (Android reads the same RPC; trimming it is not this stack's call).

**D18. The counters.** Right under the header, no "Stats" title: one stone cut in three (`split(count: 3, seed: "days|pebbles|karma")`, `surfaceContainer`), its height from its content (at least 88). Each tile: the figure in `counterFigure` (Ysabeau 36 / 280, −0.025 em, lining, `onSurface`), formatted `.number` in the user's locale ("1,240", "1 240"), "—" while loading; under it the icon at 18 in `primary` and the label in `rowLine`, on one line. Sources unchanged: days from `get_profile_engagement.days_practiced`, pebbles and karma from `v_karma_summary`. Each tile is one VoiceOver element ("1,240, Karma"). `ProfileStatsCard`, `ProfileCountersRow`, `DataTile` and `counterLg` are deleted.

**D19. Glyphs, and where their counts come from.** A section of three `PebblesLinkRow`s: My glyphs (`draw-dotted`), Owned glyphs (`pouch`), Community (`petroglyph`), each opening `GlyphsListView` on its tab through a new `YouRoute.glyphTab(GlyphTab)` (`GlyphTab` becomes `Codable`; `GlyphsListView(initialTab:)`; `.glyphs` stays for the carve intent and restored stacks). The lines carry counts ("The 12 marks you carved.", "The 5 you collected from others."). `get_profile_engagement` is the right home: You already calls it, it is per caller and `security invoker`, and the brief points at it. Back-end part B adds `glyphs_carved` (`count(*)` of `glyphs` where `user_id = auth.uid()`, the rows `listMine` reads) and `glyphs_owned` (of `glyph_entitlements`, the rows `listOwned` reads). The return type changes, so the migration drops and recreates the function (`create or replace` cannot change a `returns table`); the web types regenerate; `verify-home.ts` gains the counts (a throwaway user carves one glyph: 1 and 0; anon refused) and dumps the payload for the clients' decoders. iOS decodes both keys as optional, and the rows fall back to count-free lines ("The marks you carved.") while they are absent, so Part 6 needs B merged (its payload file) but not deployed. Before B is pushed, Android's `decodeList<ProfileEngagement>()` must be checked to ignore unknown keys (supabase-kt's default serializer), or a shipped Android build breaks on the new columns.

**D20. Achievements.** No box. Head: `sectionTitle` "Achievements" and the served count as `sectionFigure` plus the plural word "earned" / "décroché(s)" in `sectionFigureWord`. Then the first four served badges as cards in a four-column grid (two columns at accessibility sizes): the family icon at 30 in `primary`, the title in `badgeLabel` (Inclusive 12.5 / 500, two lines, centred). The head and every card open `AchievementsView`. With nothing served, one card says "Badges along your path" and opens the page. The loading logic moves from `ProfileAchievementsCard` into `YouModel` as it is (the page's kept copy, a read only when 15 minutes old, the retroactive check, the catalog once), then `ProfileAchievementsCard` is deleted.

**D21. The Lab on You.** Three blocks under `sectionTitle` "Lab", from `LogsService` (two reads in parallel, each failing alone):
- the latest announcement (`announcements(limit: 1)`) as a `.pebblesCard()`: a cover band (the log's cover image when `cover_image_path` is set, else c3's drawn band: a three-piece split stone in `primaryContainer` over `stock` washed with `primaryContainer` at 35 %), then "Lab notes · 31 October" in `labKicker` (Ysabeau 15 italic), the title in `labTitle` (Ysabeau 26 / 340, one line), the summary in `rowLine` (two lines). It opens `AnnouncementDetailView` through a new `YouRoute.announcement(UUID)` that resolves the id (routes carry ids, never models);
- the three latest shipped features (`changelog(limit: 3)`) as a dated mini-timeline: the date in `labDate` (Ysabeau 15 / 500, old-style, day and abbreviated month), a dot (filled `primary` on the newest, outlined `outline` on the others) on a `rule` line, the title in `labEntry` (Inclusive 16 / 600), the summary in `labEntryLine` (Inclusive 14 / 400, one line: the log carries no area tag, and the platform alone would read "iOS" on every row). Not tappable. Under it an "All updates" capsule (`outlineVariant` border, `primary` 15 / 600, chevron) that pushes the Lab page (`YouRoute.lab`), where announcements, the changelog, the work in progress and the backlog with its upvotes still live;
- the community callout: a `tertiaryContainer` stone with the `whatsapp` icon at 32, "Join the community" in `communityTitle` (Ysabeau 22 / 420) and "Talk with other people on WhatsApp." in `rowLine`, all in `onTertiaryContainer`, a chevron, opening `LabConfig.whatsappInviteURL`. It always shows. `PbblsIcon.whatsapp` comes from the icon library's `feat/icon-whatsapp` branch, not merged: Part 7 cannot merge before it does (R4). `LabView`'s own `FeaturedCommunityCard` is not touched.

**D22. The ranks this stack adds.** All on `SpineType.Style`, so `SpineTypeTests` holds them to the same rules (no italic with small caps, a scaling per rank). Ysabeau: `homeDateline` 17/540, `greeting` 40/270, `greetingLine` 21/360 i, `sectionTitle` 30/300, `sectionFigure` 30/300 lining, `sectionFigureWord` 19/380 i, `wrapCount` 34/280 lining, `wrapMonth` 23/400, `wrapYear` 16/400 i, `counterFigure` 36/280 lining, `memberSince` 19/380 i, `labKicker` 15/400 i, `labTitle` 26/340, `labDate` 15/500, `communityTitle` 22/420. Inclusive: `actionLabel` 15/600, `rowTitle` 17/600, `rowTitleDone` 17/500, `rowLine` 15/400, `rowLabel` 15/600, `badgeLabel` 12.5/500, `labEntry` 16/600, `labEntryLine` 14/400, `pillLabel` 15/600. Labels stay at 12 or more, body at 15 or more. Figures are always Ysabeau, so Inclusive Sans's slashed zero never shows.

## Data

Nothing new is counted on the device.

| What | Source | Through | New? |
|---|---|---|---|
| Month grid | `path_month_wrap(p_month, p_tz)` | `PathWrapService.monthWrap` + disk copy | no |
| Greeting name | `profiles.display_name` | `HomeService.displayName` | no |
| First five | achievements catalog + unlocks, `claim_blue_pebble` | `AchievementsService` | no |
| Month wraps | `home_wraps(p_tz)`, `reveal_wrap` | `HomeService` | no |
| About | `guides` (published) | `HomeService.guides` | no |
| Name, member since, glyph | `profiles` own row, `glyphs.strokes` | `YouModel` (moved from `ProfileView`) | no |
| Ripples level, active today | `v_ripple` | `PathStatsService` | no |
| Days | `get_profile_engagement.days_practiced` | `PathStatsService` | no |
| Pebbles, karma | `v_karma_summary` | `PathStatsService` | no |
| Glyph counts | `get_profile_engagement.glyphs_carved`, `.glyphs_owned` | `PathStatsService` | **yes, part B** |
| Achievements shelf + count | `get_achievements` page (`shelf`, `shelfCount`) + catalog | `AchievementsModel`, `AchievementsService` | no |
| Announcement | `v_logs_with_counts`, species `announcement`, published, newest `published_at` | `LogsService.announcements(limit: 1)` | no |
| Changelog | `v_logs_with_counts`, species `feature`, status `shipped`, newest `released_at` | `LogsService.changelog(limit: 3)` | no |
| Community link | `LabConfig.whatsappInviteURL` | static | no |

## Accessibility

**Dynamic Type caps.** Text ranks scale to `.accessibility2` (the Spine's ceiling), now including the Inclusive tokens (D1). Exceptions:

| Element | Cap | Why |
|---|---|---|
| Month grid (dates, legend, cells, cairns) | `...large` | a figure, not text; its columns cannot widen. VoiceOver carries its content |
| Greeting | one line to `.xxxLarge` (shrinks to 75 %), two lines beyond | the brief's one line, without clipping at accessibility sizes |
| Quick action labels | none; the stone grows with the cell, as today | |
| Counter figures | `...xxxLarge`; the label wraps under the icon past it | three figures must fit 358 pt |
| Achievement cards | none; four columns become two at accessibility sizes | |
| Wrap cards | none; the year hides at accessibility sizes (as today) | |
| Lab timeline | none; the date column widens and the entries wrap | |
| Name (hand) | shrinks to 50 % on one line | a name is never cut |

**VoiceOver.**
- *Month grid:* one container labelled with the month and its line ("October, 13 pebbles with 2 people", `WrapCopy`), then one element per week, as the wrap does today ("28 September to 4 October, 2 pebbles"), with the button trait and the hint "Opens this week on the Path" for weeks that open; later weeks have no button trait. Days, seals, legend and cairns are hidden.
- *Split stones:* the stone shapes are hidden; each piece's content is the element. Quick actions are three buttons (as today). Counters are three static elements, "112, Days". The first-five stepper is hidden; the head figure reads "2 of 5 done".
- *The first five:* each row one button ("Start your first collection, Gather pebbles that belong together."), done rows disabled with the value "Done" (as today).
- *Header:* the pentagon is hidden; the name is the screen's header; the Ripples line is one element; Settings is a button.
- *Cards and rows:* one element each, button trait from their link; the announcement reads its kicker, title and summary; the timeline reads each entry as "31 October, Quick actions, cut from one stone, Home".
- *Section heads:* header trait, the figure joined to the title ("Achievements, 12 earned").

## French

New keys, with both columns filled and no `New` or `Stale` row (apps/ios/CLAUDE.md):

| Key | en | fr |
|---|---|---|
| `home.firstFive.of` | of | sur |
| `home.firstFive.a11y` | %lld of 5 done | %lld sur 5 faites |
| `home.month.weekHint` | Opens this week on the Path | Ouvre cette semaine sur le Chemin |
| `you.glyphs.mine.title` | My glyphs | Mes glyphes |
| `you.glyphs.mine.line` (plural) | The %lld marks you carved. / one: The mark you carved. | Les %lld marques que tu as gravées. / one: La marque que tu as gravée. |
| `you.glyphs.mine.none` | Carve your first mark. | Grave ta première marque. |
| `you.glyphs.mine.plain` | The marks you carved. | Les marques que tu as gravées. |
| `you.glyphs.owned.title` | Owned glyphs | Glyphes acquis |
| `you.glyphs.owned.line` (plural) | The %lld you collected from others. / one: The one you collected from others. | Les %lld que tu as reçus des autres. / one: Celui que tu as reçu des autres. |
| `you.glyphs.owned.none` | Glyphs you swap for karma land here. | Les glyphes échangés contre du karma arrivent ici. |
| `you.glyphs.owned.plain` | The ones you collected from others. | Ceux que tu as reçus des autres. |
| `you.glyphs.community.title` | Community | Communauté |
| `you.glyphs.community.line` | Glyphs other people share. | Les glyphes que d'autres partagent. |
| `achievement.shelf.earnedWord` (plural one / many / other) | earned | décroché / décrochés / décrochés |
| `you.lab.kicker` | Lab notes · %@ | Notes du Lab · %@ |
| `you.lab.allUpdates` | All updates | Toutes les nouveautés |
| `you.lab.community.line` | Talk with other people on WhatsApp. | Discute avec d'autres personnes sur WhatsApp. |

French plurals take `one`, `many` and `other` (the Android Spine lesson). Reused keys: `home.firstFive.title` (Tes cinq premières fois), `home.wraps.title` (Bilans du mois), `home.about.title` (À propos de Pebbles), the greetings, `Member since %@` (Membre depuis %@), `Ripples Level %lld` (Niveau Ripples %lld), `Days`, `Pebbles`, `Karma`, `Glyphs` (Glyphes), `Achievements` (Succès), `Lab`, `Join the community` (Rejoindre la communauté), `Settings`, `Log out` (Me déconnecter). A key that loses its last caller (`News & community`, `Stats`, the Ripples progress lines `%lld more pebbles to level %lld` and `Max level reached`, and `Profile` if `grep` finds no other caller) is removed in the part that drops the caller, so none goes `Stale`; `Shape Pebbles with other pebblers on WhatsApp.` stays, `LabView` still uses it. "Pebbles" stays the counter's French label (the existing translation); whether it should read "Galets" is the maintainer's call, outside this stack.

## Reference images

Recorded on CI only, re-baselined once per part with the `rebaseline-ios-screenshots` label, fast-forwarded before any stack push (icon migration D9 and its lessons). Expected per part, checked against the red run, never assumed:

| Part | Re-baselined | New | Must not move |
|---|---|---|---|
| 1 fonts | every reference that draws a token: Home (`shortcuts*` ×4, `toast`, `guidesList`, `wrapSheetFolded`, `page*` ×3), `CollectionsReferences` (note, footnote), `PeopleReferences`, `AchievementsPageReferences` (notice), `AchievementsSheetReferences` (the chevron, SF Rounded to SF), and any other the run turns red | none | Spine, held card, wrap, stone, Welcome (no token) |
| 2 move | none | none | all (a red reference is a bug in the move) |
| 3 kit | none | `SurfaceKitReferences`: split stones (3, 5, counters seed), single stones, pentagon, card on both schemes, box, section head with and without figure, link row | `shortcuts*` |
| 4 Home head | `greeting` (3), `shortcuts*` (12), `page*` (9) | `monthGrid` (paper, night, fr, ax2), `greetingLongFr` | every `WrapMonthReferences` and `WrapIntroReferences` PNG (WeeksColumns' default metrics are unchanged) |
| 5 Home body | `checklist*` (13), `wrapCards`, `wrapsEmpty`, `guidesList`, `page*` | none | `taskSoul`, `wrapSheetFolded`, `guidePageStones` |
| 6 You head | none | `YouReferences`: `header` (with glyph, without), `counters` (loaded, loading), `glyphs` (counts, plain), `page` (paper, night, fr, ax2) | Home |
| 7 You body | `YouReferences.page*` | `achievements` (four, empty, ax2), `lab` (cover image placeholder, drawn band, no announcement), `community` | Home |

## Parts

One stack, bottom first; branch names carry `-ios` so Vercel skips them.

| Part | Branch | Content | Depends on |
|---|---|---|---|
| 1 | `feat/1382-inclusive-sans-ios` | fonts + licence + `UIAppFonts`, `InclusiveFace`, `Face.inclusive`, the token table (D1–D4), rounded rule, font tests, D5 follow-up issue | — |
| 2 | `chore/1382-stones-move-ios` | pure move of the rock geometry to `Features/Shared/Stones/` (D6) | 1 |
| 3 | `feat/1382-surface-kit-ios` | `split(count:)` + golden test, single stone, pentagon, `.pebblesCard()`, `.pebblesBox()`, `PebblesSectionHead`, `PebblesLinkRow`, the D22 ranks, kit references | 2 |
| 4 | `feat/1382-home-head-ios` | month grid (D8, D9 incl. `TabIntent.focusWeek` and the wrap sheet), greeting (D10), quick actions (D11) | 3 |
| 5 | `feat/1382-home-body-ios` | Home order, first five (D12), wraps (D13), About (D14) | 4 |
| B | back-end issue, own PR on `main` | `get_profile_engagement` glyph counts, types, harness, payload dump (D19) | — |
| 6 | `feat/1382-you-header-ios` | `YouModel`/`YouContent`, header (D16, D17), counters (D18), Glyphs (D19), Log out to Settings, deletions | 5, and B merged (its payload file feeds the decoding test; the deploy is not awaited) |
| 7 | `feat/1382-you-achievements-lab-ios` | achievements (D20), Lab (D21), deletions, decisions log, Lessons learned; `Resolves #1382` | 6, `feat/icon-whatsapp` merged |

Each part builds, passes `swiftlint`, the unit tests and its own re-recorded references alone.

## Testing

- `InclusiveSansFontTests`: both TTFs and the OFL are in the bundle; `InclusiveSans-Regular` and `-Italic` resolve; 300, 400, 600, 700 on the axis give four distinct stem widths (the `YsabeauFaceTests` probe).
- `FontTokensTests`: every `PebblesFont` case resolves to an allowed family; each Inclusive token's size grows from `.large` to `.accessibility2` and stops there; Bold Text adds 100 capped at 700.
- `ThemeLiteralsTests`: the rounded rule's cases; the ratchet stays green.
- `SpineTypeTests`: the D22 ranks obey the table's rules.
- `StoneGeometryTests`: the golden row; `split` returns `count` closed outlines inside the box with `gutter` between neighbours; `single` fills its box; the pentagon's five corners.
- `HomeModelTests`: the month loads from the kept copy then the fetch, keeps the last month on failure, reloads on a new month.
- `PathModelTests`: `focus(weekStarting:)` focuses an existing week and falls back to the current one.
- `TabNavigationTests`: a `.focusWeek` intent is taken once.
- `ProfileEngagementDecodingTests`: the payload part B dumps, verbatim, with and without the two keys.
- `YouModelTests`: the shelf's staleness and the Lab's independent failures.
- `RippleBadgeTests`: the digit hides below 32, the stroke width rule.
- References per the table above.

## Risks

- **R1. The font change is app-wide.** 77 call sites, every navigation title and every list section header change face in Part 1. Mitigated by keeping Part 1 to the swap alone, its references diffed in review, and a device pass on Record, Complete, Connections and Settings before it merges.
- **R2. Tokens that now scale may overflow fixed frames** (`SurfaceTile`'s one line, the toast, the toolbar title). The ax2 references catch the referenced ones; the device pass at AX2 catches the rest; a frame that cannot grow takes the rank's cap through `.dynamicTypeSize(...)`, case by case, in Part 1.
- **R3. `path_month_wrap` on Home's first paint.** Mitigated by the disk copy and the reserved height (D8); measured before any back-end work.
- **R4. `PbblsIcon.whatsapp` is not merged.** Part 7 is last on purpose; if the icon is late, Parts 1–6 merge and Part 7 waits.
- **R5. Android parity.** The column tap (D9) and the glyph counts (D19) are contract points; #1383's spec should take the same answers, and part B's Android decoder check is a release blocker.
- **R6. The card shadow on Paper.** Built from `SpineColors.shadowInk` (roles), so it passes `ThemeLiteralsTests`; its alphas are the mock's, tuned on device if they read heavy.
