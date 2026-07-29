// A standalone build, deliberately not part of the root build: it drags in the
// webpack toolchain and would otherwise pollute the root `kotlin-js-store/yarn.lock`
// and slow down `./gradlew build` in CI. It consumes the library modules through
// an included build, so it always measures the working tree, never a published
// artifact.
pluginManagement {
    val catalog = java.io.File(settingsDir, "../../gradle/libs.versions.toml").readText()
    val kotlinVersion = Regex("""^kotlin\s*=\s*"([^"]+)"""", RegexOption.MULTILINE)
        .find(catalog)!!
        .groupValues[1]

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("org.jetbrains.kotlin.multiplatform") version kotlinVersion
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "js-size"

includeBuild("../..")
