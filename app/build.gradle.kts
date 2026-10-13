plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Version: CI passes -PappVersion=1.2.3 (from the v1.2.3 tag). Local builds use `git describe`,
// e.g. "1.2.3-4-gabc1234-dirty", or "dev" without git.
val appVersion: String = (findProperty("appVersion") as String?)
    ?: runCatching {
        providers.exec { commandLine("git", "describe", "--tags", "--always", "--dirty") }
            .standardOutput.asText.get().trim().removePrefix("v")
    }.getOrNull()?.takeIf { it.isNotEmpty() }
    ?: "dev"

// 1.2.3 -> 10203. Must increase with every release so Android accepts the update.
val appVersionCode: Int = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)").find(appVersion)
    ?.destructured?.let { (major, minor, patch) -> major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt() }
    ?: 1

// Release signing key, provided by CI secrets (see .github/workflows/release.yml).
val releaseKeystore: String? = System.getenv("SIGNING_KEYSTORE_FILE")

android {
    namespace = "fi.subsite.phonedrop"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "fi.subsite.phonedrop"
        // MINA SSHD relies on java.nio.file, available from API 26.
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without the CI key (local builds), fall back to the debug key so the APK still installs.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/*.md",
                "META-INF/INDEX.LIST",
            )
        }
    }
    lint {
        // MANAGE_EXTERNAL_STORAGE is intentional: the app is sideloaded, not published on Play.
        disable += "ScopedStorage"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.sshd.core)
    implementation(libs.sshd.sftp)
    implementation(libs.eddsa)
    implementation(libs.slf4j.nop)

    testImplementation(libs.junit)
}
