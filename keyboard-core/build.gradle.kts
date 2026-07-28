plugins {
    id("jacoco")
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.uncoalesced.stickykeys.keyboardcore"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Opt-in Compose compiler stability report:
//   .\gradlew.bat :keyboard-core:assembleDebug -PcomposeMetrics
// Writes which composables are skippable and which parameters are unstable to
// build/compose-reports. Off by default so ordinary builds stay fast.
composeCompiler {
    if (project.hasProperty("composeMetrics")) {
        metricsDestination.set(layout.buildDirectory.dir("compose-reports"))
        reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Incognito indicator icon (Icons.Default.Lock); version from Compose BOM
    implementation("androidx.compose.material:material-icons-core")
    implementation(project(":sticker-core"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.9")
    // Compose semantics assertions run under Robolectric, so accessibility labels,
    // roles and state descriptions are verified on the real semantics tree.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.withType<Test> {
    useJUnit()
    // Need this for robolectric to work nicely with jacoco
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        setExcludes(listOf("jdk.internal.*"))
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val fileFilter =
        mutableSetOf(
            "**/R.class",
            "**/R$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "android/**/*.*",
            "**/*_Impl*.*",
            "**/Dagger*.*",
            "**/*Module*.*",
        )
    val debugTree =
        fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
            exclude(fileFilter)
        }
    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get())
            .include("jacoco/testDebugUnitTest.exec"),
    )
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("jacocoTestReport")
    val fileFilter =
        mutableSetOf(
            "**/R.class",
            "**/R$*.class",
            "**/BuildConfig.*",
            "**/Manifest*.*",
            "**/*Test*.*",
            "android/**/*.*",
            "**/*_Impl*.*",
            "**/Dagger*.*",
            "**/*Module*.*",
        )
    val debugTree =
        fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
            exclude(fileFilter)
        }
    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get())
            .include("jacoco/testDebugUnitTest.exec"),
    )

    violationRules {
        rule {
            limit {
                minimum = 0.70.toBigDecimal()
            }
        }
    }
}
