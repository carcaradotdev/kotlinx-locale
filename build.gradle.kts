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

// Guards the property the whole layering rests on: hand-written code never names
// a specific Country or Currency entry, so a -cldr from Maven links against a
// -types the Gradle plugin narrowed. See CheckLayeringRule in buildSrc.
tasks.register<CheckLayeringRule>("checkLayeringRule") {
    group = "verification"
    description = "Fails when hand-written main sources name a specific Country or Currency entry"
    sources.from(
        fileTree(rootDir) {
            include("*/src/*Main/**/*.kt")
            exclude("**/build/**")
        },
    )
    rootDirectory = layout.projectDirectory
}
