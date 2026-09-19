# Hilt replaces the Application service locator (M61)

**Date:** 2026-09-19
**Issue:** [#848](https://github.com/alexisbohns/pbbls/issues/848)
**Surface:** `apps/android` only. No database, no contract, no other client.
**Supersedes:** M38 decision **D4** ("plain service classes, manual injection, CompositionLocals. No DI framework") — `docs/superpowers/specs/2026-07-10-android-bootstrap-design.md:108`.
**Related:** [#849](https://github.com/alexisbohns/pbbls/issues/849) (ViewModel + UiState per screen) consumes this and deletes what this leaves temporary. [#851](https://github.com/alexisbohns/pbbls/issues/851) owns module boundaries. [#857](https://github.com/alexisbohns/pbbls/issues/857) owns Robolectric. [#858](https://github.com/alexisbohns/pbbls/issues/858) owns the full agent-guide rewrite.

## 1. Why

D4 was written for four services and an app that had not been built yet. It was
right then. It is wrong now, and the arithmetic says why:

| | M38, when D4 was written | Today |
|---|---|---|
| Services on `PebblesApp` | 4 (planned 6) | **20** `lateinit var`, three without `private set` |
| Files touched to add a service | 3 | 3 (unchanged — that is the problem) |
| `Local…Service.current` reads | a handful | **87** (`RecordFlowScreen.kt:107-114` reads eight) |
| Interfaces for test seams | 0 | 2 (`SignedUrlProviding`, `SnapWriteRepositing`), each for one test |
| Screen tests covering load / error / save | 0 | **0** |

The third row is the cost and the fifth row is the bill. `PebblesApp.kt:45-124`
holds the graph, `MainActivity.kt:52-73` re-lists all 20 into one
`CompositionLocalProvider`, and every screen reads through the locals. A screen
test would have to provide 20 locals backed by concrete classes whose root,
`SupabaseService`, builds a real supabase-kt client from `AppEnvironment` in its
initializer. On a fork PR `BuildConfig.SUPABASE_URL` is blank and that throws.
So the 84 unit-test files cover pure helpers only, and
`apps/android/CLAUDE.md` has to carry a whole paragraph explaining that
"screens that read services cannot be previewed".

D4's own justification does not survive the growth: *"six hand-wired singletons
don't need annotation processing and opaque errors."* Twenty do.

The iOS-mirror argument does not carry either. SwiftUI's `@Environment` +
`@Observable` **is** the platform's dependency injection; hand-rolled
CompositionLocals are not Compose's. The Android analog of `@Environment` is
Hilt. Mirroring iOS 1:1 is a rule about product behaviour and structure, not
about re-implementing a first-party framework by hand.

## 2. Hilt, not Koin — and the spike that settles it

The issue leaves the door open to Koin "if KSP is a problem". `apps/android/CLAUDE.md`'s
skill-routing table already commits to Hilt. The only real question was whether
KSP works under AGP 9.4.1's **built-in Kotlin**, where the Kotlin Gradle Plugin
is not applied by the module.

Answered empirically rather than assumed. A throwaway spike on `main`:

```
ksp  = "2.3.12"       # KSP has decoupled from the Kotlin version line
hilt = "2.60.1"
hiltNavigationCompose = "1.4.0"
```

plugins declared root-side and applied in `:app`, `@HiltAndroidApp` on
`PebblesApp`:

```
> Task :app:hiltCollectClassesDebug
> Task :app:hiltAggregateDepsDebug
> Task :app:hiltJavaCompileDebug
BUILD SUCCESSFUL in 47s
```

The spike was reverted; the tree is clean. **Decision: Hilt.** No fallback
branch is carried in the plan.

## 3. Shape

Three stacked PRs (`gh stack`), ordered by dependency. Each is independently
lint-, test- and build-green without the parts above it.

```
Part 1  the graph moves to Hilt            feat/848-hilt-service-graph
Part 2  dispatchers stop being literals    feat/848-dispatcher-injection
Part 3  interfaces, fakes, harness         feat/848-service-fakes
```

### Part 1 — the graph moves to Hilt

**The seam that makes everything else possible** is moving `createSupabaseClient`
out of `SupabaseService`'s initializer and into a `@Provides`:

```kotlin
@Module @InstallIn(SingletonComponent::class)
object SupabaseModule {
    @Provides @Singleton
    fun provideSupabaseClient(): SupabaseClient =
        createSupabaseClient(AppEnvironment.supabaseUrl, AppEnvironment.supabaseAnonKey) { … }
}

@Singleton class SupabaseService @Inject constructor(val client: SupabaseClient) { … }
```

`AppEnvironment` is read in exactly one place, and it is a place no JVM test
ever constructs. That is what turns acceptance criterion 3 from "rewrite the
services" into "don't call the module".

Everything else in this part follows:

- **16 services** are constructor-injectable as they stand: the 14 whose
  constructor is `(supabase: SupabaseService)`, plus `SupabaseService` itself
  (now `(client: SupabaseClient)`) and the parameterless
  `AchievementNotificationService`. Each gets `@Singleton class X @Inject
  constructor(…)`. No `@Provides` needed; that is less code than 20 provider
  functions and the issue's "each service as `@Singleton`" is satisfied either way.
- **4 services** need an explicit `@Provides` in `di/ServiceModule.kt`, because
  they take something Dagger cannot infer: `SnapURLCache` (an `internal` primary
  constructor plus a secondary), `ComposerSnapshotStore` (`@ApplicationContext
  Context`), and `KarmaNotificationService` + `AchievementsService` (a `scope`
  parameter with a Kotlin default — Dagger ignores defaults). The last two stop
  needing a provider in Part 2, once `@ApplicationScope` is a real binding.
- `PebblesApp` keeps `@HiltAndroidApp`, `Rive.init(this)` and `newImageLoader`.
  The 20 `lateinit var`s and the entire `onCreate` graph are **deleted**.
- `MainActivity` becomes `@AndroidEntryPoint`. Field injection lands in
  `super.onCreate`, before the first `supabase.isInitializing` read, so the
  splash contract (#846) is untouched.

**`ServiceGraph` — the deliberate, temporary locator.** `MainActivity` still has
to fill a 20-entry `CompositionLocalProvider`, because the screens still read
locals until #849 gives them ViewModels. Twenty `@Inject lateinit var` fields on
the activity would work. Instead, one injected holder:

```kotlin
/** Temporary bridge from the Hilt graph to the CompositionLocals. Deleted by #849. */
@Singleton class ServiceGraph @Inject constructor(
    val supabase: SupabaseService, val palettes: EmotionPaletteService, …
)
```

This is, honestly, still a service locator. It is kept on purpose and with its
eyes open, because the alternative costs more for the same lifespan: it is **one
file** for #849 to delete rather than twenty fields to unpick across an activity,
and it keeps `MainActivity` readable in the meantime. It is not a pattern to
copy; the KDoc says so and names the issue that removes it.

Also in this part, because leaving them stale would be a lie in the tree:

- an appended entry in `docs/decisions/log.md` superseding D4;
- the one `apps/android/CLAUDE.md` bullet that currently reads "No Hilt, no
  Koin". The rest of that document's rewrite stays with #858.

### Part 2 — dispatchers stop being literals

Qualifiers over `CoroutineDispatcher`, not a `DispatcherProvider` interface. The
issue's own `@IoDispatcher` / `@DefaultDispatcher` naming implies the qualifier
shape, and it is the Now-in-Android idiom:

```kotlin
@Qualifier annotation class IoDispatcher
@Qualifier annotation class DefaultDispatcher
@Qualifier annotation class MainDispatcher
@Qualifier annotation class ApplicationScope
```

Four of the nine literals are easy: the three service scopes
(`SupabaseService.kt:86`, `KarmaNotificationService.kt:28`,
`AchievementsService.kt:95`) and `SnapURLCache.kt:48` take theirs by injection.

**The other five sit inside composables** — `EditPebbleScreen.kt:114`,
`RecordFlowScreen.kt:140,143,173`, `CreatePebbleScreen.kt:129` — which cannot
`@Inject` and have no ViewModel until #849. Threading a `CoroutineDispatcher`
down through them as a parameter would satisfy the letter of the criterion and
leave the shape worse. Instead the work moves behind two small injected
collaborators that own their own dispatcher:

| New collaborator | Wraps | Dispatcher |
|---|---|---|
| `SnapProcessor` | `ImagePipeline.process`, `ExifCaptureDate.from` | `@IoDispatcher` |
| `ValencePrewarmer` | `prewarmValenceStones` | `@DefaultDispatcher` |

The composables then call a plain `suspend fun` with no dispatcher in sight, and
reach the collaborators through the same `ServiceGraph` bridge as every other
service — so #849 removes them the same way it removes the rest.

After this part, `Dispatchers.` appears in `app/src/main` only inside
`di/DispatchersModule.kt`. That is the grep that proves criterion 2.

### Part 3 — interfaces, fakes, harness

Five `…Servicing` interfaces, matching the gerund naming the two existing seams
already use (`SignedUrlProviding`, `SnapWriteRepositing`) and the CLAUDE.md rule
that names the `…Servicing` suffix:

`SupabaseServicing`, `PathServicing`, `ProfileServicing`, `PebbleWriteServicing`,
`ReferenceDataServicing` — bound `@Binds`-style in `di/ServiceBindings.kt`, with
the five CompositionLocals retyped to the interface.

**`SupabaseServicing` deliberately does not expose `client`.** Session state
(`session`, `isInitializing`) and the auth actions (`start`, `signIn`, `signUp`,
`signInWithGoogle`, `signOut`) are the surface screens actually read; a
`SupabaseClient` on the interface would make the fake unfakeable and the whole
extraction pointless. Exactly one screen reaches through today —
`SettingsScreen.kt:199`'s `client.functions.invoke("delete-account")` — and that
is an edge-function call that belongs on a service under the repo's own
RPC-first rule. It moves to `ProfileService.deleteAccount()`. `MainActivity`
keeps the concrete `SupabaseService` for `handleDeeplinks`; it is the
composition root, which is the one place allowed to know concrete types.

Fakes and the harness live in `app/src/test/kotlin/app/pbbls/android/testing/`:
a `FakeXService` per interface, plus

```kotlin
@Composable fun PebblesTestHarness(
    graph: FakeServiceGraph = FakeServiceGraph(),
    content: @Composable () -> Unit,
)
```

which provides the whole graph of fakes in one place. The issue calls this
`core/testing`; there is one Gradle module (`:app`) until #851, and `src/test` is
the only consumer that exists until #857 lands Robolectric. It moves to a real
module when #851 splits them — deliberately not pulled forward here.

The part closes with the test that proves criterion 3: a JVM test constructing a
service against fakes, with no `BuildConfig` and no secrets on any path.

## 4. Out of scope

Named explicitly, because each is a plausible thing to drift into:

- **The other 15 services get no interface.** The standing rule is "extract a
  `…Servicing` interface for a fake only when a test needs one." #849 pulls each
  one as its screen gets a ViewModel and a test. Extracting all 20 now is
  precisely what that rule was written against.
- **No `:core:testing` Gradle module** — #851.
- **No ViewModels, no `UiState`** — #849. This part leaves the CompositionLocals
  in place on purpose; task 6 of the issue says so.
- **No screenshot previews for the service-reading screens.** The harness makes
  them possible for the first time, which is a real payoff, but new references
  mean a re-baseline and that belongs in its own PR (#847's contract).
- **No Navigation 3, no Material 3 Expressive, no error-boundary work** — #852,
  #853, #850.

## 5. Risks

| Risk | Mitigation |
|---|---|
| KSP under AGP 9 built-in Kotlin | **Retired.** Spike built green (§2). |
| Hilt reflects; R8 could strip silently | Hilt ships its own consumer ProGuard rules, so `proguard-rules.pro` stays two rules long per the repo rule — but `android.yml`'s unsigned `bundleRelease` cannot catch a silent strip. **Smoke-test a minified build by hand before merging Part 1** (`apps/android/CLAUDE.md`, Release & distribution). |
| Screenshot references move | Part 1 and 2 change no UI, so `validateDebugScreenshotTest` should stay green. Part 3 retypes locals without changing rendering. If a reference does move, re-baseline via the `rebaseline-screenshots` label — never widen the threshold. |
| Build time regression from KSP | Measured at merge; the spike's full `assembleDebug` was 47s cold. |
| Lint baseline drift | `./gradlew lint` per part. A new finding fails the PR and is fixed, not baselined. |

## 6. Acceptance criteria → evidence

| Issue criterion | How it is proven |
|---|---|
| No `lateinit var` service on `PebblesApp`; no `application as PebblesApp` | `grep -rn "lateinit var\|application as PebblesApp" app/src/main` returns nothing in Part 1 |
| Zero `Dispatchers.*` literals outside the provider | `grep -rn "Dispatchers\." app/src/main` returns only `di/DispatchersModule.kt` in Part 2 |
| A JVM test constructs any service with fakes and no `BuildConfig`/secrets | The Part 3 test, green under `./gradlew testDebugUnitTest` |
| Decision-log entry superseding D4 | Appended in Part 1 |

## 7. Verification per part

Every part, before its PR opens:

```
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew ktlintCheck lint testDebugUnitTest assembleDebug validateDebugScreenshotTest
```

Part 1 additionally smoke-tests a minified `bundleRelease` / release APK on the
`oxymore-eclipse` Pixel 7 AVD, per the R8 risk above. CI (`android.yml`) remains
the authority.
