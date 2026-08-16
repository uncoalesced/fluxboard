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
        versionCode = 8
        versionName = "v0.1.6-BETA"

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
        // Tester diagnostics are excluded from this build type by source set, not by a
        // flag -- see keyboard-core/src/release and verifyNoUsageLoggingInRelease below.
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

/** The class that must never reach a stable build. Named once, used by the check below. */
private val usageLogClass = "com.uncoalesced.stickykeys.keyboardcore.diagnostics.UsageLog"

/**
 * Fails the release build if the tester usage log survives into it.
 *
 * The gate is a source set, not a flag: the recording implementation lives in
 * `keyboard-core/src/debug` and only a no-op lives in `src/release`, so a stable or F-Droid
 * build never compiles it.
 *
 * The first design used `BuildConfig.USAGE_LOGGING` instead, and this task is what proved it
 * did not work -- the class is constructor-injected into TypingViewModel, so Hilt's generated
 * factories referenced it unconditionally and R8 kept it whatever the flag said. A runtime
 * boolean cannot make a class stop existing.
 *
 * Checked against R8's mapping output rather than the DEX. An earlier version scanned the
 * DEX for the class name and passed even when the guarantee was deliberately broken, because
 * release builds are obfuscated and that string cannot appear either way. It proved nothing.
 * This version has been confirmed to fail when the class does survive.
 */

val verifyNoUsageLoggingInRelease by tasks.registering {
    group = "verification"
    description = "Asserts the alpha usage-log class is absent from the release APK."
    outputs.upToDateWhen { false }

    doLast {
        val mapping =
            layout.buildDirectory
                .file("outputs/mapping/release/mapping.txt")
                .get()
                .asFile
        if (!mapping.exists()) {
            logger.lifecycle("verifyNoUsageLoggingInRelease: no mapping.txt -- skipped.")
            return@doLast
        }

        // mapping.txt, not the DEX.
        //
        // The first version of this check scanned the release DEX for the string
        // "keyboardcore/diagnostics/UsageLog" and passed -- including when the gate was
        // deliberately flipped back on, which is how it was caught. R8 obfuscates release
        // class names, so that string cannot appear in a minified DEX whether the class
        // survived or not, and the check proved nothing at all.
        //
        // mapping.txt lists every class R8 *kept*, under its original name. Absence from it
        // is real evidence of removal.
        val survived =
            mapping.useLines { lines ->
                lines.any { line ->
                    !line.startsWith(" ") && line.startsWith(usageLogClass)
                }
            }

        if (survived) {
            throw GradleException(
                buildString {
                    appendLine("$usageLogClass survived into the release build.")
                    appendLine()
                    appendLine(
                        "The alpha/beta usage log must not exist in a stable or F-Droid build.",
                    )
                    appendLine(
                        "The recording implementation must stay in keyboard-core/src/debug " +
                            "only, with src/release supplying NoOpUsageRecorder. Check that " +
                            "nothing in src/main references UsageLog by its concrete type " +
                            "instead of the UsageRecorder interface.",
                    )
                },
            )
        }
        logger.lifecycle(
            "verifyNoUsageLoggingInRelease: $usageLogClass absent from the release build.",
        )
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(verifyRoomKeepRules, verifyNoUsageLoggingInRelease)
}

// assembleRelease emits app-release.apk, which says nothing about what is inside it.
// This copies it out under a name that is unambiguous when handing it to someone.
//
// Read from defaultConfig rather than repeated as a literal. It was a literal, and it did
// exactly what a second copy of a value always does: versionName went to v0.1.4-ALPHA and this
// stayed at v0.1.2-ALPHA, so the artifact built for the v0.1.4 tag was handed over named
// v0.1.2. The APK was correct inside; only the name on the box was wrong, which is the version
// of this failure most likely to be believed.
val releaseVersionName: String =
    android.defaultConfig.versionName
        ?: error("versionName is not set; the release artifact would be named after nothing.")

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
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.mockk:mockk:1.14.11")
    // Test-only: the core modules expose Room via `implementation`, so it is not on this
    // module's compile classpath. DatabaseStartupTest builds both databases directly.
    testImplementation(libs.room.runtime)
    // Accessibility assertions run against the real Compose semantics tree.
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.60.1")
    kspAndroidTest("com.google.dagger:hilt-android-compiler:2.60.1")
}
