/*
 * Copyright 2026 Carcara.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.konan.target.HostManager

/**
 * The multiplatform setup every module in this build shares: the full target
 * set, explicit API mode and Android library setup. The Android namespace
 * derives from the module name (kotlinx-locale-datetime ->
 * dev.carcara.kotlinx.locale.datetime).
 *
 * Nothing here publishes. `kotlinx-locale-multiplatform` adds publication and
 * ABI validation on top, and a module that ships to nobody — the conformance
 * suite, which exists for this build's own test source sets — applies this one
 * directly and so has neither.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    // Applied to every multiplatform module rather than only the ones with tests
    // today. The compiler plugin instruments source sets whose name matches
    // [tT]est and emits nothing where there are none, so applying it once here
    // is what stops a module's first commonTest from being silently unreachable.
    id("de.infix.testBalloon")
    id("kotlinx-locale-coverage")
    id("kotlinx-locale-ktlint")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    explicitApi()

    jvmToolchain(21)

    jvm()

    androidLibrary {
        namespace = "dev.carcara." + project.name.replace('-', '.')
        compileSdk = libs.findVersion("android-compileSdk").get().requiredVersion.toInt()
        minSdk = libs.findVersion("android-minSdk").get().requiredVersion.toInt()

        withHostTestBuilder {}
    }

    // Mocha times a whole test node, and a conformance node is a whole case set:
    // one node compares every skeleton in a locale rather than reporting one
    // green line per pattern. Thirty seconds was sized for a test that asserts
    // one thing and is not enough for one that asserts a few thousand. The cap
    // still exists, so a node that hangs still fails rather than wedging CI.
    js {
        nodejs {
            testTask {
                useMocha {
                    timeout = "120s"
                }
            }
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs {
            testTask {
                useMocha {
                    timeout = "120s"
                }
            }
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmWasi {
        nodejs()
    }

    // Native targets follow the tiers of <https://kotlinlang.org/docs/native-target-support.html>,
    // matching the targets published by kotlinx-datetime.
    // Tier 1
    macosArm64()
    iosSimulatorArm64()
    iosArm64()
    // Tier 2
    linuxX64()
    linuxArm64()
    watchosSimulatorArm64()
    watchosArm32()
    watchosArm64()
    tvosSimulatorArm64()
    tvosArm64()
    // Tier 3
    androidNativeArm32()
    androidNativeArm64()
    androidNativeX86()
    androidNativeX64()
    iosX64()
    mingwX64()
    watchosDeviceArm64()
    // Deprecated x64 Apple targets, still published by kotlinx-datetime (KT-78660)
    @Suppress("DEPRECATION", "DEPRECATION_ERROR")
    run {
        macosX64()
        watchosX64()
        tvosX64()
    }

    // Materialise the default hierarchy now, so the Apple source sets the two
    // sets below hang off exist while this block is still configuring.
    applyDefaultHierarchyTemplate()

    // Apple's NSUInteger, and so every Foundation enum typed by it, is 32 bits
    // wide on two watch targets and 64 everywhere else. watchosArm32 is armv7k,
    // and watchosArm64 is arm64_32, a 64-bit instruction set with 32-bit
    // pointers. watchosDeviceArm64 is plain arm64 and belongs with the rest
    // despite the name, which is the trap here. Kotlin refuses a type of varying
    // width in a source set that spans both, which appleMain does, so the
    // Foundation calls that name one live in these two instead.
    //
    // Both sets are children of appleMain rather than replacements for it: the
    // shared Apple code stays in one place, and only the handful of declarations
    // that mention a varying width are written twice.
    val appleIlp32Main by sourceSets.creating { dependsOn(sourceSets.getByName("appleMain")) }
    val appleLp64Main by sourceSets.creating { dependsOn(sourceSets.getByName("appleMain")) }
    for (name in listOf("watchosArm32Main", "watchosArm64Main")) {
        sourceSets.getByName(name).dependsOn(appleIlp32Main)
    }
    for (name in listOf(
        "macosArm64Main", "macosX64Main",
        "iosArm64Main", "iosSimulatorArm64Main", "iosX64Main",
        "tvosArm64Main", "tvosSimulatorArm64Main", "tvosX64Main",
        "watchosSimulatorArm64Main", "watchosX64Main", "watchosDeviceArm64Main",
    )) {
        sourceSets.getByName(name).dependsOn(appleLp64Main)
    }

    // Declared here rather than in forty build files. The TestBalloon Gradle
    // plugin instruments the test compilations but puts nothing on their
    // classpath, so without this a module compiles and reports no tests, which
    // is the one failure that looks like success.
    //
    // The assertions come from :test-assertions rather than kotlin-test. Both
    // frameworks emit a `startUnitTests` entry point, and a Kotlin/Wasm module
    // cannot export one name twice, so having both on a compilation makes the
    // Wasm targets untestable. One framework, every target.
    sourceSets.commonTest.dependencies {
        implementation(libs.findLibrary("testballoon-framework-core").get())
        implementation(libs.findLibrary("testballoon-matrix").get())
        implementation(libs.findLibrary("kotest-assertions-core").get())
        // Guarded because this plugin also builds the assertions module itself.
        if (project.name != "test-assertions") implementation(project(":test-assertions"))
    }

    // Android runs host tests through JUnit 4, and TestBalloon integrates there
    // with a runner rather than with an engine. That runner needs JUnit 4 on the
    // classpath to be found at all; without it the task compiles the tests, finds
    // nothing to run, and Gradle reports "did not discover any tests".
    //
    // It used to arrive by accident, transitively through kotlin-test. Declared
    // here now that kotlin-test is gone, and scoped to the one source set that
    // needs it rather than added everywhere.
    sourceSets.named("androidHostTest").dependencies {
        implementation(libs.findLibrary("junit4").get())
    }
}

// A Test task with no ceiling takes a quarter of physical memory, so the same
// build reserves 1 GB on a CI runner and 12 GB on a developer's machine, and
// only the second one is a problem. Nothing here needs a large heap: the tables
// are string constants and the conformance runs stream over them.
//
// Applies to the JVM and Android host test tasks. Kotlin/Native, JS and Wasm
// tests do not run in a JVM and are bounded by kotlin.native.jvmArgs and the
// worker cap instead.
tasks.withType<Test>().configureEach {
    maxHeapSize = "1g"
}

// The test framework declares kotlinx-datetime 0.7.1, and this build pins 0.8.0.
// A constraint rather than a dependency: it settles the version wherever the
// framework drags the library in, without putting it on the classpath of the
// modules that do not use dates. Without it, a module that depends on
// kotlinx-datetime compiles its tests against 0.8.0 and one that does not
// compiles them against 0.7.1, which is two klibs of the same library in one
// build for no reason anyone chose.
dependencies {
    constraints {
        "commonTestImplementation"(libs.findLibrary("kotlinx-datetime").get())
    }
}

// wasmWasi publishes and compiles; it does not run tests.
//
// The test framework's data layer, `at.asitplus.testballoon:matrix`, publishes
// twenty-four of this build's twenty-five targets and omits wasm-wasi alone.
// Everything under it — kotest-property, kotest-assertions-core,
// testBalloon-framework-core — already publishes that target, so this is one
// absent variant in one addon rather than a limit of the platform. Tracked
// upstream at a-sit-plus/testballoon-addons; when it lands, delete this block
// and wasmWasi rejoins the test matrix with no other change.
//
// wasmJs used to be here too, for a different reason: kotlin-test and
// TestBalloon each exported a `startUnitTests` function and a Wasm module cannot
// export one name twice. That is fixed rather than worked around — the
// assertions now come from :test-assertions and kotlin-test is off the
// classpath — so wasmJs runs its tests again.
//
// Two halves, and both are needed. The exclusion keeps dependency resolution
// working for anything that reads a wasmWasi test configuration, IDE sync
// included, instead of failing to find a variant that does not exist. Disabling
// the tasks is what stops the compile that would then fail on unresolved
// references. `compileKotlinWasmWasi` matches neither pattern and still runs, so
// the target has to keep compiling.
configurations.matching { it.name.startsWith("wasmWasiTest") }.configureEach {
    exclude(group = "at.asitplus.testballoon")
    exclude(group = "io.kotest")
}

// The Kotlin Gradle plugin puts kotlin-test on a JS or Wasm test compilation by
// itself, whether or not anything declared it. On Wasm that is what produced two
// `startUnitTests` exports and made the target untestable: removing the
// dependency from this build's own source sets did nothing, because it was never
// this build that added it.
//
// Excluded rather than tolerated because the assertions come from
// :test-assertions now and TestBalloon registers its own entry point, so
// kotlin-test has no job left here.
configurations.matching { it.name.startsWith("wasmJsTest") }.configureEach {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-test")
}

// Anchored on the target name rather than the end of the string: the executable
// compilations append Optimize, so `...KotlinWasmWasi$` would miss half of them
// and leave a compile running against a classpath the exclusion emptied.
val wasmWasiTestTasks = Regex("""^(compileTest\w*KotlinWasmWasi\w*|wasmWasi\w*Test)$""")
tasks.matching { wasmWasiTestTasks.matches(it.name) }.configureEach {
    enabled = false
}

// Skip Apple simulator tests whose runtime is not installed in the local Xcode
// (e.g. a Mac with the iOS runtime but not the tvOS one).
if (HostManager.hostIsMac) {
    val installedRuntimes = providers.exec {
        commandLine("xcrun", "simctl", "list", "runtimes", "-j")
    }.standardOutput.asText

    tasks.withType<KotlinNativeSimulatorTest>().configureEach {
        val family = when {
            name.startsWith("ios") -> "iOS"
            name.startsWith("watchos") -> "watchOS"
            name.startsWith("tvos") -> "tvOS"
            else -> return@configureEach
        }
        val runtimeInstalled = installedRuntimes.map {
            it.contains("com.apple.CoreSimulator.SimRuntime.$family-")
        }
        onlyIf("a $family simulator runtime is installed") { runtimeInstalled.get() }
    }
}
