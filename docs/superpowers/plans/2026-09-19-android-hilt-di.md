# Android Hilt DI Implementation Plan (#848)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the 20-service `PebblesApp` locator and the hand-wired `CompositionLocalProvider` graph with Hilt, inject dispatchers, and land the first fakeable service seams so a JVM test can construct a service without Supabase secrets.

**Architecture:** Three stacked PRs. Part 1 moves graph construction into Hilt modules (`SupabaseClient` becomes a `@Provides`, which is the seam everything else depends on) and leaves one deliberate temporary `ServiceGraph` holder feeding the existing CompositionLocals until #849 deletes them. Part 2 replaces the nine `Dispatchers.*` literals with qualifier-injected dispatchers, moving the five that live inside composables behind two small injected collaborators. Part 3 extracts five `…Servicing` interfaces, binds them `@Binds`-style, and adds fakes plus a `PebblesTestHarness` under `app/src/test`.

**Tech Stack:** Kotlin 2.4.20, AGP 9.4.1 (built-in Kotlin), Hilt 2.60.1, KSP 2.3.12, `androidx.hilt:hilt-navigation-compose` 1.4.0, Jetpack Compose, JUnit4 + `kotlinx-coroutines-test`.

**Spec:** `docs/superpowers/specs/2026-09-19-android-hilt-di-design.md`

---

## Before you start

**Read these first:** `apps/android/CLAUDE.md` (the whole file), and §2–§5 of the spec above.

**Every Gradle command in this plan needs the SDK exported.** Without it `scripts/gradle-if-sdk.sh` silently no-ops and a green result means nothing:

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
```

Run every `./gradlew` command from `apps/android/`.

**A note on TDD in this plan.** Parts 1 and 2 are a dependency-wiring refactor: the behaviour is unchanged by construction, and the real gates are the compiler, the existing 84-file unit suite, Android Lint, and the 162-reference screenshot suite. Writing "failing tests" for annotation plumbing would be theatre, so those parts run **verification-first** instead — the check is written and run *before* the change, so you see it go from red to green. Part 3 is genuine TDD: its test is the acceptance criterion.

**Branches are a `gh stack`.** See the `gh-stack` skill. Part 1 branches from `main`, Part 2 from Part 1, Part 3 from Part 2. Part 1's branch `feat/848-hilt-service-graph` already exists and holds the design doc commit.

---

# PART 1 — The graph moves to Hilt

**Branch:** `feat/848-hilt-service-graph` (exists, from `main`)

### Task 1.1: Add KSP and Hilt to the build

**Files:**
- Modify: `apps/android/gradle/libs.versions.toml`
- Modify: `apps/android/build.gradle.kts:24-30`
- Modify: `apps/android/app/build.gradle.kts:4-11`, dependencies block

- [ ] **Step 1: Confirm the branch and a green starting point**

```bash
cd apps/android
git branch --show-current          # expect: feat/848-hilt-service-graph
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If not, stop — you are not starting from a green tree.

- [ ] **Step 2: Add the versions to the catalog**

In `gradle/libs.versions.toml`, append to `[versions]` (after the `splashscreen` entry):

```toml
# DI (#848, supersedes M38 D4). KSP has decoupled from the Kotlin version line —
# it is versioned independently now, so this does NOT track `kotlin` above.
# Verified against AGP 9.4.1's built-in Kotlin, where no KGP is applied by the
# module: `:app:assembleDebug` runs hiltCollectClasses/hiltAggregateDeps/hiltJavaCompile green.
ksp = "2.3.12"
hilt = "2.60.1"
hiltNavigationCompose = "1.4.0"
```

Append to `[libraries]`:

```toml
# Hilt (#848). hilt-navigation-compose supplies hiltViewModel(), which #849 uses.
hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
```

Append to `[plugins]`:

```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

- [ ] **Step 3: Declare the plugins at the root**

In `apps/android/build.gradle.kts`, inside the `plugins { }` block, after `alias(libs.plugins.screenshot) apply false`:

```kotlin
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
```

- [ ] **Step 4: Apply them in `:app` and add the dependencies**

In `apps/android/app/build.gradle.kts`, inside `plugins { }`, after `alias(libs.plugins.screenshot)`:

```kotlin
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
```

In the `dependencies { }` block, immediately before `testImplementation(libs.junit)`:

```kotlin
    // Hilt (#848). The library's own consumer ProGuard rules cover its
    // reflection, so proguard-rules.pro stays at two rules — but R8 can strip
    // silently, so Part 1 smoke-tests a minified build by hand before merge.
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

```

- [ ] **Step 5: Verify the build still passes with the plugins applied but nothing annotated**

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. KSP runs and finds nothing to process; that is correct at this point.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml build.gradle.kts app/build.gradle.kts
git commit -m "$(cat <<'EOF'
chore(android): add KSP and Hilt to the catalog (#848)

Plugins applied, nothing annotated yet. KSP 2.3.12 is versioned independently
of Kotlin now and works under AGP 9's built-in Kotlin, where the module applies
no KGP of its own.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 1.2: Move `createSupabaseClient` into a Hilt module

This is the seam the whole issue rests on: after this task `AppEnvironment` is read in exactly one place, and it is a place no JVM test constructs.

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/di/SupabaseModule.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/services/SupabaseService.kt:45-77`

- [ ] **Step 1: Create the module**

Create `apps/android/app/src/main/kotlin/app/pbbls/android/di/SupabaseModule.kt`:

```kotlin
package app.pbbls.android.di

import app.pbbls.android.AppEnvironment
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

/**
 * The one place `AppEnvironment` is read (#848, supersedes D4).
 *
 * Moving the client out of [app.pbbls.android.services.SupabaseService]'s
 * initializer is what makes the services constructible in a JVM test: nothing
 * reaches `BuildConfig` unless this module is instantiated, and a test never
 * instantiates it. `AppEnvironment` still throws with setup instructions when a
 * secret is blank — a setup bug that fails loud at first launch, never at build
 * (D8). The initializer performs no network I/O, so building it on the main
 * thread during launch stays safe.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient =
        createSupabaseClient(
            supabaseUrl = AppEnvironment.supabaseUrl,
            supabaseKey = AppEnvironment.supabaseAnonKey,
        ) {
            // Google hosted OAuth returns via the pebbles://auth-callback deep
            // link; PKCE + this scheme/host are the D15/D16 contract. Shared
            // with iOS, so the dashboard allowlist is unchanged.
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = "pebbles"
                host = "auth-callback"
            }
            install(Postgrest)
            // Storage signs the private pebbles-media snap URLs (sub-project D).
            install(Storage)
            // Functions registers the compose-pebble / compose-pebble-update
            // edge-function surface (M39 sub-project A). PebbleWriteService posts
            // via raw Ktor to read the 5xx soft-success body (D2), but the plugin
            // is installed so the standard functions surface is available.
            install(Functions)
        }
}
```

- [ ] **Step 2: Make `SupabaseService` take the client**

In `services/SupabaseService.kt`, replace the class declaration and the whole `val client: SupabaseClient = createSupabaseClient(…) { … }` block (lines 45–77) with:

```kotlin
@Singleton
class SupabaseService
    @Inject
    constructor(
        val client: SupabaseClient,
    ) {
```

Note the indentation: ktlint's stock ruleset indents the body of a class with an annotated constructor. Do not fight it — run `./gradlew ktlintFormat` and accept its output.

Remove these imports, now unused:

```kotlin
import app.pbbls.android.AppEnvironment
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
```

Add:

```kotlin
import javax.inject.Inject
import javax.inject.Singleton
```

Keep `import io.github.jan.supabase.SupabaseClient`, and keep every existing member (`session`, `isInitializing`, `didAttemptNamePatch`, `scope`, `start`, `signIn`, `signUp`, `signInWithGoogle`, `signOut`, `nameFromUserMetadata`, `patchDisplayNameIfDefault`, `companion object`) exactly as they are. The `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` stays for now — Part 2 owns it.

Update the class KDoc's last paragraph, which now describes the module's job rather than this class's:

```kotlin
 * The client is built by [app.pbbls.android.di.SupabaseModule] and injected, so
 * this class reaches no `BuildConfig` and a JVM test can construct it.
```

- [ ] **Step 3: Verify it compiles and the existing suite still passes**

```bash
./gradlew ktlintFormat :app:compileDebugKotlin :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`. `ConsentMetadataTest` exercises `SupabaseService.consentMetadata` (a companion function) and is unaffected.

At this point `PebblesApp.onCreate` still calls `SupabaseService()` with no argument and will NOT compile. That is expected — Task 1.5 fixes it. If you want a compiling tree at every step instead, do Tasks 1.2–1.5 as one unit and commit once at the end of 1.5.

- [ ] **Step 4: Commit (with Task 1.5, once the tree compiles)**

Do not commit a non-compiling tree. Carry this change into the Task 1.5 commit.

---

### Task 1.3: Annotate the 16 constructor-injectable services

**Files (all under `apps/android/app/src/main/kotlin/app/pbbls/android/`):**
- Modify: `services/CollectionsService.kt:22`, `services/ConnectionsService.kt:24`, `services/EmotionPaletteService.kt:20`, `services/PathService.kt:16`, `services/PathStatsService.kt:31`, `services/PebbleDetailService.kt:15`, `services/PebbleDraftsService.kt:37`, `services/PebbleWriteService.kt:60`, `services/ProfileService.kt:27`, `services/ReferenceDataService.kt:27`, `services/SoulsService.kt:22`
- Modify: `features/glyph/services/GlyphService.kt:26`, `features/glyph/services/GlyphMarketService.kt:25`, `features/lab/services/LogsService.kt:27`
- Modify: `features/karma/AchievementNotificationService.kt:36`

(`SupabaseService` was done in Task 1.2 — that is the sixteenth.)

- [ ] **Step 1: Apply the uniform edit to the 14 `(supabase: SupabaseService)` services**

Every one of these fourteen has the identical shape. For each file, change:

```kotlin
class PathService(
    private val supabase: SupabaseService,
) {
```

to:

```kotlin
@Singleton
class PathService
    @Inject
    constructor(
        private val supabase: SupabaseService,
    ) {
```

and add to the imports:

```kotlin
import javax.inject.Inject
import javax.inject.Singleton
```

The fourteen: `CollectionsService`, `ConnectionsService`, `EmotionPaletteService`, `PathService`, `PathStatsService`, `PebbleDetailService`, `PebbleDraftsService`, `PebbleWriteService`, `ProfileService`, `ReferenceDataService`, `SoulsService`, `GlyphService`, `GlyphMarketService`, `LogsService`.

**Do not touch** `PebbleSnapRepository` — it also takes `(supabase: SupabaseService)` but is constructed per-use inside composables, not a graph singleton. Part 3 revisits it; leaving it alone here keeps the diff honest.

Leave every body, companion object and trailing `val LocalXService = staticCompositionLocalOf<…>` untouched.

- [ ] **Step 2: Annotate the parameterless service**

In `features/karma/AchievementNotificationService.kt`, change:

```kotlin
class AchievementNotificationService {
```

to:

```kotlin
@Singleton
class AchievementNotificationService
    @Inject
    constructor() {
```

and add the same two imports.

- [ ] **Step 3: Verify the annotation count**

```bash
grep -rc "@Inject" app/src/main/kotlin/app/pbbls/android/services app/src/main/kotlin/app/pbbls/android/features/glyph/services app/src/main/kotlin/app/pbbls/android/features/karma app/src/main/kotlin/app/pbbls/android/features/lab/services | grep -v ":0"
```

Expected: 16 files listed (the 14, plus `SupabaseService` from Task 1.2, plus `AchievementNotificationService`).

- [ ] **Step 4: Format**

```bash
./gradlew ktlintFormat
```

Carry into the Task 1.5 commit.

---

### Task 1.4: `ServiceModule` for the four that need a provider

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/di/ServiceModule.kt`

- [ ] **Step 1: Create the module**

```kotlin
package app.pbbls.android.di

import android.content.Context
import app.pbbls.android.features.karma.AchievementNotificationService
import app.pbbls.android.features.karma.KarmaNotificationService
import app.pbbls.android.services.AchievementsService
import app.pbbls.android.services.ComposerSnapshotStore
import app.pbbls.android.services.SnapURLCache
import app.pbbls.android.services.SupabaseService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The four graph singletons Dagger cannot infer a constructor for (#848).
 * Everything else is `@Singleton class X @Inject constructor(…)`; if you are
 * adding a service, prefer that and do not grow this file.
 *
 * - [SnapURLCache] has an `internal` primary constructor plus a secondary one.
 * - [ComposerSnapshotStore] needs the application [Context].
 * - [KarmaNotificationService] and [AchievementsService] take a `scope` with a
 *   Kotlin default, and Dagger ignores defaults. Both stop needing a provider
 *   in #848 Part 2, once `@ApplicationScope` is a real binding — delete them
 *   from here then rather than leaving a provider that only restates the
 *   constructor.
 */
@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {
    @Provides
    @Singleton
    fun provideSnapURLCache(supabase: SupabaseService): SnapURLCache = SnapURLCache(supabase)

    @Provides
    @Singleton
    fun provideComposerSnapshotStore(
        @ApplicationContext context: Context,
    ): ComposerSnapshotStore = ComposerSnapshotStore(context)

    @Provides
    @Singleton
    fun provideKarmaNotificationService(): KarmaNotificationService = KarmaNotificationService()

    @Provides
    @Singleton
    fun provideAchievementsService(
        supabase: SupabaseService,
        notify: AchievementNotificationService,
    ): AchievementsService = AchievementsService(supabase, notify)
}
```

Carry into the Task 1.5 commit.

---

### Task 1.5: `ServiceGraph`, `PebblesApp`, `MainActivity`

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/di/ServiceGraph.kt`
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/PebblesApp.kt` (whole file)
- Modify: `apps/android/app/src/main/kotlin/app/pbbls/android/MainActivity.kt:47-49, 87-125`

- [ ] **Step 1: Create the temporary bridge**

```kotlin
package app.pbbls.android.di

import app.pbbls.android.features.glyph.services.GlyphMarketService
import app.pbbls.android.features.glyph.services.GlyphService
import app.pbbls.android.features.karma.AchievementNotificationService
import app.pbbls.android.features.karma.KarmaNotificationService
import app.pbbls.android.features.lab.services.LogsService
import app.pbbls.android.services.AchievementsService
import app.pbbls.android.services.CollectionsService
import app.pbbls.android.services.ComposerSnapshotStore
import app.pbbls.android.services.ConnectionsService
import app.pbbls.android.services.EmotionPaletteService
import app.pbbls.android.services.PathService
import app.pbbls.android.services.PathStatsService
import app.pbbls.android.services.PebbleDetailService
import app.pbbls.android.services.PebbleDraftsService
import app.pbbls.android.services.PebbleWriteService
import app.pbbls.android.services.ProfileService
import app.pbbls.android.services.ReferenceDataService
import app.pbbls.android.services.SnapURLCache
import app.pbbls.android.services.SoulsService
import app.pbbls.android.services.SupabaseService
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TEMPORARY. Deleted by #849.
 *
 * This is, honestly, still a service locator — and it is kept on purpose with
 * its eyes open. The screens still read `Local…Service.current` (87 call sites)
 * until #849 gives each one a ViewModel, so `MainActivity` still has to fill a
 * 20-entry `CompositionLocalProvider` from somewhere. The alternative is 20
 * `@Inject lateinit var` fields on the activity, which costs the same for the
 * same lifespan and leaves #849 unpicking twenty fields instead of deleting one
 * file.
 *
 * **Do not copy this pattern.** New code takes its dependencies by constructor
 * (`@Inject`) or, once #849 lands, through `hiltViewModel()`. Nothing should
 * ever inject `ServiceGraph` except `MainActivity`.
 */
@Singleton
class ServiceGraph
    @Inject
    constructor(
        val supabase: SupabaseService,
        val palettes: EmotionPaletteService,
        val pathService: PathService,
        val pathStats: PathStatsService,
        val profileService: ProfileService,
        val pebbleDetailService: PebbleDetailService,
        val snapUrls: SnapURLCache,
        val referenceData: ReferenceDataService,
        val pebbleWrite: PebbleWriteService,
        val soulsService: SoulsService,
        val collectionsService: CollectionsService,
        val draftsService: PebbleDraftsService,
        val connectionsService: ConnectionsService,
        val composerSnapshots: ComposerSnapshotStore,
        val glyphService: GlyphService,
        val glyphMarket: GlyphMarketService,
        val logsService: LogsService,
        val karma: KarmaNotificationService,
        val achievementNotify: AchievementNotificationService,
        val achievements: AchievementsService,
    )
```

- [ ] **Step 2: Gut `PebblesApp`**

Replace the whole of `apps/android/app/src/main/kotlin/app/pbbls/android/PebblesApp.kt` with:

```kotlin
package app.pbbls.android

import android.app.Application
import app.rive.runtime.kotlin.core.Rive
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point — the `PebblesApp.swift` analog. Initializes Rive (D14)
 * and roots the Hilt graph.
 *
 * It no longer constructs services. Since #848 (which supersedes M38 D4) the
 * graph is Hilt's: `di/SupabaseModule` builds the client from `AppEnvironment`,
 * `di/ServiceModule` provides the four singletons Dagger cannot infer, and every
 * other service is `@Singleton class X @Inject constructor(…)`. Adding a service
 * is now one annotation on that service, not an edit in three files.
 */
@HiltAndroidApp
class PebblesApp :
    Application(),
    SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Rive.init(this)
    }

    /**
     * Coil's singleton loader (used by AsyncImage) — the OkHttp network
     * fetcher is registered explicitly rather than via service-loader
     * autodiscovery so a minification/config change can't silently drop
     * network loading. Signed URLs carry their token in the query string, so
     * no auth headers are needed.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
}
```

- [ ] **Step 3: Rewire `MainActivity`**

In `MainActivity.kt`:

Add imports:

```kotlin
import app.pbbls.android.di.ServiceGraph
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
```

Annotate the class and replace the two `app` / `supabase` accessors (lines 47–49):

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /**
     * Injected in `super.onCreate`, which runs before the first read below.
     * [ServiceGraph] is the temporary bridge to the CompositionLocals — #849
     * deletes it and this field with it.
     */
    @Inject
    lateinit var graph: ServiceGraph

    private val supabase get() = graph.supabase
```

In the `CompositionLocalProvider` block, replace every `app.` prefix with `graph.`:

```kotlin
                CompositionLocalProvider(
                    LocalSupabaseService provides supabase,
                    LocalEmotionPaletteService provides graph.palettes,
                    LocalPathService provides graph.pathService,
                    LocalPathStatsService provides graph.pathStats,
                    LocalProfileService provides graph.profileService,
                    LocalSnapURLCache provides graph.snapUrls,
                    LocalReferenceDataService provides graph.referenceData,
                    LocalPebbleWriteService provides graph.pebbleWrite,
                    LocalPebbleDetailService provides graph.pebbleDetailService,
                    LocalSoulsService provides graph.soulsService,
                    LocalCollectionsService provides graph.collectionsService,
                    LocalPebbleDraftsService provides graph.draftsService,
                    LocalConnectionsService provides graph.connectionsService,
                    LocalComposerSnapshotStore provides graph.composerSnapshots,
                    LocalGlyphService provides graph.glyphService,
                    LocalGlyphMarketService provides graph.glyphMarket,
                    LocalLogsService provides graph.logsService,
                    LocalKarmaNotificationService provides graph.karma,
                    LocalAchievementNotificationService provides graph.achievementNotify,
                    LocalAchievementsService provides graph.achievements,
                ) {
                    RootScreen()
                }
```

In `captureInviteToken`, change `app.connectionsService.pendingInviteToken = token` to:

```kotlin
        graph.connectionsService.pendingInviteToken = token
```

Update the class KDoc's first sentence to say the graph comes from Hilt rather than from `PebblesApp`.

- [ ] **Step 4: Prove the acceptance criterion, then build**

```bash
grep -rn "lateinit var\|application as PebblesApp" app/src/main/kotlin/app/pbbls/android/PebblesApp.kt app/src/main/kotlin/app/pbbls/android/MainActivity.kt
```

Expected: exactly one line — `MainActivity`'s `@Inject lateinit var graph: ServiceGraph`. No `application as PebblesApp` anywhere. (The issue's criterion is "no `lateinit var` **service** on `PebblesApp`"; `PebblesApp.kt` must return nothing at all.)

```bash
./gradlew ktlintFormat :app:assembleDebug :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`. If Dagger reports a missing binding, the error names the type and the injection site — add the `@Singleton`/`@Inject` pair you missed in Task 1.3.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/app/pbbls/android
git commit -m "$(cat <<'EOF'
feat(android): Hilt owns the service graph (#848)

createSupabaseClient moves into di/SupabaseModule, so AppEnvironment is read in
exactly one place and nothing else in the app can reach BuildConfig. Sixteen
services become @Singleton @Inject constructor; four that Dagger cannot infer
get a provider in di/ServiceModule.

PebblesApp keeps only Rive.init and the Coil loader — the twenty lateinit vars
and the whole onCreate graph are gone, and so is `application as PebblesApp`.
MainActivity is @AndroidEntryPoint and fills the (still present)
CompositionLocalProvider from one injected ServiceGraph, which #849 deletes.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 1.6: Decision log and the CLAUDE.md bullet

Leaving `apps/android/CLAUDE.md` saying "No Hilt, no Koin" while Hilt is in the build would be a lie in the tree. #858 owns the full rewrite; this is the minimum that keeps the doc true.

**Files:**
- Modify: `docs/decisions/log.md` (append only)
- Modify: `apps/android/CLAUDE.md` (the "State:" bullet under **Kotlin conventions**)

- [ ] **Step 1: Append the decision entry**

Read the last few entries of `docs/decisions/log.md` first and match their heading format exactly. Append (never edit a prior entry — the log is supersede-don't-edit):

```markdown
## 2026-09-19 — Hilt is the Android DI container (supersedes M38 D4)

**Status:** accepted. Supersedes **D4** ("plain service classes, manual
injection, CompositionLocals. No DI framework") from
`docs/superpowers/specs/2026-07-10-android-bootstrap-design.md`.

D4 was written for four services and it was right for four services. There are
now twenty: `PebblesApp` held 20 `lateinit var`s, `MainActivity` re-listed all
twenty into one `CompositionLocalProvider`, screens read them at 87 call sites,
and adding a service touched three files. D4's own justification — "six
hand-wired singletons don't need annotation processing and opaque errors" —
does not survive that growth.

The bill was testability. Every service's root, `SupabaseService`, built a live
supabase-kt client from `AppEnvironment` in its initializer, which throws on a
blank `BuildConfig.SUPABASE_URL` (the fork-PR case). A screen test would have
had to provide twenty locals backed by classes that need real secrets, so no
screen's load, error or save path was tested at all.

**Hilt, not Koin.** The deciding question was whether KSP works under AGP 9's
built-in Kotlin, where the module applies no Kotlin Gradle Plugin of its own. A
spike answered it: KSP 2.3.12 (KSP is versioned independently of Kotlin now) +
Hilt 2.60.1 built `:app:assembleDebug` green. Koin would have avoided KSP and
bought nothing else; `apps/android/CLAUDE.md`'s skill routing already named Hilt
as the target.

**The seam that matters** is not the annotations, it is that
`createSupabaseClient` now lives in a `@Provides`. `AppEnvironment` is read in
one place, and a JVM test never constructs that module.

**What stays temporary:** the CompositionLocals survive this change, fed by one
injected `ServiceGraph` holder. That is still a service locator, kept
deliberately until #849 gives each screen a ViewModel and deletes it. New code
takes its dependencies by constructor; nothing but `MainActivity` injects
`ServiceGraph`.
```

- [ ] **Step 2: Correct the CLAUDE.md bullet**

In `apps/android/CLAUDE.md`, under **Kotlin conventions**, replace the bullet beginning `- **State: plain service classes, manual injection, CompositionLocals — no DI` (through `No Hilt, no Koin.`) with:

```markdown
- **State: plain service classes, constructor-injected by Hilt (#848, supersedes
  D4).** Each iOS `@Observable` service becomes a plain Kotlin class holding
  Compose state (`mutableStateOf` for UI-read values; `StateFlow` where a
  non-Compose consumer needs it), annotated `@Singleton class X @Inject
  constructor(…)`. Adding a service is that one annotation — do **not** add a
  `@Provides` unless Dagger genuinely cannot infer the constructor, and do not
  add a new `Local…Service`. `di/SupabaseModule` is the only place
  `AppEnvironment` is read. Screens still read `Local…Service.current` through a
  temporary `di/ServiceGraph` bridge; #849 replaces those reads with ViewModels
  and deletes the bridge, so write new screens against `hiltViewModel()`, not
  against the locals.
```

- [ ] **Step 3: Commit**

```bash
cd /Users/alexis/code/pbbls
git add docs/decisions/log.md apps/android/CLAUDE.md
git commit -m "$(cat <<'EOF'
docs(android): record Hilt as the DI container, superseding D4 (#848)

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 1.7: Full verification and the Part 1 PR

- [ ] **Step 1: Run the whole gate**

```bash
cd apps/android
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew ktlintCheck lint testDebugUnitTest assembleDebug validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL` for all five.

- `lint` — a NEW finding fails the PR. Fix it; never run `updateLintBaseline` to silence something you introduced.
- `validateDebugScreenshotTest` — Part 1 changes no UI, so this must stay green. If it does not, read the diff before assuming host-platform noise.

- [ ] **Step 2: Smoke-test a minified build by hand**

This is the R8 gate from the spec's risk table and it is not optional. Hilt reflects; `android.yml`'s unsigned `bundleRelease` runs R8 but cannot catch a member it strips *silently*, which would surface as a runtime crash on first launch, in release only.

```bash
./gradlew :app:assembleRelease
adb devices                              # start the oxymore-eclipse Pixel 7 AVD first
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n app.pbbls.android/.MainActivity
adb logcat -d | grep -iE "FATAL|AndroidRuntime|Dagger|Hilt" | head -40
```

Expected: the app launches past the splash to Welcome or Path, and the logcat grep is empty. A `ClassNotFoundException` or a Dagger/Hilt message here means a keep rule is missing — but check the library's own bundled `META-INF/proguard/*.pro` before adding anything to `app/proguard-rules.pro` (repo rule: a redundant `-keep` widens the keep radius and undoes the shrinking).

- [ ] **Step 3: Note the build-time cost**

The spec's risk table says KSP's cost is measured at merge, not guessed. Record
the number in the PR body so a later regression has a baseline:

```bash
./gradlew --stop
./gradlew :app:assembleDebug   # cold; note the reported wall time
```

Reference point: the pre-Hilt tree and the Hilt spike both came in around 47s
cold on an M-series Mac. A materially larger number is worth a comment, not a
blocker.

- [ ] **Step 4: Open the PR**

Part 1 has the `feat` label and changes no user-visible Arkaik view node — it is pure architecture. Per `CLAUDE.md`'s gate the `feat` label alone means a Lab Note is required; this ships nothing a user would notice, so **delete the Lab Note section from the PR body and add the `no-lab-note` label**. Confirm this reading with the maintainer rather than assuming it.

Before opening, move the acceptance/view nodes this touches to `development` on the hosted Arkaik map via `arkaik-mcp` (`update_node`). If the `arkaik-mcp` tools are unavailable, say so and stop — do not edit `docs/arkaik/bundle.json`.

**Never write a Kritik finding id (`F-…`) in the PR body.** Reference follow-ups by issue number (#849, #851, #857, #858) only.

```bash
gh pr create \
  --title "feat(android): Hilt owns the service graph" \
  --body "Resolves #848 (part 1 of 3)
…"
```

Propose inheriting #848's labels (`android`, `core`, `feat`) and its milestone (`M61 · Android Refacto`); **confirm with the maintainer before applying**.

---

# PART 2 — Dispatchers stop being literals

**Branch:** `feat/848-dispatcher-injection`, stacked on Part 1.

```bash
cd /Users/alexis/code/pbbls
gh stack branch feat/848-dispatcher-injection    # see the gh-stack skill
```

### Task 2.1: Write the verification first, then the module

- [ ] **Step 1: Run the check that must eventually be empty — see it fail**

```bash
cd apps/android
grep -rn "Dispatchers\." app/src/main/kotlin
```

Expected now: **9 lines**, in `EditPebbleScreen.kt:114`, `RecordFlowScreen.kt:140,143,173`, `CreatePebbleScreen.kt:129`, `KarmaNotificationService.kt:28`, `SnapURLCache.kt:48`, `AchievementsService.kt:95`, `SupabaseService.kt:86`.

This grep is the acceptance criterion. It ends this part matching only `di/DispatchersModule.kt`.

- [ ] **Step 2: Create the dispatchers module**

Create `apps/android/app/src/main/kotlin/app/pbbls/android/di/DispatchersModule.kt`:

```kotlin
package app.pbbls.android.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Disk and network I/O. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** CPU-bound work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** The UI thread, `immediate` so a call already on it does not post. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

/**
 * Process-lifetime scope for work that must outlive any screen. A
 * `SupervisorJob` so one failed child never cancels the rest.
 *
 * This is NOT for view-scoped async — that stays `LaunchedEffect` /
 * `rememberCoroutineScope` / `viewModelScope`, and `GlobalScope` is still
 * banned (`apps/android/CLAUDE.md`).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * The ONLY place `Dispatchers.*` is named in `app/src/main` (#848).
 *
 * `grep -rn "Dispatchers\." app/src/main/kotlin` returning only this file is
 * the acceptance criterion — a literal anywhere else is a hard-coded thread
 * policy a test cannot replace with a `TestDispatcher`.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @MainDispatcher main: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + main)
}
```

- [ ] **Step 3: Build and commit**

```bash
./gradlew ktlintFormat :app:compileDebugKotlin
git add app/src/main/kotlin/app/pbbls/android/di/DispatchersModule.kt
git commit -m "$(cat <<'EOF'
feat(android): dispatcher qualifiers and an application scope (#848)

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.2: The four service-side literals

**Files:**
- Modify: `services/SupabaseService.kt:86`
- Modify: `features/karma/KarmaNotificationService.kt:27-29`
- Modify: `services/AchievementsService.kt:92-96`
- Modify: `services/SnapURLCache.kt:41-52`
- Modify: `di/ServiceModule.kt`

- [ ] **Step 1: `SupabaseService` takes the application scope**

Change the constructor to:

```kotlin
@Singleton
class SupabaseService
    @Inject
    constructor(
        val client: SupabaseClient,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
```

and delete the `private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` line and its now-unused `Dispatchers` / `SupervisorJob` imports. Add `import app.pbbls.android.di.ApplicationScope`.

Keep the comment that explains *why* the scope exists — move it onto the constructor parameter:

```kotlin
        // Scope for work that REACTS to a status change (never inline in the
        // collector — see start()).
```

- [ ] **Step 2: `KarmaNotificationService` loses its default**

```kotlin
@Singleton
class KarmaNotificationService
    @Inject
    constructor(
        @ApplicationScope private val scope: CoroutineScope,
    ) {
```

Add `import app.pbbls.android.di.ApplicationScope`, `javax.inject.Inject`, `javax.inject.Singleton`; remove `import kotlinx.coroutines.Dispatchers` and `import kotlinx.coroutines.SupervisorJob` if now unused.

Its KDoc already says "`scope` is injectable so the auto-dismiss is unit-testable off-device" — that stays true, and `KarmaNotificationServiceTest` passes its scope positionally, so removing the Kotlin default does not break it.

- [ ] **Step 3: `AchievementsService` loses its default**

```kotlin
@Singleton
class AchievementsService
    @Inject
    constructor(
        private val supabase: SupabaseService,
        private val notify: AchievementNotificationService,
        @ApplicationScope private val scope: CoroutineScope,
    ) {
```

Same import changes.

- [ ] **Step 4: `SnapURLCache` drops its secondary constructor**

Delete the whole secondary constructor (lines 46–52):

```kotlin
    constructor(supabase: SupabaseService) : this(
        provider = PebbleSnapRepository(supabase),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        nowMillis = System::currentTimeMillis,
    )
```

The `internal` primary constructor stays exactly as it is — `SnapURLCacheTest` already drives it and must keep passing unchanged. Remove the now-unused `Dispatchers`, `SupervisorJob` and `CoroutineScope` imports only if nothing else in the file uses them (`CoroutineScope` is still the primary constructor's parameter type, so it stays).

- [ ] **Step 5: `ServiceModule` shrinks from four providers to two**

`KarmaNotificationService` and `AchievementsService` are now constructor-injectable, so **delete both of their `@Provides` functions** and their imports. Rewrite `provideSnapURLCache` to use the primary constructor:

```kotlin
    @Provides
    @Singleton
    fun provideSnapURLCache(
        supabase: SupabaseService,
        @IoDispatcher io: CoroutineDispatcher,
    ): SnapURLCache =
        SnapURLCache(
            provider = PebbleSnapRepository(supabase),
            scope = CoroutineScope(SupervisorJob() + io),
            nowMillis = System::currentTimeMillis,
        )
```

Add imports: `app.pbbls.android.services.PebbleSnapRepository`, `kotlinx.coroutines.CoroutineDispatcher`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.SupervisorJob`. Update the module KDoc: it now documents **two** providers, not four, and the note about "both stop needing a provider in Part 2" is spent — delete that sentence rather than leaving a stale promise.

- [ ] **Step 6: Verify and commit**

```bash
./gradlew ktlintFormat :app:compileDebugKotlin :app:testDebugUnitTest
grep -rn "Dispatchers\." app/src/main/kotlin
```

Expected: tests green; the grep now shows **5 lines** — the composable sites plus `di/DispatchersModule.kt`'s own three. (Four service literals gone.)

```bash
git add app/src/main/kotlin/app/pbbls/android
git commit -m "$(cat <<'EOF'
refactor(android): service scopes take an injected dispatcher (#848)

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.3: Two injected collaborators for the composable sites

The five remaining literals sit inside composables, which cannot `@Inject` and have no ViewModel until #849. Threading a `CoroutineDispatcher` down as a parameter would satisfy the grep and leave the shape worse, so the work moves behind collaborators that own their dispatcher.

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/pebblemedia/SnapProcessor.kt`
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/features/path/valence/ValencePrewarmer.kt`

- [ ] **Step 1: `SnapProcessor`**

```kotlin
package app.pbbls.android.features.pebblemedia

import android.content.Context
import android.net.Uri
import app.pbbls.android.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The picked-photo work, off the main thread, with the dispatcher injected
 * rather than named at the call site (#848).
 *
 * The three composers used to write `withContext(Dispatchers.IO) { … }` inline.
 * A composable cannot be injected, so the dispatcher had nowhere to come from;
 * moving the work here gives it an owner and leaves the call sites with a plain
 * `suspend fun`. [ImagePipeline] and [ExifCaptureDate] stay pure objects — this
 * only decides where they run.
 *
 * Order matters at the call sites and the reason is not obvious: read the EXIF
 * date BEFORE processing. [ImagePipeline] re-encodes with `Bitmap.compress`,
 * which writes no metadata at all, so the capture date is gone by the time
 * processed bytes exist (M42 D7).
 */
@Singleton
class SnapProcessor
    @Inject
    constructor(
        @IoDispatcher private val io: CoroutineDispatcher,
    ) {
        /** The photo's EXIF capture date, or null — see [ExifCaptureDate.from]. */
        suspend fun captureDate(
            context: Context,
            uri: Uri,
        ): OffsetDateTime? = withContext(io) { ExifCaptureDate.from(context, uri) }

        /** Both renditions, re-encoded to the snap budgets — see [ImagePipeline.process]. */
        suspend fun process(
            context: Context,
            uri: Uri,
        ): ProcessedImage = withContext(io) { ImagePipeline.process(context, uri) }
    }
```

- [ ] **Step 2: `ValencePrewarmer`**

```kotlin
package app.pbbls.android.features.path.valence

import android.content.Context
import app.pbbls.android.di.DefaultDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps [prewarmValenceStones] with an injected dispatcher (#848).
 *
 * Composing the valence step wobbles eighteen assets on the first frame, which
 * is a visible hitch on the main thread, so the record flow kicks this off two
 * steps ahead. Both caches are process-wide and safe to warm twice.
 *
 * Lives in this package because [prewarmValenceStones] is `internal` to it.
 */
@Singleton
class ValencePrewarmer
    @Inject
    constructor(
        @DefaultDispatcher private val default: CoroutineDispatcher,
    ) {
        suspend fun prewarm(context: Context) = withContext(default) { prewarmValenceStones(context) }
    }
```

- [ ] **Step 3: Compile and commit**

```bash
./gradlew ktlintFormat :app:compileDebugKotlin
git add app/src/main/kotlin/app/pbbls/android/features/pebblemedia/SnapProcessor.kt \
        app/src/main/kotlin/app/pbbls/android/features/path/valence/ValencePrewarmer.kt
git commit -m "$(cat <<'EOF'
feat(android): SnapProcessor and ValencePrewarmer own their dispatchers (#848)

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2.4: Rewire the five composable call sites

**Files:**
- Modify: `features/pebblemedia/SnapProcessor.kt` (append the CompositionLocal)
- Modify: `features/path/valence/ValencePrewarmer.kt` (append the CompositionLocal)
- Modify: `di/ServiceGraph.kt`
- Modify: `MainActivity.kt` (provider block)
- Modify: `features/path/record/RecordFlowScreen.kt:112-174`
- Modify: `features/path/EditPebbleScreen.kt:86-115`
- Modify: `features/path/create/CreatePebbleScreen.kt:100-130`

Two more CompositionLocals is the opposite direction of travel, and it is the right call here: it keeps these two collaborators arriving the same way as the other twenty, so #849 removes all twenty-two together instead of leaving two odd ones out.

- [ ] **Step 1: Add the two locals**

At the end of `SnapProcessor.kt`:

```kotlin
/**
 * CompositionLocal for [SnapProcessor]. Temporary, like every `Local…` in this
 * app — #849 replaces it with a ViewModel dependency.
 */
val LocalSnapProcessor =
    staticCompositionLocalOf<SnapProcessor> {
        error("LocalSnapProcessor not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
```

(import `androidx.compose.runtime.staticCompositionLocalOf`)

At the end of `ValencePrewarmer.kt`, the same shape with `LocalValencePrewarmer` / `ValencePrewarmer`.

- [ ] **Step 2: Add them to `ServiceGraph`**

Two more constructor parameters, keeping the file's alphabetical-ish grouping:

```kotlin
        val snapProcessor: SnapProcessor,
        val valencePrewarmer: ValencePrewarmer,
```

(imports `app.pbbls.android.features.pebblemedia.SnapProcessor`, `app.pbbls.android.features.path.valence.ValencePrewarmer`)

- [ ] **Step 3: Provide them in `MainActivity`**

Two more lines in the `CompositionLocalProvider`:

```kotlin
                    LocalSnapProcessor provides graph.snapProcessor,
                    LocalValencePrewarmer provides graph.valencePrewarmer,
```

- [ ] **Step 4: `RecordFlowScreen` — three literals**

Add to the reads at the top of the composable (after `val snapshots = LocalComposerSnapshotStore.current`):

```kotlin
    val snapProcessor = LocalSnapProcessor.current
    val valencePrewarmer = LocalValencePrewarmer.current
```

In the `photoPicker` lambda, replace:

```kotlin
                        val picked = withContext(Dispatchers.IO) { ExifCaptureDate.from(context, uri) }
                        captureDate = picked
                        model.applyCaptureDate(picked)
                        val processed = withContext(Dispatchers.IO) { ImagePipeline.process(context, uri) }
```

with:

```kotlin
                        val picked = snapProcessor.captureDate(context, uri)
                        captureDate = picked
                        model.applyCaptureDate(picked)
                        val processed = snapProcessor.process(context, uri)
```

Keep the "EXIF first" comment above it — it explains an ordering that is still load-bearing.

In the prewarm `LaunchedEffect`, replace:

```kotlin
        withContext(Dispatchers.Default) { prewarmValenceStones(context) }
```

with:

```kotlin
        valencePrewarmer.prewarm(context)
```

Remove the now-unused imports: `kotlinx.coroutines.Dispatchers`, and `ExifCaptureDate` / `ImagePipeline` / `prewarmValenceStones` if nothing else in the file references them. Keep `withContext` only if still used elsewhere in the file — check before deleting.

- [ ] **Step 5: `EditPebbleScreen` — one literal**

Add `val snapProcessor = LocalSnapProcessor.current` to the service reads near line 86, and replace line 114:

```kotlin
                        val processed = withContext(Dispatchers.IO) { ImagePipeline.process(context, uri) }
```

with:

```kotlin
                        val processed = snapProcessor.process(context, uri)
```

Clean up the unused imports the same way.

- [ ] **Step 6: `CreatePebbleScreen` — one literal**

Identical to Step 5, at line 129.

- [ ] **Step 7: Prove the criterion**

```bash
grep -rn "Dispatchers\." app/src/main/kotlin
```

Expected: **exactly 3 lines, all in `app/src/main/kotlin/app/pbbls/android/di/DispatchersModule.kt`.** Anything else is not done.

- [ ] **Step 8: Verify and commit**

```bash
./gradlew ktlintFormat lint testDebugUnitTest assembleDebug validateDebugScreenshotTest
```

Expected: `BUILD SUCCESSFUL`. Behaviour is unchanged (same dispatchers, same order), so the screenshot suite must stay green.

```bash
git add app/src/main/kotlin/app/pbbls/android
git commit -m "$(cat <<'EOF'
refactor(android): no Dispatchers literal outside the provider (#848)

The five sites inside composables move behind SnapProcessor and
ValencePrewarmer, which own their dispatcher. `grep -rn "Dispatchers\."
app/src/main/kotlin` now matches only di/DispatchersModule.kt.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

- [ ] **Step 9: Open the Part 2 PR**

Same labels/milestone/Lab-Note handling as Task 1.7 Step 4. Title:
`refactor(android): inject dispatchers, no literals outside the provider`.
Body starts `Resolves #848 (part 2 of 3)`. No `F-…` ids.

---

# PART 3 — Interfaces, fakes, harness

**Branch:** `feat/848-service-fakes`, stacked on Part 2.

### Task 3.1: Move `delete-account` off the screen

`SettingsScreen` is the only screen reaching `supabase.client`, and a
`SupabaseClient` on `SupabaseServicing` would make the fake unfakeable. The call
is an edge-function invoke, which belongs on a service under the repo's RPC-first
rule anyway.

**Files:**
- Modify: `services/ProfileService.kt`
- Modify: `features/profile/SettingsScreen.kt:194-207`

- [ ] **Step 1: Add the method to `ProfileService`**

After `setPublicProfile`, add:

```kotlin
    /**
     * Invokes the `delete-account` edge function, which purges the row graph and
     * the auth user. Throws on failure; the caller signs out and maps the error.
     *
     * Lives here rather than on the screen (#848) so `SupabaseServicing` never
     * has to expose the raw client — a client on that interface would make every
     * fake of it pointless.
     */
    suspend fun deleteAccount() {
        supabase.client.functions.invoke("delete-account")
    }
```

Add `import io.github.jan.supabase.functions.functions`.

- [ ] **Step 2: Call it from the screen**

In `SettingsScreen.kt`'s `deleteAccount()`, replace:

```kotlin
                supabase.client.functions.invoke("delete-account")
                supabase.signOut()
```

with:

```kotlin
                profileService.deleteAccount()
                supabase.signOut()
```

`profileService` is already read from `LocalProfileService` in this screen — confirm with `grep -n "LocalProfileService" features/profile/SettingsScreen.kt` and add the read if it is missing. Remove `import io.github.jan.supabase.functions.functions` from the screen.

- [ ] **Step 3: Verify no screen reaches the client**

```bash
grep -rn "\.client" app/src/main/kotlin --include=*.kt | grep -v "/services/\|/di/\|features/glyph/services\|features/lab/services\|features/karma"
```

Expected: only `MainActivity.kt`'s two `handleDeeplinks` lines. `MainActivity` is the composition root and keeps the concrete type.

- [ ] **Step 4: Build and commit**

```bash
./gradlew ktlintFormat :app:compileDebugKotlin :app:testDebugUnitTest
git add app/src/main/kotlin/app/pbbls/android
git commit -m "$(cat <<'EOF'
refactor(android): delete-account moves to ProfileService (#848)

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3.2: Extract the five `…Servicing` interfaces

**Files:**
- Create: `apps/android/app/src/main/kotlin/app/pbbls/android/di/ServiceBindings.kt`
- Modify: `services/SupabaseService.kt`, `services/PathService.kt`, `services/ProfileService.kt`, `services/PebbleWriteService.kt`, `services/ReferenceDataService.kt`

Naming follows the two seams that already exist (`SignedUrlProviding`,
`SnapWriteRepositing`) and the CLAUDE.md rule that names the `…Servicing` suffix.

- [ ] **Step 1: `SupabaseServicing` — session and auth, deliberately no `client`**

In `services/SupabaseService.kt`, above the class:

```kotlin
/**
 * The auth surface screens actually read (#848).
 *
 * `client` is deliberately NOT on this interface. Screens reach the database
 * through the other services; putting a `SupabaseClient` here would mean a fake
 * had to produce one, which needs real secrets — i.e. it would defeat the entire
 * extraction. The composition root (`MainActivity`) and the other services keep
 * the concrete [SupabaseService].
 */
interface SupabaseServicing {
    val session: UserSession?
    val isInitializing: Boolean

    suspend fun start()

    suspend fun signIn(email: String, password: String)

    suspend fun signUp(email: String, password: String)

    suspend fun signInWithGoogle()

    suspend fun signOut()
}
```

Make the class implement it (`) : SupabaseServicing {`) and mark the five functions and two properties `override`. `session` and `isInitializing` keep their `private set` — an interface `val` is satisfied by a `var` with a private setter.

Retype the local:

```kotlin
val LocalSupabaseService =
    staticCompositionLocalOf<SupabaseServicing> {
        error("LocalSupabaseService not provided — wrap the tree in MainActivity's CompositionLocalProvider")
    }
```

- [ ] **Step 2: The other four interfaces**

Same pattern, each above its class in its own file, each with its local retyped.

`PathServicing` in `services/PathService.kt`:

```kotlin
/** The Path timeline read seam — see [SupabaseServicing] for why these exist (#848). */
interface PathServicing {
    suspend fun loadPathPebbles(): List<Pebble>
}
```

`ProfileServicing` in `services/ProfileService.kt`:

```kotlin
/** The profile read/write seam (#848). */
interface ProfileServicing {
    suspend fun loadProfile(): ProfileRow

    suspend fun loadGlyphStrokes(glyphId: String): List<GlyphStroke>

    suspend fun loadCollections(): List<Collection>

    suspend fun saveSettings(displayName: String?, glyphId: String?, password: String?)

    suspend fun setHandle(handle: String?)

    suspend fun setPublicProfile(isPublic: Boolean)

    suspend fun deleteAccount()
}
```

`PebbleWriteServicing` in `services/PebbleWriteService.kt`. Note the defaults: put them on the **interface**, not the implementation, or callers passing one argument stop compiling.

```kotlin
/** The pebble create/update/delete seam (#848). */
interface PebbleWriteServicing {
    suspend fun create(draft: PebbleDraft, snaps: List<PebbleSnapPayload>? = null): ComposeResult

    suspend fun update(pebbleId: String, draft: PebbleDraft, snaps: List<PebbleSnapPayload> = emptyList()): ComposeResult

    suspend fun delete(pebbleId: String)
}
```

`ReferenceDataServicing` in `services/ReferenceDataService.kt` — this one carries observable Compose state, so the interface exposes read-only `val`s over the class's `var … by mutableStateOf`:

```kotlin
/** The reference-data cache seam. The `val`s are Compose state, read during composition (#848). */
interface ReferenceDataServicing {
    val domains: List<Domain>
    val souls: List<SoulWithGlyph>
    val collections: List<PebbleCollection>
    val hasLoaded: Boolean

    suspend fun load()

    suspend fun refreshSouls()

    suspend fun refreshCollections()

    suspend fun createSoul(name: String): SoulWithGlyph?
}
```

In each class, add `override` to the members named above. In `ReferenceDataService` the four properties stay `var … by mutableStateOf(…)` with `override` added — a `var` satisfies a `val`.

- [ ] **Step 3: Bind them**

Create `apps/android/app/src/main/kotlin/app/pbbls/android/di/ServiceBindings.kt`:

```kotlin
package app.pbbls.android.di

import app.pbbls.android.services.PathService
import app.pbbls.android.services.PathServicing
import app.pbbls.android.services.PebbleWriteService
import app.pbbls.android.services.PebbleWriteServicing
import app.pbbls.android.services.ProfileService
import app.pbbls.android.services.ProfileServicing
import app.pbbls.android.services.ReferenceDataService
import app.pbbls.android.services.ReferenceDataServicing
import app.pbbls.android.services.SupabaseService
import app.pbbls.android.services.SupabaseServicing
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Interface → implementation for the five services a test needs to fake (#848).
 *
 * Only five, on purpose: the standing rule is "extract a `…Servicing` interface
 * for a fake only when a test needs one" (`apps/android/CLAUDE.md`). #849 pulls
 * the remaining fifteen as each screen gets a ViewModel and a test. Adding an
 * interface here with no fake and no test behind it is the thing that rule
 * exists to prevent.
 */
@Module
@InstallIn(SingletonComponent::class)
interface ServiceBindings {
    @Binds
    @Singleton
    fun bindSupabaseServicing(impl: SupabaseService): SupabaseServicing

    @Binds
    @Singleton
    fun bindPathServicing(impl: PathService): PathServicing

    @Binds
    @Singleton
    fun bindProfileServicing(impl: ProfileService): ProfileServicing

    @Binds
    @Singleton
    fun bindPebbleWriteServicing(impl: PebbleWriteService): PebbleWriteServicing

    @Binds
    @Singleton
    fun bindReferenceDataServicing(impl: ReferenceDataService): ReferenceDataServicing
}
```

- [ ] **Step 4: Retype `ServiceGraph`'s five fields**

Change the five field types to the interfaces (`supabase: SupabaseServicing`, `pathService: PathServicing`, `profileService: ProfileServicing`, `pebbleWrite: PebbleWriteServicing`, `referenceData: ReferenceDataServicing`) and fix the imports.

`MainActivity` needs the **concrete** `SupabaseService` for `handleDeeplinks`, which the graph no longer carries. Add a second injected field to the activity:

```kotlin
    /**
     * The concrete client owner, for `handleDeeplinks` only. The composition
     * root is the one place allowed to know a concrete service type; everything
     * below it gets [SupabaseServicing].
     */
    @Inject
    lateinit var supabaseClientOwner: SupabaseService
```

and change `private val supabase get() = graph.supabase` to `private val supabase get() = supabaseClientOwner`. (`supabase.isInitializing` and the two `supabase.client.handleDeeplinks` calls then both resolve.)

- [ ] **Step 5: Build, and fix what the compiler finds**

```bash
./gradlew ktlintFormat :app:compileDebugKotlin
```

Expected: any errors are call sites that assumed a concrete type. Fix each by using the interface member; if a call site genuinely needs something not on the interface, **add it to the interface** rather than casting.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/app/pbbls/android
git commit -m "$(cat <<'EOF'
feat(android): five ...Servicing seams, bound through Hilt (#848)

SupabaseServicing exposes session state and the auth actions but not `client` —
a SupabaseClient on the interface would need real secrets to fake, which is
exactly what this extraction exists to avoid.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3.3: The fakes

**Files:**
- Create: `apps/android/app/src/test/kotlin/app/pbbls/android/testing/FakeSupabaseService.kt`
- Create: `apps/android/app/src/test/kotlin/app/pbbls/android/testing/FakePathService.kt`
- Create: `apps/android/app/src/test/kotlin/app/pbbls/android/testing/FakeProfileService.kt`
- Create: `apps/android/app/src/test/kotlin/app/pbbls/android/testing/FakePebbleWriteService.kt`
- Create: `apps/android/app/src/test/kotlin/app/pbbls/android/testing/FakeReferenceDataService.kt`

The issue calls this `core/testing`. There is one Gradle module until #851 and
`src/test` is the only consumer until #857 lands Robolectric, so it goes here and
moves when the modules split. Say so in the KDoc.

Each fake follows one shape: settable results, recorded calls, and a
`failNext`-style switch so the error path is reachable. Mirror `SnapURLCacheTest`'s
existing `FakeProvider`, which is the house pattern.

- [ ] **Step 1: `FakeSupabaseService`**

```kotlin
package app.pbbls.android.testing

import app.pbbls.android.services.SupabaseServicing
import io.github.jan.supabase.auth.user.UserSession

/**
 * In-memory [SupabaseServicing] (#848). Holds no client and reads no
 * `BuildConfig`, which is the whole point.
 *
 * Lives under `src/test` because that is the only consumer until #857 lands
 * Robolectric; it moves to a real `core/testing` module with #851.
 */
class FakeSupabaseService(
    override var session: UserSession? = null,
    override var isInitializing: Boolean = false,
) : SupabaseServicing {
    var startCalls = 0
    var signOutCalls = 0
    val signInCalls = mutableListOf<Pair<String, String>>()
    val signUpCalls = mutableListOf<Pair<String, String>>()
    var signInWithGoogleCalls = 0

    /** Thrown by the next auth action, then cleared. */
    var failNext: Exception? = null

    private fun throwIfArmed() {
        failNext?.let {
            failNext = null
            throw it
        }
    }

    override suspend fun start() {
        startCalls += 1
    }

    override suspend fun signIn(email: String, password: String) {
        signInCalls += email to password
        throwIfArmed()
    }

    override suspend fun signUp(email: String, password: String) {
        signUpCalls += email to password
        throwIfArmed()
    }

    override suspend fun signInWithGoogle() {
        signInWithGoogleCalls += 1
        throwIfArmed()
    }

    override suspend fun signOut() {
        signOutCalls += 1
        session = null
    }
}
```

- [ ] **Step 2: `FakePathService`**

```kotlin
package app.pbbls.android.testing

import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.services.PathServicing

/** In-memory [PathServicing] (#848). See [FakeSupabaseService] for where this lives and why. */
class FakePathService(
    var pebbles: List<Pebble> = emptyList(),
) : PathServicing {
    var loadCalls = 0

    /** Thrown by the next load, then cleared — the screen's error path. */
    var failNext: Exception? = null

    override suspend fun loadPathPebbles(): List<Pebble> {
        loadCalls += 1
        failNext?.let {
            failNext = null
            throw it
        }
        return pebbles
    }
}
```

- [ ] **Step 3: `FakeProfileService`**

```kotlin
package app.pbbls.android.testing

import app.pbbls.android.features.glyph.models.GlyphStroke
import app.pbbls.android.features.profile.models.Collection
import app.pbbls.android.services.ProfileRow
import app.pbbls.android.services.ProfileServicing
import java.time.OffsetDateTime

/** In-memory [ProfileServicing] (#848). See [FakeSupabaseService] for where this lives and why. */
class FakeProfileService(
    var profile: ProfileRow = ProfileRow(displayName = "Pebbler", createdAt = OffsetDateTime.parse("2026-01-01T00:00:00Z")),
    var glyphStrokes: List<GlyphStroke> = emptyList(),
    var collections: List<Collection> = emptyList(),
) : ProfileServicing {
    /** `(displayName, glyphId, password)` per call, in order. */
    val saveSettingsCalls = mutableListOf<Triple<String?, String?, String?>>()
    val setHandleCalls = mutableListOf<String?>()
    val setPublicProfileCalls = mutableListOf<Boolean>()
    var deleteAccountCalls = 0

    /** Thrown by the next call, then cleared — every screen's error path. */
    var failNext: Exception? = null

    private fun throwIfArmed() {
        failNext?.let {
            failNext = null
            throw it
        }
    }

    override suspend fun loadProfile(): ProfileRow {
        throwIfArmed()
        return profile
    }

    override suspend fun loadGlyphStrokes(glyphId: String): List<GlyphStroke> {
        throwIfArmed()
        return glyphStrokes
    }

    override suspend fun loadCollections(): List<Collection> {
        throwIfArmed()
        return collections
    }

    override suspend fun saveSettings(
        displayName: String?,
        glyphId: String?,
        password: String?,
    ) {
        saveSettingsCalls += Triple(displayName, glyphId, password)
        throwIfArmed()
    }

    override suspend fun setHandle(handle: String?) {
        setHandleCalls += handle
        throwIfArmed()
        profile = profile.copy(handle = handle)
    }

    override suspend fun setPublicProfile(isPublic: Boolean) {
        setPublicProfileCalls += isPublic
        throwIfArmed()
        profile = profile.copy(publicProfile = isPublic)
    }

    override suspend fun deleteAccount() {
        deleteAccountCalls += 1
        throwIfArmed()
    }
}
```

- [ ] **Step 4: `FakePebbleWriteService`**

Kotlin forbids default values on an override — they are inherited from the
interface — so the overrides here take every parameter positionally.

```kotlin
package app.pbbls.android.testing

import app.pbbls.android.features.path.models.PebbleDraft
import app.pbbls.android.features.path.models.PebbleSnapPayload
import app.pbbls.android.services.ComposeResult
import app.pbbls.android.services.PebbleWriteServicing

/** In-memory [PebbleWriteServicing] (#848). See [FakeSupabaseService] for where this lives and why. */
class FakePebbleWriteService(
    /** Returned by [create] unless [failNext] is armed. */
    var createResult: ComposeResult = ComposeResult.SoftSuccess("fake-pebble-id"),
    /** Returned by [update] unless [failNext] is armed. */
    var updateResult: ComposeResult = ComposeResult.SoftSuccess("fake-pebble-id"),
) : PebbleWriteServicing {
    val createCalls = mutableListOf<Pair<PebbleDraft, List<PebbleSnapPayload>?>>()
    val updateCalls = mutableListOf<Triple<String, PebbleDraft, List<PebbleSnapPayload>>>()
    val deleteCalls = mutableListOf<String>()

    /** Thrown by the next call, then cleared. */
    var failNext: Exception? = null

    private fun throwIfArmed() {
        failNext?.let {
            failNext = null
            throw it
        }
    }

    override suspend fun create(
        draft: PebbleDraft,
        snaps: List<PebbleSnapPayload>?,
    ): ComposeResult {
        createCalls += draft to snaps
        throwIfArmed()
        return createResult
    }

    override suspend fun update(
        pebbleId: String,
        draft: PebbleDraft,
        snaps: List<PebbleSnapPayload>,
    ): ComposeResult {
        updateCalls += Triple(pebbleId, draft, snaps)
        throwIfArmed()
        return updateResult
    }

    override suspend fun delete(pebbleId: String) {
        deleteCalls += pebbleId
        throwIfArmed()
    }
}
```

- [ ] **Step 5: `FakeReferenceDataService`**

The four state properties are plain `var`s — a fake needs no Compose state, and
a `var` satisfies the interface's `val`. `createSoul` appends to `souls` and
returns the new row, so a test can assert the cache updated rather than only
that the call happened.

```kotlin
package app.pbbls.android.testing

import app.pbbls.android.features.path.models.Domain
import app.pbbls.android.features.path.models.PebbleCollection
import app.pbbls.android.features.profile.models.SoulWithGlyph
import app.pbbls.android.services.ReferenceDataServicing

/** In-memory [ReferenceDataServicing] (#848). See [FakeSupabaseService] for where this lives and why. */
class FakeReferenceDataService(
    override var domains: List<Domain> = emptyList(),
    override var souls: List<SoulWithGlyph> = emptyList(),
    override var collections: List<PebbleCollection> = emptyList(),
    override var hasLoaded: Boolean = true,
) : ReferenceDataServicing {
    var loadCalls = 0
    var refreshSoulsCalls = 0
    var refreshCollectionsCalls = 0
    val createSoulCalls = mutableListOf<String>()

    /**
     * Row returned by [createSoul]. Null makes it return null — the real service
     * swallows its failure and returns null rather than throwing, so that is the
     * error path a screen test has to drive.
     */
    var createSoulResult: SoulWithGlyph? = null

    override suspend fun load() {
        loadCalls += 1
        hasLoaded = true
    }

    override suspend fun refreshSouls() {
        refreshSoulsCalls += 1
    }

    override suspend fun refreshCollections() {
        refreshCollectionsCalls += 1
    }

    override suspend fun createSoul(name: String): SoulWithGlyph? {
        createSoulCalls += name
        val created = createSoulResult ?: return null
        souls = (souls + created).sortedBy { it.name }
        return created
    }
}
```

Check `SoulWithGlyph`'s constructor before writing a fixture for
`createSoulResult` — `grep -n "data class SoulWithGlyph" -A 12
app/src/main/kotlin/app/pbbls/android/features/profile/models/SoulWithGlyph.kt`.

- [ ] **Step 6: Compile the test source set**

```bash
./gradlew ktlintFormat :app:compileDebugUnitTestKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/test/kotlin/app/pbbls/android/testing
git commit -m "$(cat <<'EOF'
test(android): fakes for the five service seams (#848)

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3.4: `PebblesTestHarness` and the acceptance test

This is the genuine TDD task: the test IS acceptance criterion 3.

**Files:**
- Create: `apps/android/app/src/test/kotlin/app/pbbls/android/testing/PebblesTestHarness.kt`
- Create: `apps/android/app/src/test/kotlin/app/pbbls/android/testing/ServiceGraphFakesTest.kt`

- [ ] **Step 1: Write the failing test**

Create `apps/android/app/src/test/kotlin/app/pbbls/android/testing/ServiceGraphFakesTest.kt`:

```kotlin
package app.pbbls.android.testing

import app.pbbls.android.features.path.models.Pebble
import app.pbbls.android.services.PathServicing
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Acceptance criterion 3 of #848: a JVM test constructs the graph a screen
 * depends on, drives its success AND error paths, and touches no `BuildConfig`,
 * no `AppEnvironment` and no Supabase secret on any path.
 *
 * Before #848 this test could not exist. Every service's root built a live
 * supabase-kt client from `AppEnvironment` in its initializer, which throws on a
 * blank `BuildConfig.SUPABASE_URL` — so a screen test had to provide twenty
 * CompositionLocals backed by classes that needed real secrets, and nobody did.
 */
class ServiceGraphFakesTest {
    @Test
    fun fakeGraphSuppliesEveryServiceAScreenReads() {
        val graph = FakeServiceGraph()

        // Every seam is present and is a fake — no client, no secrets anywhere.
        assertTrue(graph.supabase is FakeSupabaseService)
        assertTrue(graph.pathService is FakePathService)
        assertTrue(graph.profileService is FakeProfileService)
        assertTrue(graph.pebbleWrite is FakePebbleWriteService)
        assertTrue(graph.referenceData is FakeReferenceDataService)
    }

    @Test
    fun pathLoadSucceedsThroughTheFake() =
        runTest {
            val pebbles = listOf<Pebble>()
            val service: PathServicing = FakePathService(pebbles = pebbles)

            assertEquals(pebbles, service.loadPathPebbles())
        }

    @Test
    fun pathLoadErrorPathIsReachable() =
        runTest {
            val fake = FakePathService()
            fake.failNext = IOException("network down")

            val thrown =
                try {
                    fake.loadPathPebbles()
                    null
                } catch (e: IOException) {
                    e
                }

            assertEquals("network down", thrown?.message)
            // Cleared after throwing: the next attempt succeeds, so a test can
            // drive retry without rebuilding the fake.
            assertEquals(emptyList<Pebble>(), fake.loadPathPebbles())
            assertEquals(2, fake.loadCalls)
        }
}
```

- [ ] **Step 2: Run it and watch it fail**

```bash
./gradlew :app:testDebugUnitTest --tests "*ServiceGraphFakesTest*"
```

Expected: **compilation failure** — `Unresolved reference: FakeServiceGraph`. That is the red state.

- [ ] **Step 3: Write the harness**

Create `apps/android/app/src/test/kotlin/app/pbbls/android/testing/PebblesTestHarness.kt`:

```kotlin
package app.pbbls.android.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import app.pbbls.android.services.LocalPathService
import app.pbbls.android.services.LocalPebbleWriteService
import app.pbbls.android.services.LocalProfileService
import app.pbbls.android.services.LocalReferenceDataService
import app.pbbls.android.services.LocalSupabaseService
import app.pbbls.android.services.PathServicing
import app.pbbls.android.services.PebbleWriteServicing
import app.pbbls.android.services.ProfileServicing
import app.pbbls.android.services.ReferenceDataServicing
import app.pbbls.android.services.SupabaseServicing

/**
 * The fake graph a test drives (#848). Every field defaults to a fresh fake, so
 * a test names only the seam it cares about:
 *
 * ```
 * val graph = FakeServiceGraph(pathService = FakePathService(pebbles = fixture))
 * ```
 *
 * Only the five extracted seams are here. The other fifteen services still have
 * no interface, on purpose — #849 pulls each one as its screen gets a ViewModel
 * and a test (`apps/android/CLAUDE.md`: extract a seam when a test needs one,
 * not before).
 */
class FakeServiceGraph(
    val supabase: SupabaseServicing = FakeSupabaseService(),
    val pathService: PathServicing = FakePathService(),
    val profileService: ProfileServicing = FakeProfileService(),
    val pebbleWrite: PebbleWriteServicing = FakePebbleWriteService(),
    val referenceData: ReferenceDataServicing = FakeReferenceDataService(),
)

/**
 * Provides the whole graph of fakes in one place, so a screen test is one wrap
 * rather than five nested `CompositionLocalProvider`s.
 *
 * Nothing composes this yet: driving a composable on the JVM needs Robolectric,
 * which is #857. It is written now because #849 will move screens to ViewModels
 * against exactly this shape, and because a harness that arrives with the seams
 * is one that gets used.
 */
@Composable
fun PebblesTestHarness(
    graph: FakeServiceGraph = FakeServiceGraph(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSupabaseService provides graph.supabase,
        LocalPathService provides graph.pathService,
        LocalProfileService provides graph.profileService,
        LocalPebbleWriteService provides graph.pebbleWrite,
        LocalReferenceDataService provides graph.referenceData,
        content = content,
    )
}
```

- [ ] **Step 4: Run the test and watch it pass**

```bash
./gradlew :app:testDebugUnitTest --tests "*ServiceGraphFakesTest*"
```

Expected: `BUILD SUCCESSFUL`, 3 tests passing.

If the `@Composable` function fails to compile in the unit-test source set, the
Compose compiler is not applied there. Check `./gradlew :app:dependencies` and,
if needed, move `PebblesTestHarness` alone (not the fakes, not `FakeServiceGraph`)
behind `androidTestImplementation` — but confirm first, and record what you found
in the PR body, because it changes what #857 can assume.

- [ ] **Step 5: Prove the criterion end to end**

```bash
grep -rn "BuildConfig\|AppEnvironment" app/src/test/kotlin/app/pbbls/android/testing
```

Expected: **no output.** Nothing in the harness reaches build-time config.

```bash
./gradlew ktlintCheck lint testDebugUnitTest assembleDebug validateDebugScreenshotTest
```

Expected: all green.

- [ ] **Step 6: Commit**

```bash
git add app/src/test/kotlin/app/pbbls/android/testing
git commit -m "$(cat <<'EOF'
test(android): PebblesTestHarness and the fakeable-graph test (#848)

Acceptance criterion 3: a JVM test now constructs the graph a screen depends on
and drives both its success and error paths without BuildConfig, AppEnvironment
or a Supabase secret on any path. Before this it could not exist.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3.5: Close out the stack

- [ ] **Step 1: Re-verify all four acceptance criteria in one pass**

```bash
cd apps/android
export ANDROID_HOME=$HOME/Library/Android/sdk

# 1. No lateinit service on PebblesApp, no `application as PebblesApp`
grep -rn "lateinit var\|application as PebblesApp" app/src/main/kotlin/app/pbbls/android/PebblesApp.kt
# expect: no output

# 2. No Dispatchers literal outside the provider
grep -rn "Dispatchers\." app/src/main/kotlin
# expect: only di/DispatchersModule.kt

# 3. A JVM test constructs a service with fakes and no secrets
./gradlew :app:testDebugUnitTest --tests "*ServiceGraphFakesTest*"
# expect: BUILD SUCCESSFUL

# 4. Decision-log entry superseding D4
grep -n "Hilt is the Android DI container" ../../docs/decisions/log.md
# expect: the 2026-09-19 heading. NOTE: the log's house template is
# Status/Scope/Context/Decision/Why/Consequences/Supersedes/Refs with
# `Status: taken` — the entry was written to that, not to the prose format
# this plan originally drafted.
```

- [ ] **Step 2: Open the Part 3 PR**

Title: `feat(android): service seams, fakes and a test harness`. Body starts
`Resolves #848 (part 3 of 3)`. Same labels/milestone/Lab-Note handling as
Task 1.7 Step 4. No `F-…` ids in the body.

- [ ] **Step 3: Rebase the stack if any part changed during review**

A fix for a lower part is made **in that part**, not patched at the top:

```bash
gh stack down            # navigate to the part that owns the code
# …commit the fix there…
gh stack rebase --upstack
```

- [ ] **Step 4: Update the hosted Arkaik map**

Move the acceptance/view nodes to `releasing` only through the Arkaik GitHub App
(it does this from the PR automatically). Do **not** claim `live` — nothing ships
to a store from this stack. If `arkaik-mcp` is unavailable, say so and stop;
never edit `docs/arkaik/bundle.json`.

---

## Notes for whoever executes this

- **Do not refactor anything this plan does not name.** If you see something to
  improve, put it in the PR body as a note. That is the repo rule and it is not
  negotiable here — this stack is already large.
- **`PebbleSnapRepository` stays concrete.** It takes `SupabaseService` like the
  fourteen, but it is constructed per-use inside composables, not a graph
  singleton. Annotating it would add a binding nothing injects.
- **The screenshot suite is a gate.** Parts 1 and 2 change no UI and Part 3
  retypes locals without changing rendering, so `validateDebugScreenshotTest`
  should stay green throughout. If it goes red, read the diff — do not widen the
  threshold, and do not re-baseline from your own machine. Add the
  `rebaseline-screenshots` label and let CI regenerate.
- **The R8 smoke test (Task 1.7 Step 2) is the one thing CI cannot do for you.**
