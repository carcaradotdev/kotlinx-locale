// An included build rather than buildSrc: a change here invalidates only the
// consumers of the changed plugin, where a buildSrc change invalidates the whole
// build. Included via pluginManagement.includeBuild in the root settings file.
rootProject.name = "build-logic"

dependencyResolutionManagement {
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
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
