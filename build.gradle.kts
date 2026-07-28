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
