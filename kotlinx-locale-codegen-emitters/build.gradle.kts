// The half of code generation that a user's build can run: emitters plus the
// reader for the pre-resolved CLDR bundle. Nothing here clones a repository or
// parses XML, so it is safe on a build classpath.
plugins {
    id("kotlinx-locale-jvm")
    `maven-publish`
}

kotlin {
    explicitApi()
}

dependencies {
    testImplementation(libs.kotlin.test)
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
