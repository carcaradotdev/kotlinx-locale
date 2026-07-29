import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.konan.target.HostManager

/**
 * Shared configuration for the published multiplatform library modules:
 * the full target set, explicit API mode, ABI validation, Android library
 * setup and publishing. The Android namespace derives from the module name
 * (kotlinx-locale-datetime -> dev.carcara.kotlinx.locale.datetime).
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    `maven-publish`
    id("kotlinx-locale-ktlint")
}

val libs = the<VersionCatalogsExtension>().named("libs")

kotlin {
    explicitApi()

    // Dumps the public ABI of every target to api/<module>.klib.api and
    // api/jvm/<module>.api. `checkKotlinAbi` compares the code against those
    // files; `updateKotlinAbi` rewrites them.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()

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
}

// The plugin wires checkKotlinAbi into `check` by default; detach it again. A
// complete comparison needs a klib for every target and only a macOS host can
// compile all of them, so `check` on Linux or Windows would quietly compare a
// subset of the ABI and report success. Run `./gradlew checkKotlinAbi` on a Mac
// instead.
//
// This is afterEvaluate rather than a lazy Provider chain because KGP adds the
// dependency from its own afterEvaluate and offers no lazy hook to intercept it,
// which is the third-party-bridge exception to the no-afterEvaluate rule. It was
// gradle.projectsEvaluated before, which registered a build-scoped callback that
// then mutated this project: the same job, but an Isolated Projects violation.
@OptIn(ExperimentalAbiValidation::class)
val abiCheckTaskName = kotlin.abiValidation.checkTaskProvider.name
afterEvaluate {
    tasks.named("check") {
        setDependsOn(dependsOn.filterNot { it is TaskProvider<*> && it.name == abiCheckTaskName })
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
