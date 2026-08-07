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

    js {
        nodejs {
            testTask {
                useMocha {
                    timeout = "30s"
                }
            }
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs {
            testTask {
                useMocha {
                    timeout = "30s"
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
