import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * ktlint, with generated sources excluded.
 *
 * Applied by each module through the convention plugin it already uses, rather
 * than pushed onto every project from the root with `subprojects {}`. The root
 * cannot configure another project without reading its state, which is what
 * Isolated Projects forbids and what makes parallel configuration possible.
 */
plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

configure<KtlintExtension> {
    filter {
        // Generated sources carry a marker header and are not hand-formatted.
        // Matching on the header rather than on a path list means a new
        // generated file cannot quietly start being linted, or stop being.
        exclude { entry ->
            !entry.isDirectory &&
                entry.file.useLines { lines -> lines.firstOrNull()?.startsWith("// GENERATED") == true }
        }
    }
}
