import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * Shared Compose Multiplatform module: every game engine AND the entire
 * console UI (shell, screens, themes) live in commonMain. Platform seams
 * (audio, prefs, webview) have androidMain/iosMain actuals. Android app and
 * iOS app are thin entry points on top of this module.
 */
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    androidLibrary {
        namespace = "com.jasmeet.pixeldrop.shared"
        compileSdk = 36
        minSdk = 24

        // res/raw chimes and the gameplay loop live in this module now.
        androidResources {
            enable = true
        }

        // Engine unit tests live in src/androidHostTest (plain JVM, JUnit4).
        withHostTestBuilder {}.configure {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.animation)
            implementation(compose.material3)
            implementation(compose.ui)
            // Multiplatform back handling (androidx.compose.ui.backhandler).
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.11.1")
            // Multiplatform lifecycle: LocalLifecycleOwner + observers in
            // PixelDropApp. Version is the documented pairing for CMP 1.11.x.
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.11.0-beta01")
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.junit)
        }
    }
}
