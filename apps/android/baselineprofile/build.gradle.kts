import java.util.Properties

// Generates the baseline + startup profiles :app ships (#856), and measures cold
// start with and without them. Everything here runs on a device (an emulator or
// a phone on API 33+, no root needed), by hand — nothing in CI runs it. See
// "Baseline profile" in apps/android/CLAUDE.md for the commands.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.ktlint)
}

// The journey signs in through the real Auth screen, so it needs a throwaway
// test account. Its credentials follow the D8 chain the app's secrets use —
// the git-ignored secrets.properties, else an env var — and reach the device as
// instrumentation arguments. Absent, the signed-in half of the journey is
// skipped (Welcome and Auth are still profiled) rather than failing the build.
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
    namespace = "app.pbbls.android.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 33
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Only when set: an empty `-e key ""` shifts every flag after it on the
        // `am instrument` command line, and the run dies with "Invalid userId".
        listOf("benchmarkEmail" to "BENCHMARK_EMAIL", "benchmarkPassword" to "BENCHMARK_PASSWORD")
            .forEach { (argument, key) ->
                secret(key).takeIf { it.isNotBlank() }?.let { testInstrumentationRunnerArguments[argument] = it }
            }
        // Macrobenchmark refuses to report timings from an emulator unless told
        // to. The numbers are still comparable run to run on one AVD, which is
        // what a before/after needs; a phone is the authority for absolutes.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }

    targetProjectPath = ":app"
}

// Connected device only: a Gradle-managed device would need its own system image
// download and root-capable `aosp` image, and generation is a by-hand run.
baselineProfile {
    useConnectedDevices = true
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
