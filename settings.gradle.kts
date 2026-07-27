pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // Not FAIL_ON_PROJECT_REPOS: the Kotlin Gradle plugin injects project-level ivy
    // repositories (nodejs.org, Binaryen) to provision the JS/Wasm toolchains.
    repositories {
        mavenCentral()
        google {
            content {
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
                includeGroupAndSubgroups("androidx")
            }
        }
    }
}

rootProject.name = "kotlinx-locale-project"

include(":locale")
project(":locale").name = "kotlinx-locale"
include(":datetime")
project(":datetime").name = "kotlinx-locale-datetime"
include(":codegen")
