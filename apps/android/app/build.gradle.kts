import com.android.compose.screenshot.tasks.PreviewScreenshotValidationTask
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.screenshot)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.baselineprofile)
}

// Secrets chain (D8): read the git-ignored secrets.properties if present,
// otherwise fall back to an environment variable of the same name, otherwise
// default to an empty string so the build NEVER fails without secrets.
// AppEnvironment turns an empty value into a loud runtime crash with setup
// instructions — the iOS contract: setup bugs fail at launch, never at build.
//
// The env-var fallback lets CI bake real config into the debug APK from GitHub
// Actions secrets (the maintainer has no local Android SDK and installs the CI
// artifact directly), while local builds keep using secrets.properties. Local
// file wins over env when both are set.
val secretsFile = rootProject.file("secrets.properties")
val secrets =
    Properties().apply {
        if (secretsFile.exists()) {
            secretsFile.inputStream().use { load(it) }
        }
    }

fun secret(key: String): String {
    val fromFile = secrets.getProperty(key)
    if (!fromFile.isNullOrBlank()) return fromFile
    return System.getenv(key).orEmpty()
}

android {
    namespace = "app.pbbls.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.pbbls.android"
        minSdk = 33
        targetSdk = 37
        // Derive the version from the CI run number so every uploaded debug APK
        // is a strictly-increasing, distinct version. A static versionCode makes
        // Android treat a reinstall as the same version and silently keep the old
        // code — the "I installed it but nothing changed" trap. Local builds (no
        // GITHUB_RUN_NUMBER) fall back to 1 / "0.1.0".
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = System.getenv("GITHUB_RUN_NUMBER")?.let { "0.1.0.$it" } ?: "0.1.0"

        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
    }

    // Release signing for Play distribution. Signing material flows the same way
    // secrets do (D8): the release CI job decodes the base64 upload keystore to a
    // file and passes KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
    // as env vars; a local release build could instead put them in
    // secrets.properties. When no keystore is present the config stays empty and
    // the release build is left UNSIGNED — the same fail-soft contract as the
    // Supabase secrets, so a fork (or a run before the keystore secret exists)
    // still builds green. Only a run with the secrets produces an installable,
    // Play-uploadable AAB.
    val keystorePath = secret("KEYSTORE_FILE").ifBlank { "upload-keystore.jks" }
    val keystoreFile = rootProject.file(keystorePath)
    val hasKeystore = keystoreFile.exists() && secret("KEYSTORE_PASSWORD").isNotBlank()

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = keystoreFile
                storePassword = secret("KEYSTORE_PASSWORD")
                keyAlias = secret("KEY_ALIAS")
                keyPassword = secret("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // The petroglyph wobble experiment (#555) is always on in debug —
            // the analog of iOS WobbleFlags' `#if DEBUG`.
            buildConfigField("boolean", "WOBBLE_ENABLED", "true")
        }
        release {
            // R8 shrink + obfuscate + optimize, and the resource shrinker on top
            // (#845). Everything this app ships that needs reflection is named in
            // proguard-rules.pro; everything else relies on the libraries' own
            // consumer rules. `proguard-android-optimize.txt` is the default file
            // that leaves the optimization passes on.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Attach the upload key only when it was configured above; a keyless
            // build produces an unsigned AAB rather than failing configuration.
            if (hasKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            // Wobble in release is opt-in per build via the D8 env/secrets
            // chain: android-release.yml sets WOBBLE_ENABLED=true so the
            // maintainer's Play-updated phone shows the experiment (the Android
            // device loop has no debug channel, unlike iOS/Xcode — see the
            // 2026-07-14 decisions-log entry). Any other release build (fork,
            // local, a future production pipeline) stays wobble-free.
            buildConfigField(
                "boolean",
                "WOBBLE_ENABLED",
                if (secret("WOBBLE_ENABLED").equals("true", ignoreCase = true)) "true" else "false",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // `android.util.Log` is a stub in the JVM unit-test android.jar: every method
    // throws "not mocked" unless this is on. Every ViewModel #849 adds logs on its
    // error path and every one of them has a test that drives that path, so
    // without this the choice is a `Log` call that fails the test or no logging at
    // all — and "log, don't swallow" is a standing rule (`apps/android/CLAUDE.md`).
    // The existing escape hatch was to inject the logger as a lambda
    // (`SnapUploadCoordinator.onLog`), which is fine for one class and absurd
    // across eleven.
    //
    // The cost is real and bounded: any OTHER unmocked android.jar call now
    // returns 0/null/false in a unit test instead of throwing, so a test can
    // quietly exercise a stub. It is bounded because these are JVM tests of pure
    // logic and state holders by policy — anything needing real framework
    // behaviour waits for Robolectric (#857).
    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // Android Lint runs in android.yml on every PR (#845), so it has to be a
    // gate, not a report: warnings are errors and the build aborts. The baseline
    // freezes what existed when lint was first turned on — new findings fail,
    // old ones are tracked in the issues that own them. `checkDependencies`
    // widens the scan past :app (there is only one module today, but the flag
    // also pulls in the AARs' manifest/resource findings). The SARIF report is
    // what the workflow uploads to GitHub code scanning.
    lint {
        abortOnError = true
        warningsAsErrors = true
        baseline = file("lint-baseline.xml")
        checkDependencies = true
        sarifReport = true
        // NewerVersionAvailable resolves the latest published version over the
        // network, so it goes red on its own schedule — every dependency release
        // is a new finding the committed baseline cannot have. Dependabot already
        // owns version bumps here (D2 keeps them isolated commits), so this check
        // would only ever turn someone else's release into a red PR.
        disable += "NewerVersionAvailable"
    }

    // Compose Preview Screenshot Testing (experimental/alpha). Also flagged in
    // gradle.properties; set here so the :app module opts in explicitly.
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

// The screenshot suite is a validation gate (#847): `validateDebugScreenshotTest`
// diffs every rendered @PreviewTest against the committed PNG under
// app/src/screenshotTestDebug/reference/ and fails the build on a mismatch.
//
// The plugin's comparator is PixelPerfect: it counts differing pixels and fails
// when that fraction of the image exceeds this threshold. 0.0f (its default)
// makes one stray pixel a failure, which is brittle against a layoutlib patch
// that nudges antialiasing; 0.0005f lets 0.05% of the pixels move instead.
//
// Calibrated, not guessed: re-wording one two-word label ("Delete account" ->
// "Remove account") on the tallest render in the suite — 1080x3675, so the
// single changed row is the smallest slice a real regression can occupy — came
// out at 0.12%, 2.4x over this line. Anything larger, or on any shorter image,
// clears it by more. Rendering the same source twice on one machine is
// byte-identical, so the headroom underneath costs no sensitivity.
//
// Do NOT widen this to absorb a host-platform difference. Rendering the suite on
// macOS and on the CI runner gave 108 of 162 byte-identical and 54 differing, up
// to 1.77% — and the worst offender is visually indistinguishable: the pebble
// silhouettes come out one colour level apart, so byte-exact comparison counts
// every pixel of a filled area. A threshold loose enough to swallow that would be
// ~35x looser than the smallest real regression measured above, i.e. no gate at
// all. The baseline is rendered on the runner instead (android-screenshots.yml).
//
// There is no public DSL for this in alpha16 — the plugin sets the task input's
// convention itself and leaves a TODO to expose it — so it is set on the task.
// That is exactly why libs.versions.toml pins the plugin strictly: a version bump
// that moves this property fails at configuration time instead of silently
// restoring the 0.0f default.
tasks.withType<PreviewScreenshotValidationTask>().configureEach {
    testEngineInput.threshold.set(0.0005f)
}

// Baseline + startup profiles (#856). The profiles are generated on a device by
// :baselineprofile and committed under src/release/generated/baselineProfiles/;
// every release build (bundleRelease in CI included) packs the committed files
// and never re-generates, because generation needs a device and a signed-in
// test account that CI does not have. `dexLayoutOptimization` turns the
// startup-flagged rules into a startup profile, which R8 uses to put the code
// cold start touches into the primary dex. Regenerate with
// `./gradlew :app:generateBaselineProfile` — see apps/android/CLAUDE.md.
baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
    dexLayoutOptimization = true
}

// The plugin's nonMinifiedRelease / benchmarkRelease build types copy the
// release signing config, which is empty on any machine without the upload
// keystore (the D8 fail-soft contract) — and an unsigned APK cannot be installed
// on the device that generates the profile. Sign those two with the debug key
// instead. `release` itself is untouched, so nothing uploadable changes.
androidComponents {
    onVariants(selector().withBuildType("nonMinifiedRelease")) { variant ->
        variant.signingConfig.setConfig(android.signingConfigs.getByName("debug"))
    }
    onVariants(selector().withBuildType("benchmarkRelease")) { variant ->
        variant.signingConfig.setConfig(android.signingConfigs.getByName("debug"))
    }
}

// jvmToolchain sets sourceCompatibility/targetCompatibility for Java and the
// jvmTarget for Kotlin in one place (JDK 21 toolchain, D3).
kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidsvg)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.window.core)
    implementation(libs.androidx.compose.foundation)
    // Navigation 3 (#852). navigation-compose is gone (Part 5) — nothing imports it.
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigationevent.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // supabase-kt: BOM pins the module versions; OkHttp is the Ktor engine and
    // kotlinx-serialization-json backs the consent-metadata JSON block.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // Snap thumbnails: Coil 3 + the OkHttp network fetcher (registered
    // explicitly in PebblesApp.newImageLoader).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Invite QR encoding (M49); the BitMatrix is drawn in a Compose Canvas.
    implementation(libs.zxing.core)

    // Hilt (#848). The library's own consumer ProGuard rules cover its
    // reflection, so proguard-rules.pro stays at two rules — but R8 can strip
    // silently, so Part 1 smoke-tests a minified build by hand before merge.
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // ViewModel + lifecycle-aware collection (#849). collectAsStateWithLifecycle
    // lives in runtime-compose, not runtime-ktx — the two are different artifacts
    // and only the former stops a StateFlow collecting while the app is backgrounded.
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    // Installs the committed baseline profile into ART on first launch (#856).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)

    // Konsist parses the Kotlin sources and asserts the core/features boundary
    // (#851). Test-only, and a JVM test like any other, so `testDebugUnitTest`
    // in android.yml is already its CI gate.
    testImplementation(libs.konsist)

    // Compose Preview Screenshot Testing renders the @PreviewTest composables in
    // src/screenshotTest/ to PNGs. ui-tooling supplies the @Preview runtime.
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}
