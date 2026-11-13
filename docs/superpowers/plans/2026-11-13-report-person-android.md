# Report a person and a glyph on Android: implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship #839, the Android mirror of the iOS report flow (#838): report a connected person, an inviter or a market glyph, with the same nine reasons, the same 1,000-code-point detail cap, the same EN/FR copy, and "Also remove and block" on by default for a connection.

**Architecture:** The server is done (`report_person` for people, `report_content('glyph', …)` for glyphs; spec `docs/superpowers/specs/2026-11-12-report-person-design.md`). Android adds a `ReportService` in `core/data`, the target and reason models in `core/model`, and a `features/report` screen (`ReportViewModel` + `ReportScreen` + stateless `ReportContent`) reached through a new Nav 3 key, `PebblesKey.Report(target)`. The three hosts (Connections, glyph detail, invite accept) never import `features/report` (Konsist forbids feature → feature): they take an `onReport` lambda and `navigation/PebblesEntryProvider.kt` fills it.

**Tech Stack:** Kotlin, Jetpack Compose Material 3 (Expressive), Hilt, Navigation 3, supabase-kt postgrest, kotlinx.serialization, JUnit + `runTest` + `MainDispatcherRule`, Compose Preview Screenshot Testing.

**Reference (iOS, the behaviour to mirror):** `apps/ios/Pebbles/Features/Report/*.swift`, and the entry-point diff in commit `2afff82c1` (`ConnectionsListView.swift`, `GlyphDetailDrawer.swift`, `InviteAcceptSheet.swift`). Read iOS for *what it does*; build it the Android way (`apps/android/CLAUDE.md`, "What mirror iOS means here").

---

## Working environment (every task)

- Worktree: `/Users/alexis/code/pbbls/.worktrees/report-android` (already created; `apps/android/secrets.properties` copied in). Work only there.
- Gate, from `apps/android`:
  ```bash
  export ANDROID_HOME=$HOME/Library/Android/sdk
  ./gradlew ktlintFormat ktlintCheck testDebugUnitTest assembleDebug
  ```
  `./gradlew lint` once per part (it is slow). The screenshot gate (`validateDebugScreenshotTest`) is **CI-only**: dozens of renders fail locally on untouched main. Never re-baseline locally; new and changed previews are re-baselined by CI through the `rebaseline-screenshots` label on the PR.
- `$?` after a pipe to `tail` is tail's. Read the printed `BUILD SUCCESSFUL`/`FAILED`.
- Commits: conventional, lowercase, `feat(android): …`, ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. One logical change per commit. Never push; the orchestrator pushes.
- Konsist (`architecture/ArchitectureBoundaryTest.kt`): `core` never imports `features`, and a feature never imports another feature. `navigation/` sees everything.
- No exception `.message` reaches the UI. Map `DataError` → `@StringRes`.

## Model routing

| Thread | Model |
|---|---|
| Implementer, each task | sonnet |
| Spec + code-quality review, each task | sonnet |
| A thread reported BLOCKED or looping | re-dispatch on opus |
| Pushes, PRs, Arkaik, device pass | orchestrator |

## File map

**Part 1, branch `feat/839-report-screen-android`**

| File | Responsibility |
|---|---|
| Create `core/model/Report.kt` | `ReportReason` (nine slugs, sheet order, `@StringRes` label), `ReportTarget` (sealed, `@Serializable`: `Glyph`, `Connection`, `Invite`) |
| Create `core/data/ReportService.kt` | `ReportServicing`, `ReportService`, `reportParams()` (pure), `ReportReceipt`, `reportErrorMessage(DataError)` |
| Modify `di/ServiceBindings.kt` | bind `ReportServicing` |
| Create `features/report/ReportViewModel.kt` | form state, `canSend`, code-point clamp, submit, phases |
| Create `features/report/ReportScreen.kt` | `ReportScreen` (stateful) and `ReportContent` (stateless, previewed) |
| Modify `navigation/PebblesKeys.kt`, `navigation/PebblesEntryProvider.kt` | `PebblesKey.Report(target)` and its entry |
| Modify `res/values/strings.xml`, `res/values-fr/strings.xml` | the report strings |
| Create `test/…/core/data/ReportServiceTest.kt`, `test/…/features/report/ReportViewModelTest.kt` | JVM tests |
| Create `screenshotTest/…/ReportScreenshots.kt` | previews |

**Part 2, branch `feat/839-report-entries-android`**

| File | Responsibility |
|---|---|
| Modify `features/connections/ConnectionsScreen.kt`, `ConnectionsViewModel.kt` | row overflow menu (Report, Remove); refresh on resume |
| Modify `features/glyph/store/GlyphDetailDrawer.kt` | "Report this glyph" for a priced glyph you don't own |
| Modify `features/connections/AcceptInviteScreen.kt` | quiet "Report" under Accept |
| Modify `navigation/PebblesEntryProvider.kt` | wire the three `onReport` lambdas; pop the invite after a report |
| Modify the three screenshot files | previews of the new affordances |

All `main` paths are under `apps/android/app/src/main/kotlin/app/pbbls/android/`; tests under `apps/android/app/src/test/kotlin/app/pbbls/android/`; screenshots under `apps/android/app/src/screenshotTest/kotlin/app/pbbls/android/`; resources under `apps/android/app/src/main/res/`.

---

# Part 1: the report screen (branch `feat/839-report-screen-android`)

### Task 1: Models, service, error mapping

**Files:**
- Create: `core/model/Report.kt`
- Create: `core/data/ReportService.kt`
- Modify: `di/ServiceBindings.kt` (add one `@Binds`, same shape as its neighbours)
- Modify: `res/values/strings.xml`, `res/values-fr/strings.xml` (reason labels and the three error strings only; Task 3 adds the rest)
- Test: `test/kotlin/app/pbbls/android/core/data/ReportServiceTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package app.pbbls.android.core.data

import app.pbbls.android.R
import app.pbbls.android.core.model.ReportReason
import app.pbbls.android.core.model.ReportTarget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

class ReportServiceTest {
    @Test
    fun `reason slugs are the server's, in the sheet's order`() {
        assertEquals(
            listOf("harassment", "hate", "impersonation", "sexual", "violence", "self_harm", "illegal", "spam", "other"),
            ReportReason.entries.map { it.slug },
        )
    }

    @Test
    fun `a glyph goes to report_content`() {
        val call = reportCall(ReportTarget.Glyph("g-1"), ReportReason.SPAM, detail = "x", removeAndBlock = true)
        assertEquals("report_content", call.function)
        assertEquals(JsonPrimitive("glyph"), call.params["p_target_kind"])
        assertEquals(JsonPrimitive("g-1"), call.params["p_target_id"])
        assertEquals(JsonPrimitive("spam"), call.params["p_reason"])
        assertEquals(JsonPrimitive("x"), call.params["p_detail"])
        assertFalse(call.params.containsKey("p_remove_and_block"))
    }

    @Test
    fun `a connection goes to report_person and carries the block`() {
        val call = reportCall(ReportTarget.Connection("c-1", "Ana"), ReportReason.HATE, detail = null, removeAndBlock = true)
        assertEquals("report_person", call.function)
        assertEquals(JsonPrimitive("c-1"), call.params["p_connection_id"])
        assertEquals(JsonPrimitive(true), call.params["p_remove_and_block"])
        assertFalse(call.params.containsKey("p_invite_token"))
        assertFalse(call.params.containsKey("p_detail"))
    }

    @Test
    fun `an invite never blocks`() {
        val call = reportCall(ReportTarget.Invite("tok", null), ReportReason.OTHER, detail = null, removeAndBlock = true)
        assertEquals("report_person", call.function)
        assertEquals(JsonPrimitive("tok"), call.params["p_invite_token"])
        assertEquals(JsonPrimitive(false), call.params["p_remove_and_block"])
        assertFalse(call.params.containsKey("p_connection_id"))
    }

    @Test
    fun `the receipt decodes the payload report_person returned on prod, verbatim`() {
        // packages/supabase/fixtures/report-person-receipt.json
        val raw = """{"id":"8ce2b8e9-1a29-456c-86a1-eb0d3b27c10a","status":"open","created_at":"2026-11-12T20:54:43Z"}"""
        val receipt = Json { ignoreUnknownKeys = true }.decodeFromString(ReportReceipt.serializer(), raw)
        assertEquals("8ce2b8e9-1a29-456c-86a1-eb0d3b27c10a", receipt.id)
        assertEquals("open", receipt.status)
        assertEquals(2026, receipt.createdAt.year)
        assertEquals(ZoneOffset.UTC, receipt.createdAt.offset)
    }

    @Test
    fun `the receipt tolerates an offset and fractional seconds`() {
        val raw = """{"id":"a","status":"open","created_at":"2026-11-12T20:54:43.123456+00:00"}"""
        val receipt = Json.decodeFromString(ReportReceipt.serializer(), raw)
        assertEquals(43, receipt.createdAt.second)
    }

    @Test
    fun `server conditions map to the three messages`() {
        assertEquals(R.string.report_error_unavailable, reportErrorMessage(DataError.Conflict("not_found")))
        assertEquals(R.string.report_error_unavailable, reportErrorMessage(DataError.NotFound))
        assertEquals(R.string.report_error_own, reportErrorMessage(DataError.Conflict("cannot_report_own")))
        assertEquals(R.string.report_error_generic, reportErrorMessage(DataError.Conflict("invalid_reason")))
        assertEquals(R.string.report_error_generic, reportErrorMessage(DataError.Network))
        assertEquals(R.string.report_error_generic, reportErrorMessage(DataError.Unauthorized))
    }

    @Test
    fun `only a connection offers the block, and names fall back when blank`() {
        assertTrue(ReportTarget.Connection("c", "Ana").offersBlock)
        assertFalse(ReportTarget.Invite("t", "Ana").offersBlock)
        assertFalse(ReportTarget.Glyph("g").offersBlock)
        assertNull(ReportTarget.Connection("c", "").personName)
        assertEquals("Ana", ReportTarget.Invite("t", "Ana").personName)
    }
}
```

`detail = null` must omit `p_detail` so the RPC default applies, as iOS's encoder omits nil optionals.

- [ ] **Step 2: Run, expect a compile failure** (`./gradlew testDebugUnitTest --tests '*ReportServiceTest*'`).

- [ ] **Step 3: Write `core/model/Report.kt`**

```kotlin
package app.pbbls.android.core.model

import androidx.annotation.StringRes
import app.pbbls.android.R
import kotlinx.serialization.Serializable

/**
 * Why something is reported (#839, mirrors iOS `ReportReason`). [slug] is the
 * `content_reports.reason` CHECK value, so it never changes; declaration order
 * is the order the report screen lists them in.
 */
enum class ReportReason(
    val slug: String,
    @StringRes val label: Int,
) {
    HARASSMENT("harassment", R.string.report_reason_harassment),
    HATE("hate", R.string.report_reason_hate),
    IMPERSONATION("impersonation", R.string.report_reason_impersonation),
    SEXUAL("sexual", R.string.report_reason_sexual),
    VIOLENCE("violence", R.string.report_reason_violence),
    SELF_HARM("self_harm", R.string.report_reason_self_harm),
    ILLEGAL("illegal", R.string.report_reason_illegal),
    SPAM("spam", R.string.report_reason_spam),
    OTHER("other", R.string.report_reason_other),
}

/**
 * What a report is about. A person is addressed by the route the reporter met
 * them by (their connection, or the invite they hold), never by user id: the
 * app never has one (standing rule), and `report_person` resolves it.
 * `@Serializable` because it rides in `PebblesKey.Report`.
 */
@Serializable
sealed interface ReportTarget {
    @Serializable
    data class Glyph(
        val glyphId: String,
    ) : ReportTarget

    @Serializable
    data class Connection(
        val connectionId: String,
        val name: String?,
    ) : ReportTarget

    @Serializable
    data class Invite(
        val token: String,
        val name: String?,
    ) : ReportTarget

    /** Only a connection can be removed and blocked in the same step. */
    val offersBlock: Boolean get() = this is Connection

    val personName: String?
        get() =
            when (this) {
                is Glyph -> null
                is Connection -> name?.takeIf { it.isNotEmpty() }
                is Invite -> name?.takeIf { it.isNotEmpty() }
            }
}
```

- [ ] **Step 4: Write `core/data/ReportService.kt`**

```kotlin
package app.pbbls.android.core.data

import androidx.annotation.StringRes
import app.pbbls.android.R
import app.pbbls.android.core.model.OffsetDateTimeSerializer
import app.pbbls.android.core.model.ReportReason
import app.pbbls.android.core.model.ReportTarget
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** The report seam (#839) — what [app.pbbls.android.features.report.ReportViewModel] is tested against. */
interface ReportServicing {
    suspend fun send(
        target: ReportTarget,
        reason: ReportReason,
        detail: String?,
        removeAndBlock: Boolean,
    ): ReportReceipt
}

/**
 * Files a report (#839, ports iOS `ReportService`). A glyph goes through
 * `report_content`; a person through `report_person`, which resolves them from
 * the connection or invite on the server. Both return scalar `jsonb`, so the
 * receipt uses `decodeAs()`. Filing twice is a success: both RPCs hand back the
 * standing open report.
 */
@Singleton
class ReportService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) : ReportServicing {
        override suspend fun send(
            target: ReportTarget,
            reason: ReportReason,
            detail: String?,
            removeAndBlock: Boolean,
        ): ReportReceipt {
            val call = reportCall(target, reason, detail, removeAndBlock)
            return supabase.client.postgrest
                .rpc(call.function, call.params)
                .decodeAs()
        }
    }

/** Which RPC a report goes through, with its wire params. Pure; JVM-tested. */
internal data class ReportCall(
    val function: String,
    val params: JsonObject,
)

/** A null [detail] is omitted so the RPC's default applies. An invite never blocks. */
internal fun reportCall(
    target: ReportTarget,
    reason: ReportReason,
    detail: String?,
    removeAndBlock: Boolean,
): ReportCall =
    when (target) {
        is ReportTarget.Glyph ->
            ReportCall(
                "report_content",
                buildJsonObject {
                    put("p_target_kind", "glyph")
                    put("p_target_id", target.glyphId)
                    put("p_reason", reason.slug)
                    detail?.let { put("p_detail", it) }
                },
            )

        is ReportTarget.Connection ->
            ReportCall(
                "report_person",
                buildJsonObject {
                    put("p_reason", reason.slug)
                    detail?.let { put("p_detail", it) }
                    put("p_connection_id", target.connectionId)
                    put("p_remove_and_block", removeAndBlock)
                },
            )

        is ReportTarget.Invite ->
            ReportCall(
                "report_person",
                buildJsonObject {
                    put("p_reason", reason.slug)
                    detail?.let { put("p_detail", it) }
                    put("p_invite_token", target.token)
                    put("p_remove_and_block", false)
                },
            )
    }

/** `{id, status, created_at}` — the projection both report RPCs return. */
@Serializable
data class ReportReceipt(
    val id: String,
    val status: String,
    @Serializable(with = OffsetDateTimeSerializer::class)
    @SerialName("created_at") val createdAt: OffsetDateTime,
)

/**
 * A report failure as its inline message (pure; JVM-tested). The same three
 * messages as iOS `ReportError`: every condition is raised as P0001 with the
 * slug as its message, so they arrive as [DataError.Conflict].
 */
@StringRes
fun reportErrorMessage(error: DataError): Int =
    when (error) {
        DataError.NotFound -> R.string.report_error_unavailable
        is DataError.Conflict ->
            when (error.code) {
                "not_found" -> R.string.report_error_unavailable
                "cannot_report_own" -> R.string.report_error_own
                else -> R.string.report_error_generic
            }

        DataError.Network, DataError.Unauthorized, DataError.Quota, is DataError.Unknown ->
            R.string.report_error_generic
    }
```

If `rpc(name, JsonObject)` is not the overload in use, copy the call shape from `ConnectionsService.createInvite`. If `OffsetDateTimeSerializer` lives elsewhere, import it from where it is (`core/model/OffsetDateTimeSerializer.kt`).

- [ ] **Step 5: Bind it** in `di/ServiceBindings.kt`:

```kotlin
    @Binds
    @Singleton
    fun bindReportServicing(impl: ReportService): ReportServicing
```

- [ ] **Step 6: Strings.** Add near the connections strings, one block each file. Escape apostrophes (`\'`).

`values/strings.xml`:
```xml
    <!-- Report (#839) -->
    <string name="report_reason_harassment">Harassment</string>
    <string name="report_reason_hate">Hate</string>
    <string name="report_reason_impersonation">Impersonation</string>
    <string name="report_reason_sexual">Sexual content</string>
    <string name="report_reason_violence">Violence</string>
    <string name="report_reason_self_harm">Self-harm</string>
    <string name="report_reason_illegal">Illegal</string>
    <string name="report_reason_spam">Spam</string>
    <string name="report_reason_other">Other</string>
    <string name="report_error_unavailable">This is no longer available.</string>
    <string name="report_error_own">You can\'t report your own.</string>
    <string name="report_error_generic">Couldn\'t send the report. Try again.</string>
```

`values-fr/strings.xml`:
```xml
    <!-- Report (#839) -->
    <string name="report_reason_harassment">Harcèlement</string>
    <string name="report_reason_hate">Haine</string>
    <string name="report_reason_impersonation">Usurpation d\'identité</string>
    <string name="report_reason_sexual">Contenu sexuel</string>
    <string name="report_reason_violence">Violence</string>
    <string name="report_reason_self_harm">Automutilation</string>
    <string name="report_reason_illegal">Illégal</string>
    <string name="report_reason_spam">Spam</string>
    <string name="report_reason_other">Autre</string>
    <string name="report_error_unavailable">Ce n\'est plus disponible.</string>
    <string name="report_error_own">Tu ne peux pas signaler ce qui est à toi.</string>
    <string name="report_error_generic">Impossible d\'envoyer le signalement. Réessaie.</string>
```

Insert these as text at an anchor (an `Edit`), never by re-serialising the XML.

- [ ] **Step 7: Run the tests, expect PASS.** Then the full gate.

- [ ] **Step 8: Commit** `feat(android): report service, reasons and targets`.

---

### Task 2: `ReportViewModel`

**Files:**
- Create: `features/report/ReportViewModel.kt`
- Test: `test/kotlin/app/pbbls/android/features/report/ReportViewModelTest.kt`

**Design.** The form fields are Compose snapshot state on the ViewModel (`mutableStateOf`), not a `StateFlow`: a `TextField` fed from a flow collected with `collectAsStateWithLifecycle` lags a frame and drops letters typed quickly (found on the Pixel 8 in the Android record-flow stack). The phase is a `StateFlow<ReportPhase>`. The text is clamped to 1,000 **code points** (Postgres `length()`; a family emoji is one grapheme but five code points), matching iOS `clampDetail`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package app.pbbls.android.features.report

import app.pbbls.android.R
import app.pbbls.android.core.data.ReportReceipt
import app.pbbls.android.core.data.ReportServicing
import app.pbbls.android.core.model.ReportReason
import app.pbbls.android.core.model.ReportTarget
import app.pbbls.android.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.OffsetDateTime

private data class Sent(
    val target: ReportTarget,
    val reason: ReportReason,
    val detail: String?,
    val removeAndBlock: Boolean,
)

private class FakeReports : ReportServicing {
    val sent = mutableListOf<Sent>()
    var failure: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun send(
        target: ReportTarget,
        reason: ReportReason,
        detail: String?,
        removeAndBlock: Boolean,
    ): ReportReceipt {
        gate?.await()
        failure?.let { throw it }
        sent += Sent(target, reason, detail, removeAndBlock)
        return ReportReceipt("r-1", "open", OffsetDateTime.parse("2026-11-12T20:54:43Z"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val connection = ReportTarget.Connection("c-1", "Ana")

    private fun model(fake: FakeReports = FakeReports()) = ReportViewModel(fake)

    @Test
    fun `cannot send until a reason is picked`() {
        val vm = model().apply { start(connection) }
        assertFalse(vm.canSend)
        vm.reason = ReportReason.SPAM
        assertTrue(vm.canSend)
    }

    @Test
    fun `remove and block is on by default`() {
        val vm = model().apply { start(connection) }
        assertTrue(vm.removeAndBlock)
    }

    @Test
    fun `sends trimmed detail, null when blank, and the block for a connection`() =
        runTest {
            val fake = FakeReports()
            val vm = model(fake).apply { start(connection) }
            vm.reason = ReportReason.HATE
            vm.onDetailChange("   ")
            vm.submit()
            advanceUntilIdle()
            assertEquals(Sent(connection, ReportReason.HATE, null, true), fake.sent.single())
            assertEquals(ReportPhase.Sent(didBlock = true), vm.phase.value)
        }

    @Test
    fun `an invite never sends the block`() =
        runTest {
            val fake = FakeReports()
            val invite = ReportTarget.Invite("tok", null)
            val vm = model(fake).apply { start(invite) }
            vm.reason = ReportReason.SPAM
            vm.onDetailChange("  rude  ")
            vm.submit()
            advanceUntilIdle()
            assertEquals(Sent(invite, ReportReason.SPAM, "rude", false), fake.sent.single())
            assertEquals(ReportPhase.Sent(didBlock = false), vm.phase.value)
        }

    @Test
    fun `turning the block off sends without it`() =
        runTest {
            val fake = FakeReports()
            val vm = model(fake).apply { start(connection) }
            vm.reason = ReportReason.OTHER
            vm.removeAndBlock = false
            vm.submit()
            advanceUntilIdle()
            assertFalse(fake.sent.single().removeAndBlock)
            assertEquals(ReportPhase.Sent(didBlock = false), vm.phase.value)
        }

    @Test
    fun `sending refuses a second tap and blocks canSend`() =
        runTest {
            val fake = FakeReports().apply { gate = CompletableDeferred() }
            val vm = model(fake).apply { start(connection) }
            vm.reason = ReportReason.SPAM
            vm.submit()
            advanceUntilIdle()
            assertEquals(ReportPhase.Sending, vm.phase.value)
            assertFalse(vm.canSend)
            vm.submit()
            fake.gate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, fake.sent.size)
        }

    @Test
    fun `a failure shows its message and allows a retry`() =
        runTest {
            val fake = FakeReports().apply { failure = java.io.IOException("offline") }
            val vm = model(fake).apply { start(connection) }
            vm.reason = ReportReason.SPAM
            vm.submit()
            advanceUntilIdle()
            assertEquals(ReportPhase.Failed(R.string.report_error_generic), vm.phase.value)
            assertTrue(vm.canSend)
        }

    @Test
    fun `detail is capped at 1000 code points, not characters`() {
        val vm = model().apply { start(connection) }
        val family = "👨‍👩‍👧" // 5 code points
        vm.onDetailChange("a".repeat(998) + family)
        assertEquals(1000, vm.detail.codePointCount(0, vm.detail.length))
        vm.onDetailChange("b".repeat(1200))
        assertEquals(1000, vm.detail.length)
    }

    @Test
    fun `start is idempotent for the same target`() {
        val vm = model().apply { start(connection) }
        vm.reason = ReportReason.SPAM
        vm.start(connection)
        assertEquals(ReportReason.SPAM, vm.reason)
        assertNull(model().target)
    }
}
```

Look at an existing failure test (for example `ConnectionsViewModelTest`) for how a thrown exception becomes a `DataError` in a JVM test: an `IOException` maps to `DataError.Network`, which maps to the generic message.

- [ ] **Step 2: Run, expect a compile failure.**

- [ ] **Step 3: Write `features/report/ReportViewModel.kt`**

```kotlin
package app.pbbls.android.features.report

import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pbbls.android.core.common.runCatchingCancellable
import app.pbbls.android.core.data.ReportServicing
import app.pbbls.android.core.data.reportErrorMessage
import app.pbbls.android.core.data.toDataError
import app.pbbls.android.core.model.ReportReason
import app.pbbls.android.core.model.ReportTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "report"

/** `content_reports.detail`'s CHECK, in code points (Postgres `length()`). */
internal const val REPORT_DETAIL_LIMIT = 1_000

/** Where a report is. Mirrors iOS `ReportSheetModel.Phase`. */
sealed interface ReportPhase {
    data object Editing : ReportPhase

    data object Sending : ReportPhase

    /** [didBlock]: the connection was also removed and blocked. */
    data class Sent(
        val didBlock: Boolean,
    ) : ReportPhase

    data class Failed(
        @StringRes val messageRes: Int,
    ) : ReportPhase
}

/**
 * The report screen's state (#839, ports iOS `ReportSheetModel`). A
 * `PebblesKey.Report` entry, so it is scoped to that entry and a second report
 * gets a fresh instance.
 *
 * The form fields are snapshot state, read synchronously by the text field;
 * the phase is a flow. The send runs under `NonCancellable`: with the block on
 * it severs a connection for both sides, so leaving mid-request must not drop
 * the outcome.
 */
@HiltViewModel
class ReportViewModel
    @Inject
    constructor(
        private val service: ReportServicing,
    ) : ViewModel() {
        var target: ReportTarget? = null
            private set

        var reason by mutableStateOf<ReportReason?>(null)

        var detail by mutableStateOf("")
            private set

        /** Only read for a connection; on by default (design decision 3). */
        var removeAndBlock by mutableStateOf(true)

        private val _phase = MutableStateFlow<ReportPhase>(ReportPhase.Editing)
        val phase: StateFlow<ReportPhase> = _phase.asStateFlow()

        val canSend: Boolean
            get() = reason != null && _phase.value.let { it !is ReportPhase.Sending && it !is ReportPhase.Sent }

        fun start(target: ReportTarget) {
            if (this.target == target) return
            this.target = target
        }

        /** Caps at [REPORT_DETAIL_LIMIT] code points, never splitting a surrogate pair. */
        fun onDetailChange(text: String) {
            detail =
                if (text.codePointCount(0, text.length) <= REPORT_DETAIL_LIMIT) {
                    text
                } else {
                    text.substring(0, text.offsetByCodePoints(0, REPORT_DETAIL_LIMIT))
                }
        }

        fun submit() {
            val target = target ?: return
            val reason = reason ?: return
            if (!canSend) return
            _phase.value = ReportPhase.Sending
            val trimmed = detail.trim().ifEmpty { null }
            val block = target.offersBlock && removeAndBlock
            viewModelScope.launch {
                withContext(NonCancellable) {
                    runCatchingCancellable { service.send(target, reason, trimmed, block) }
                        .fold(
                            onSuccess = { _phase.value = ReportPhase.Sent(didBlock = block) },
                            onFailure = {
                                Log.e(TAG, "report failed", it)
                                _phase.value = ReportPhase.Failed(reportErrorMessage(it.toDataError()))
                            },
                        )
                }
            }
        }
    }
```

`canSend` reads `_phase.value`, which is not snapshot state; the screen derives its button state from the collected `phase` plus `reason`, so recomposition is still driven correctly (see Task 3). If `Log.e` breaks the JVM test, follow what `ConnectionsViewModelTest` does (the project may set `unitTests.isReturnDefaultValues`).

- [ ] **Step 4: Run the tests, expect PASS.** Then the full gate.

- [ ] **Step 5: Commit** `feat(android): report view model`.

---

### Task 3: `ReportScreen`, its strings, its key, its previews

**Files:**
- Create: `features/report/ReportScreen.kt`
- Modify: `navigation/PebblesKeys.kt`, `navigation/PebblesEntryProvider.kt`
- Modify: `res/values/strings.xml`, `res/values-fr/strings.xml`
- Create: `screenshotTest/kotlin/app/pbbls/android/ReportScreenshots.kt`

**What it looks like (Android-native, stock M3, the app's own components):**

- A full page (not a bottom sheet: the glyph detail is already a sheet on phones, and sheets never stack, D5) on `colorScheme.surface`, `safeDrawingPadding()`, `readableWidth()`, like `ConnectionsContent`.
- `PebblesTopBar(title)`: leading `PebblesTopBarTextButton(Cancel)`; trailing `Send` (disabled until `canSend`; while sending, a small `LoadingIndicator` with content description "Sending" in its place). In the sent phase: no leading, trailing `Done`.
- Title: "Report {name}" / "Report this person" for a person, "Report this glyph" for a glyph.
- A scrolling column with `PebblesTheme.spacing` gaps:
  1. `ListGroup(header = "Why are you reporting?")` of nine `SegmentedListItem`s, each `selectable(selected, role = Role.RadioButton)` with a trailing stock `RadioButton(selected, onClick = null)`; the group is a `selectableGroup()`. Copy the `SegmentedListItem` call shape from `ListGroupSwitchItem` in `core/designsystem/ListGroup.kt` (it uses `shapes`/`colors` from `ListGroupItemScope`).
  2. `ListGroupHeader("Add details (optional)")` then a stock `OutlinedTextField(value = vm.detail, onValueChange = vm::onDetailChange, placeholder = "What happened?", minLines = 3, maxLines = 8)`, full width, horizontally inset like the list items.
  3. Connection only: `ListGroup(items = listOf { ListGroupSwitchItem(headline = blockLabel, checked, onCheckedChange, supporting = "Blocking also stops them joining again through your invite link.") })`.
  4. Failed only: the message in `colorScheme.error` (it is orange in this theme; there is no red anywhere).
- Everything `enabled = false` while sending.
- Sent phase: centred column, `PbblsGlyphImage(PbblsGlyph.CHECK_CIRCLE_DOT …)` at hero size (find the size token next to `PbblsGlyphSize`), then "Thanks. We'll look at it." in `titleMedium`. Polite live region on the text so TalkBack announces it.
- Back: `NavigationBackHandler` (never `BackHandler`). While sending, back does nothing; otherwise back = Cancel (or Done once sent).

- [ ] **Step 1: Strings.** Append to the Task 1 block. Names and copy:

| name | EN | FR |
|---|---|---|
| `report_title_glyph` | Report this glyph | Signaler ce glyphe |
| `report_title_person` | Report this person | Signaler cette personne |
| `report_title_named` | Report %1$s | Signaler %1$s |
| `report_reasons_header` | Why are you reporting? | Pourquoi tu signales ? |
| `report_details_header` | Add details (optional) | Ajouter des détails (facultatif) |
| `report_details_placeholder` | What happened? | Que s’est-il passé ? |
| `report_block_named` | Also remove and block %1$s | Retirer et bloquer aussi %1$s |
| `report_block_person` | Also remove and block this person | Retirer et bloquer aussi cette personne |
| `report_block_footer` | Blocking also stops them joining again through your invite link. | Bloquer l\'empêche aussi de revenir par ton lien d\'invitation. |
| `report_send` | Send report | Envoyer le signalement |
| `report_sending` | Sending | Envoi en cours |
| `report_sent` | Thanks. We\'ll look at it. | Merci. On va regarder. |
| `report_action` | Report | Signaler |

French uses a non-breaking space before `?` where the existing FR file does (check its other questions and match). `Cancel` and `Done` reuse `action_cancel` / `action_done`.

- [ ] **Step 2: The key.** In `navigation/PebblesKeys.kt`, next to `AcceptInvite`:

```kotlin
    /** Report a person or a glyph (#839). A modal: neither a tab nor a bar key. */
    @Serializable
    data class Report(
        val target: ReportTarget,
    ) : PebblesKey
```

- [ ] **Step 3: The screen.** `features/report/ReportScreen.kt`:

```kotlin
/**
 * Report a person or a glyph (#839, ports iOS `ReportSheet`): one reason,
 * optional details and, for a connection, remove-and-block in the same step.
 * [onFinished] fires on Done after a sent report, with whether a block
 * happened; [onCancel] on Cancel or back before sending.
 */
@Composable
fun ReportScreen(
    target: ReportTarget,
    onCancel: () -> Unit,
    onFinished: (blocked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    LaunchedEffect(target) { viewModel.start(target) }
    val phase by viewModel.phase.collectAsStateWithLifecycle()

    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(state = backState) {
        when (val p = phase) {
            ReportPhase.Sending -> Unit
            is ReportPhase.Sent -> onFinished(p.didBlock)
            else -> onCancel()
        }
    }

    ReportContent(
        target = target,
        phase = phase,
        reason = viewModel.reason,
        detail = viewModel.detail,
        removeAndBlock = viewModel.removeAndBlock,
        onReason = { viewModel.reason = it },
        onDetail = viewModel::onDetailChange,
        onRemoveAndBlock = { viewModel.removeAndBlock = it },
        onSend = viewModel::submit,
        onCancel = onCancel,
        onDone = { (phase as? ReportPhase.Sent)?.let { onFinished(it.didBlock) } },
        modifier = modifier,
    )
}
```

Copy the `NavigationBackHandler` imports and `isBackEnabled` handling from `AcceptInviteScreen.kt`; if the API takes an `isBackEnabled`, pass `phase != ReportPhase.Sending` rather than swallowing the gesture. `ReportContent` is a stateless `@Composable fun ReportContent(target, phase, reason, detail, removeAndBlock, on…, modifier)` drawing the layout above. Its Send button's enabled state is `reason != null && phase !is ReportPhase.Sending && phase !is ReportPhase.Sent`, computed from its parameters (not from `viewModel.canSend`). Titles and block label: a small `@Composable` helper in this file that switches on `target` and `target.personName`.

- [ ] **Step 4: The entry.** In `navigation/PebblesEntryProvider.kt`, next to the `AcceptInvite` entry:

```kotlin
    entry<PebblesKey.Report>(metadata = NavTransitions.forKey(PebblesKey.Report(ReportTarget.Glyph("")))) { key ->
        ReportScreen(
            target = key.target,
            onCancel = navigator::goBack,
            // Part 2 pops the invite too; for now every report just closes.
            onFinished = { navigator.goBack() },
        )
    }
```

Check `NavTransitions.forKey` treats a non-tab, non-bar key as a modal (it should; read it). Check whether the entry needs a `clazzContentKey`: the default is `toString()`, which is fine for a target this small.

- [ ] **Step 5: Previews.** `ReportScreenshots.kt`, following `ConnectionsScreenshots.kt` (its annotations, theme wrapper and `PreviewVariants`). Render `ReportContent` for: a connection named "Ana", editing, reason Harassment, block on; the same in French; an invite with no name, failed (`report_error_generic`); a glyph, editing, nothing picked; sent. Load the `android-screenshot-gate` skill for how new previews are added and baselined; do not generate references locally.

- [ ] **Step 6: Gate**, plus `./gradlew lint` (this is the end of Part 1). Fix every new lint warning.

- [ ] **Step 7: Commit** `feat(android): report screen and its route`.

### Task 4 (orchestrator): open Part 1

Push the branch, open the PR with `gh stack`, labels `feat ui legal android`, milestone M68, `no-lab-note`, `rebaseline-screenshots`. Body: `Part of #839` (Part 2 resolves it), `Data safety: no change`.

---

# Part 2: entry points (branch `feat/839-report-entries-android`)

### Task 5: Report from the Connections list

**Files:**
- Modify: `features/connections/ConnectionsScreen.kt`, `features/connections/ConnectionsViewModel.kt`
- Modify: `navigation/PebblesEntryProvider.kt` (the `Connections` entry)
- Modify: `screenshotTest/kotlin/app/pbbls/android/ConnectionsScreenshots.kt`
- Test: `test/kotlin/app/pbbls/android/features/connections/ConnectionsViewModelTest.kt`

**Behaviour.** iOS puts "Report" above "Remove" in the row's context menu. Android's native equivalent is a row overflow: the row's "Remove" text button becomes a stock `IconButton` (a vertical-more glyph from `PbblsGlyph`; content description "More options for {name}", add the string) opening a `DropdownMenu` with "Report" (`PbblsGlyph.CIRCLE_DOT_EXCLAMATION` leading icon) then "Remove". Copy the `DropdownMenu` shape from `features/profile/SoulsListScreen.kt`. "Remove" opens the existing `RemoveConnectionDialog`, unchanged.

"Report" calls a new `onReport: (Connection) -> Unit` parameter on `ConnectionsScreen`, which the entry fills with `navigator.navigate(PebblesKey.Report(ReportTarget.Connection(row.connectionId, row.peer.displayName)))`.

After a report with the block on, the server has already removed the connection. The list must drop the row: add a resume refresh to `ConnectionsViewModel` (a quiet `fetch()` that keeps the list up, like the souls and collections lists do; find their `onResume`/`refresh` and copy it), triggered from `ConnectionsScreen` with `LifecycleResumeEffect` or the pattern those screens use. Rewrite the KDoc paragraph that says this screen has no resume refresh: it now has a child to come back from.

- [ ] **Step 1: Failing test** in `ConnectionsViewModelTest`: after the fake's list changes (a row removed server-side), `refresh()` publishes the shorter list without passing through `Loading`.
- [ ] **Step 2: Run, expect FAIL.**
- [ ] **Step 3: Implement** the refresh, the overflow menu and the `onReport` lambda; keep `ConnectionsContent` stateless (the menu's expanded state is local UI state in the row, which is fine).
- [ ] **Step 4: Update `ConnectionsScreenshots.kt`** so the content renders with the new parameters; add one preview with a row's menu expanded if the existing harness can render a `DropdownMenu` (popups often do not render in layoutlib: if it renders blank, drop that preview and say so).
- [ ] **Step 5: Gate. Commit** `feat(android): report a connection from its row menu`.

### Task 6: Report from the glyph drawer

**Files:**
- Modify: `features/glyph/store/GlyphDetailDrawer.kt`
- Modify: `navigation/PebblesEntryProvider.kt` (the `GlyphDetail` entry)
- Modify: the screenshot file that renders `GlyphDetailDrawerContent` (`grep -l GlyphDetailDrawerContent app/src/screenshotTest -r`)

**Behaviour.** iOS shows a quiet "Report this glyph" footnote button at the bottom of the drawer when `item.price > 0 && !isOwned` (a market glyph you don't own; the server's `cannot_report_own` covers the rest). Android: a stock `TextButton` in `onSurfaceVariant`, `labelLarge`, at the bottom of `GlyphDetailDrawerContent`, shown under the same condition, reading `report_title_glyph`.

Thread an optional `onReport: (() -> Unit)? = null` through `GlyphDetailScreen` → `GlyphSwapPanel` → `GlyphDetailDrawerContent`; the button shows only when it is non-null **and** the condition holds. `GlyphSwapPanel` is also embedded in the tabbed glyph picker sheet (a `ModalBottomSheet`; a pushed entry would land under it), which passes nothing, so the picker shows no Report. iOS has the same split (only the market drawer reports).

The entry: `GlyphDetailScreen(item = key.item, onReport = { navigator.navigate(PebblesKey.Report(ReportTarget.Glyph(key.item.id))) })`.

**Risk to check on the device:** on phones the glyph detail is a bottom-sheet scene (`navigation/BottomSheetSceneStrategy.kt`). Read it: pushing `Report` on top must close or cover the sheet and render the report full-page, and Back from the report must return to the open drawer. If the strategy would render `Report` inside the sheet, make the strategy treat only `GlyphDetail` as a sheet (it may already) and report what you found.

- [ ] **Step 1: Implement**, with a preview of the drawer showing the Report button (priced, not owned) and one where it is absent (owned).
- [ ] **Step 2: Gate. Commit** `feat(android): report a market glyph from its drawer`.

### Task 7: Report from the invite screen

**Files:**
- Modify: `features/connections/AcceptInviteScreen.kt`
- Modify: `navigation/PebblesEntryProvider.kt` (the `AcceptInvite` and `Report` entries)
- Modify: the screenshot file that renders `AcceptInviteContent`

**Behaviour.** iOS: a quiet "Report" text button under Accept, only for a valid preview, disabled while accepting. After a sent report the invite sheet closes without accepting and the parked token is cleared.

Android: in `ConsentPrompt`, under the Accept `TextButton`, a second `TextButton` reading `report_action` in `onSurfaceVariant`, `enabled = !state.isAccepting`, calling a new `onReport: (inviterName: String?) -> Unit` threaded from `AcceptInviteScreen` → `AcceptInviteContent` → `ConsentPrompt`. The unusable-invite and accepted states show no Report.

The parked token is already cleared: `RootScreen` calls `viewModel.onInviteConsumed()` right after navigating to `AcceptInvite`. Confirm by reading `RootScreen.kt` around the `pendingInvite` `LaunchedEffect`; nothing to add if so.

Wiring in `PebblesEntryProvider.kt`:
- `AcceptInvite` entry: `onReport = { name -> navigator.navigate(PebblesKey.Report(ReportTarget.Invite(key.token, name))) }`.
- `Report` entry `onFinished`: pop the report; if `key.target is ReportTarget.Invite`, pop the `AcceptInvite` beneath it too. Read `navigation/Navigator.kt` `goBack()` to be sure two calls pop both entries of the current stack and never switch tabs or exit; if it is not safe, add a small `Navigator.popWhile`/`goBack(count)` with a test next to the existing navigator tests.

- [ ] **Step 1: Implement**, with a preview of the valid prompt showing Report.
- [ ] **Step 2: Gate**, plus `./gradlew lint` (end of Part 2).
- [ ] **Step 3: Commit** `feat(android): report an inviter from the invite screen`.

### Task 8 (orchestrator): device pass, Arkaik, PRs

On the maintainer's Pixel 8 (`ANDROID_SERIAL=39141FDJH006H4`) with throwaway accounts the maintainer signs in (never their real account for writes):

1. Connection: two throwaways connected. Report with block on: row leaves the list, server shows the report and a `connection_blocks` row. Report again on another connection with block off: row stays.
2. Glyph: a priced, unowned market glyph shows Report; an owned one does not. Back from the report returns to the drawer.
3. Invite: open a throwaway's invite link; Report; Done returns to where the user was, no connection made.
4. Duplicate: report the same person twice; both land on "Thanks."
5. French: one screen in FR.
6. TalkBack: the reason list reads as radio buttons, the block row as a switch.

Screenshots of the three entry points onto the three Arkaik acceptances. Move their Android status to `development` at start (done by the orchestrator before Task 1). Part 2 PR: `Resolves #839`, labels `feat ui legal android`, M68, `no-lab-note`, `rebaseline-screenshots`, `Data safety: no change`.

## Binding notes learned while building

- **Hilt test graph.** A new `…Servicing` binding needs a provider in `test/.../testing/FakeServicesModule.kt` too, or `hiltJavaCompileDebugUnitTest` fails for the whole suite, not just the new tests.
- **Lint `warningsAsErrors`.** A string added before its first use fails `UnusedResources`, so `report_action` moved to the part that uses it.
- **Back while sending.** Turning `NavigationBackHandler` off hands the gesture to `NavDisplay`, which pops the entry mid-send. Keep the handler on and swallow the gesture.
- **Finishing a report is idempotent.** Done and back can both fire while the entry composes its exit. `Navigator.closeReport(key)` pops only while the report is on top, and pops the invite beneath only when its token matches.
- **Resume refresh vs. an optimistic removal.** A resume reload during an in-flight `remove_connection` brought the row back. Skip the reload while removals are pending.
- **Found only on the Pixel 8:**
  - disabled top-bar buttons never dim, app-wide (#1634); the report screen works around it locally;
  - selected `SegmentedListItem` rows turn green in this theme, so the reason rows keep their container and show the radio only;
  - Remove had no leading icon next to Report's;
  - a recreated activity reopens a dismissed invite link (#1633, which predates this work).
- **Device pass mechanics.**
  - `adb shell input text` drops text typed before the field has focus. Tap, wait, then type.
  - On the consent gate, a tap on a label hits its document link; tap the checkbox at the row's left edge instead.
  - Per-app French is `cmd locale set-app-locales app.pbbls.android --locales fr-FR` (and `""` to reset).
  - A reporter cannot read their own `content_reports` rows (RLS), so the read-back proves the blocks and connections, and the app's Thanks state proves the inserts.
