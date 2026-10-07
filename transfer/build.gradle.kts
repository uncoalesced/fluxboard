// Engineered by uncoalesced
plugins {
    id("jacoco")
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.uncoalesced.stickykeys.transfer"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// This module has no UI. It is pairing, packaging, crypto and sockets, and the app
// module owns every screen that drives it -- including the QR pairing screen, which
// declares ZXing and qrcode-kotlin itself. Compose, activity-compose, core-ktx and
// the two QR libraries were all declared here and imported by nothing: there is not
// one androidx import in transfer/src/main. Adding a dependency back means a source
// file needs it, not that a sibling module has it.
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
}

apply(from = rootProject.file("gradle/jacoco-module.gradle.kts"))
