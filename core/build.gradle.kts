import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

kotlin {
    explicitApi()

    jvmToolchain(21)

    jvm()

    androidLibrary {
        namespace = "dev.srsouza.kotlinx.datetime.locale"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

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

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
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
