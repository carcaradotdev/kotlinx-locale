// Extraction: clones the pinned CLDR and ICU repositories and parses their XML.
// This half cannot run in a user's build, which is why the emitters live in
// :kotlinx-locale-codegen and the resolved data in :kotlinx-locale-cldr-data.
plugins {
    id("kotlinx-locale-jvm")
}

dependencies {
    implementation(project(":kotlinx-locale-codegen"))
    testImplementation(libs.kotlin.test)
}

val mainClassFqn = "dev.carcara.kotlinx.locale.codegen.MainKt"

// Clones the pinned CLDR and ICU repositories into codegen/repos/ (gitignored).
tasks.register<JavaExec>("cloneLocaleRepos") {
    group = "codegen"
    description = "Clone the pinned CLDR and ICU repositories into codegen/repos/"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = mainClassFqn
    args("clone", rootDir.absolutePath)
}

// Full pipeline: clone (if needed) + parse + write the bundle + generate every
// shipped Kotlin source from it.
tasks.register<JavaExec>("generateLocaleData") {
    group = "codegen"
    description = "Generate the CLDR bundle and every generated Kotlin source from it"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = mainClassFqn
    args("generate", rootDir.absolutePath)
}

tasks.test {
    // The round-trip test regenerates the shipped sources and diffs them, so it
    // needs to know where the checked-in ones are.
    systemProperty("kotlinx.locale.rootDir", rootDir.absolutePath)
}
