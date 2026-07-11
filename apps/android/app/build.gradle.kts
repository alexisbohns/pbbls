import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
}

// Secrets chain (D8): read the git-ignored secrets.properties if present,
// otherwise default every key to an empty string so the build NEVER fails
// without secrets (CI has none). AppEnvironment turns an empty value into a
// loud runtime crash with setup instructions — the iOS contract: setup bugs
// fail at launch, never at build.
val secretsFile = rootProject.file("secrets.properties")
val secrets =
    Properties().apply {
        if (secretsFile.exists()) {
            secretsFile.inputStream().use { load(it) }
        }
    }

fun secret(key: String): String = secrets.getProperty(key, "")

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

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
