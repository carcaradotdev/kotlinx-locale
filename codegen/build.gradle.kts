plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
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

// Full pipeline: clone (if needed) + parse + generate Kotlin sources into the
// :kotlinx-locale and :kotlinx-locale-datetime modules.
tasks.register<JavaExec>("generateLocaleData") {
    group = "codegen"
    description = "Generate locale data Kotlin sources from CLDR, plus ICU golden test fixtures"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = mainClassFqn
    args("generate", rootDir.absolutePath)
}

dependencies {
    implementation(project(":kotlinx-locale-codegen"))
}

dependencies {
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
    // The round-trip test regenerates the shipped sources and diffs them, so it
    // needs to know where the checked-in ones are.
    systemProperty("kotlinx.locale.rootDir", rootDir.absolutePath)
}
