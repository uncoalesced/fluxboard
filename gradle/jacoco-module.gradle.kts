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

fun JacocoReportBase.coverDebugUnitTests() {
    sourceDirectories.setFrom(files("${project.projectDir}/src/main/java"))
    classDirectories.setFrom(
        fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") {
            exclude(coverageExcludes)
        },
    )
    executionData.setFrom(
        fileTree(layout.buildDirectory.get())
            .include("jacoco/testDebugUnitTest.exec"),
    )
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
