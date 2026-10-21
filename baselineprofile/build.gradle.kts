// Engineered by uncoalesced
//
// Generates the app's Baseline Profile: a list of the classes and methods used at startup and
// while typing, which ART compiles ahead of time on install so the first launch and the first
// keystrokes do not run interpreted. Nothing in this module ships; only the generated profile
// file does, inside the app.
//
// Generate on a device running Android 13+ or a rooted one:
//   .\gradlew.bat :app:generateBaselineProfile
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.uncoalesced.stickykeys.baselineprofile"
    compileSdk = 36

    defaultConfig {
        // Macrobenchmark needs API 28; profile capture without root needs 33.
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The benchmark build types install the app under this id (see app/build.gradle.kts),
        // beside the user's own copy. The plugin does not pass it on connected devices.
        testInstrumentationRunnerArguments["targetAppId"] = "com.uncoalesced.stickykeys.benchmark"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    targetProjectPath = ":app"
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
