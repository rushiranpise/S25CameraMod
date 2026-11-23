import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.rushiranpise.cameramod"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.rushiranpise.cameramod"
        minSdk = 33
        targetSdk = 36
        versionCode = 10
        versionName = "1.0.0"

        // DexKit is a native library. The S25 Ultra is arm64-only.
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Keep the same certificate as the debug build so release APKs install as updates.
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Supplied at runtime by LSPosed / Vector.
    compileOnly("de.robv.android.xposed:api:82")

    // Version-resilient lookup of the obfuscated camera zoom method.
    implementation("org.luckypray:dexkit:2.3.0")

    // Material 3 Expressive settings UI. 1.5.0-alpha18 is the last expressive release that
    // supports AGP 8.x / compileSdk 36; later 1.5.0 builds require AGP 9.1 and compileSdk 37.
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.compose.ui:ui:1.11.0-beta02")
    implementation("androidx.compose.foundation:foundation:1.11.0-beta02")
}
