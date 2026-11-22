plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.browsery.app"
    // Dependency Compose 1.12.x mensyaratkan compile terhadap API 37. targetSdk tetap 36.
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.browsery.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.2.0"

        // Stage 1: hanya arm64-v8a (sesuai artefak GeckoView yang dipakai).
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        // GeckoView memakai API Java 17.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.0")

    // Mesin web: GeckoView rilis stabil, arm64-v8a.
    implementation("org.mozilla.geckoview:geckoview-arm64-v8a:159.0.20261102144711")

    testImplementation("junit:junit:4.13.2")
}
