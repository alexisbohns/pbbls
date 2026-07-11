import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.screenshot)
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
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${secret("SUPABASE_ANON_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Compose Preview Screenshot Testing (experimental/alpha). Also flagged in
    // gradle.properties; set here so the :app module opts in explicitly.
    experimentalProperties["android.experimental.enableScreenshotTest"] = true
}

// jvmToolchain sets sourceCompatibility/targetCompatibility for Java and the
// jvmTarget for Kotlin in one place (JDK 21 toolchain, D3).
kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.rive.android)
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.browser)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidsvg)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // supabase-kt: BOM pins the module versions; OkHttp is the Ktor engine and
    // kotlinx-serialization-json backs the consent-metadata JSON block.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.storage)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)

    // Snap thumbnails: Coil 3 + the OkHttp network fetcher (registered
    // explicitly in PebblesApp.newImageLoader).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Compose Preview Screenshot Testing renders the @PreviewTest composables in
    // src/screenshotTest/ to PNGs. ui-tooling supplies the @Preview runtime.
    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)
}
