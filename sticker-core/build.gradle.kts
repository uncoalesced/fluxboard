// Engineered by uncoalesced
plugins {
    id("jacoco")
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.uncoalesced.stickykeys.stickercore"
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // For GIF encoding
    implementation("com.shakster:gifkt-jvm:0.3.3")

    // For Animated WebP encoding
    implementation("com.aureusapps.android:webp-android:1.1.2")

    // No automatic segmentation engine in v1. ML Kit Subject Segmentation was
    // removed because it pulls Play Services and the com.google.android.datatransport
    // (CCT logging) transport, which cannot coexist with the zero-telemetry rule.
    // Sticker extraction is manual-eraser-only until a real on-device model exists.

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}

apply(from = rootProject.file("gradle/jacoco-module.gradle.kts"))
