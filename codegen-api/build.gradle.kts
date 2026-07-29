// The half of code generation that a user's build can run: emitters plus the
// reader for the pre-resolved CLDR bundle. Nothing here clones a repository or
// parses XML, so it is safe on a build classpath.
plugins {
    kotlin("jvm")
    `maven-publish`
}

kotlin {
    explicitApi()
    jvmToolchain(21)
}

dependencies {
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
