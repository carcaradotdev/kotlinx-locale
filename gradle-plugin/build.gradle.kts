// The published Gradle plugin. It runs the emitters from
// :kotlinx-locale-codegen against the bundle the consumer resolves from
// :kotlinx-locale-cldr-data.
plugins {
    `java-gradle-plugin`
    id("kotlinx-locale-jvm")
    `maven-publish`
}

dependencies {
    // The emitters are pinned to the plugin: a plugin and an emitter that
    // disagree about the generator API is not a combination anyone should be
    // able to assemble. The data is not pinned, so a consumer can move to a
    // newer CLDR release without waiting for a plugin release.
    implementation(project(":kotlinx-locale-codegen"))

    // Only to wire the generated directory into the right Kotlin source set. The
    // consumer's own Kotlin plugin provides it at runtime, so compileOnly.
    compileOnly(libs.gradle.plugin.kotlin.multiplatform)

    // LocaleRef so the DSL can take a locale rather than a string, and the
    // catalog so a consumer can write Pt.BR in their own build script.
    api(project(":kotlinx-locale-core"))
    api(project(":kotlinx-locale-types"))

    testImplementation(libs.kotlin.test)
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("kotlinxLocale") {
            id = "dev.carcara.kotlinx-locale"
            implementationClass = "dev.carcara.kotlinx.locale.gradle.KotlinxLocalePlugin"
            displayName = "kotlinx-locale"
            description = "Generates a locale data set narrowed to the locales a build declares"
        }
    }
}

// Strict validation so a missing annotation or an implicit ABSOLUTE path
// sensitivity on the generation task fails rather than warns.
tasks.withType<org.gradle.plugin.devel.tasks.ValidatePlugins>().configureEach {
    failOnWarning = true
    enableStricterValidation = true
}

// The plugin needs its own version to default the bundle dependency to a
// matching release. Writing it as a resource beats hard-coding a string that
// would be wrong the first time nobody remembers to edit it.
val pluginVersion by tasks.registering(WriteProperties::class) {
    destinationFile = layout.buildDirectory.file("generated/version/kotlinx-locale-plugin.properties")
    property("version", project.version.toString())
}

sourceSets.main {
    output.dir(pluginVersion.map { it.destinationFile.get().asFile.parentFile })
}

tasks.test {
    // The functional tests need the bundle to narrow, and resolving it from
    // Maven inside a nested build would mean publishing first. Handing them the
    // checked-in resource directly keeps them fast and hermetic.
    systemProperty(
        "kotlinx.locale.bundle",
        rootProject.layout.projectDirectory
            .file("cldr-data/src/main/resources/dev/carcara/kotlinx/locale/cldr-data.txt")
            .asFile.absolutePath,
    )
}
