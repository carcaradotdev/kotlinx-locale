// The CLDR intermediate, versioned by the release it came from. The Gradle
// plugin resolves this from Maven instead of cloning CLDR, so generation needs
// no network beyond dependency resolution and the version is visible in the
// consumer's lock file.
plugins {
    id("kotlinx-locale-jvm")
    `maven-publish`
}

publishing {
    publications {
        // A plain JVM project has no publication until one is declared; the
        // Kotlin Multiplatform plugin creates them, this one does not.
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
