# Android Spine Path Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Android Path tab renders each week as the Spine (opening, persons of the week, contents, a margin line with lit stones, cards and set-type entries, seams, ripple on press), on the brand paper shipped in #1120, with souls coming from `path_moments`.

**Architecture:** Six stacked PRs. Part 1 is one additive RPC change. Parts 2–5 add pure logic, tokens, the Compose stone port and the week components, each visible only in `@PreviewTest` screenshots. Part 6 switches `PathContent` to the new week, restyles the roll and deletes the rows. Pure modules are transcriptions of the web's `apps/web/lib/utils/spine/*.ts`, `lib/config/stone-*.ts` and `lib/i18n/number-words.ts`, tested with the web's own Vitest vectors. The stone is one `Canvas` painted into a cached bitmap with native blur paints.

**Tech Stack:** Kotlin, Jetpack Compose (material3 1.5.0-alpha27), `android.graphics` (`Paint`, `BlurMaskFilter`, `Path`, `Matrix`), Coil 3, kotlinx.serialization, JUnit 4 + Robolectric, Compose Preview Screenshot Testing, Postgres (plpgsql/sql), Deno (verify harness).

**Spec:** `docs/superpowers/specs/2026-10-18-android-spine-path-design.md` (and the web spec it cites for every number: `docs/superpowers/specs/2026-10-11-web-path-spine-design.md` §The rule set). Issues: #1121 (Part 1), #1122 (Parts 2–6). Milestone M64 · New Path.

**Environment notes for every task:**
- Repo root `/Users/alexis/code/pbbls`. Android code under `apps/android/app/src/main/kotlin/app/pbbls/android/` (written `…/android/` below), tests under `apps/android/app/src/test/kotlin/app/pbbls/android/`, screenshot previews under `apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/`.
- Gradle: `export ANDROID_HOME=$HOME/Library/Android/sdk` first, run `./gradlew` from `apps/android`. Unit tests: `./gradlew testDebugUnitTest --tests '<fqcn>'`. Lint: `./gradlew ktlintCheck lint`. Format: `./gradlew ktlintFormat`.
- `ThemeLiteralsTest` fails on `Color(0x…)`, a literal `RoundedCornerShape(` radius or `.copy(fontSize = …)` in `features/` and `core/ui`; `ArchitectureBoundaryTest` fails on `core → features` or `features → features` imports. `core/ui -> core/designsystem -> core/model`; `core/data -> core/model`.
- Commit messages: conventional, lowercase, no period, ending with
  ```
  Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01S5qk3FP8ZKzoigiDjCDdo2
  ```
- Web sources to port are read, never modified. Where a step says "transcribe `X.ts`", keep the web's names, constants and comments' *why*, in idiomatic Kotlin (objects with pure functions, `data class`es, `enum`s); keep the web's rounding (`r2` = round to 2 decimals) where the tests compare numbers.
- Screenshot references are never rendered locally: a new `@PreviewTest` ships without a reference and CI's `rebaseline-screenshots` label creates it.
- The `gh-stack` skill governs branch chaining from Part 2 on: read it before creating Part 2's branch.

---

## File map

| Part | Create | Modify | Delete |
|---|---|---|---|
| 1 | `packages/supabase/supabase/migrations/20261018120000_path_moments_souls.sql` | `packages/supabase/types/database.ts`, `packages/supabase/scripts/verify-moments.ts` | |
| 2 | `core/common/NumberWords.kt`, `core/common/UuidHash.kt`, `core/designsystem/SpineTypography.kt`, `core/designsystem/SpineColors.kt`, `features/path/spine/logic/{LocalDays,SpineWeekBuilder,PersonsOfWeek,PeopleLine,EntryType,CardSize,WeekWords}.kt`, tests for each, `res/values*/strings.xml` entries | `core/model/PathMoment.kt`, `core/designsystem/PebblesTheme.kt`, `features/path/PathViewModel.kt`, `test/…/features/path/PathViewModelTest.kt`, `testing/FakeSoulsService.kt` (if absent) | |
| 3 | `core/ui/render/stone/{StoneGeometry,StoneCarvings,StoneRecipe,StoneTones,StoneMotion,StonePainter,StoneBitmapCache,LitStone,StoneSeal,StoneRipple}.kt`, tests, `screenshotTest/…/LitStoneScreenshots.kt` | | |
| 4 | `features/path/spine/{SpineGround,DaySeam,SpinePebble,SpineEntry,SpineCard,PeopleLine}.kt`, `core/data/SnapAspectCache.kt`, tests, `screenshotTest/…/SpineFeedScreenshots.kt`, `screenshotTest/…/SpineFixtureWeek.kt` | `MainActivity.kt` (provide nothing new: the cache is injected into the ViewModel), `di/` (none: `@Singleton @Inject`) | |
| 5 | `features/path/spine/{WeekMasthead,WeekOpening,PersonsOfWeek,WeekContents,WeekMouth,SpineEmptyWeek,SpineWeek,ReadingDay}.kt`, tests, `screenshotTest/…/SpineWeekScreenshots.kt` | | |
| 6 | | `features/path/PathScreen.kt`, `features/path/components/WeekRoll.kt`, `screenshotTest/…/PathScreenScreenshots.kt`, `MomentPreviewFixtures.kt`, `apps/android/CLAUDE.md`, `docs/decisions/log.md`, `JourneyTags` users | `features/path/components/{PathPebbleRow,WeekPebbleList,WeekHeader,PathSnapThumb}.kt`, `features/path/{WeekHeaderFormatting,PathRowFormatting}.kt` and their tests, `screenshotTest/…/PathRowScreenshots.kt`, `res/drawable/cairn_static.xml` |

---

# Part 1 — `path_moments` carries souls (#1121)

Branch: `feat/1121-path-moments-souls`, created from the spec branch (the spec and this plan ride along): `git branch -m docs/1122-android-spine-spec feat/1121-path-moments-souls`. PR title `feat(db): path_moments carries each pebble's souls`, body `Resolves #1121`, labels `feat`, `api`, `supabase`, `no-lab-note`, milestone M64.

### Task 1.1: The migration

**Files:**
- Create: `packages/supabase/supabase/migrations/20261018120000_path_moments_souls.sql`

- [ ] **Step 1: Confirm no other migration re-emits `path_moments`**

```bash
cd /Users/alexis/code/pbbls && grep -ln "function public.path_moments" packages/supabase/supabase/migrations/*.sql
```

Expected: exactly `20261012090200_moments_split_readers.sql`. If a second file appears, stop: the standing rule says two re-emissions of one body drop each other's appends; union them by hand instead.

- [ ] **Step 2: Write the migration**

The body is `20261012090200`'s `path_moments` verbatim plus the `souls` key (read that file's lines 109–131 first and copy the current body, so nothing else moves):

```sql
-- path_moments: the pebble gains its souls (#1121), for the Spine's persons
-- of the week and people lines. Additive: every installed client ignores
-- unknown keys. Same subquery as v_moments_full, plus glyph_id so a client
-- can draw the soul's mark from its own glyph cache.
create or replace function public.path_moments()
returns table (
  id uuid, name text, happened_at timestamptz, created_at timestamptz,
  first_snap_path text, pebble jsonb
)
language sql
security invoker
stable
set search_path = public
as $$
  select
    m.id, m.name, m.happened_at, m.created_at,
    (select s.storage_path from public.snaps s where s.moment_id = m.id
      order by s.sort_order asc nulls last, s.created_at asc limit 1) as first_snap_path,
    (select jsonb_build_object(
        'id', p.id, 'intensity', p.intensity, 'positiveness', p.positiveness, 'visibility', p.visibility,
        'emotion', case when e.id is null then null else jsonb_build_object('id', e.id, 'slug', e.slug, 'name', e.name) end,
        'glyph', public.resolve_pebble_glyph(p.id),
        'souls', coalesce((
          select jsonb_agg(jsonb_build_object('id', so.id, 'name', so.name, 'glyph_id', so.glyph_id) order by so.name)
          from public.pebble_souls ps join public.souls so on so.id = ps.soul_id
          where ps.pebble_id = p.id), '[]'::jsonb))
       from public.pebbles p left join public.emotions e on e.id = p.emotion_id
      where p.moment_id = m.id and p.user_id = (select auth.uid())) as pebble
  from public.moments m
  where m.user_id = (select auth.uid())
  order by m.happened_at desc;
$$;
comment on function public.path_moments() is
  'The caller''s timeline, newest happened_at first. A null pebble is an uncompleted moment; pebble.souls lists its souls by name (#1121).';
```

Grants are unchanged by `create or replace`; do not re-emit them.

- [ ] **Step 3: Replay on the local Postgres and push**

Per the repo memory, migrations are validated on the native Homebrew `postgresql@16`, never Docker. If the local replay harness is unavailable, skip to the push. Then:

```bash
cd /Users/alexis/code/pbbls && npm run db:push --workspace=packages/supabase
```

The repo's classifier may block `db push` from an agent: if it does, **hand the command to the maintainer** and continue with Step 4 once they confirm it ran.

- [ ] **Step 4: Regenerate the types (remote variant)**

```bash
cd /Users/alexis/code/pbbls && npm run db:types:remote --workspace=packages/supabase && git diff --stat packages/supabase/types/database.ts
```

Expected: no diff, or only the `path_moments` Functions comment line (the return type is unchanged: `pebble` is `Json`). Never use `db:types` (local; truncates the file).

- [ ] **Step 5: Commit**

```bash
cd /Users/alexis/code/pbbls && git add packages/supabase/supabase/migrations/20261018120000_path_moments_souls.sql packages/supabase/types/database.ts
git commit -m "$(cat <<'EOF'
feat(db): path_moments carries each pebble's souls

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01S5qk3FP8ZKzoigiDjCDdo2
EOF
)"
```

### Task 1.2: The harness check

**Files:**
- Modify: `packages/supabase/scripts/verify-moments.ts`

- [ ] **Step 1: Find the souls the harness already creates**

```bash
grep -n "souls\|soul_ids\|pebble_souls\|check(\"path_moments" /Users/alexis/code/pbbls/packages/supabase/scripts/verify-moments.ts | head -20
```

Note which completed pebble carries souls (the `complete_pebble` call with `soul_ids`). If none does, add two souls for user `a` right after their first `complete_pebble` (insert into `souls` `{ name: "Mara" }`, `{ name: "Elio" }`, then call `complete_pebble` again on that pebble with both `soul_ids`; copy the payload shape from the existing call).

- [ ] **Step 2: Add the checks**

After the existing `path_moments lists the moment with a null pebble` check:

```ts
  const { data: pathWithSouls } = await a.rpc("path_moments");
  const rowWithSouls = (pathWithSouls ?? []).find((r: { pebble: { souls?: unknown[] } | null }) => r.pebble && Array.isArray(r.pebble.souls) && r.pebble.souls.length > 0) as
    | { pebble: { souls: { id: string; name: string; glyph_id: string }[] } }
    | undefined;
  check("path_moments pebble.souls lists the pebble's souls", !!rowWithSouls && rowWithSouls.pebble.souls.length === 2);
  check(
    "path_moments pebble.souls is ordered by name and carries glyph_id",
    !!rowWithSouls &&
      rowWithSouls.pebble.souls.map((s) => s.name).join(",") === "Elio,Mara" &&
      rowWithSouls.pebble.souls.every((s) => typeof s.glyph_id === "string" && s.glyph_id.length > 0),
  );
  check(
    "path_moments pebble.souls is [] on a pebble with no souls",
    (pathWithSouls ?? []).some((r: { pebble: { souls?: unknown[] } | null }) => r.pebble && Array.isArray(r.pebble.souls) && r.pebble.souls.length === 0),
  );
```

Update the file header's `Expected: Summary: passed=N` by +3.

- [ ] **Step 3: Run it against the linked project**

```bash
cd /Users/alexis/code/pbbls/packages/supabase && set -a; . ./.env; set +a; npm run db:verify:moments
```

Expected: `Summary: passed=<N+3> failed=0`. If `.env` is missing, ask the maintainer to run it and paste the summary.

- [ ] **Step 4: Commit and open the PR**

```bash
cd /Users/alexis/code/pbbls && git add packages/supabase/scripts/verify-moments.ts
git commit -m "$(cat <<'EOF'
test(db): verify path_moments souls

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01S5qk3FP8ZKzoigiDjCDdo2
EOF
)"
git push -u origin feat/1121-path-moments-souls
```

The PR itself is opened at the end of Part 2 together with the stack (`gh stack`), so the two land as one story.

---

# Part 2 — Android foundations (#1122)

Branch `feat/1122-spine-foundations`, stacked on Part 1 (`gh stack` skill). PR: `feat(android): spine foundations, tokens, logic and souls`, `Part of #1122`, labels `feat`, `ui`, `android`, `no-lab-note`.

### Task 2.1: `PathPebble.souls` and the decoding test

**Files:**
- Modify: `…/android/core/model/PathMoment.kt`
- Test: `test/…/core/model/PathMomentDecodingTest.kt` (create)

- [ ] **Step 1: Write the failing test**

```kotlin
package app.pbbls.android.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verbatim `path_moments()` rows, with and without the #1121 souls key (the foreign-payload rule). */
class PathMomentDecodingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val withSouls =
        """
        {"id":"m1","name":"Flag march","happened_at":"2026-10-05T08:15:00+00:00","created_at":"2026-10-05T08:16:02.123456+00:00",
         "first_snap_path":"u1/s1","pebble":{"id":"p1","intensity":3,"positiveness":1,"visibility":"secret",
         "emotion":{"id":"e1","slug":"pride","name":"Pride"},"glyph":null,
         "souls":[{"id":"s-elio","name":"Elio","glyph_id":"g2"},{"id":"s-mara","name":"Mara","glyph_id":"g1"}]}}
        """.trimIndent()

    private val beforeSouls =
        """
        {"id":"m2","name":null,"happened_at":"2026-10-05T08:15:00Z","created_at":"2026-10-05T08:16:02Z",
         "first_snap_path":null,"pebble":{"id":"p2","intensity":1,"positiveness":0,"visibility":"secret","emotion":null,"glyph":null}}
        """.trimIndent()

    @Test
    fun `decodes souls in server order`() {
        val m = json.decodeFromString<PathMoment>(withSouls)
        assertEquals(listOf("Elio", "Mara"), m.pebble!!.souls.map { it.name })
        assertEquals("g1", m.pebble!!.souls[1].glyphId)
    }

    @Test
    fun `a row from before the key decodes with no souls`() {
        val m = json.decodeFromString<PathMoment>(beforeSouls)
        assertTrue(m.pebble!!.souls.isEmpty())
        assertNull(m.name)
    }

    @Test
    fun `an uncompleted moment still has a null pebble`() {
        val m = json.decodeFromString<PathMoment>("""{"id":"m3","name":"x","happened_at":"2026-10-05T08:15:00Z","created_at":"2026-10-05T08:15:00Z","first_snap_path":null,"pebble":null}""")
        assertNull(m.pebble)
    }
}
```

- [ ] **Step 2: Run it, expect a compile failure** (`souls` unresolved): `./gradlew testDebugUnitTest --tests 'app.pbbls.android.core.model.PathMomentDecodingTest'`.

- [ ] **Step 3: Add the model**

In `PathMoment.kt`, add after `PathPebble`:

```kotlin
/** A soul on a Path pebble (#1121): enough to name it and find its glyph in the souls list. */
@Serializable
data class PathSoul(
    val id: String,
    val name: String,
    @SerialName("glyph_id")
    val glyphId: String,
)
```

and in `PathPebble` add `val souls: List<PathSoul> = emptyList(),` after `glyph` (the default keeps pre-#1121 payloads decoding).

- [ ] **Step 4: Run the test, expect PASS.** Then `./gradlew ktlintFormat`.

- [ ] **Step 5: Commit** — `feat(android): path pebbles carry their souls`.

### Task 2.2: `NumberWords` and `UuidHash`

**Files:**
- Create: `…/android/core/common/NumberWords.kt`, `…/android/core/common/UuidHash.kt`
- Test: `test/…/core/common/NumberWordsTest.kt`, `test/…/core/common/UuidHashTest.kt`

- [ ] **Step 1: Tests first.** Transcribe `apps/web/lib/i18n/number-words.test.ts` (every `expect`, including the EN/FR tables at lines 5–20 and 38–56, read them in full) into JUnit:

```kotlin
package app.pbbls.android.core.common

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class NumberWordsTest {
    private val en = Locale.ENGLISH
    private val fr = Locale.FRENCH

    @Test
    fun `cardinals en and fr`() {
        // copy every (n, en, fr) triple of the web test's table here
        assertEquals("twelve", NumberWords.cardinal(12, en))
        assertEquals("douze", NumberWords.cardinal(12, fr))
        assertEquals("forty-one", NumberWords.cardinal(41, en))
        assertEquals("quarante et un", NumberWords.cardinal(41, fr))
    }

    @Test
    fun `feminine french ones`() {
        assertEquals("une", NumberWords.cardinal(1, fr, NumberWords.Gender.FEMININE))
        assertEquals("vingt et une", NumberWords.cardinal(21, fr, NumberWords.Gender.FEMININE))
        assertEquals("cinquante et une", NumberWords.cardinal(51, fr, NumberWords.Gender.FEMININE))
        assertEquals("deux", NumberWords.cardinal(2, fr, NumberWords.Gender.FEMININE))
        assertEquals("one", NumberWords.cardinal(1, en, NumberWords.Gender.FEMININE))
    }

    @Test
    fun `digits outside range`() {
        assertEquals("61", NumberWords.cardinal(61, en))
        assertEquals("-1", NumberWords.cardinal(-1, fr))
        assertEquals("0", NumberWords.ordinal(0, en))
        assertEquals("54", NumberWords.ordinal(54, fr))
        assertEquals("32", NumberWords.dayOfMonth(32, en))
    }

    @Test
    fun `ordinals`() {
        // copy the web table (lines 38–56) here
        assertEquals("forty-first", NumberWords.ordinal(41, en))
        assertEquals("quarante et unième", NumberWords.ordinal(41, fr))
        assertEquals("première", NumberWords.ordinal(1, fr, NumberWords.Gender.FEMININE))
        assertEquals("vingt et unième", NumberWords.ordinal(21, fr, NumberWords.Gender.FEMININE))
        assertEquals("cinquième", NumberWords.ordinal(5, fr))
        assertEquals("neuvième", NumberWords.ordinal(9, fr))
        assertEquals("quarante-neuvième", NumberWords.ordinal(49, fr))
    }

    @Test
    fun `days of the month`() {
        assertEquals("first", NumberWords.dayOfMonth(1, en))
        assertEquals("twenty-first", NumberWords.dayOfMonth(21, en))
        assertEquals("thirty-first", NumberWords.dayOfMonth(31, en))
        assertEquals("premier", NumberWords.dayOfMonth(1, fr))
        assertEquals("cinq", NumberWords.dayOfMonth(5, fr))
        assertEquals("vingt et un", NumberWords.dayOfMonth(21, fr))
        assertEquals("trente et un", NumberWords.dayOfMonth(31, fr))
    }
}
```

and

```kotlin
class UuidHashTest {
    @Test
    fun `fnv1a matches the web seed`() {
        // The web's hashUUID("0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b"); compute the expected value once with
        // `node -e 'const s=require("/Users/alexis/code/pbbls/apps/web/lib/engine/seed.ts")'` is not possible (TS);
        // instead run from apps/web: `npx tsx -e 'import {hashUUID} from "./lib/engine/seed"; console.log(hashUUID("0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b"), hashUUID("8d1f6c3e-2b7a-4c1e-9f0a-5e6d7c8b9a01"))'`
        // and paste both numbers below.
        assertEquals(<paste first>L, UuidHash.fnv1a("0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b"))
        assertEquals(<paste second>L, UuidHash.fnv1a("8d1f6c3e-2b7a-4c1e-9f0a-5e6d7c8b9a01"))
    }
}
```

(The two `<paste …>` values are produced by the command in the comment, in this task, before the test runs; they are not placeholders left in the file.)

- [ ] **Step 2: Implement.** `NumberWords`: an `object` with `enum class Gender { MASCULINE, FEMININE }`, `fun cardinal(n: Int, locale: Locale, gender: Gender = Gender.MASCULINE): String`, `fun ordinal(...)`, `fun dayOfMonth(day: Int, locale: Locale): String`; `locale.language == "fr"` selects French, anything else English; tables and the `frOrdinal` spelling rules transcribed from `number-words.ts`. `UuidHash`: 

```kotlin
package app.pbbls.android.core.common

/** FNV-1a over the UUID's UTF-16 code units, as the web's `hashUUID` (`lib/engine/seed.ts`): the same pebble seeds the same stone on every surface. */
object UuidHash {
    private const val OFFSET = 0x811c9dc5L
    private const val PRIME = 0x01000193L

    fun fnv1a(uuid: String): Long {
        var hash = OFFSET
        for (ch in uuid) {
            hash = hash xor ch.code.toLong()
            hash = (hash * PRIME) and 0xffffffffL
        }
        return hash
    }
}
```

(`Math.imul` is a 32-bit wrap; masking the product to 32 bits each step reproduces it because both operands are already below 2^32 and the mask keeps the low 32 bits of the true product, which is what `imul` returns as unsigned after `>>> 0`.)

- [ ] **Step 3: Run both tests, expect PASS. Commit** — `feat(android): number words and the uuid seed hash`.

### Task 2.3: Pure Spine logic, with the web's vectors

**Files:**
- Create: `…/android/features/path/spine/logic/LocalDays.kt`, `SpineWeekBuilder.kt`, `PersonsOfWeek.kt`, `PeopleLine.kt`, `EntryType.kt`, `CardSize.kt`, `WeekWords.kt`
- Test: `test/…/features/path/spine/logic/{SpineWeekBuilderTest,PersonsOfWeekTest,PeopleLineTest,EntryTypeTest,CardSizeTest,WeekWordsTest}.kt`

Work module by module, test first each time, reading the web `.ts` and its `.test.ts` side by side (`apps/web/lib/utils/spine/<name>.ts` and `.test.ts`; `week-opening.ts` → `WeekWords.kt`).

- [ ] **Step 1: `LocalDays`** — `object LocalDays { fun dayIndexInWeek(weekStart: LocalDate, at: OffsetDateTime, zone: ZoneId): Int }` returning `ChronoUnit.DAYS.between(weekStart, at.atZoneSameInstant(zone).toLocalDate()).toInt()`. (Local dates make the DST rounding unnecessary; the tests below prove the DST cases.)

- [ ] **Step 2: `SpineWeekBuilder`** — API:

```kotlin
data class SpineDay(val date: LocalDate, val index: Int, val moments: List<PathMoment>, val still: Boolean, val isToday: Boolean)
data class SpineWeek(val weekStart: LocalDate, val days: List<SpineDay>) { val readingOrder: List<PathMoment> get() = days.flatMap { it.moments } }
object SpineWeekBuilder {
    fun build(entry: WeekRollEntry, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): SpineWeek
}
```

Transcribe `spineWeek`: bucket by local day, sort each day by `happenedAt` then `id`, keep still past days, drop empty future days, `isToday`. Test: every case of `spine-week.test.ts` (lines 70–194; build the fixtures with `MomentPreviewFixtures`-like helpers in the test), including "keeps a Sunday 23:59 pebble on the Sunday the clocks go back" with `ZoneId.of("Europe/Paris")` and "lays out what iOS and Android wrote, verbatim" with the test's timestamps as `OffsetDateTime.parse`.

- [ ] **Step 3: `PersonsOfWeek`** — API:

```kotlin
data class PersonOfWeek(val soul: PathSoul, val days: BooleanArray, val dayCount: Int, val pebbleCount: Int)
object PersonsOfWeek {
    const val CAP = 8
    fun of(moments: List<PathMoment>, weekStart: LocalDate, zone: ZoneId, locale: Locale): List<PersonOfWeek>
    fun split(rows: List<PersonOfWeek>, cap: Int = CAP): Pair<List<PersonOfWeek>, List<PersonOfWeek>>
}
```

Souls come from `moment.pebble?.souls` (deduplicated by id). Ordering: days desc, pebbles desc, `Collator.getInstance(locale)` on name, then id. (The web skips souls missing from its map; here the row carries the soul, so no map.) Test: every case of `persons-of-week.test.ts` (DST spring and autumn, next-Monday exclusion, ordering, accent collation with `Locale.FRENCH`, id tie, duplicates, the cap).

- [ ] **Step 4: `PeopleLine`** — API: `data class PeopleLine(val named: List<String>, val others: Int, val othersWords: String, val marks: List<String>, val overflow: Int)`; `object PeopleLine { const val MARKS = 5; fun of(soulIds: List<String>, order: List<String>, locale: Locale): PeopleLine }` using `NumberWords.cardinal`. Test: the seven cases of `people-line.test.ts`.

- [ ] **Step 5: `EntryType`** — API with `enum class Cut { HIGHLIGHT, NEUTRAL, LOWLIGHT }` mapped from `ValencePolarity`: `data class NameType(val fontSizeSp: Float, val fontWeight: Int, val italic: Boolean, val letterSpacingEm: Float)`, `data class EntryType(val name: NameType, val lineHeight: Float, val anchorDp: Float)`; `object EntryTypes { fun entry(intensity: Int, polarity: ValencePolarity): EntryType; fun contentsName(intensity: Int, polarity: ValencePolarity): NameType; fun cardTitleCut(polarity: ValencePolarity): Pair<Int, Boolean> }`. Phone values only (spec D4): sizes 19/24/31, weights 600/460/340, contents 16.5 and 400/470/620, lowlight italic 350. Test: `entry-type.test.ts`'s phone expectations.

- [ ] **Step 6: `CardSize`** — transcribe `card-size.ts` with phone caps only: `object CardSizes { val CHROME_VERTICAL = 123f; val CHROME_HORIZONTAL = 95f; val MIN_WIDTH = 180f; val PENDING_RATIO = 1f; fun cap(intensity: Int): Float; fun edge(intensity: Int): Float; fun size(intensity: Int, ratio: Float?, column: Float): CardSize }` with `data class CardSize(val width: Float, val height: Float, val cap: Float)`. Test: the phone rows of the web table (`card-size.test.ts` lines 1–47), the cap, the floor, the column, D14.

- [ ] **Step 7: `WeekWords`** — API:

```kotlin
sealed interface WeekTitle {
    data class SameMonth(val from: String, val to: String, val month: String) : WeekTitle
    data class CrossMonth(val from: String, val fromMonth: String, val to: String, val toMonth: String) : WeekTitle
}
data class WeekWords(val number: Int, val year: Int, val ordinal: String, val title: WeekTitle, val previousNumber: Int, val nextNumber: Int, val nextOrdinal: String, val nextMonday: String)
object WeekWordsBuilder {
    fun of(weekStart: LocalDate, locale: Locale): WeekWords
    fun summary(pebbles: Int, people: Int, locale: Locale): Pair<String, String>
    fun sentenceCase(text: String, locale: Locale): String
    fun weekdayLetters(weekStart: LocalDate, locale: Locale): List<String>   // TextStyle.NARROW, lowercase
    fun dotDays(days: BooleanArray, weekStart: LocalDate, locale: Locale): Pair<Int, String>   // ListFormatter.getInstance(locale)
}
```

Month names: `Month.getDisplayName(TextStyle.FULL, locale)`; ISO week and week-year from `IsoFields`. `ListFormatter` is `android.icu.text.ListFormatter` (API 26+, fine). Test: the cases of `week-opening.test.ts` (the brief's week Mon 5 Oct 2026, French feminine, the cross-month week, the new-year wrap, "premier").

- [ ] **Step 8: Run all six tests, ktlintFormat, commit** — `feat(android): spine week, persons, people line, type ranks and card sizing`.

### Task 2.4: Tokens — `SpineTypography` and `SpineColors`

**Files:**
- Create: `…/android/core/designsystem/SpineTypography.kt`, `SpineColors.kt`
- Modify: `…/android/core/designsystem/PebblesTheme.kt`
- Test: `test/…/core/designsystem/SpineColorsTest.kt`

- [ ] **Step 1: `SpineColors`**

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * The Spine's paper, from the scheme (spec 2026-10-18-android-spine-brand-scheme-design.md,
 * §The rule set). Alphas ride on `onSurface` as the web's tokens ride on its ink. Emotion
 * palettes are never scheme roles (#853): [spot], [deep] and [edge] take a palette.
 */
@Immutable
data class SpineColors(
    val isNight: Boolean,
    val ground: Color,
    val stock: Color,
    val halo: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val rule: Color,
    val cardRule: Color,
    val line: Color,
    val seam: Color,
    val teeth: Color,
    val ring: Color,
    val ripple: Color,
    val shadow: Color,
    val shadowAlpha: Float,
    val tintK: Float,
    val patK: Float,
    val error: Color,
) {
    companion object {
        fun from(scheme: ColorScheme, night: Boolean): SpineColors {
            val ink = scheme.onSurface
            return SpineColors(
                isNight = night,
                ground = scheme.surface,
                stock = if (night) scheme.surfaceContainerLow else scheme.surfaceContainerLowest,
                halo = scheme.surfaceContainerLow,
                ink = ink,
                ink2 = scheme.onSurfaceVariant,
                ink3 = scheme.outline,
                rule = ink.copy(alpha = if (night) 0.12f else 0.14f),
                cardRule = ink.copy(alpha = if (night) 0.18f else 0.22f),
                line = ink.copy(alpha = if (night) 0.30f else 0.34f),
                seam = ink.copy(alpha = if (night) 0.24f else 0.30f),
                teeth = ink.copy(alpha = if (night) 0.14f else 0.20f),
                ring = if (night) ink.copy(alpha = 0.17f) else scheme.onSurfaceVariant.copy(alpha = 0.30f),
                ripple = ink.copy(alpha = if (night) 0.50f else 0.55f),
                shadow = if (night) Color.Black else ink,
                shadowAlpha = if (night) 0.62f else 0.30f,
                tintK = if (night) 0.13f else 0.09f,
                patK = if (night) 0.40f else 0.34f,
                error = scheme.error,
            )
        }
    }
}

@Composable
fun rememberSpineColors(): SpineColors {
    val scheme = MaterialTheme.colorScheme
    val night = isSystemInDarkTheme()
    return remember(scheme, night) { SpineColors.from(scheme, night) }
}
```

Test: `from(LightScheme, false).ground == LightScheme.surface`, `stock == surfaceContainerLowest`, `from(DarkScheme, true).stock == DarkScheme.surfaceContainerLow`, `ring` alphas, `shadow` is black at night.

- [ ] **Step 2: `SpineTypography`** — in the pattern of `PebblesHandTypography`, on `YsabeauFamily` (it is `internal` in `PebblesTypeface.kt`; same package, fine):

```kotlin
object SpineTypography {
    private const val SMALL_CAPS = "smcp, c2sc"
    private const val LINING = "lnum, pnum"
    private const val TABULAR = "lnum, tnum"

    private fun ysabeau(size: TextUnit, weight: Int, tracking: TextUnit = 0f.em, italic: Boolean = false, features: String? = null, lineHeight: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontFamily = YsabeauFamily, fontSize = size, fontWeight = FontWeight(weight), letterSpacing = tracking,
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal, fontFeatureSettings = features, lineHeight = lineHeight,
            platformStyle = PlatformTextStyle(includeFontPadding = false))

    val kicker = ysabeau(12.sp, 600, 0.2f.em, features = SMALL_CAPS)
    val title = ysabeau(40.sp, 300, (-0.026f).em, features = LINING, lineHeight = 40.8.sp)
    val summary = ysabeau(18.sp, 380, italic = true)
    val sectionLabel = ysabeau(11.5.sp, 600, 0.2f.em, features = SMALL_CAPS)
    val masthead = ysabeau(11.5.sp, 600, 0.18f.em, features = SMALL_CAPS)
    val personName = ysabeau(15.sp, 600, 0.1f.em, features = SMALL_CAPS)
    val dayLetter = ysabeau(9.5.sp, 600, features = SMALL_CAPS)
    val contentsDay = ysabeau(10.5.sp, 600, 0.18f.em, features = SMALL_CAPS)
    val contentsTime = ysabeau(12.5.sp, 500, 0.04f.em, features = TABULAR)
    val entryTime = ysabeau(13.sp, 560, 0.06f.em, features = TABULAR)
    val entryEmotion = ysabeau(12.sp, 600, 0.12f.em, features = SMALL_CAPS)
    val people = ysabeau(15.sp, 400, lineHeight = 22.5.sp)
    val soulName = ysabeau(13.sp, 620, 0.1f.em, features = SMALL_CAPS)
    val dayNumeral = ysabeau(25.sp, 330, (-0.02f).em, features = LINING)
    val dayWeekday = ysabeau(10.sp, 600, 0.2f.em, features = SMALL_CAPS)
    val mouthLabel = ysabeau(12.sp, 600, 0.18f.em, features = SMALL_CAPS)
    val mouthLine = ysabeau(16.sp, 380, italic = true)
    val rollNumeral = ysabeau(14.sp, 600, 0.1f.em, features = SMALL_CAPS + ", " + LINING)
    val still = ysabeau(15.sp, 380, italic = true)

    fun contentsName(weight: Int, italic: Boolean) = ysabeau(16.5.sp, weight, italic = italic, lineHeight = 19.8.sp)
    fun entryName(sizeSp: Float, weight: Int, italic: Boolean, trackingEm: Float) = ysabeau(sizeSp.sp, weight, trackingEm.em, italic = italic, lineHeight = (sizeSp * 1.12f).sp)
    /** Card ranks take the card unit `u` in sp (the caller converts its dp unit at the density). */
    fun cardIndexNumeral(u: Float) = ysabeau((5.3f * u).sp, 600, (-0.045f).em, features = LINING, lineHeight = (5.3f * u * 0.86f).sp)
    fun cardIndexDay(u: Float) = ysabeau((1.8f * u).sp, 650, 0.14f.em, features = SMALL_CAPS)
    fun cardRail(u: Float) = ysabeau((1.95f * u).sp, 560, 0.07f.em, features = TABULAR)
    fun cardRailEmotion(u: Float) = ysabeau((1.95f * u).sp, 650, 0.15f.em, features = SMALL_CAPS)
    fun cardTitle(u: Float, weight: Int, italic: Boolean) = ysabeau((4.4f * u).sp, weight, (-0.012f).em, italic = italic, lineHeight = (4.4f * u * 1.1f).sp)
    fun cardSoul(u: Float) = ysabeau((2.5f * u).sp, 650, 0.12f.em, features = SMALL_CAPS)
    fun cardMore(u: Float) = ysabeau((2.5f * u).sp, 560, features = TABULAR)
}
```

Keep the `fontFeatureSettings` strings exactly: Android parses the CSS `font-feature-settings` syntax. Add `val spine: SpineTypography @Composable get() = LocalSpineTypography.current` to `PebblesTheme` with a `staticCompositionLocalOf { SpineTypography }` provided in `PebblesTheme()`.

- [ ] **Step 3: `./gradlew testDebugUnitTest --tests '*SpineColorsTest' --tests '*ThemeLiteralsTest'`** (the latter must stay green: these files are in `core/designsystem`, which it does not scan; confirm). Commit — `feat(android): spine colour and type tokens`.

### Task 2.5: Strings EN/FR

**Files:**
- Modify: `apps/android/app/src/main/res/values/strings.xml`, `values-fr/strings.xml`

- [ ] **Step 1: Add the keys.** Under a `<!-- Path · Spine (#1122) -->` comment, EN then the FR equivalents (the web's `path.spine.*` values in `apps/web/lib/i18n/messages/{en,fr}.json`, transcribed; `%1$s`-style args in the order the composables will pass them):

```xml
    <string name="path_spine_kicker">the %1$s week · %2$d</string>
    <string name="path_spine_title_same_month">The %1$s to the %2$s of %3$s</string>
    <string name="path_spine_title_cross_month">The %1$s of %2$s to the %3$s of %4$s</string>
    <plurals name="path_spine_summary_pebbles">
        <item quantity="one">%1$s pebble</item>
        <item quantity="other">%1$s pebbles</item>
    </plurals>
    <string name="path_spine_summary_no_pebbles">no pebbles</string>
    <plurals name="path_spine_summary_people">
        <item quantity="one">, %1$s person</item>
        <item quantity="other">, %1$s people</item>
    </plurals>
    <string name="path_spine_persons">persons of the week</string>
    <plurals name="path_spine_persons_more">
        <item quantity="one">and %1$s other</item>
        <item quantity="other">and %1$s others</item>
    </plurals>
    <string name="path_spine_contents">contents</string>
    <string name="path_spine_with">with</string>
    <plurals name="path_spine_others">
        <item quantity="one">&amp; %1$s other</item>
        <item quantity="other">&amp; %1$s others</item>
    </plurals>
    <string name="path_spine_still">still</string>
    <string name="path_spine_untitled">untitled</string>
    <string name="path_spine_photo_unavailable">photo unavailable</string>
    <string name="path_spine_week_ends">the %1$s week ends here</string>
    <string name="path_spine_next_begins">The %1$s begins on Monday the %2$s.</string>
    <string name="path_spine_week_link">week %1$d</string>
    <plurals name="path_spine_day_dots_a11y">
        <item quantity="one">On one day: %2$s</item>
        <item quantity="other">On %1$d days: %2$s</item>
    </plurals>
    <string name="path_spine_stone_a11y">%1$s. %2$s, %3$s. %4$s, %5$s. %6$s Opens the pebble.</string>
    <string name="path_spine_blank_a11y">%1$s. %2$s, %3$s. Not a pebble yet. %4$s Opens the moment.</string>
    <string name="path_spine_with_names">With %1$s.</string>
    <string name="path_spine_glance_a11y">The week at a glance</string>
    <string name="path_spine_feed_a11y">The week, %1$s</string>
    <string name="path_spine_stone_small_lowlight">Small lava stone</string>
    <string name="path_spine_stone_small_neutral">Small river pebble</string>
    <string name="path_spine_stone_small_highlight">Small gem</string>
    <string name="path_spine_stone_medium_lowlight">Medium lava stone</string>
    <string name="path_spine_stone_medium_neutral">Medium river pebble</string>
    <string name="path_spine_stone_medium_highlight">Medium gem</string>
    <string name="path_spine_stone_large_lowlight">Large lava stone</string>
    <string name="path_spine_stone_large_neutral">Large river pebble</string>
    <string name="path_spine_stone_large_highlight">Large gem</string>
```

French (`values-fr`): `la %1$s semaine · %2$d`; `Du %1$s au %2$s %3$s`; `Du %1$s %2$s au %3$s %4$s`; `%1$s galet` / `%1$s galets`; `aucun galet`; `, %1$s personne` / `, %1$s personnes`; `les personnes de la semaine`; `et %1$s autre` / `et %1$s autres`; `sommaire`; `avec`; `&amp; %1$s autre` / `&amp; %1$s autres`; `calme`; `sans titre`; `photo indisponible`; `la %1$s semaine s\'arrête ici`; `La %1$s commence le lundi %2$s.`; `semaine %1$d`; `Un jour : %2$s` / `%1$d jours : %2$s`; `%1$s. %2$s, %3$s. %4$s, %5$s. %6$s Ouvre le galet.`; `%1$s. %2$s, %3$s. Pas encore un galet. %4$s Ouvre le moment.`; `Avec %1$s.`; `La semaine en un coup d\'œil`; `La semaine, %1$s`; the nine stones: `Petite pierre de lave`, `Petit galet de rivière`, `Petite gemme`, `Pierre de lave moyenne`, `Galet de rivière moyen`, `Gemme moyenne`, `Grande pierre de lave`, `Grand galet de rivière`, `Grande gemme`.

- [ ] **Step 2: `./gradlew testDebugUnitTest --tests '*LocalizationParityTest'`** green (key-set parity). Lint will flag unused strings until Part 5 uses them: add `tools:ignore="UnusedResources"` on the block's first element is NOT the convention; instead run `./gradlew lint` and, if `UnusedResources` fires, suppress per-file with `<string … tools:ignore="UnusedResources">` on each new key and remove the attribute in Part 5. Commit — `feat(android): spine strings en/fr`.

### Task 2.6: Souls in the Path ViewModel

**Files:**
- Modify: `…/android/features/path/PathViewModel.kt`, `test/…/features/path/PathViewModelTest.kt`, `test/…/testing/FakeSoulsService.kt` (create if absent; check `ls test/…/testing/`)

- [ ] **Step 1: Failing test** — in `PathViewModelTest`, a case "content carries the souls by id" arming the fake souls service with two `SoulWithGlyph`s and asserting `(state as PathUiState.Content).soulsById.keys == setOf(...)`; and "a souls failure leaves an empty map, not an error" (fake throws → `Content` with empty map, `Log.e` tagged `path`).

- [ ] **Step 2: Implement** — inject `SoulsServicing` (it exists), add `val soulsById: Map<String, SoulWithGlyph>` to `Content`, load it in `fetch()` in parallel with the timeline (`coroutineScope { async {} }`), `runCatchingCancellable` around the souls call mapping failure to `emptyMap()` with a log. Keep `publish()` passing it through.

- [ ] **Step 3: Tests green, ktlintFormat, commit** — `feat(android): path view model loads the souls map`. Then push the stack (`gh stack` skill) and open the Part 1 and Part 2 PRs.

---

# Part 3 — The lit stone and the seal

Branch `feat/1122-lit-stone` on Part 2. PR `feat(android): compose lit stone, seal and ripple`, `Part of #1122`, `no-lab-note`, plus `rebaseline-screenshots` added alone after creation (new previews).

All files in `…/android/core/ui/render/stone/`. Package `app.pbbls.android.core.ui.render.stone`. Everything is `internal` except `LitStone`, `StoneSeal`, `StoneRipple` and the `StonePalette` type.

### Task 3.1: `StoneGeometry`

**Files:** `StoneGeometry.kt`; test `StoneGeometryTest.kt`.

- [ ] **Step 1: Tests.** Glyph zones equal `StoneComposer.glyphSlot` for all nine; `unitsPerPx(SMALL) == 337f / 34f`; `ringGeometry(SMALL, NEUTRAL)` yields one ring and a halo whose matrices, rounded to 3 decimals, equal the web's (compute them once with the web: from `apps/web`, `npx tsx -e 'import {ringGeometry} from "./lib/config/stone-geometry"; console.log(ringGeometry("small","neutral"))'` and paste the `matrix(...)` numbers as the expected `a, d, e, f`); `carveTransform(MEDIUM, LOWLIGHT)` scale equals `canvasScale × pebbleScale × 1.22`.

- [ ] **Step 2: Implement** — transcribe `stone-geometry.ts`:

```kotlin
internal object StoneGeometry {
    val STONE_WIDTH_DP = mapOf(ValenceSizeGroup.SMALL to 34f, ValenceSizeGroup.MEDIUM to 44f, ValenceSizeGroup.LARGE to 54f)
    const val CARVE_SCALE = 1.22f
    const val CARVE_STROKE_PX = 1.25f
    val SEED_CANVAS: Map<ValenceSizeGroup, Size>   // 250×200, 260×260, 260×310
    val SHADOW_FAR_PX = Offset(3.86f, 6.95f); val SHADOW_NEAR_PX = Offset(0.93f, 2.01f); val LIP_PX = Offset(0.32f, 0.54f)
    object Blur { const val NEAR = 5f; const val FAR = 15f; const val AO = 7f; const val SOFT = 12f; const val GLOW = 4f }
    val RING_COUNT = mapOf(SMALL to 1, MEDIUM to 2, LARGE to 3); private val RING_STEP_PX = mapOf(SMALL to 4.8f, MEDIUM to 4.4f, LARGE to 4.2f); private const val HALO_STEP = 0.78f
    fun outlineBox(size): Size        // from PebbleOutlineGeometry's table (expose it there as internal)
    fun unitsPerPx(size): Float = outlineBox(size).width / STONE_WIDTH_DP.getValue(size)
    fun canvasScale(size): Float
    fun canvasTransform(size, polarity): Matrix   // centred fit of the seed canvas in the outline box
    fun carveTransform(size, polarity): Matrix    // same, scale × pebbleScale × CARVE_SCALE
    fun glyphTransform(size, polarity, viewBoxSide: Float): Matrix   // translate(zone) scale(GLYPH_SIZE/side), zones = StoneComposer.glyphSlot
    data class RingGeometry(val rings: List<Matrix>, val halo: Matrix)
    fun ringGeometry(size, polarity, outerContour: List<Offset>): RingGeometry   // centroid + bbox of the flattened outer contour; steps as the web
    fun glyphSide(viewBox: String): Float   // "0 0 200 200" → 200f; 200f if unreadable
}
```

Flattening comes from `WobblePathFlattener` (reuse; it is `internal` in `wobble`, same module). The outer contour is the first contour of the outline asset's path (`large-lowlight`'s second is its hole).

- [ ] **Step 3: Tests green, commit** — `feat(android): stone geometry, the three spaces of a lit stone`.

### Task 3.2: `StoneCarvings`

**Files:** `StoneCarvings.kt`; test `StoneCarvingsTest.kt`.

- [ ] **Step 1: Test** — for all nine `R.raw.shape_*` (read through `Robolectric`'s resources or by file path `apps/android/app/src/main/res/raw/` in a plain JVM test, as `StoneComposerTest` does; follow its pattern), `StoneCarvings.parse(svgText)` yields `veins.size` = 0/0/0, 1/1/2, 1/2/6 (small/medium/large × lowlight/neutral/highlight), `fossil == null` for large highlight only, and the dropped outline of `large-neutral` is the third stroked path in document order.

- [ ] **Step 2: Implement** — `data class StoneCarving(val width: Float, val height: Float, val veins: List<String>, val fossil: Fossil?)`, `data class Fossil(val d: String, val evenOdd: Boolean)`. Parse with the existing `PebbleSvgModel`'s XML approach (`parsePebbleSvg` strips stroke-only detail; here write a small dedicated parser in this file using `org.xmlpull` as the existing one does: collect `<path>` elements with `d`, `fill`, `stroke`, `fill-rule`); classify: stroked (`stroke != "none"`) paths → measure the extent (flatten with `WobblePathFlattener`, bounding-box diagonal); the largest is the outline, dropped; the rest are veins in document order; the first filled path (`fill != "none"`) is the fossil. Cache per resource id in a `ConcurrentHashMap` like `StoneShapes.parsedShapes`.

- [ ] **Step 3: Tests green, commit** — `feat(android): stone carvings from the shape seeds`.

### Task 3.3: `StoneRecipe` and `StoneMotion`

**Files:** `StoneRecipe.kt`, `StoneMotion.kt`; test `StoneRecipeTest.kt`.

- [ ] **Step 1: Test** — transcribe `stone-recipe.test.ts` fully: `SEED = UuidHash.fnv1a("0e9d8c7b-6a5f-4e3d-8c2b-1a0f9e8d7c6b")`; determinism; difference for the second uuid; gem = 5 facets of 4 points with edges + a 5-point face at opacity .14, 10 light dots, 2 sparkles; facet slots `white, white, dark, dark, white`; river 14 dots light or shaded; lava 9 dark dots; dots inside the canvas with `r × px / width` in `[0.39, 1.0]`; `glintGeometry(MEDIUM)`: sheen `cx 98.8, cy 78, rx 78, rotate -24`, travel 416, sweep `x -143, y -104, height 468`; `starPath(82.5, 50, 21.25)` equals the web string exactly (keep the `r2` rounding and the `"${x} ${y}"` formatting; Kotlin `Float` prints differ from JS, so format with `BigDecimal`-free logic: round to 2 decimals and strip trailing zeros as JS does).

- [ ] **Step 2: Implement** — transcribe `stone-recipe.ts` (mulberry32 on `Int`/`Long` with the same bit ops: `a = (a + 0x6d2b79f5) and 0xffffffff`, `imul` as 32-bit multiply, `>>> 15` as `ushr`), `Slot { LIGHT, SHADED, DARK, WHITE }`, `Dot`, `Facet`, `Ellipse`, `sealed interface StoneRecipe { Gem, Pebble, Lava }`, `stoneRecipe(size, polarity, seed: Long)`, `glintGeometry(size)`, `starPath(cx, cy, r): String` (the string is for the test; also provide `starPathAsPath()` building an `android.graphics.Path` with `quadTo`).

`StoneMotion`: constants `RIPPLE_REACH {7.2, 5.6, 4.4}`, `WAVE { duration 1650, step 170, delay 120, delayStep 230 }`, `DIP`, `RINGS`, `SWEEP`, `SPARK` as `(durationMs, delayMs, easing: CubicBezierEasing, keyframes)`; `STILL_TOUCH_MS 900`, `OPEN_DELAY_MS 360`, `GO_PRESS_DELAY_MS 620`.

- [ ] **Step 3: Tests green, commit** — `feat(android): stone recipe and press motion tables`.

### Task 3.4: `StoneTones` and `StonePalette`

**Files:** `StoneTones.kt`; test `StoneTonesTest.kt`.

- [ ] **Step 1: Test** — `BODY_RAMP(HIGHLIGHT, palette)` has stops at 0, .4, 1 with the mixes (sRGB mix `secondary 66% + light`, `primary`, `shaded 70% + dark`); `BLANK_PALETTE` equals the shame greys; `GLINT_STRENGTH` and `GRAIN_STRENGTH` values; `mixSrgb(Color.White, Color.Black, 0.5f)` ≈ `#808080`; `mixOklab` of the lowlight stop 0 for the pride palette is within 2/255 of the web's baked hex (compute once in the browser or trust the formula; mark the tolerance).

- [ ] **Step 2: Implement** — `data class StonePalette(val primary: Color, val secondary: Color, val light: Color, val shaded: Color, val dark: Color)` with `fun EmotionPalette.toStonePalette()` and `val BLANK_PALETTE`; also the palette-dependent Spine colours the spec lists on `SpineColors` (`spot(palette)`, `deep(palette)`, `edge(palette, polarity)`) and the night `ColorMatrix` filters: they live HERE in `StoneTones` (Part 2 deliberately shipped `SpineColors` without them, since they need the palette type); `SpineColors` is left as is; `data class Paint(val color: Color, val opacity: Float, val widthPx: Float? = null, val dx: Float = 0f, val dy: Float = 0f, val glow: Boolean = false)`; functions of `(polarity, palette)` returning the web tables: `bodyRamp(): List<Pair<Float, Color>>`, `glyphTones(): Pair<Paint, Paint>` (lip, ink), `veinTones(): List<Paint>`, `fossilTones(): List<Paint>`, `edgeShade(): Paint`, `glintStrength(): Float`, `grainStrength(): Float`, `slotColor(slot, palette)`. `mixSrgb` is a linear interpolation of the sRGB channels (CSS `color-mix(in srgb)`); `mixOklab` converts through OKLab (the standard sRGB→linear→LMS→Lab matrices) and back. Night filters: `nightMatrix(polarity): ColorMatrix` building brightness .92 × saturation .96, or brightness 1.2 × saturation 1.05 for lowlight (`ColorMatrix.setToSaturation` then scale).

- [ ] **Step 3: Tests green, commit** — `feat(android): stone tones over the palette slots`.

### Task 3.5: `StonePainter` and `StoneBitmapCache`

**Files:** `StonePainter.kt`, `StoneBitmapCache.kt`; test `StonePainterTest.kt` (Robolectric, draws into a bitmap and asserts a few pixels).

- [ ] **Step 1: Test** — paint a medium highlight pride stone at 44 dp × 3 density into a bitmap: the centre pixel is not transparent and is closer to the palette's primary than to white; a pixel outside the silhouette (top-left corner) is transparent; painting twice yields byte-identical bitmaps (determinism); a null palette paints the blank greys (centre pixel grey, `r == g == b` within 2).

- [ ] **Step 2: Implement `StonePainter`** — `internal class StoneSpec(val size: ValenceSizeGroup, val polarity: ValencePolarity, val palette: StonePalette?, val glyph: PebbleGlyph?, val seed: Long, val colors: SpineColors, val density: Float)` and `internal object StonePainter { fun paint(spec: StoneSpec, canvas: android.graphics.Canvas, widthPx: Int, heightPx: Int) }`. Steps, with `pxPerUnit = widthPx / outlineBox.width`:
  1. Build the body `Path`: the outline asset (`OutlineAssets.resId`) parsed to a `Path` (`androidx.core.graphics.PathParser.createPathFromPathData`), wobbled through `WobbleOutlineBuilder` only for `LOWLIGHT`, then scaled by `pxPerUnit`.
  2. Shadows: `Paint { color = colors.shadow; alpha = (shadowAlpha × 255); maskFilter = BlurMaskFilter(Blur.FAR × canvasScale × pxPerUnit × BLUR_K, NORMAL) }`, `canvas.save(); translate(SHADOW_FAR_PX × upx × pxPerUnit); drawPath(body); restore()`, then the near copy at `× 1.35` alpha. `BLUR_K = 2.5f` (the σ → radius factor; a named constant to tune on the gallery, spec §Risks).
  3. Body: `Paint { shader = RadialGradient(cx = .36 w, cy = .27 h, r = .9 w, colors = ramp, stops = [0, mid, 1], CLAMP) }`, `drawPath(body)`.
  4. `canvas.save(); clipPath(body)`; then in canvas space (`concat(canvasTransform × pxPerUnit)`): gem facets (`drawPath(polygon)` with slot colour and alpha; edges stroked `light` @ .3, width `0.8 / scale`); lava dots; carve space (`carveTransform`): lava fossil, veins (each `Paint` stroke width in seed units; `glow` → `BlurMaskFilter(Blur.GLOW)`; offsets `translate(dx, dy)`), river/gem fossil; river/gem dots; gem sparkles (white @ .92); river/lava soft light (`drawOval` with `BlurMaskFilter(Blur.SOFT)`); grain: for `grainStrength > 0`, sample `SVGTurbulence(7)` at `(x + shift, y + shift)` per device pixel over the clip's bounds, `baseFrequency .9` two octaves, and draw dark specks where `R × 6 − 3.5 > 0` and light where `G × 4 − 2.75 > 0` (alpha = that value × strength) by writing into a small `Bitmap` once per `(size, strength, shift)` and drawing it clipped; edge shade: `drawPath(body)` with `style = STROKE`, width `edgeShade.widthPx × density`, colour `dark` @ opacity, `BlurMaskFilter(Blur.AO × canvasScale × pxPerUnit × BLUR_K)`; glyph: if `glyph != null && palette != null`, `concat(carve); concat(glyphTransform)`, strokes as `Path`s from `d`, width `CARVE_STROKE_PX × density / totalScale`, round cap/join; lip first (translated by `LIP_PX × upx` before the carve), ink second. `canvas.restore()`.
  5. Night: the caller applies `nightMatrix` when blitting the bitmap (`Paint.colorFilter = ColorMatrixColorFilter`), not here, so one bitmap serves both editions? No: the shadow colour differs per edition anyway; keep the edition in the key and apply the matrix inside `paint()` by drawing the stone into an intermediate layer with `saveLayer(paintWithColorFilter)`.

`StoneBitmapCache`: `internal object StoneBitmapCache { private val lru = android.util.LruCache<String, ImageBitmap>(64); fun get(spec: StoneSpec, w: Int, h: Int): ImageBitmap }` with the key `"${size}:${polarity}:${palette?.hashCode()}:${glyph?.id}:${glyph?.strokes.hashCode()}:${seed}:${colors.shadow.value}:${colors.shadowAlpha}:${colors.isNight}:${w}x${h}"`; paints on a miss into `Bitmap.createBitmap(w, h, ARGB_8888)`.

- [ ] **Step 3: Test green, commit** — `feat(android): stone painter and bitmap cache`.

### Task 3.6: `LitStone`, `StoneSeal`, `StoneRipple`

**Files:** `LitStone.kt`, `StoneSeal.kt`, `StoneRipple.kt`; `screenshotTest/…/LitStoneScreenshots.kt`.

- [ ] **Step 1: `LitStone`**

```kotlin
/** The pressable stone on the margin line (spec §The lit stone). Decorative: the caller's hit box carries the label. */
@Composable
fun LitStone(
    valence: Valence,
    palette: EmotionPalette?,
    glyph: PebbleGlyph?,
    seed: Long,
    press: StonePressState,        // see below
    modifier: Modifier = Modifier,
)
```

Layout: a `Box` of width `STONE_WIDTH_DP[size].dp` and height by the outline aspect. Children: (a) the rings `Canvas` (`graphicsLayer { scaleX = scaleY = press.rings.value }`): halo path filled `colors.halo`, rings stroked 1 dp `colors.ring` (or `ink2` when `press.touchedStill`); (b) the stone `Image(bitmap)` from `StoneBitmapCache` inside `graphicsLayer { scale = press.dip.value }`, the bitmap requested at `size.toIntPx()` once the layout size is known (`onSizeChanged` → `remember(size, spec)`); (c) the glint `Canvas` clipped to the body: sheen ellipse (`press.sheen.value` alpha), sweep rect translated by `press.sweep.value × travel`, spark (`press.spark` scale/rotation/alpha), overall alpha `glintStrength`.

`StonePressState`: `@Stable class StonePressState { val dip = Animatable(1f); val rings = Animatable(1f); val sheen = Animatable(0f); val sweep = Animatable(0f); val sparkScale = Animatable(0f); val sparkRot = Animatable(0f); val sparkAlpha = Animatable(0f); var touchedStill by mutableStateOf(false); var rippleToken by mutableStateOf<Long?>(null) }` with `suspend fun press(reduceMotion: Boolean)` launching the keyframe animations from `StoneMotion` concurrently (`coroutineScope { launch { dip.animateTo(1f, keyframes { durationMillis = 520; 0.9f at 135 using ...; 1.03f at 312 ... }) } ... }`) and setting `rippleToken = System.nanoTime()`; under reduced motion: `touchedStill = true; sheen.snapTo(.9f); delay(900); sheen.snapTo(0f); touchedStill = false`. `rememberStonePressState()`.

- [ ] **Step 2: `StoneRipple`** — `@Composable fun StoneRipple(valence: Valence, token: Long, onDone: () -> Unit, modifier: Modifier)`: three `Animatable` pairs (scale, alpha) animated with the wave keyframes from `StoneMotion` in a `LaunchedEffect(token)`; a `Canvas` drawing the flat outline path three times, stroked 1 dp `colors.ripple`, scaled about the centre; `onDone()` after the last wave. The caller mounts it only while `token != null` and clears on done.

- [ ] **Step 3: `StoneSeal`** — `@Composable fun StoneSeal(valence: Valence, palette: EmotionPalette?, glyph: PebbleGlyph?, knockout: Color, modifier: Modifier, variant: SealVariant = SealVariant.MINI)`: a `Canvas` port of `StoneSeal.tsx`: spot = `primary` (Paper) / `secondary` (Night); deep = `shaded` (Paper) / `mixOklab(secondary, ground, .8)` (Night); highlight: body filled + stroked 1 px spot, glyph in `knockout` at weight 1 px; neutral: body filled `mixOklab(spot, knockout, .15)`, glyph in spot, rim 1.1 px; lowlight: body filled `mixOklab(deep, knockout, .09)`, 45° hatch 30 canvas units apart stroked .8 px @ .85 clipped to the body, glyph halo in `knockout` 2.6 px then glyph in deep 1 px, rim 1.1 px. Weights ×1.4 / 3.6 / 1.3 for `PRINT`.

- [ ] **Step 4: Gallery preview** — `LitStoneScreenshots.kt`: `@PreviewTest` light and dark (`PreviewVariants` conventions) of a 9 × 2 grid: every `Valence` with the pride palette and the fixture glyph, as `LitStone` and as `StoneSeal`, plus a blank stone, plus one `touchedStill`. Plus a "seeds" row: the same medium gem with four different seeds.

- [ ] **Step 5: `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug`, commit** — `feat(android): lit stone, seal and ripple composables`; push the stack; open the PR; add `rebaseline-screenshots` alone.

---

# Part 4 — The feed

Branch `feat/1122-spine-feed` on Part 3. PR `feat(android): spine feed, cards, entries and the press`, `Part of #1122`, `no-lab-note`, re-baseline label.

Package `app.pbbls.android.features.path.spine`. Read `apps/web/components/path/spine/{SpineFeed,DaySeam,SpinePebble,SpineEntry,SpineCard,PeopleLine}.tsx` and the rule set before each composable; the numbers below are the rule set's phone column.

### Task 4.1: `SnapAspectCache`

**Files:** `…/android/core/data/SnapAspectCache.kt`; test `SnapAspectCacheTest.kt`.

- [ ] **Step 1: Test** — with a fake `SnapURLCache` (extract `SnapUrlSigning` interface only if needed: `apps/android/CLAUDE.md` says extract a seam when a test needs it) and a fake decoder `(url) -> Pair<Int, Int>?`: a ratio is decoded once and cached by prefix; a `null` decode is cached as a failure for the session; two concurrent requests share one decode.

- [ ] **Step 2: Implement**

```kotlin
@Singleton
class SnapAspectCache @Inject constructor(
    private val urls: SnapURLCache,
    @IoDispatcher private val io: CoroutineDispatcher,
    private val decode: suspend (String) -> Pair<Int, Int>? = ::decodeBounds,   // overridable for tests via a secondary constructor
) {
    private val ratios = ConcurrentHashMap<String, Float>()
    private val failed = ConcurrentHashMap.newKeySet<String>()
    private val inflight = ConcurrentHashMap<String, Deferred<Float?>>()
    fun cached(prefix: String): Float? = ratios[prefix]
    suspend fun ratio(prefix: String): Float? { /* cached → failed → inflight → start: withTimeoutOrNull(4_000) { decode(urls.signedUrls(prefix).thumb) } */ }
}
private suspend fun decodeBounds(url: String): Pair<Int, Int>? = /* OkHttp/Coil fetch of the bytes to a temp file, BitmapFactory.decodeFile(path, Options().apply { inJustDecodeBounds = true }) → (outWidth, outHeight) */
```

Use Coil's `ImageLoader.execute(ImageRequest.Builder(context).data(url).size(Size.ORIGINAL).allowHardware(false).build())` and read `image.width / height` if simpler than a raw fetch; both are acceptable, pick one and keep it in this file.

- [ ] **Step 3: Tests green, commit** — `feat(android): snap aspect cache`.

### Task 4.2: `SpineGround` and `DaySeam`

**Files:** `SpineGround.kt`, `DaySeam.kt`.

- [ ] **Step 1: `SpineGround`** — `@Composable fun SpineGround(modifier: Modifier)`: a `Canvas` filling its box. Build once per `(density, colors)` a 36 × 240 dp tile `ImageBitmap` drawing the two path sets from the rule set (the mock's six grooves: transcribe the twelve `M…L…` polylines from `apps/web/app/globals.css`'s `--spine-rake` data URI; shadow set stroked .75 px in `onSurface @ .20` Paper / black @ .34 Night; lit set stroked .6 px in white @ .55 / `#DCE2EA` @ .075 — the only hex this part writes, in `SpineColors` as `rakeLight`, not here); `drawRect(ShaderBrush(ImageShader(tile, REPEAT, REPEAT)))`, then `drawRect(Brush.horizontalGradient(0f to Black, (colX − 6).dp to Black, (colX + 110).dp to Black.copy(alpha = .22f), end to .22f), blendMode = BlendMode.DstIn)` inside a `drawWithContent` layer (`graphicsLayer { compositingStrategy = Offscreen }`).

- [ ] **Step 2: `DaySeam`** — `@Composable fun DaySeam(day: SpineDay, modifier: Modifier)`: a `Box` 8 dp tall drawing the hairline (`seam`, 1 dp), the teeth (`repeating` via a 7-dp-wide one-pixel bitmap shader, 3.5 dp tall at y 1, `teeth`), the second period (19 dp, 6.5 dp tall); the label `Box(width 46.dp)` at `x = spineX − 23`, `y = −14`, background `ground`, bottom corners `shapes.medium`, containing the numeral (`PebblesTheme.spine.dayNumeral`, `lnum`) and the weekday (`dayWeekday`, short weekday lowercase, period stripped, `ink2`). When `day.still`: the row shows *still* (`SpineTypography.still`, `ink3`) in the column.

- [ ] **Step 3: Commit** — `feat(android): spine ground and day seam`.

### Task 4.3: `PeopleLine`, `SpineEntry`, `SpineCard`

Call-site rule for every Part 4/5 composable: pass `moment.valence.intensity` and `moment.valence.polarity` (normalised by `Valence.fromOrDefault`), never `pebble.intensity`: `EntryTypes`, `CardSizes` and `StoneGeometry` `require` intensity in 1..3, and a stray server value must not crash the Path.

**Files:** `PeopleLine.kt`, `SpineEntry.kt`, `SpineCard.kt`; test `PeopleLineTextTest.kt` (the `AnnotatedString` shape).

- [ ] **Step 1: `PeopleLine`** — `@Composable fun PeopleLine(line: PeopleLine, soulsById: Map<String, SoulWithGlyph>, namesById: Map<String, String>, style: TextStyle, modifier)`: builds an `AnnotatedString` "*with* [g] MARA, [g] ELIO *& twelve others* [g][g][g][g][g] +7" with `appendInlineContent` ids per glyph (`InlineTextContent(Placeholder(1.25.em, 1.25.em, TextCenter)) { GlyphView(case = READ, strokes = …, side = …) }` — check `GlyphViewCase` names), names in `soulName` style; `others` in italic; `+k` in `cardMore`. No line when `named` is empty.

- [ ] **Step 2: `SpineEntry`** — `@Composable fun SpineEntry(moment: PathMoment, type: EntryType, emotionName: String, emotionInk: Color, people: @Composable () -> Unit, onOpen: () -> Unit, onLongPress: () -> Unit, modifier)`: `Column` with the name (`spine.entryName(...)`, `ink`; untitled in `ink3` italic), meta row 8 dp below (`entryTime` in `ink`, a `·` in `ink3`, emotion in `entryEmotion` tinted `emotionInk`), people 6 dp below; `combinedClickable` with merged semantics and the stone's description (Task 4.4 passes it).

- [ ] **Step 3: `SpineCard`** — `@Composable fun SpineCard(moment: PathMoment, intensity: Int, polarity: ValencePolarity, palette: EmotionPalette?, ratio: Float?, photoUrl: String?, thumbUrl: String?, day: Int, weekday: String, time: String, emotionName: String, people: @Composable () -> Unit, onOpen, onLongPress, modifier)`: `BoxWithConstraints` gives the column width; `CardSizes.size(intensity, ratio ?: null, columnDp)` gives the width; `u = min(width / 70, 4.4.dp)`; the field's aspect animates with `animateFloatAsState(ratio ?: 1f, tween(240))` (snap under `rememberReduceMotion()`). Structure per `SpineCard.tsx`: a `Surface`-less `Box` with `background(stock, shapes.medium)`, `drawBehind` the painted edge (`edge` colour from `SpineColors`/palette by polarity, thickness `CardSizes.edge(intensity)`) and two blurred shadow rects (`drawIntoCanvas` with `BlurMaskFilter`, the web's `--spine-card-shadow` offsets/alphas), the printed border (`border(1.dp, cardRule)` inset `1.6u`, radius `1.3u` as a computed `RoundedCornerShape(size)`), the index column, the rail (`Modifier.rotate(90f)` text or `VerticalText`), the field (`background(tint)` with the valence pattern brush: `Brush.sweepGradient` with many stops for the rays, `Brush.radialGradient` repeating for ripples via a tile shader, same for contours), `AsyncImage(model = ImageRequest { data(photoUrl); placeholderMemoryCacheKey(thumbUrl) }, contentScale = Fit)`, title (`cardTitle(u, weight, italic)`), souls strip (`people`). Error state: `onError` → "photo unavailable" in `colors.error` over the field.

- [ ] **Step 4: Tests (people line) green, commit** — `feat(android): spine entry, card and people line`.

### Task 4.4: `SpinePebble` and the press choreography

**Files:** `SpinePebble.kt`, `SpineFixtureWeek.kt` (screenshotTest), `SpineFeedScreenshots.kt`.

- [ ] **Step 1: `SpinePebble`**

```kotlin
@Composable
fun SpinePebble(
    moment: PathMoment,
    palette: EmotionPalette?,
    soulsById: Map<String, SoulWithGlyph>,
    personsOrder: List<String>,
    aspect: SnapAspectCache?,              // null in previews
    urls: SnapURLCache?,                   // LocalSnapURLCache.current
    pressRequests: Flow<String>,           // moment ids the contents ask to press (Part 5)
    onOpen: (PathMoment) -> Unit,
    onRequestDelete: (PathMoment) -> Unit,
    modifier: Modifier = Modifier,
)
```

Layout: a `Box` padded `start = colX, end = padR`; the stone absolutely placed at `x = spineX − w/2`, `y = anchor − h/2` (anchor 31 dp for a card, `type.anchorDp` for an entry); `LitStone` inside a `Box` with `Role.Button`, `contentDescription` (built from `path_spine_stone_a11y` / `blank_a11y` with name or untitled, the date and time through the locale's formatters, the stone kind string, the emotion name, `with_names`), `minimumInteractiveComponentSize()`, `clickable { scope.launch { haptics(TapHaptic.SELECTION); press.press(reduce); delay(OPEN_DELAY_MS or 0); onOpen(moment) } }`; `StoneRipple` mounted while `press.rippleToken != null`; a 1 dp leader `drawBehind` from `spineX` to `colX` at the anchor when the pebble has a card; then `SpineCard` or `SpineEntry` (`moment.firstSnapPath != null` → card), whose `onOpen` calls `onOpen(moment)` at once and launches `press.press()`; `onLongPress` → `onRequestDelete` (keeps today's menu: port the `DropdownMenu` from `PathPebbleRow.kt` into a small `PebbleDeleteMenu` here). A blank moment (`pebble == null`) draws `LitStone(palette = null)`, uses `blank_a11y`, and shows the name or "untitled" as an entry. `pressRequests.collect { if (it == moment.id) press.press(reduce) }`. Test tag `JourneyTags.PATH_PEBBLE_ROW` on the pebble `Box`.

- [ ] **Step 2: Fixture** — `SpineFixtureWeek.kt`: the web's fixture week (`apps/web/lib/seed/spine-fixture-week.ts`) transcribed with `MomentPreviewFixtures`-style builders: Mon 5 – Sun 11 Oct 2026, eleven pebbles, five souls with glyphs, three photos (no network: `urls = null`, ratios primed through a `SnapAspectCache` test double or a `ratioOverride` parameter on the card for previews), the 14-soul picnic, Friday still, Bruno's glyph-less pebble, the untitled photo, two blank moments. Expose `SpineFixtureWeek.entry: WeekRollEntry`, `.soulsById`, `.palettes`.

- [ ] **Step 3: Preview** — `SpineFeedScreenshots.kt`: `@PreviewTest` Paper/Night of a `Column` with `SpineGround` behind three days from the fixture (a card day, an entry day, a still day), each `DaySeam` + `SpinePebble`s; and one `SpinePebble` with the 14-soul line.

- [ ] **Step 4: Gates, commit** — `feat(android): spine pebble with the press, ripple and delete menu`; push; PR; re-baseline label.

---

### Task 4.5: The Spine lab (debug-only, on device before Part 6)

Added during execution at the maintainer's request ("build it on the Pixel"): Parts 2–5 are preview-only, so nothing of the Spine reaches a device until Part 6. A debug-only activity makes the stone gallery and the fixture week visible on the phone now.

**Files:**
- Create: `apps/android/app/src/debug/AndroidManifest.xml` (a second launcher activity, label "Spine Lab", `exported="true"`, debug builds only)
- Create: `apps/android/app/src/debug/kotlin/app/pbbls/android/SpineLabActivity.kt` (`ComponentActivity`, no Hilt; `PebblesTheme()` brand scheme following system dark mode; two tabs: Stones (the gallery grid with pressable `LitStone`s and their ripples, the seals, the blank and touched stones, four seeds) and Week (the fixture week's days as `DaySeam` + `SpinePebble` over `SpineGround`, `aspect = null`, no photo URLs))
- Move: `SpineFixtureWeek.kt` from `src/screenshotTest/` to `src/debug/kotlin/app/pbbls/android/` (same package; the screenshot source set of the debug variant compiles against debug sources; verify with `compileDebugScreenshotTestKotlin`; if it cannot see it, keep the file in `debug` and say so)
- No change to `main/`: the lab must not leak into release (`src/debug` only), and `ArchitectureBoundaryTest` is unaffected (debug sources are outside its scan; check).

- [ ] **Step 1:** Create the manifest and the activity; move the fixture; `./gradlew assembleDebug compileDebugScreenshotTestKotlin`.
- [ ] **Step 2:** `adb -s 39141FDJH006H4 install -r app/build/outputs/apk/debug/app-debug.apk && adb shell am start -n app.pbbls.android/.SpineLabActivity`, screenshot both tabs in Paper and Night.
- [ ] **Step 3:** Commit `chore(android): debug-only spine lab activity` and record the device observations in the Part 4 PR body.

# Part 5 — The opening and the week

Branch `feat/1122-spine-opening` on Part 4. PR `feat(android): spine week, opening, persons and contents`, `Part of #1122`, `no-lab-note`, re-baseline label.

### Task 5.1: `WeekMasthead`, `WeekOpening`, `WeekMouth`, `SpineEmptyWeek`

- [ ] **Step 1: Implement** per the spec §The opening, reading `WeekOpening.tsx` and `WeekMouth.tsx`: `WeekMasthead(words, onPreviousWeek: (() -> Unit)?)`, `WeekOpening(words, pebbleCount, peopleCount)` (kicker: `path_spine_kicker(words.ordinal, words.year)`; title: `title_same_month` / `title_cross_month`; summary: pebbles plural with `summary(...)` words, `sentenceCase`), `WeekMouth(words, current: Boolean, onNextWeek: (() -> Unit)?)` (cairn `Canvas` of three flat outlines 14/19/25 dp stroked 1.2 dp `ink2` filled `ground`), `SpineEmptyWeek(onCreate)` (today's `EmptyWeek` body, `spine.title` style for the title).

- [ ] **Step 2: Commit** — `feat(android): week masthead, opening, mouth and empty week`.

### Task 5.2: `PersonsOfWeek`, `WeekContents`, `ReadingDay`

- [ ] **Step 1: `ReadingDay`** — `@Composable fun rememberReadingDay(listState: LazyListState, dayItemKeys: List<Any>): Int?`: `derivedStateOf` over `listState.layoutInfo.visibleItemsInfo`: the last day-header item whose `offset <= viewportSize.height × .35`; `null` before the first. Test (Robolectric or pure: factor the choice into `fun readingDay(headers: List<Pair<Int, Int>>, lineY: Int): Int?` and test that).

- [ ] **Step 2: `PersonsOfWeek`** — `@Composable fun PersonsOfWeek(persons: List<PersonOfWeek>, weekStart: LocalDate, soulsById: Map<String, SoulWithGlyph>, readingDay: Int?, modifier)`: label, header letters (`weekdayLetters`), rows (`GlyphView` 24 dp from `soulsById[soul.id]?.glyph`, name in `personName`, seven dots `Canvas` 7 dp, 5 dp gaps, `contentDescription = dotDays`), the fold ("and N others" row toggling a `remember` boolean; `NumberWords.cardinal` for N).

- [ ] **Step 3: `WeekContents`** — `@Composable fun WeekContents(week: SpineWeek, palettes: (PathMoment) -> EmotionPalette?, readingDay: Int?, onGo: (PathMoment) -> Unit, modifier)`: rows per `readingOrder`, grid 40 / 36 / fill / auto dp, the day cell only on a day's first row (rule above), `StoneSeal` 20/24/27 dp, name (`contentsName`), time (`contentsTime`), the 5 dp ink dot on the reading day's rows; each row `clickable(role = Button)`.

- [ ] **Step 4: Commit** — `feat(android): persons of the week, contents and the reading day`.

### Task 5.3: `SpineWeek`

- [ ] **Step 1: Implement**

```kotlin
@Composable
fun SpineWeek(
    entry: WeekRollEntry,
    today: LocalDate,
    soulsById: Map<String, SoulWithGlyph>,
    paletteFor: (PathMoment) -> EmotionPalette?,
    aspect: SnapAspectCache?,
    onOpen: (PathMoment) -> Unit,
    onRequestDelete: (PathMoment) -> Unit,
    onPreviousWeek: (() -> Unit)?,
    onNextWeek: (() -> Unit)?,
    onCreatePebble: () -> Unit,
    modifier: Modifier = Modifier,
)
```

`remember(entry, today)` the `SpineWeek` from `SpineWeekBuilder`, the persons, the order, the words. A `LazyColumn` (`state = rememberLazyListState()`, `contentPadding(bottom = 96.dp)`): `item { WeekMasthead }`, `item { WeekOpening }`, `item { PersonsOfWeek }`, `item { WeekContents }`, then if the week is empty `item { SpineEmptyWeek }` else for each day: `item(key = "day-$index") { DaySeam }` and `items(day.moments, key = { it.id }) { SpinePebble }`, with `SpineGround` drawn behind the day range (a `Box` wrapping the `LazyColumn` with the ground sized to it is wrong for a lazy list: instead draw the rake per item with `Modifier.drawBehind` using the shared tile shader, offset by the item's index × height so the tile phase continues; simplest correct form: give `DaySeam` and `SpinePebble` `Modifier.spineGround(tile)` that draws the tile at a vertical phase from `Modifier.onGloballyPositioned`'s `positionInParent().y`), then `item { WeekMouth }`. The contents' `onGo(moment)`: `scope.launch { listState.animateScrollToItem(indexOf(moment.id), scrollOffset = −viewport/2 + stoneHeight/2); delay(GO_PRESS_DELAY_MS); pressRequests.emit(moment.id) }` (a `MutableSharedFlow<String>` passed to every `SpinePebble`). `readingDay` from Task 5.2 feeds the persons and contents. The title text carries `semantics { liveRegion = Polite }`.

- [ ] **Step 2: Preview** — `SpineWeekScreenshots.kt`: the fixture week in Paper and Night, phone width; `PreviewVariants` large font and French; plus the empty current week.

- [ ] **Step 3: Gates, commit** — `feat(android): the spine week`; push; PR; re-baseline label.

---

# Part 6 — The switch

Branch `feat/1122-path-becomes-spine` on Part 5. PR `feat(android): the path becomes the spine`, `Resolves #1122`, `feat`, `ui`, `android`, re-baseline label; Lab Note per the maintainer's standing rule (Arkaik coverage → `no-lab-note`; confirm at PR time).

### Task 6.1: `PathContent` renders the week; the roll restyle

- [ ] **Step 1:** In `PathScreen.kt` / `PathContent`: remove `WeekHeader`; the pager page becomes `SpineWeek(entry, today, soulsById, paletteFor, aspect, onOpen = onPebbleTap, onRequestDelete = onPebbleDelete, onPreviousWeek = previous?.let { { onFocusChange(it.weekStart) } }, onNextWeek = …, onCreatePebble)`. `PathScreen` passes `content.soulsById` and the injected `SnapAspectCache` (add it to `PathViewModel`'s constructor and expose it, or inject into the screen through the ViewModel: `viewModel.snapAspect`). Keep the pager steering, the FAB, the delete dialogs.
- [ ] **Step 2:** `WeekRoll.kt`: cells 56 × 44 dp, `Text(weekNumber, style = spine.rollNumeral, color = if focused ink else ink2)` with a 1 dp underline (`drawBehind`) when focused; delete the `Icon(cairn_static)`; delete `res/drawable/cairn_static.xml` after `grep -rn cairn_static apps/android` shows only this file.
- [ ] **Step 3: Deletions** — `PathPebbleRow.kt`, `WeekPebbleList.kt`, `WeekHeader.kt`, `PathSnapThumb.kt`, `WeekHeaderFormatting.kt`, `PathRowFormatting.kt`, `WeekHeaderFormattingTest.kt`, `PathRowFormattingTest.kt`, `PathRowScreenshots.kt`, and the `path_week_previous` / `path_week_next` strings if no longer referenced (they are: the masthead and mouth use them as a11y labels; keep). Remove the `tools:ignore="UnusedResources"` attributes added in Part 2.
- [ ] **Step 4:** Update `PathScreenScreenshots.kt` to the fixture week; update `MomentPreviewFixtures.pathPebble` to accept `souls`.
- [ ] **Step 5:** `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug` green; the Robolectric Path journey passes (`JourneyTags.PATH_PEBBLE_ROW` now on `SpinePebble`). Commit — `feat(android): the path renders the spine week, roll restyled, rows deleted`.

### Task 6.2: Docs, Arkaik, decision log

- [ ] **Step 1:** `apps/android/CLAUDE.md` Path rendering section: replace the "week-roll cairn is a static drawable" bullet with one on the Spine (`features/path/spine/`, the stone in `core/ui/render/stone/`, tokens in `SpineTypography`/`SpineColors`, the web rule set as the source of numbers, `StoneBitmapCache`), and update the "stone is composed on device" bullet to say the lit stone paints the seeds' veins and fossil too.
- [ ] **Step 2:** Append the decision-log entry per the spec §Decision-log entry (Status taken; Scope android, ui; Context, Decision D1–D6, Why, Consequences incl. #979 open and the rows deleted, Extends 2026-10-13 #1052 and 2026-10-18 #1118, Refs).
- [ ] **Step 3:** Arkaik over MCP (`update_node`): `V-timeline` platformStatuses android → `development`; the eight acceptances listed in the spec → android `development`, sending gherkin + values + platformStatuses together. If the tools are unavailable, say so and stop that step.
- [ ] **Step 4:** Commit — `docs(android): the spine path in CLAUDE.md and the decision log`; push; PR; re-baseline label; approve held runs after the bot commits; device check on the Pixel 8 (wallpaper off and on, Paper and Night, three photo ratios, the 14-soul pebble, TalkBack over a stone, a card and the contents).

---

## Self-review

- **Spec coverage.** D1 (Part 3), D2 (Parts 1, 2.1, 2.6), D3 (4.1, 4.3), D4 (5.3 layout, no spread), D5 (6.1), D6 (4.2 no grain), D7 (2.4), D8 (2.2, 2.3, 3.1–3.4 tests). Component map: every composable has a task. Data table: every row. Accessibility: 4.4, 5.2, 5.3. Testing table: every row has a test or preview. Delivery: six parts, labels, re-baseline, Arkaik, decision log.
- **Placeholders.** The two `<paste …>` numbers in Task 2.2 and the ring matrix numbers in Task 3.1 are produced by a named command inside the task before the test is run; they are instructions, not deferred work. Port steps name the exact web file and the exact API to produce.
- **Consistency.** `SpineColors` field names (`ground, stock, halo, ink, ink2, ink3, rule, cardRule, line, seam, teeth, ring, ripple, shadow, shadowAlpha, tintK, patK`) are the same in 2.4, 3.5, 4.2, 4.3; `StonePressState.press(reduceMotion)` in 3.6 and 4.4; `SpineWeekBuilder.build` in 2.3 and 5.3; `PersonsOfWeek.of/split` in 2.3 and 5.2; `CardSizes.size/edge` in 2.3 and 4.3; `UuidHash.fnv1a` in 2.2, 3.3 and 4.4 (the pebble seed is `UuidHash.fnv1a(pebble.id)`; a blank moment seeds on the moment id).

## Lessons learned (execution, Part 2, 2026-10-18)

- **`YsabeauFamily` snaps weights.** It declares four fixed-weight `Font`s; Compose picks the nearest declared font and uses its pinned `wght`, so the Spine's 300/380/460/620/650 collapsed to 400/500/700 until `ysabeauAt(weight)` built one family per exact weight. Caught by the Part 2 quality review, before any screenshot reference was baked.
- **French plurals need `many`.** Android Lint `MissingQuantity` fails a French `<plurals>` without `quantity="many"`; the existing `pebbles_count` in `values-fr` predates the check and sits in the baseline (and is untranslated: a follow-up).
- **Deviations from the spec, all kept:** `prev_week`/`next_week` merged into `path_spine_week_link` and `card_a11y` folded into `stone_a11y` (identical texts on web); `dotDays` hand-joins lists (Oxford comma for every non-French locale, where `Intl.ListFormat("en-GB")` would not) because `android.icu` is unusable in plain JVM tests; `SpineWeekBuilder.build` takes an explicit `zone`, no ambient default; the "skips souls it cannot name" case has no Android counterpart because the row carries its souls.
- **`MomentDetail.toPathMoment()` yields no souls**, so a `PathMoment` from a detail must never feed a people line.
- **Two hand-backs arrived as the word "placeholder"**; the work was done both times. Check `git log` before asking for a resend.
