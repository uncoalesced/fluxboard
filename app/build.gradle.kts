import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

// Release signing credentials, deliberately kept out of the repository:
// keystore.properties is gitignored and the keystore it points at lives outside
// the project tree entirely. When the file is absent -- a fresh clone, CI, or a
// contributor who only builds debug -- the release build stays unsigned rather
// than failing, which is what F-Droid wants anyway since it signs its own builds.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }
val hasReleaseSigning = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.uncoalesced.stickykeys"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.uncoalesced.stickykeys"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "v0.1.0-ALPHA"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        androidResources {
            localeFilters += "en"
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // v1 (JAR signing) is only needed below API 24; this app's floor is 26, so
                // AGP omits it and v2/v3 carry the signature. Left explicit so the intent
                // is visible rather than implied by defaults.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig =
                if (hasReleaseSigning) signingConfigs.getByName("release") else null
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // VERSION_NAME is shown in the settings brand header.
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
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

/**
 * Fails the release build if R8 strips a Room database's no-arg constructor.
 *
 * This exact failure shipped once: R8 kept StickyKeysDatabase_Impl but removed its
 * `<init>()`, because Room only reaches it reflectively via
 * `Class.forName(name + "_Impl").getDeclaredConstructor()`, so nothing in the compiled
 * code references it. The app died in Application.onCreate with NoSuchMethodException,
 * and nothing before the device caught it -- unit tests run un-minified, so the
 * constructor exists there no matter what R8 would have done to it.
 *
 * The only place this is observable without a phone is R8's own usage.txt, which lists
 * what was discarded. Reading it after every release build turns a crash-on-launch into
 * a build failure.
 */
val verifyRoomKeepRules by tasks.registering {
    group = "verification"
    description = "Asserts R8 kept the no-arg constructor of every Room *Database_Impl."
    val usageFile = layout.buildDirectory.file("outputs/mapping/release/usage.txt")
    outputs.upToDateWhen { false }

    doLast {
        val file = usageFile.get().asFile
        if (!file.exists()) {
            logger.lifecycle("verifyRoomKeepRules: no usage.txt (minification off) -- skipped.")
            return@doLast
        }

        // usage.txt format: a bare class name means the whole class went; a line ending
        // in ':' introduces a kept class whose listed, indented members were removed.
        val offenders = mutableListOf<String>()
        var currentClass: String? = null
        file.forEachLine { line ->
            when {
                line.isBlank() -> Unit
                !line.startsWith(" ") && line.endsWith(":") ->
                    currentClass = line.dropLast(1)
                !line.startsWith(" ") -> {
                    currentClass = null
                    if (line.endsWith("Database_Impl")) {
                        offenders += "$line -- entire class removed"
                    }
                }
                else -> {
                    val owner = currentClass
                    if (owner != null &&
                        owner.endsWith("Database_Impl") &&
                        line.trim().endsWith("<init>()")
                    ) {
                        offenders += "$owner.<init>() -- constructor removed"
                    }
                }
            }
        }

        if (offenders.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine(
                        "R8 removed Room database members that are only reached reflectively.",
                    )
                    appendLine(
                        "The release build would crash on launch with NoSuchMethodException.",
                    )
                    appendLine()
                    offenders.forEach { appendLine("  - $it") }
                    appendLine()
                    appendLine("Expected app/proguard-rules.pro to contain:")
                    appendLine(
                        "  -keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }",
                    )
                },
            )
        }
        logger.lifecycle("verifyRoomKeepRules: all Room *Database_Impl constructors survived R8.")
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(verifyRoomKeepRules)
}

// assembleRelease emits app-release.apk, which says nothing about what is inside it.
// This copies it out under a name that is unambiguous when handing it to someone.
val releaseVersionName = "v0.1.0-ALPHA"

tasks.register<Copy>("packageReleaseArtifact") {
    group = "distribution"
    description = "Copies the signed release APK to outputs/distributable under a clear name."
    dependsOn("assembleRelease")
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("app-release.apk")
    }
    into(layout.buildDirectory.dir("outputs/distributable"))
    rename { "FluxBoard-$releaseVersionName-release.apk" }
}

dependencies {
    implementation(project(":sticker-core"))
    implementation(project(":keyboard-core"))
    implementation(project(":transfer"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    // Icons.Default.Delete + Icons.AutoMirrored.Filled.ArrowBack (version from Compose BOM)
    implementation("androidx.compose.material:material-icons-core")
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation("io.github.g0dkar:qrcode-kotlin-android:4.5.0")
    // ZXing for QR scanning (Apache-2.0, no Play Services) -- see docs/repo-reference.md.
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.12.1")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.mockk:mockk:1.13.9")
    // Test-only: the core modules expose Room via `implementation`, so it is not on this
    // module's compile classpath. DatabaseStartupTest builds both databases directly.
    testImplementation(libs.room.runtime)
    // Accessibility assertions run against the real Compose semantics tree.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.51.1")
    kspAndroidTest("com.google.dagger:hilt-android-compiler:2.51.1")
}
