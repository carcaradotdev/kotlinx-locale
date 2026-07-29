// A standalone build, deliberately: it consumes kotlinx-locale the way a real
// project does, from a repository, rather than through project dependencies that
// would hide whether the published artifacts actually work together.
pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

rootProject.name = "narrowed"
