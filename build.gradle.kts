// Plugin versions come from the buildSrc classpath (see buildSrc/build.gradle.kts);
// library modules apply the kotlinx-locale-multiplatform convention plugin.

import org.jlleitschuh.gradle.ktlint.KtlintExtension

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<KtlintExtension> {
        filter {
            // Generated sources carry a marker header and are not hand-formatted.
            exclude { entry ->
                !entry.isDirectory &&
                    entry.file.useLines { lines -> lines.firstOrNull()?.startsWith("// GENERATED") == true }
            }
        }
    }
}

// Runs every Kotlin/JS probe and prints what each dependency set costs. The
// budgets live with the probes; this only collects what they measured.
val sizeProbes = project(":tools").subprojects.map { it.name }

tasks.register<SizeReport>("sizeReport") {
    group = "verification"
    description = "Builds every size probe and prints the gzipped bundle table"
    dependsOn(sizeProbes.map { ":tools:$it:checkSize" })
    reports.from(sizeProbes.map { layout.projectDirectory.file("tools/$it/build/reports/size/bundle.tsv") })
}

// Guards the property the whole layering rests on: hand-written code never names
// a specific Country or Currency entry, so a -cldr from Maven links against a
// -types the Gradle plugin narrowed. See CheckLayeringRule in buildSrc.
tasks.register<CheckLayeringRule>("checkLayeringRule") {
    group = "verification"
    description = "Fails when hand-written main sources name a specific Country or Currency entry"
    // The published modules only. Every artifact name ends in one of the three
    // layers, and tools/ is not published, so nothing there ships an entry.
    sources.from(
        fileTree(rootDir) {
            include("*-types/src/*Main/**/*.kt")
            include("*-core/src/*Main/**/*.kt")
            include("*-cldr/src/*Main/**/*.kt")
            exclude("**/build/**")
        },
    )
    rootDirectory = layout.projectDirectory
}
