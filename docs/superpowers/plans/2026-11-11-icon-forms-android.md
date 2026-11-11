# Icon forms on Android (#1607, part 6) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The Android app draws every glyph from generated forms data (regular, solid, parts, progress, dotted, with or without the mark) through `PbblsGlyphImage`, picks state forms through the shared state tokens, and no longer names a twin — mirroring iOS part 5 (#1616) 1:1.

**Architecture:** The icons generator emits `res/raw/pbbls_glyphs.json` (byte-identical to the iOS resource), a `PbblsGlyph` enum of names and a `PbblsGlyphState` enum. Hand-written Kotlin in `core/designsystem/glyph/` parses the normalised path data, ports `renderForm` and `resolveState` (as the Swift port did), and builds one Compose `ImageVector` per glyph and form, cached. `PbblsGlyphImage` draws it through M3 `Icon` (so it tints like today's `PbblsIconImage`), and `rememberPbblsGlyphPainter` hands a `Painter` to the few sites that need one. The same two fixtures that hold iOS equal to the generator hold Android equal: `packages/icons/fixtures/path-contract.json` and `packages/icons/glyphs/forms-contract.json`.

**Tech Stack:** TypeScript generator (Vitest), Kotlin + Jetpack Compose (minSdk 33), kotlinx.serialization 1.12, JUnit 4 (+ Robolectric where a `Context` is needed), Compose Preview Screenshot Testing.

**Spec:** `docs/superpowers/specs/2026-11-08-icon-forms-design.md` (D3, D6, D7, D9, §2, §4). **Mirror:** `docs/superpowers/plans/2026-11-10-icon-forms-ios.md` and the Swift that shipped in #1616 (`apps/ios/Pebbles/Theme/Glyphs/*.swift`): when this plan and the Swift disagree on behaviour, the Swift (and the contracts) win.

**Branch:** `chore/1607-icon-forms-android` from updated `main` **after #1616 merges** (it carries the two fixtures, the simplified carving and the iOS emitter this part shares a helper with). The `-android` slug keeps Vercel from deploying it. Never touch the untracked `apps/web/AGENTS.md`. Commits are conventional and end with a blank line and `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Never hand-edit a generated file: change the emitter, then `npm run generate --workspace=@pbbls/icons`.

**Checks:**
- Package: `npm run build --workspace=@pbbls/icons && npm run test --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons`.
- Android (from `apps/android`, after `export ANDROID_HOME=$HOME/Library/Android/sdk`): `./gradlew ktlintCheck`, `./gradlew :app:testDebugUnitTest --tests '<pattern>'`, and before the PR `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug`. Without `ANDROID_HOME` the npm wrapper silently no-ops: always call Gradle directly.
- Screenshots: CI is the gate. `./gradlew updateDebugScreenshotTest` locally is only for looking; never commit a locally rendered reference (`.claude/skills/android-screenshot-gate`).

## Decisions taken in this plan

- **Drawn as `ImageVector`, not bitmaps.** A vector per glyph and form (no size in the cache key: it scales) painted by `Icon`, which tints it with `LocalContentColor` like today's VectorDrawables. Every slot that takes a composable (`NavigationSuiteItem`'s `icon`, buttons, rows) takes `PbblsGlyphImage`; the three sites that take a `Painter` take `rememberPbblsGlyphPainter`.
- **The composable is `PbblsGlyphImage`, not `PbblsGlyph`.** The spec's table names the composable like the enum; a function and an enum with one name read badly at call sites. `PbblsGlyphImage(glyph, contentDescription, modifier, form, size, tint)` mirrors today's `PbblsIconImage` (and iOS's `PbblsGlyphView` naming of "the drawing of a glyph"), with a `PbblsIconSize` overload.
- **One catalog, installed once.** `GlyphCatalog` is a process-wide object: `PebblesApp.onCreate` installs the raw resource and loads it on a background thread; `PbblsGlyphImage` installs it from `LocalContext` if nothing did (previews, Robolectric); plain JUnit installs the repo's file. `PbblsGlyph.forms` and `PbblsGlyph.form(state, k)` are then plain functions, as on iOS.
- **Animation (D9):** `AnimatedContent` on the form, a 220 ms fade with a 0.9 → 1 scale (the pop duration of `RevealMotion.kt`), none under `rememberReduceMotion()` (which is also true in previews, so references capture end states).
- **The People tab selects its solid with the mark**, as iOS does after the maintainer's device pass (#1616): `PebblesKey.selectedForm()` mirrors `AppTab.selectedForm`.
- **The souls search lens becomes `magnifying-md`**, as on iOS. Android's settings rows carry no leading icons (M3 grouped list, platform-native chrome), so the iOS settings-link icons have no Android mirror.
- **No debug icon lab.** Android never had one; the per-form screenshot pages are the review surface.
- **The old outputs stay** (`PbblsIcons.kt`, `PbblsIconImage`, the `ic_pbbls_*` drawables, `PbblsIconsTest`, the icon-sheet screenshots): part 7 removes them. After this part no code outside `core/designsystem/PbblsIcons.kt`, `PbblsIconSize.kt` and those tests names `PbblsIcon` or `PbblsIconImage`.

## File map

| File | Status | Responsibility |
|---|---|---|
| `packages/icons/scripts/lib/emit-glyphs.ts` | modify | export `nativeGlyphData` (moved from the iOS emitter) |
| `packages/icons/scripts/lib/emit-ios-glyphs.ts` | modify | import it instead of its own copy |
| `packages/icons/scripts/lib/emit-android-glyphs.ts` | create | the Android glyphs emitter |
| `packages/icons/scripts/build.ts` | modify | register it |
| `apps/android/app/src/main/res/raw/pbbls_glyphs.json` | generated | the native data |
| `apps/android/.../core/designsystem/PbblsGlyph.kt` | generated | `enum class PbblsGlyph`, names only |
| `apps/android/.../core/designsystem/PbblsGlyphState.kt` | generated | `enum class PbblsGlyphState` from `states.json` |
| `apps/android/.../core/designsystem/glyph/GlyphForm.kt` | create | `GlyphFill`, `GlyphForm`, keys both ways |
| `apps/android/.../core/designsystem/glyph/GlyphData.kt` | create | the serialisable data model and `formKeys` |
| `apps/android/.../core/designsystem/glyph/GlyphPathParser.kt` | create | `segments`, `pathNodes` |
| `apps/android/.../core/designsystem/glyph/GlyphRenderer.kt` | create | `render`, `resolve`, `vector` |
| `apps/android/.../core/designsystem/glyph/GlyphCatalog.kt` | create | install, load, prewarm, vector cache, `PbblsGlyph.forms` / `.form(...)` |
| `apps/android/.../core/designsystem/PbblsGlyphImage.kt` | create | the composable, its size overload, the painter |
| `apps/android/.../PebblesApp.kt` | modify | prewarm in `onCreate` |
| `apps/android/app/src/test/.../core/designsystem/glyph/*.kt` | create | `GlyphTestData`, form, parser, contract, vector tests |
| `apps/android/app/src/screenshotTest/.../GlyphFormSheetScreenshots.kt` | create | per-form pages of the set |
| call sites under `apps/android/app/src/{main,test,screenshotTest}` | modify | `PbblsIconImage` → `PbblsGlyphImage`, `PbblsIcon` → `PbblsGlyph`, twins → forms |
| `packages/icons/CLAUDE.md` | modify | the new generated paths |

`...` is `app/src/main/kotlin/app/pbbls/android` (or `app/src/test/kotlin/app/pbbls/android` for tests).

---

### Task 1: The Android glyphs emitter

**Files:**
- Modify: `packages/icons/scripts/lib/emit-glyphs.ts`, `packages/icons/scripts/lib/emit-ios-glyphs.ts`, `packages/icons/scripts/build.ts`
- Create: `packages/icons/scripts/lib/emit-android-glyphs.ts`
- Test: `packages/icons/scripts/lib/emit-android-glyphs.test.ts`
- Generated: `apps/android/app/src/main/res/raw/pbbls_glyphs.json`, `apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem/PbblsGlyph.kt`, `.../PbblsGlyphState.kt`

- [ ] **Step 1: Move the shared data writer.** Cut `nativeData` out of `emit-ios-glyphs.ts` into `emit-glyphs.ts` as an export, unchanged in body:

```ts
/** The forms data the native apps bundle (spec §2): keyed by name, without meaning, tags or family. */
export function nativeGlyphData(glyphs: readonly GlyphData[], states: StateTable): string {
  const byName: Record<string, Omit<GlyphData, "name" | "meaning" | "family" | "tags">> = {}
  for (const { name, meaning: _m, family: _f, tags: _t, ...forms } of glyphs) byName[name] = forms
  return JSON.stringify({ notice: GENERATED_NOTICE, version: 1, states, glyphs: byName }) + "\n"
}
```

(import `GENERATED_NOTICE` and `StateTable` there if not already), and in `emit-ios-glyphs.ts` import `nativeGlyphData` and use it for `IOS_GLYPH_DATA`. Run `npm run test --workspace=@pbbls/icons && npm run check --workspace=@pbbls/icons` — PASS, nothing stale (the move changes no output). Commit `chore(ui): share the native glyph data writer`.

- [ ] **Step 2: Failing test** — `emit-android-glyphs.test.ts`:

```ts
import { describe, expect, it } from "vitest"
import { buildGlyph } from "./emit-glyphs"
import { ANDROID_GLYPH_DATA, ANDROID_GLYPH_KOTLIN, ANDROID_STATE_KOTLIN, androidGlyphFiles } from "./emit-android-glyphs"
import { IOS_GLYPH_DATA, iosGlyphFiles } from "./emit-ios-glyphs"
import type { IconRow } from "./manifest"

const row = (name: string, meaning: string): IconRow => ({ name, meaning, family: null, tags: { category: ["stones"] }, variants: [], wave: "w", status: "candidate", migrated: [], replaces: { ios: [], android: [], web: [] } })
const SVG = (inner: string) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`
const glyphs = [
  buildGlyph(row("pebble-round", "A recorded moment."), SVG('<path d="M4 12 C4 6 20 6 20 12 C20 18 4 18 4 12" data-role="body"/>'), {}),
  buildGlyph(row("case", "A case."), SVG('<path d="M4 4 L20 20"/>'), {}),
]
const states = { rest: ["regular"], selected: ["solid", "regular"] }

describe("androidGlyphFiles", () => {
  const files = androidGlyphFiles(glyphs, states)

  it("writes the same data as iOS", () => {
    expect(files.get(ANDROID_GLYPH_DATA)).toBe(iosGlyphFiles(glyphs, states).get(IOS_GLYPH_DATA))
    expect(ANDROID_GLYPH_DATA).toBe("apps/android/app/src/main/res/raw/pbbls_glyphs.json")
  })

  it("writes one enum entry per glyph, its meaning as the KDoc", () => {
    const kotlin = files.get(ANDROID_GLYPH_KOTLIN) ?? ""
    expect(kotlin.startsWith("// Generated by packages/icons")).toBe(true)
    expect(kotlin).toContain("package app.pbbls.android.core.designsystem")
    expect(kotlin).toContain("enum class PbblsGlyph(\n    val manifestName: String,\n) {")
    expect(kotlin).toContain('    /** A recorded moment. */\n    PEBBLE_ROUND("pebble-round"),')
    expect(kotlin).toContain('    CASE("case"),\n    ;')
    expect(kotlin).toContain("fun fromManifestName(name: String): PbblsGlyph? = byManifestName[name]")
  })

  it("writes the states in the table's order", () => {
    const kotlin = files.get(ANDROID_STATE_KOTLIN) ?? ""
    expect(kotlin).toContain('enum class PbblsGlyphState(\n    val key: String,\n) {\n    REST("rest"),\n    SELECTED("selected"),\n}')
  })
})
```

- [ ] **Step 3: Run** `npx vitest run scripts/lib/emit-android-glyphs.test.ts` (from `packages/icons`) — FAIL (no module).

- [ ] **Step 4: Implement** `emit-android-glyphs.ts`:

```ts
/**
 * The Android glyphs emitter (spec D7, §2): the forms data as a raw resource,
 * the enum of names and the enum of state tokens. The old drawables and
 * `PbblsIcon` stay with `androidEmitter` until part 7 removes them. The enum
 * reads no resource ids, so its initializer stays far under the JVM's 64 KB
 * method limit that #1606 hit (950 entries of one string each).
 */
import { GENERATED_NOTICE, type Emitter } from "./emit"
import { kdocLines } from "./emit-android"
import { loadGlyphsOnce, nativeGlyphData } from "./emit-glyphs"
import type { GlyphData } from "./forms"
import { constantName } from "./names"
import { loadStates, type StateTable } from "./states"

const KOTLIN_DIR = "apps/android/app/src/main/kotlin/app/pbbls/android/core/designsystem"
export const ANDROID_GLYPH_DATA = "apps/android/app/src/main/res/raw/pbbls_glyphs.json"
export const ANDROID_GLYPH_KOTLIN = `${KOTLIN_DIR}/PbblsGlyph.kt`
export const ANDROID_STATE_KOTLIN = `${KOTLIN_DIR}/PbblsGlyphState.kt`

function glyphKotlin(glyphs: readonly GlyphData[]): string {
  const entries = glyphs.map((g) => [...kdocLines(g.meaning), `    ${constantName(g.name)}("${g.name}"),`].join("\n"))
  return `// ${GENERATED_NOTICE}
package app.pbbls.android.core.designsystem

/**
 * The hand-drawn Pebbles glyphs (\`packages/icons\`): one entry per drawing,
 * [manifestName] being its manifest name. Its paths and forms are in
 * \`res/raw/pbbls_glyphs.json\`; draw it with [PbblsGlyphImage].
 */
enum class PbblsGlyph(
    val manifestName: String,
) {
${entries.join("\n\n")}
    ;

    companion object {
        private val byManifestName = entries.associateBy { it.manifestName }

        /** The glyph whose [manifestName] is [name], or null. */
        fun fromManifestName(name: String): PbblsGlyph? = byManifestName[name]
    }
}
`
}

function stateKotlin(states: StateTable): string {
  return `// ${GENERATED_NOTICE}
package app.pbbls.android.core.designsystem

/**
 * The interaction states of \`packages/icons/states.json\` (spec D6). Each
 * names the forms it shows, first available wins; \`PbblsGlyph.form(state, k)\`
 * resolves one, the same way on every surface. [key] is the table's name.
 */
enum class PbblsGlyphState(
    val key: String,
) {
${Object.keys(states).map((s) => `    ${constantName(s)}("${s}"),`).join("\n")}
}
`
}

/** The three Android files for these glyphs and states. */
export function androidGlyphFiles(glyphs: readonly GlyphData[], states: StateTable): Map<string, string> {
  return new Map([
    [ANDROID_GLYPH_DATA, nativeGlyphData(glyphs, states)],
    [ANDROID_GLYPH_KOTLIN, glyphKotlin(glyphs)],
    [ANDROID_STATE_KOTLIN, stateKotlin(states)],
  ])
}

export function androidGlyphsEmitter(packageRoot: string): Emitter {
  return {
    name: "android-glyphs",
    owned: { dirs: [], files: [ANDROID_GLYPH_DATA, ANDROID_GLYPH_KOTLIN, ANDROID_STATE_KOTLIN], patterns: [] },
    emit: () => androidGlyphFiles(loadGlyphsOnce(packageRoot), loadStates(packageRoot)),
  }
}
```

The first test's `kdocLines` output for a short meaning is one line (`    /** A recorded moment. */`); if `constantName` lives elsewhere than `./names`, import it from where `emit-android.ts` does. In `build.ts`, register it last:

```ts
const EMITTERS: readonly Emitter[] = [reactEmitter, iosEmitter, androidEmitter, glyphsEmitter(PACKAGE_ROOT), iosGlyphsEmitter(PACKAGE_ROOT), androidGlyphsEmitter(PACKAGE_ROOT)]
```

- [ ] **Step 5: Run** the package checks — PASS. `npm run generate --workspace=@pbbls/icons`: three new files under `apps/android`, nothing else changes (`git status --short` lists only them plus the package). `cmp apps/android/app/src/main/res/raw/pbbls_glyphs.json apps/ios/Pebbles/Resources/pbbls-glyphs.json` — identical. From `apps/android`: `./gradlew ktlintCheck assembleDebug` — PASS (if ktlint flags the generated files, fix the emitter's output shape, never add an exclusion).

- [ ] **Step 6: Commit** `feat(android): emit the glyph forms data and names for Android` with the emitter, its test, `build.ts` and the three generated files.

---

### Task 2: Forms, data and the path parser in Kotlin

**Files:**
- Create: `.../core/designsystem/glyph/GlyphForm.kt`, `GlyphData.kt`, `GlyphPathParser.kt`
- Test: `app/src/test/kotlin/app/pbbls/android/core/designsystem/glyph/GlyphTestData.kt`, `GlyphFormTest.kt`, `GlyphPathContractTest.kt`

Plain JUnit (no Robolectric): Gradle runs unit tests from `apps/android/app`, so repo files are read by relative path, as `PbblsIconsTest` reads `../../../packages/icons/icons.json`.

- [ ] **Step 1: Failing tests.** `GlyphTestData.kt`:

```kotlin
package app.pbbls.android.core.designsystem.glyph

import java.io.File

/** Repository files the glyph tests read in place (Gradle runs unit tests from `apps/android/app`). */
object GlyphTestData {
    fun repoFile(path: String): File = File("../../../$path")

    /** The generated resource, the same bytes the app bundles. */
    val resource: File get() = File("src/main/res/raw/pbbls_glyphs.json")

    /** Points the process-wide catalog at the generated resource (Task 3). */
    fun install() = GlyphCatalog.installSource { resource.inputStream() }
}
```

(`install()` compiles once Task 3 lands; until then leave that function out and add it in Task 3, Step 1.)

`GlyphFormTest.kt`:

```kotlin
package app.pbbls.android.core.designsystem.glyph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphFormTest {
    private val pairs =
        listOf(
            "regular" to GlyphForm(),
            "marked" to GlyphForm(marked = true),
            "solid" to GlyphForm(GlyphFill.Solid),
            "solid-marked" to GlyphForm(GlyphFill.Solid, marked = true),
            "dotted" to GlyphForm(GlyphFill.Dotted),
            "parts-left+middle" to GlyphForm(GlyphFill.Parts(listOf("left", "middle"))),
            "parts-moon-marked" to GlyphForm(GlyphFill.Parts(listOf("moon")), marked = true),
            "progress-2" to GlyphForm(GlyphFill.Progress(2)),
            "progress-12-marked" to GlyphForm(GlyphFill.Progress(12), marked = true),
        )

    @Test
    fun keysRoundTrip() {
        for ((key, form) in pairs) {
            assertEquals(key, form.key)
            assertEquals(key, form, GlyphForm.fromKey(key))
        }
    }

    @Test
    fun refusesWhatIsNotAFormKey() {
        for (key in listOf("", "marked-marked", "regular-marked", "dotted-marked", "progress-0", "progress-02", "parts-", "parts-Moon", "parts-a++b", "half")) {
            assertNull(key, GlyphForm.fromKey(key))
        }
    }

    @Test
    fun partCombinationsFollowTheGenerator() {
        assertTrue(GlyphData.partCombinations(emptyList()).isEmpty())
        assertTrue(GlyphData.partCombinations(listOf("a")).isEmpty())
        assertEquals(
            listOf(listOf("l"), listOf("m"), listOf("r"), listOf("l", "m"), listOf("l", "r"), listOf("m", "r")),
            GlyphData.partCombinations(listOf("l", "m", "r")),
        )
    }
}
```

`GlyphPathContractTest.kt`:

```kotlin
package app.pbbls.android.core.designsystem.glyph

import androidx.compose.ui.graphics.vector.PathNode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Android side of the path-parser contract (spec §4): `packages/icons/fixtures/path-contract.json`. */
class GlyphPathContractTest {
    @Serializable
    data class Seg(
        val op: String,
        val pts: List<Double>,
    )

    @Serializable
    data class Case(
        val d: String,
        val segments: List<Seg>,
    )

    @Serializable
    data class Fixture(
        val cases: List<Case>,
    )

    private fun flat(s: GlyphPathParser.Segment): Seg =
        when (s) {
            is GlyphPathParser.Segment.Move -> Seg("M", listOf(s.p.x, s.p.y))
            is GlyphPathParser.Segment.Line -> Seg("L", listOf(s.p.x, s.p.y))
            is GlyphPathParser.Segment.Cubic -> Seg("C", listOf(s.c1.x, s.c1.y, s.c2.x, s.c2.y, s.p.x, s.p.y))
            is GlyphPathParser.Segment.Quad -> Seg("Q", listOf(s.c.x, s.c.y, s.p.x, s.p.y))
            GlyphPathParser.Segment.Close -> Seg("Z", emptyList())
        }

    @Test
    fun everyCaseParsesAsTheFixtureSays() {
        val text = GlyphTestData.repoFile("packages/icons/fixtures/path-contract.json").readText()
        val fixture = Json { ignoreUnknownKeys = true }.decodeFromString<Fixture>(text)
        assertTrue(fixture.cases.size > 5)
        for (c in fixture.cases) {
            assertEquals(c.d, c.segments, GlyphPathParser.segments(c.d).map(::flat))
        }
    }

    @Test
    fun buildsComposePathNodes() {
        assertEquals(
            listOf(
                PathNode.MoveTo(2f, 3f),
                PathNode.LineTo(20f, 3f),
                PathNode.CurveTo(1f, 2f, 3f, 4f, 5f, 6f),
                PathNode.QuadTo(7f, 8f, 9f, 10f),
                PathNode.Close,
            ),
            GlyphPathParser.pathNodes("M2 3 L20 3 C1 2 3 4 5 6 Q7 8 9 10 Z"),
        )
    }
}
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests 'app.pbbls.android.core.designsystem.glyph.*'` — fails to compile (types missing).

- [ ] **Step 3: Implement** `GlyphForm.kt`:

```kotlin
package app.pbbls.android.core.designsystem.glyph

/** What a form fills (spec D3): nothing, the body, some of its parts, the first k steps, or the drawn dotted line. */
sealed interface GlyphFill {
    data object Regular : GlyphFill

    data object Solid : GlyphFill

    data object Dotted : GlyphFill

    data class Parts(
        val names: List<String>,
    ) : GlyphFill

    data class Progress(
        val k: Int,
    ) : GlyphFill
}

/**
 * A form (spec D3): a fill, plus the mark when the glyph has one. Its [key] is
 * the generator's form key (`solid-marked`, `parts-moon`, `progress-2`).
 */
data class GlyphForm(
    val fill: GlyphFill = GlyphFill.Regular,
    val marked: Boolean = false,
) {
    val fillKey: String
        get() =
            when (fill) {
                GlyphFill.Regular -> "regular"
                GlyphFill.Solid -> "solid"
                GlyphFill.Dotted -> "dotted"
                is GlyphFill.Parts -> "parts-" + fill.names.joinToString("+")
                is GlyphFill.Progress -> "progress-${fill.k}"
            }

    /** `regular` marked is `marked`; any other fill takes a `-marked` suffix. */
    val key: String
        get() =
            when {
                !marked -> fillKey
                fillKey == "regular" -> "marked"
                else -> "$fillKey-marked"
            }

    companion object {
        val REGULAR = GlyphForm()
        val MARKED = GlyphForm(marked = true)
        val SOLID = GlyphForm(GlyphFill.Solid)
        val DOTTED = GlyphForm(GlyphFill.Dotted)

        private val WORD = Regex("[a-z]+")
        private val COUNT = Regex("[1-9][0-9]*")

        /** The form a key names, or null when it is not a form key (`parseFormKey` in `forms.ts`). */
        fun fromKey(key: String): GlyphForm? {
            if (key == "marked") return MARKED
            val marked = key.endsWith("-marked")
            val head = if (marked) key.removeSuffix("-marked") else key
            return when {
                head == "regular" -> REGULAR.takeIf { !marked }
                head == "dotted" -> DOTTED.takeIf { !marked }
                head == "solid" -> GlyphForm(GlyphFill.Solid, marked)
                head.startsWith("parts-") ->
                    head
                        .removePrefix("parts-")
                        .split("+")
                        .takeIf { names -> names.all { WORD.matches(it) } }
                        ?.let { GlyphForm(GlyphFill.Parts(it), marked) }
                head.startsWith("progress-") ->
                    head
                        .removePrefix("progress-")
                        .takeIf { COUNT.matches(it) }
                        ?.toIntOrNull()
                        ?.let { GlyphForm(GlyphFill.Progress(it), marked) }
                else -> null
            }
        }
    }
}
```

`GlyphData.kt`:

```kotlin
package app.pbbls.android.core.designsystem.glyph

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A path as a form draws it: filled (non-zero or even-odd), stroked, or both. */
@Serializable
data class RenderedPathData(
    val d: String,
    val fill: Boolean,
    val evenOdd: Boolean,
    val stroke: Boolean,
)

/** A path's role (spec D2). */
@Serializable
enum class GlyphRole {
    @SerialName("body")
    BODY,

    @SerialName("mark")
    MARK,

    @SerialName("step")
    STEP,
}

/** One path of a glyph with its role, as `pbbls_glyphs.json` stores it. */
@Serializable
data class GlyphPathData(
    val d: String,
    val role: GlyphRole? = null,
    val part: String? = null,
    val step: Int? = null,
    val hidden: Boolean? = null,
    /** A mark or step with lines inside it: its carved fill, drawn instead of [d] when it fills. */
    val fill: String? = null,
    /** The paths [fill] is carved through, left out while it fills. */
    val cuts: List<Int>? = null,
)

/** A solid or parts fill worked out at build time: indices are into `paths`. */
@Serializable
data class CarvedFillData(
    val d: String,
    val keep: List<Int>,
    val lit: List<Int>,
)

/** A glyph as `pbbls_glyphs.json` stores it (spec §2), the contract the generator writes. */
@Serializable
data class GlyphData(
    val paths: List<GlyphPathData>,
    val parts: List<String> = emptyList(),
    val steps: Int = 0,
    val carved: Map<String, CarvedFillData> = emptyMap(),
    val dotted: List<RenderedPathData>? = null,
    val overrides: Map<String, List<RenderedPathData>> = emptyMap(),
) {
    val hasMark: Boolean get() = paths.any { it.role == GlyphRole.MARK }
    val hasBody: Boolean get() = paths.any { it.role == GlyphRole.BODY }

    /** The forms this glyph has, as keys, in the generator's order (`formKeysOf` in `forms.ts`). */
    val formKeys: List<String> by lazy {
        val fills =
            buildList {
                add(GlyphFill.Regular)
                if (hasBody) add(GlyphFill.Solid)
                partCombinations(parts).forEach { add(GlyphFill.Parts(it)) }
                for (k in 1..steps) add(GlyphFill.Progress(k))
            }
        val keys =
            fills
                .flatMap { fill ->
                    if (hasMark) listOf(GlyphForm(fill).key, GlyphForm(fill, marked = true).key) else listOf(GlyphForm(fill).key)
                }.toMutableList()
        if (dotted != null) keys.add(if (hasMark) 2 else 1, "dotted")
        keys
    }

    companion object {
        val EMPTY = GlyphData(paths = emptyList())

        /** Every non-empty combination of parts short of all of them, smallest first, in source order (`partCombinations` in `roles.ts`). */
        fun partCombinations(names: List<String>): List<List<String>> {
            val n = names.size
            if (n < 2) return emptyList()
            // `sortedBy` is stable, like JavaScript's sort: the bit order holds within a size.
            return (1 until (1 shl n) - 1)
                .map { bits -> names.filterIndexed { i, _ -> bits and (1 shl i) != 0 } }
                .sortedBy { it.size }
        }
    }
}

/** The resource as a whole. */
@Serializable
data class GlyphFile(
    val version: Int,
    val states: Map<String, List<String>>,
    val glyphs: Map<String, GlyphData>,
) {
    companion object {
        val EMPTY = GlyphFile(version = 1, states = emptyMap(), glyphs = emptyMap())
    }
}
```

(A `by lazy` property has no backing field, so kotlinx.serialization ignores it; if the plugin complains, mark it `@Transient`.)

`GlyphPathParser.kt`:

```kotlin
package app.pbbls.android.core.designsystem.glyph

import androidx.compose.ui.graphics.vector.PathNode

/**
 * Reads the normalised path data of `pbbls_glyphs.json` (spec D7): absolute
 * `M L C Q Z`, numbers split by spaces, commas or a sign. A command's extra
 * coordinate groups repeat it, an `M`'s as `L` (SVG 1.1 §8.3.2). Any other
 * command is skipped: the generator never writes one
 * (`packages/icons/fixtures/path-contract.json` is the contract; iOS's
 * `GlyphPathParser.swift` reads it the same way).
 */
object GlyphPathParser {
    data class Point(
        val x: Double,
        val y: Double,
    )

    sealed interface Segment {
        data class Move(val p: Point) : Segment

        data class Line(val p: Point) : Segment

        data class Cubic(val c1: Point, val c2: Point, val p: Point) : Segment

        data class Quad(val c: Point, val p: Point) : Segment

        data object Close : Segment
    }

    fun segments(d: String): List<Segment> =
        buildList {
            for (token in tokens(d)) {
                val n = token.numbers
                val points = (0 until n.size - 1 step 2).map { Point(n[it], n[it + 1]) }
                when (token.command) {
                    'M' -> points.forEachIndexed { i, p -> add(if (i == 0) Segment.Move(p) else Segment.Line(p)) }
                    'L' -> points.forEach { add(Segment.Line(it)) }
                    'C' -> for (i in 0 until points.size - 2 step 3) add(Segment.Cubic(points[i], points[i + 1], points[i + 2]))
                    'Q' -> for (i in 0 until points.size - 1 step 2) add(Segment.Quad(points[i], points[i + 1]))
                    'Z' -> add(Segment.Close)
                    else -> Unit
                }
            }
        }

    /** The same segments as Compose path nodes. */
    fun pathNodes(d: String): List<PathNode> =
        segments(d).map { s ->
            when (s) {
                is Segment.Move -> PathNode.MoveTo(s.p.x.toFloat(), s.p.y.toFloat())
                is Segment.Line -> PathNode.LineTo(s.p.x.toFloat(), s.p.y.toFloat())
                is Segment.Cubic ->
                    PathNode.CurveTo(
                        s.c1.x.toFloat(), s.c1.y.toFloat(), s.c2.x.toFloat(), s.c2.y.toFloat(), s.p.x.toFloat(), s.p.y.toFloat(),
                    )
                is Segment.Quad -> PathNode.QuadTo(s.c.x.toFloat(), s.c.y.toFloat(), s.p.x.toFloat(), s.p.y.toFloat())
                Segment.Close -> PathNode.Close
            }
        }

    private class Token(
        val command: Char,
    ) {
        val numbers = mutableListOf<Double>()
    }

    private const val COMMANDS = "MLCQZmlcqzAaHhVvSsTt"

    private fun tokens(d: String): List<Token> {
        val out = mutableListOf<Token>()
        val number = StringBuilder()

        fun flush() {
            if (number.isNotEmpty()) {
                number.toString().toDoubleOrNull()?.let { value -> out.lastOrNull()?.numbers?.add(value) }
                number.clear()
            }
        }
        for (ch in d) {
            when (ch) {
                in COMMANDS -> {
                    flush()
                    out.add(Token(ch))
                }
                '-' -> {
                    if (number.isNotEmpty() && !number.endsWith('e')) flush()
                    number.append(ch)
                }
                '.' -> {
                    if (number.contains('.') && !number.contains('e')) flush()
                    number.append(ch)
                }
                'e', in '0'..'9' -> number.append(ch)
                else -> flush()
            }
        }
        flush()
        return out
    }
}
```

- [ ] **Step 4: Run** the glyph tests — PASS. `./gradlew ktlintCheck` — PASS (let `ktlintFormat` fix wrapping if it only reflows; never disable a rule).

- [ ] **Step 5: Commit** `feat(android): read glyph forms, data and path strings`.

---

### Task 3: The renderer, the catalog and the state tokens, against the forms contract

**Files:**
- Create: `.../core/designsystem/glyph/GlyphRenderer.kt`, `GlyphCatalog.kt`
- Test: `.../core/designsystem/glyph/GlyphFormsContractTest.kt`, `GlyphVectorTest.kt` (and add `install()` to `GlyphTestData`)

- [ ] **Step 1: Failing tests.** `GlyphFormsContractTest.kt`:

```kotlin
package app.pbbls.android.core.designsystem.glyph

import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.PbblsGlyphState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The Android side of the forms contract (spec §4): `packages/icons/glyphs/forms-contract.json`,
 * generated from `formsOf`, `renderForm` and `resolveState`, against the bundled data.
 */
class GlyphFormsContractTest {
    @Serializable
    data class Entry(
        val forms: List<String>,
        val rendered: Map<String, List<RenderedPathData>>,
        val states: Map<String, String>,
    )

    @Serializable
    data class Contract(
        val glyphs: Map<String, Entry>,
    )

    private val contract by lazy {
        val text = GlyphTestData.repoFile("packages/icons/glyphs/forms-contract.json").readText()
        Json { ignoreUnknownKeys = true }.decodeFromString<Contract>(text)
    }

    private fun glyph(name: String): PbblsGlyph = requireNotNull(PbblsGlyph.fromManifestName(name)) { "no PbblsGlyph for $name" }

    @Before
    fun install() = GlyphTestData.install()

    @Test
    fun everySampledGlyphHasTheGeneratorsForms() {
        assertTrue(contract.glyphs.size >= 10)
        for ((name, entry) in contract.glyphs) {
            assertEquals(name, entry.forms, GlyphCatalog.glyph(glyph(name)).formKeys)
        }
    }

    @Test
    fun everyFormRendersAsTheGeneratorRendersIt() {
        for ((name, entry) in contract.glyphs) {
            val data = GlyphCatalog.glyph(glyph(name))
            for ((key, expected) in entry.rendered) {
                val form = GlyphForm.fromKey(key)
                assertNotNull("$name $key", form)
                assertEquals("$name $key", expected, GlyphRenderer.render(data, form!!))
            }
        }
    }

    @Test
    fun everyStateResolvesAsTheGeneratorResolvesIt() {
        for ((name, entry) in contract.glyphs) {
            for ((spec, expected) in entry.states) {
                val parts = spec.split("@")
                val state = PbblsGlyphState.entries.firstOrNull { it.key == parts[0] }
                assertNotNull(spec, state)
                val k = parts.getOrNull(1)?.toIntOrNull() ?: 0
                assertEquals("$name $spec", expected, glyph(name).form(state!!, k).key)
            }
        }
    }

    @Test
    fun progressZeroIsRegularAndAMissingFormFallsBackToRegular() {
        val stack = GlyphCatalog.glyph(PbblsGlyph.STACK)
        assertEquals(GlyphRenderer.render(stack, GlyphForm.REGULAR), GlyphRenderer.render(stack, GlyphForm(GlyphFill.Progress(0))))
        assertEquals(GlyphRenderer.render(stack, GlyphForm.REGULAR), GlyphRenderer.render(stack, GlyphForm.SOLID))
    }

    @Test
    fun everyEntryHasData() {
        for (glyph in PbblsGlyph.entries) {
            assertTrue(glyph.manifestName, GlyphCatalog.glyph(glyph).paths.isNotEmpty())
        }
    }

    @Test
    fun loadsTheWholeSetQuickly() {
        val start = System.nanoTime()
        GlyphCatalog.read { GlyphTestData.resource.inputStream() }
        val ms = (System.nanoTime() - start) / 1_000_000
        println("pbbls_glyphs.json decoded in $ms ms on the JVM")
        assertTrue("$ms ms", ms < 2_000)
    }
}
```

`GlyphVectorTest.kt`:

```kotlin
package app.pbbls.android.core.designsystem.glyph

import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.VectorPath
import app.pbbls.android.core.designsystem.PbblsGlyph
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GlyphVectorTest {
    @Before
    fun install() = GlyphTestData.install()

    private fun paths(glyph: PbblsGlyph, form: GlyphForm): List<VectorPath> =
        GlyphCatalog.vector(glyph, form).root.toList().filterIsInstance<VectorPath>()

    @Test
    fun aLineIsStrokedOnlyOnTheTwentyFourSquare() {
        val vector = GlyphCatalog.vector(PbblsGlyph.HOME, GlyphForm.REGULAR)
        assertEquals(24f, vector.viewportWidth)
        val first = paths(PbblsGlyph.HOME, GlyphForm.REGULAR).first()
        assertNull(first.fill)
        assertNotNull(first.stroke)
        assertEquals(1.75f, first.strokeLineWidth)
    }

    @Test
    fun aSolidStartsWithItsCarvedFillEvenOddAndUnstroked() {
        val carved = paths(PbblsGlyph.PEBBLE_ROUND, GlyphForm.SOLID).first()
        assertEquals(PathFillType.EvenOdd, carved.pathFillType)
        assertNotNull(carved.fill)
        assertNull(carved.stroke)
    }

    @Test
    fun cachesOneVectorPerGlyphAndForm() {
        val a = GlyphCatalog.vector(PbblsGlyph.STACK, GlyphForm(GlyphFill.Progress(2)))
        assertSame(a, GlyphCatalog.vector(PbblsGlyph.STACK, GlyphForm(GlyphFill.Progress(2))))
        assertTrue(a !== GlyphCatalog.vector(PbblsGlyph.STACK, GlyphForm(GlyphFill.Progress(3))))
    }
}
```

`VectorGroup` is iterable over its children (`root.toList()`); if the Compose version in use is not, iterate `(0 until root.size).map { root[it] }`.

Add to `GlyphTestData`: `fun install() = GlyphCatalog.installSource { resource.inputStream() }`.

- [ ] **Step 2: Run** — fails to compile.

- [ ] **Step 3: Implement** `GlyphRenderer.kt`:

```kotlin
package app.pbbls.android.core.designsystem.glyph

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * `renderForm` and `resolveState` from `packages/icons/scripts/lib`, in Kotlin
 * (iOS: `GlyphRenderer.swift`). The forms contract (`GlyphFormsContractTest`)
 * holds them equal.
 */
object GlyphRenderer {
    private fun stroked(d: String) = RenderedPathData(d, fill = false, evenOdd = false, stroke = true)

    private fun filled(d: String) = RenderedPathData(d, fill = true, evenOdd = false, stroke = true)

    private fun carved(d: String) = RenderedPathData(d, fill = true, evenOdd = true, stroke = false)

    /** A filled mark or step: its carved fill when lines run inside it. */
    private fun lit(p: GlyphPathData) = p.fill?.let(::carved) ?: filled(p.d)

    /**
     * The paths a form draws, overrides first (spec D3, D4). `progress(0)` is
     * regular; a form the glyph does not have draws regular (the generator
     * throws there, a view should not).
     */
    fun render(
        glyph: GlyphData,
        form: GlyphForm,
    ): List<RenderedPathData> {
        val fill = form.fill
        if (fill is GlyphFill.Progress && fill.k == 0) return render(glyph, GlyphForm(marked = form.marked))
        val key = form.key
        if (key !in glyph.formKeys) return if (key == "regular") emptyList() else render(glyph, GlyphForm.REGULAR)
        glyph.overrides[key]?.let { return it }
        when (fill) {
            GlyphFill.Dotted -> return glyph.dotted.orEmpty()
            GlyphFill.Solid, is GlyphFill.Parts -> {
                val worked = glyph.carved[key] ?: return render(glyph, GlyphForm.REGULAR)
                return listOf(carved(worked.d)) +
                    worked.keep.mapNotNull { i -> glyph.paths.getOrNull(i)?.let { stroked(it.d) } } +
                    worked.lit.mapNotNull { i -> glyph.paths.getOrNull(i)?.let(::lit) }
            }
            GlyphFill.Regular, is GlyphFill.Progress -> Unit
        }
        val k = (fill as? GlyphFill.Progress)?.k ?: 0
        // A hidden path is drawn only when its role fills it; everything else is stroked.
        // A filled path with a carved `fill` draws that instead, and the lines it is carved through are left out.
        val fills = { p: GlyphPathData -> (form.marked && p.role == GlyphRole.MARK) || (p.role == GlyphRole.STEP && (p.step ?: 0) <= k) }
        val cut = glyph.paths.filter { fills(it) && it.fill != null }.flatMap { it.cuts.orEmpty() }.toSet()
        return glyph.paths.mapIndexedNotNull { i, p ->
            val on = fills(p)
            when {
                (p.hidden == true && !on) || i in cut -> null
                on -> lit(p)
                else -> stroked(p.d)
            }
        }
    }

    /** The form a state shows on a glyph with these form keys (`resolveState` in `states.ts`). */
    fun resolve(
        states: Map<String, List<String>>,
        formKeys: List<String>,
        state: String,
        k: Int = 0,
    ): GlyphForm {
        val n = formKeys.count { it.startsWith("progress-") && !it.endsWith("-marked") }
        for (word in states[state] ?: listOf("regular")) {
            when {
                word == "regular" -> return GlyphForm.REGULAR
                word == "marked" && "marked" in formKeys -> return GlyphForm.MARKED
                word == "solid" && "solid" in formKeys -> return GlyphForm.SOLID
                word == "progress-n" && n > 0 -> return GlyphForm(GlyphFill.Progress(n))
                word == "progress-k" && n > 0 -> return GlyphForm(GlyphFill.Progress(k.coerceIn(0, n)))
            }
        }
        return GlyphForm.REGULAR
    }

    private val INK = SolidColor(Color.Black)

    /**
     * Paths on the 24-unit square as one vector: fill then stroke per path, in
     * order, as SVG paints them, with the 1.75 stroke, round caps and joins.
     * Black ink: `Icon` tints it with the content colour.
     */
    fun vector(
        name: String,
        paths: List<RenderedPathData>,
    ): ImageVector =
        ImageVector
            .Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .apply {
                for (p in paths) {
                    addPath(
                        pathData = GlyphPathParser.pathNodes(p.d),
                        pathFillType = if (p.evenOdd) PathFillType.EvenOdd else PathFillType.NonZero,
                        fill = if (p.fill) INK else null,
                        stroke = if (p.stroke) INK else null,
                        strokeLineWidth = if (p.stroke) 1.75f else 0f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }.build()
}
```

`GlyphCatalog.kt`:

```kotlin
package app.pbbls.android.core.designsystem.glyph

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.vector.ImageVector
import app.pbbls.android.R
import app.pbbls.android.core.designsystem.PbblsGlyph
import app.pbbls.android.core.designsystem.PbblsGlyphState
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * `res/raw/pbbls_glyphs.json`, loaded once (spec §2; iOS `GlyphCatalog`):
 * `PebblesApp` installs and prewarms it off the main thread at launch;
 * `PbblsGlyphImage` installs it from its context if nothing did (previews,
 * Robolectric); plain JUnit installs the repository's file. A read before any
 * install gets empty data and is not cached, so a later install still loads.
 */
object GlyphCatalog {
    private const val TAG = "PbblsGlyphs"
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()

    @Volatile private var source: (() -> InputStream)? = null

    @Volatile private var loaded: GlyphFile? = null
    private val vectors = ConcurrentHashMap<String, ImageVector>()

    /** Points the catalog at the app's raw resource, once. */
    fun install(context: Context) {
        if (source != null) return
        val resources = context.applicationContext.resources
        installSourceIfAbsent { resources.openRawResource(R.raw.pbbls_glyphs) }
    }

    /** Loads the data on a background thread so the first glyph on screen does not wait for it. */
    fun prewarm(context: Context) {
        install(context)
        thread(name = "pbbls-glyphs", isDaemon = true) { file() }
    }

    /** Replaces the source and drops what was loaded from the previous one (tests). */
    fun installSource(open: () -> InputStream) =
        synchronized(lock) {
            source = open
            loaded = null
            vectors.clear()
        }

    private fun installSourceIfAbsent(open: () -> InputStream) =
        synchronized(lock) {
            if (source == null) source = open
        }

    val states: Map<String, List<String>> get() = file().states

    fun glyph(glyph: PbblsGlyph): GlyphData =
        file().glyphs[glyph.manifestName] ?: GlyphData.EMPTY.also {
            if (loaded != null) Log.e(TAG, "no data for glyph ${glyph.manifestName}")
        }

    /** A form as a vector, cached per glyph and form (a vector scales: no size in the key). */
    fun vector(
        glyph: PbblsGlyph,
        form: GlyphForm,
    ): ImageVector {
        val key = "${glyph.manifestName}|${form.key}"
        vectors[key]?.let { return it }
        val built = GlyphRenderer.vector(key, GlyphRenderer.render(glyph(glyph), form))
        // Only cache once the data is there: a vector built before the install is empty.
        return if (loaded != null) vectors.putIfAbsent(key, built) ?: built else built
    }

    private fun file(): GlyphFile {
        loaded?.let { return it }
        synchronized(lock) {
            loaded?.let { return it }
            val open = source ?: return GlyphFile.EMPTY.also { Log.w(TAG, "read before install") }
            val start = System.nanoTime()
            val read = read(open)
            Log.i(TAG, "pbbls_glyphs.json loaded in ${(System.nanoTime() - start) / 1_000_000} ms")
            loaded = read
            return read
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun read(open: () -> InputStream): GlyphFile =
        runCatching { open().use { json.decodeFromStream<GlyphFile>(it) } }
            .getOrElse { error ->
                Log.e(TAG, "pbbls_glyphs.json did not decode", error)
                GlyphFile.EMPTY
            }
}

/** The forms this glyph has, in the generator's order (spec D3). */
val PbblsGlyph.forms: List<GlyphForm>
    get() = GlyphCatalog.glyph(this).formKeys.mapNotNull(GlyphForm::fromKey)

/**
 * The form a state shows on this glyph (spec D6): the state's first form the
 * glyph has. [k] is the caller's progress for [PbblsGlyphState.PROGRESS].
 */
fun PbblsGlyph.form(
    state: PbblsGlyphState,
    k: Int = 0,
): GlyphForm = GlyphRenderer.resolve(GlyphCatalog.states, GlyphCatalog.glyph(this).formKeys, state.key, k)
```

`android.util.Log` returns defaults in plain JUnit (`isReturnDefaultValues = true` in `testOptions`). If `R.raw.pbbls_glyphs` does not resolve, Task 1's generated file is missing: run `generate`.

- [ ] **Step 4: Run** the glyph tests — PASS; note the JVM decode time it prints. `./gradlew ktlintCheck` — PASS. The Konsist boundary test (`ArchitectureBoundaryTest`) — PASS (`core.designsystem.glyph` imports nothing from `features`).

- [ ] **Step 5: Commit** `feat(android): render glyph forms and resolve state tokens`.

---

### Task 4: The composable, the painter and launch cost

**Files:**
- Create: `.../core/designsystem/PbblsGlyphImage.kt`
- Modify: `.../PebblesApp.kt`
- Test: `app/src/test/kotlin/app/pbbls/android/core/designsystem/PbblsGlyphImageTest.kt` (Robolectric, like `PbblsIconsTest`)

- [ ] **Step 1: Failing test** — `PbblsGlyphImageTest.kt` checks the install-from-context path and the painter's intrinsic size:

```kotlin
package app.pbbls.android.core.designsystem

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.pbbls.android.core.designsystem.glyph.GlyphCatalog
import app.pbbls.android.core.designsystem.glyph.GlyphForm
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PbblsGlyphImageTest {
    @Test
    fun theRawResourceLoadsThroughTheContext() {
        GlyphCatalog.installSource { ApplicationProvider.getApplicationContext<Context>().resources.openRawResource(app.pbbls.android.R.raw.pbbls_glyphs) }
        assertTrue(GlyphCatalog.glyph(PbblsGlyph.HOME).paths.isNotEmpty())
        assertTrue(GlyphCatalog.vector(PbblsGlyph.HOME, GlyphForm.SOLID).root.size > 0)
    }
}
```

- [ ] **Step 2: Run** — PASS already if Task 3 is right (it proves the resource is packaged and readable through `Resources`); if it fails, fix the packaging before writing the composable.

- [ ] **Step 3: Implement** `PbblsGlyphImage.kt`:

```kotlin
package app.pbbls.android.core.designsystem

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pbbls.android.core.designsystem.glyph.GlyphCatalog
import app.pbbls.android.core.designsystem.glyph.GlyphForm

/** A form change (spec D9): `RevealMotion`'s pop length. */
private const val FORM_CHANGE_MS = 220

/**
 * A [PbblsGlyph] in one of its forms (spec §2; iOS `PbblsGlyphView`), drawn
 * through [Icon] on a square [size] side (24dp by default: icons keep dp and
 * do not follow the font scale, the Material convention). It takes
 * [LocalContentColor] unless [tint] is given, and is hidden from accessibility
 * unless [contentDescription] is. A form change crossfades with a slight
 * scale, none under reduce motion (spec D9).
 */
@Composable
fun PbblsGlyphImage(
    glyph: PbblsGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    form: GlyphForm = GlyphForm.REGULAR,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    GlyphCatalog.install(LocalContext.current)
    if (rememberReduceMotion()) {
        Icon(
            painter = rememberVectorPainter(GlyphCatalog.vector(glyph, form)),
            contentDescription = contentDescription,
            tint = tint,
            modifier = modifier.size(size),
        )
        return
    }
    val labelled =
        if (contentDescription == null) {
            modifier
        } else {
            modifier.semantics {
                this.contentDescription = contentDescription
                role = Role.Image
            }
        }
    AnimatedContent(
        targetState = form,
        transitionSpec = {
            (fadeIn(tween(FORM_CHANGE_MS)) + scaleIn(tween(FORM_CHANGE_MS), initialScale = 0.9f)) togetherWith
                fadeOut(tween(FORM_CHANGE_MS))
        },
        label = "glyph form",
        modifier = labelled.size(size),
    ) { shown ->
        Icon(
            painter = rememberVectorPainter(GlyphCatalog.vector(glyph, shown)),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}

/**
 * [PbblsGlyphImage] at a named [size]. Pass the size here (or a `Dp` to the
 * other overload's `size`), never through [modifier]: the size is applied
 * after yours, and the first size wins.
 */
@Composable
fun PbblsGlyphImage(
    glyph: PbblsGlyph,
    size: PbblsIconSize,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    form: GlyphForm = GlyphForm.REGULAR,
    tint: Color = LocalContentColor.current,
) {
    PbblsGlyphImage(
        glyph = glyph,
        contentDescription = contentDescription,
        modifier = modifier,
        form = form,
        size = size.toDp(LocalDensity.current),
        tint = tint,
    )
}

/** A form as a [Painter], for the slots that take one instead of a composable. */
@Composable
fun rememberPbblsGlyphPainter(
    glyph: PbblsGlyph,
    form: GlyphForm = GlyphForm.REGULAR,
): Painter {
    GlyphCatalog.install(LocalContext.current)
    return rememberVectorPainter(GlyphCatalog.vector(glyph, form))
}
```

Check `RevealMotion.kt`: if it exposes its 220 ms pop as a public constant, use it instead of `FORM_CHANGE_MS`.

In `PebblesApp.kt` add (its KDoc warns that `onCreate` work blocks the first frame: this only starts a thread):

```kotlin
override fun onCreate() {
    super.onCreate()
    // Decode the glyph data off the main thread before the first screen asks for it.
    GlyphCatalog.prewarm(this)
}
```

- [ ] **Step 4: Measure.** `./gradlew installDebug` on the Pixel 7 AVD (or any emulator; the maintainer's Pixel 8 only with their yes), launch, `adb logcat -d -s PbblsGlyphs` — note the "loaded in N ms" line for the PR body (iOS: ~18 ms on the simulator). If it is over 300 ms on the emulator, report it before Task 5.

- [ ] **Step 5:** `./gradlew ktlintCheck testDebugUnitTest --tests '*Glyph*'` — PASS. Commit `feat(android): draw a glyph's form with PbblsGlyphImage`.

---

### Task 5: Move every call site

**Files:** every Kotlin file under `apps/android/app/src/main`, `src/test` and `src/screenshotTest` that names `PbblsIcon` or `PbblsIconImage`, except `core/designsystem/PbblsIcons.kt`, `core/designsystem/PbblsIconSize.kt`, `test/.../core/designsystem/PbblsIconsTest.kt`, `PbblsIconSizeTest.kt` and `screenshotTest/.../PbblsIconsScreenshots.kt` (part 7 removes those).

- [ ] **Step 1: Rename mechanically.** From `apps/android/app/src`:

```bash
FILES=$(grep -rlE 'PbblsIcon(Image)?\b' --include='*.kt' main test screenshotTest \
  | grep -vE 'designsystem/PbblsIcons\.kt|designsystem/PbblsIconSize\.kt|PbblsIcons?Test\.kt|PbblsIconSizeTest\.kt|PbblsIconsScreenshots\.kt')
perl -0pi -e 's/\bPbblsIconImage\((\s*)icon =/PbblsGlyphImage($1glyph =/g; s/\bPbblsIconImage\b/PbblsGlyphImage/g; s/\bPbblsIcon\b/PbblsGlyph/g' $FILES
```

`PbblsGlyphImage` takes `PbblsIconImage`'s parameters (plus `form`), so the rename compiles wherever the entry is a base glyph. Imports follow (both live in `core.designsystem`).

- [ ] **Step 2: Build** (`./gradlew compileDebugKotlin compileDebugUnitTestKotlin`) and fix what is left by hand. Every remaining error is a twin, a twin helper, or a resource id:

| Site | Today | After |
|---|---|---|
| `navigation/PebblesNavigationSuite.kt:84` | `if (selected) tab.icon().activeTwin ?: tab.icon() else tab.icon()` | `PbblsGlyphImage(glyph = tab.icon(), contentDescription = null, form = if (selected) tab.selectedForm() else GlyphForm.REGULAR)`; beside `PebblesKey.icon()` add `internal fun PebblesKey.selectedForm(): GlyphForm = if (this == PebblesKey.People) GlyphForm(GlyphFill.Solid, marked = true) else icon().form(PbblsGlyphState.SELECTED)` with a KDoc mirroring iOS `AppTab.selectedForm` (People keeps its mark lit on the solid; the rest follow the `selected` token: home and collection their solid, path its last step, you its mark); update `icon()`'s KDoc (no more `active` twin) |
| `core/ui/AchievementCopy.kt:82-92` `achievementFamilyIcon` | `STACK_LIT_3`, `PEBBLE_GLYPH_DOTTED` | returns `STACK` / `PEBBLE_GLYPH`; add `fun achievementFamilyForm(family: String): GlyphForm` beside it: `first_five` → `PbblsGlyph.STACK.form(PbblsGlyphState.DONE)`, `first_glyph` and `glyph_count` → `GlyphForm.DOTTED`, else `GlyphForm.REGULAR`; pass `form = achievementFamilyForm(family)` wherever the family icon is drawn |
| `core/ui/GlyphView.kt:92` (placeholder) and `:107` (`PbblsIcon` param) | `PEBBLE_GLYPH_DOTTED` | `PEBBLE_GLYPH` with `form = GlyphForm.DOTTED`; give the composable taking a glyph a `form: GlyphForm = GlyphForm.REGULAR` parameter passed through |
| `features/home/guides/GuideIcon.kt:22` | `GLYPHS -> PEBBLE_GLYPH_DOTTED` | `PEBBLE_GLYPH`; add a `form` beside the icon mapping (`GLYPHS -> GlyphForm.DOTTED`, else regular) and pass it where drawn |
| `features/home/checklist/FirstFiveBox.kt:190` | `PEBBLE_ROUND_LIT_1` | `PEBBLE_ROUND`, `form = PbblsGlyph.PEBBLE_ROUND.form(PbblsGlyphState.SELECTED)` |
| `features/lab/components/LogTimeline.kt:79` | `IN_PROGRESS -> PEBBLE_ROUND_LIT_1` | the icon is `PEBBLE_ROUND` for both pebble modes; add an `iconForm` (`IN_PROGRESS` → `PEBBLE_ROUND.form(SELECTED)`, else regular) passed where drawn, as iOS `LogTimeline.iconForm` |
| `features/onboarding/steps/ProgressStones.kt:37`, `ReasonsStep.kt:92`, `PromiseStep.kt:115` | `lit ? PEBBLE_ROUND_LIT_1 : PEBBLE_ROUND` (or always lit) | `PEBBLE_ROUND` with `form = if (lit) PEBBLE_ROUND.form(SELECTED) else GlyphForm.REGULAR` |
| `features/profile/components/PentagonAvatar.kt:53` | `PEBBLE_GLYPH_DOTTED` | `PEBBLE_GLYPH`, `form = GlyphForm.DOTTED` |
| `navigation/PebblesSceneStrategies.kt:173` `GlyphsPlaceholder` | `PEBBLE_GLYPH_DOTTED` | `PEBBLE_GLYPH` with `form = GlyphForm.DOTTED`; give `DetailPlaceholder` a `form` parameter defaulting to regular |
| `features/path/create/AttachedPhotoView.kt:84`, `ExistingSnapRow.kt:93`, `features/glyph/carve/GlyphCarveScreen.kt:190` | `PbblsIcon.X.drawableRes` → `painterResource` | pass the `PbblsGlyph` down instead of an `@DrawableRes Int` and draw it with `rememberPbblsGlyphPainter(glyph)` where `painterResource` was called (`MediaStateLabel`, `CarvePillButton`) |
| `core/model/Onboarding.kt:36,51` | holds `PbblsIcon` | holds `PbblsGlyph` (renamed in Step 1; add a `form` field only if a reason uses a twin) |
| `features/profile/YouGlyphsSection.kt:46` | `DRAW_DOTTED` | unchanged: `draw-dotted` is its own drawing, not a twin |

Read each site before editing; if one draws something other than what the table assumes, report it rather than guess. No call site may name a twin: `PbblsGlyph` has none, so the compiler enforces it.

- [ ] **Step 3: The search lens.** `core/ui/souls/SoulChooserSearchField.kt:107` draws `painterResource(R.drawable.ic_search)` in the search button: draw `PbblsGlyphImage(glyph = PbblsGlyph.MAGNIFYING_MD, contentDescription = stringResource(R.string.souls_search))` instead (iOS #1616). If `ic_search` then has no other reference (`grep -rn ic_search app/src`), delete `res/drawable/ic_search.xml`: Lint runs with `warningsAsErrors` and would flag it unused.

- [ ] **Step 4: Tests that named twins.** `test/.../navigation/PebblesNavigationIconTest.kt` (asserted `activeTwin`): every tab has a distinct glyph; `selectedForm()` differs from regular and is one the glyph has (`tab.icon().forms.contains(tab.selectedForm())`); People's is solid-marked; the others equal `icon().form(SELECTED)`. It needs the catalog: call `GlyphTestData.install()`-equivalent in `@Before` (`GlyphCatalog.installSource { File("src/main/res/raw/pbbls_glyphs.json").inputStream() }`, or the Robolectric context). `AchievementFamilyIconTest.kt` and `GuideIconTest.kt` now expect `STACK` / `PEBBLE_GLYPH`; add assertions for `achievementFamilyForm` (`first_five` → `GlyphForm(GlyphFill.Progress(3))`, `first_glyph` → `DOTTED`, `first_moment` → `REGULAR`) and the guide's form.

- [ ] **Step 5: Prove nothing is left.** From `apps/android/app/src`:

```bash
grep -rnE '\bPbblsIcon\b|PbblsIconImage|activeTwin|\.lit\([0-9]|PbblsGlyph\.[A-Z_]+\.drawableRes' --include='*.kt' main test screenshotTest \
  | grep -vE 'designsystem/PbblsIcons\.kt|designsystem/PbblsIconSize\.kt|PbblsIcons?Test\.kt|PbblsIconSizeTest\.kt|PbblsIconsScreenshots\.kt'
```

Expected: no output. Then from `apps/android`: `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug` — PASS.

- [ ] **Step 6: Look at it.** Install on the emulator; on the sign-in screen and onboarding (Debug "Replay onboarding" launcher activity) check the promise and reasons steps' lit pebbles. With a signed-in account only if the maintainer opens one: each tab selected once (screenshot the bar each time), the souls search button, the Home checklist. Screenshots go to the scratchpad, never into the repo.

- [ ] **Step 7: Commit** `chore(android): draw every glyph through PbblsGlyphImage`.

---

### Task 6: Per-form screenshot pages

**Files:**
- Create: `apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/GlyphFormSheetScreenshots.kt`

- [ ] **Step 1:** Model it on `PbblsIconsScreenshots.kt` (`PER_PAGE = 240`, a 360dp `FlowRow` on `surface`, 8dp spacing, light and dark `@PreviewTest` functions over a `PreviewParameterProvider`). The provider needs each glyph's forms before composition, where there is no `Context`: it installs the repository's file, which the screenshot task reads from the module directory like the unit tests.

```kotlin
package app.pbbls.android

// imports as in PbblsIconsScreenshots.kt, plus PbblsGlyph, PbblsGlyphImage,
// glyph.GlyphCatalog, glyph.GlyphFill, glyph.GlyphForm, glyph.forms, java.io.File

private const val FORM_PER_PAGE = 240

/** One page of a form group. */
data class GlyphFormSheetPage(
    val group: String,
    val index: Int,
)

private val GROUPS = listOf("regular", "solid", "marked", "other")

/** The form a group draws a glyph in, or null when the glyph is not in the group (iOS `GlyphFormSheetReferences`). */
private fun formOf(
    glyph: PbblsGlyph,
    group: String,
): GlyphForm? {
    val forms = glyph.forms
    return when (group) {
        "regular" -> GlyphForm.REGULAR
        "solid" -> GlyphForm.SOLID.takeIf { it in forms }
        "marked" -> GlyphForm.MARKED.takeIf { it in forms }
        else ->
            forms.lastOrNull { it.fill is GlyphFill.Progress && !it.marked }
                ?: forms.firstOrNull { it.fill is GlyphFill.Parts }
                ?: GlyphForm.DOTTED.takeIf { it in forms }
    }
}

/** Each group's glyphs with their forms, read once from the repository's resource (a provider has no Context). */
private val DRAWN: Map<String, List<Pair<PbblsGlyph, GlyphForm>>> by lazy {
    GlyphCatalog.installSource { File("src/main/res/raw/pbbls_glyphs.json").inputStream() }
    GROUPS.associateWith { group -> PbblsGlyph.entries.mapNotNull { glyph -> formOf(glyph, group)?.let { glyph to it } } }
}

class GlyphFormSheetPages : PreviewParameterProvider<GlyphFormSheetPage> {
    override val values: Sequence<GlyphFormSheetPage> =
        GROUPS
            .flatMap { group ->
                (0 until (DRAWN.getValue(group).size + FORM_PER_PAGE - 1) / FORM_PER_PAGE).map { GlyphFormSheetPage(group, it) }
            }.asSequence()
}

/** The glyph set in its forms (spec §4): regular, solid, marked, and the rest (each stepped glyph done, each parted glyph's first parts form, each dotted drawing), at 24dp, in manifest order. */
@Composable
private fun GlyphFormSheet(page: GlyphFormSheetPage) {
    FlowRow(/* exactly as IconSheet */) {
        DRAWN.getValue(page.group).drop(page.index * FORM_PER_PAGE).take(FORM_PER_PAGE).forEach { (glyph, form) ->
            PbblsGlyphImage(glyph = glyph, contentDescription = null, form = form)
        }
    }
}

// GlyphFormSheetLight / GlyphFormSheetDark: @PreviewTest + @Preview(showBackground = true[, uiMode = UI_MODE_NIGHT_YES])
// over @PreviewParameter(GlyphFormSheetPages::class) page, PebblesTheme { GlyphFormSheet(page) }, as PbblsIconsSheetLight/Dark.
```

Write the elided parts in full, copying `PbblsIconsScreenshots.kt`. Expected counts with today's set (as iOS): regular 950 (4 pages), solid 598 (3), marked 521 (3), other about 100 (1) — 11 pages × 2 = 22 references.

- [ ] **Step 2: Look locally, never commit.** `./gradlew updateDebugScreenshotTest --tests '*GlyphFormSheet*'` if the task accepts a filter (else the whole task), open two of the new PNGs under `app/src/screenshotTestDebug/reference/` (a solid page and the "other" page: solids carved, marks lit, progress full, dotted drawn). If a page renders blank, the provider could not read the file from the module directory: report it rather than move the data. Then `git checkout -- app/src/screenshotTestDebug && git clean -fd app/src/screenshotTestDebug` to drop every locally rendered reference.

- [ ] **Step 3: Commit** `test(android): screenshot pages of every glyph form`.

---

### Task 7: Docs and the PR

**Files:**
- Modify: `packages/icons/CLAUDE.md`, this plan (Lessons learned)

- [ ] **Step 1:** In `packages/icons/CLAUDE.md`, "Never hand-edit a generated file": add `apps/android/app/src/main/res/raw/pbbls_glyphs.json`, `apps/android/.../core/designsystem/PbblsGlyph.kt`, `PbblsGlyphState.kt` (the Android forms data, names and state tokens). One line.

- [ ] **Step 2:** Package checks; from `apps/android`: `./gradlew ktlintCheck lint testDebugUnitTest assembleDebug` — all green. Push the branch.

- [ ] **Step 3: PR**, base `main`:
  - Title: `chore(android): draw glyphs from their forms`
  - Body: `Part of #1607` (the issue stays open for web and removal); what changed (data resource, renderer, `PbblsGlyphImage`, call sites, per-form pages); the tab bar's selected look to check on device (solid for Home and Collections, People's solid with its mark, Path's last step, You's mark), the souls search lens, onboarding's lit pebbles; the load time from Task 4; the two contracts; `Data safety: no change`. End with `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
  - Labels `chore`, `android`, `ui`, `no-lab-note`; milestone `M66 · Brand icons`.
- [ ] **Step 4:** Add `rebaseline-screenshots` alone (its own `gh pr edit`; if it is already on the PR, remove it and add it again: only `labeled` fires the workflow). Approve the held runs on the bot's commit (`gh api "repos/alexisbohns/pbbls/actions/runs?head_sha=<bot sha>&status=action_required"` → `gh api --method POST repos/alexisbohns/pbbls/actions/runs/<id>/approve`), wait for green. Read a few re-rendered references from the bot's commit (one form page, the navigation bar preview) before calling it done.

## Lessons learned

(Filled in after the PR merges.)
