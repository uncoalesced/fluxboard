// Engineered by uncoalesced
//
// Coverage for the three library modules (keyboard-core, sticker-core, transfer).
// Each applies the jacoco plugin in its own plugins {} block and then this script
// with apply(from = ...). It used to be the same 81 lines pasted into all three,
// where changing an exclusion meant three edits and missing one only showed up as
// coverage that was quietly wrong. app has no coverage gate.
//
// Script plugins get no generated accessors, so the extensions are configured by type.

configure<JacocoPluginExtension> {
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

val coverageExcludes =
    setOf(
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

// Both paths moved under AGP 9. Its built-in Kotlin compiles into intermediates/ rather than
// tmp/kotlin-classes/, and the exec file is read from the test task itself rather than from a
// guessed location. The old paths matched nothing, so Gradle skipped both tasks for "no
// source" and the 0.70 gate in CI passed without measuring anything.
val debugKotlinClasses = "intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"
// A provider rather than tasks.named: AGP registers the test task after this script runs.
val debugUnitTestExec =
    provider {
        tasks
            .getByName<Test>("testDebugUnitTest")
            .extensions
            .getByType<JacocoTaskExtension>()
            .destinationFile!!
    }

fun JacocoReportBase.coverDebugUnitTests() {
    sourceDirectories.setFrom(files("${project.projectDir}/src/main/java"))
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir(debugKotlinClasses)) {
            exclude(coverageExcludes)
        },
    )
    executionData.setFrom(debugUnitTestExec)
}

// A missing input has to fail the build rather than skip the report. JacocoReport skips
// itself when its exec file is absent, so the check lives on the test task that should have
// written it -- skipping is exactly how this gate went quiet.
tasks.withType<Test>().matching { it.name == "testDebugUnitTest" }.configureEach {
    doLast {
        val classes = layout.buildDirectory.dir(debugKotlinClasses).get().asFile
        val exec = debugUnitTestExec.get()
        check(classes.isDirectory) { "Coverage: no compiled classes at $classes" }
        check(exec.isFile) { "Coverage: no execution data at $exec" }
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    coverDebugUnitTests()
}

tasks.register<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn("jacocoTestReport")
    coverDebugUnitTests()
    violationRules {
        rule {
            limit {
                minimum = 0.70.toBigDecimal()
            }
        }
    }
}
