/**
 * The repo-wide verification tasks, applied by the root project only.
 *
 * Both tasks read across the build, so both take the Isolated-Projects-safe
 * route: the layering check reads source files it declares as inputs, and the
 * size report consumes each probe's measurement through a dependency
 * configuration rather than by naming another project's tasks.
 */
plugins {
    // For the `check` lifecycle task the layering check hangs off.
    base
    id("kotlinx-locale-ktlint")
}

// Guards the property the whole layering rests on: hand-written code never names
// a specific Country or Currency entry, so a -cldr from Maven links against a
// -types the Gradle plugin narrowed.
val checkLayeringRule = tasks.register<CheckLayeringRule>("checkLayeringRule") {
    group = "verification"
    description = "Fails when hand-written main sources name a specific Country or Currency entry"
    // The published modules only. Every artifact name ends in one of the three
    // layers, and tools/ is not published, so nothing there ships an entry.
    sources.from(
        layout.projectDirectory.asFileTree.matching {
            include("*-types/src/*Main/**/*.kt")
            include("*-core/src/*Main/**/*.kt")
            include("*-cldr/src/*Main/**/*.kt")
            include("*-cldr-format/src/*Main/**/*.kt")
            exclude("**/build/**")
        },
    )
    rootDirectory = layout.projectDirectory
    stamp = layout.buildDirectory.file("reports/layering/checked.txt")
}

// Each probe offers its measurement through a consumable configuration and this
// project consumes it, so nothing reads across projects. Gradle 9 keeps the
// three roles separate: dependencies are declared against a dependency scope,
// and the resolvable configuration extends it.
val sizeProbes = configurations.dependencyScope("sizeProbes")

val sizeReports = configurations.resolvable("sizeReports") {
    extendsFrom(sizeProbes.get())
    attributes { attribute(LocaleAttributes.KIND, LocaleAttributes.SIZE_REPORT) }
}

val probeDirectories = providers
    .fileContents(layout.projectDirectory.file("gradle/size-probes.txt"))
    .asText
    .map { text ->
        text.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toList()
    }

// The probe list has to steer configuration, since it decides which projects
// this one depends on, so resolving it here is deliberate rather than an
// accidental eager read.
probeDirectories.get().forEach { probe ->
    dependencies.add(sizeProbes.name, dependencies.project(mapOf("path" to ":tools:$probe")))
}

tasks.register<SizeReport>("sizeReport") {
    group = "verification"
    description = "Builds every size probe and prints the gzipped bundle table"
    // Consuming the configuration carries the dependency on every probe's
    // checkSize task, so there is no task path to get wrong.
    reports.from(sizeReports)
    expectedProbes = probeDirectories.map { it.size }
}

tasks.named("check") {
    dependsOn(checkLayeringRule)
}
