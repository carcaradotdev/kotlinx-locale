import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension

/**
 * Line and branch coverage over the code a person wrote.
 *
 * ## Why the filters are the whole design
 *
 * 1003 of this build's 1160 main source files are generated: locale tables,
 * country and currency enums, the typed locale catalog. Measured raw, coverage
 * reports on whether a test happened to touch a particular string constant in
 * `LocaleData_q.kt`, and the headline number moves when CLDR adds a locale.
 * That is worse than no number, because it looks like one.
 *
 * What is worth measuring is the ~157 hand-written files: the parsers, the
 * matchers, the renderers, the fallback ladders. Those are where a bug can hide,
 * and they are what the conformance suites exercise indirectly. Coverage here
 * answers a question the conformance suites cannot: which branch has nothing
 * pointing at it at all.
 *
 * ## Why it only measures the JVM
 *
 * Kover instruments JVM bytecode. The sources are common, so a line covered on
 * the JVM is the same line on every other target; what the JVM number cannot see
 * is an `actual` declaration in a native or JS source set. Those are few and are
 * listed as a known limit rather than papered over.
 */
plugins {
    id("org.jetbrains.kotlinx.kover")
}

extensions.configure<KoverProjectExtension> {
    reports {
        filters {
            excludes {
                // Generated tables. Named by package rather than by file, because
                // the emitters decide file names and a glob over those would rot
                // the first time one is renamed.
                packages(
                    "dev.carcara.kotlinx.locale.*.internal.data",
                    "dev.carcara.kotlinx.locale.internal.data",
                    // The typed locale catalog: 322 generated enums, one per CLDR
                    // language, whose every member is a constant.
                    "dev.carcara.kotlinx.locale.catalog",
                    // Conformance fixtures, which are test data rather than code.
                    "dev.carcara.kotlinx.locale.*.conformance",
                    "dev.carcara.kotlinx.locale.conformance",
                )
                // The generated source objects: `CldrCountry`, `CldrNumber` and
                // the rest are emitter output that binds a table to an interface.
                // The interface is hand-written and measured; the binding is not.
                classes(
                    "dev.carcara.kotlinx.locale.*.cldr.Cldr*",
                    "dev.carcara.kotlinx.locale.*.cldr.*.Cldr*",
                )
                // Enum plumbing the compiler writes.
                annotatedBy("kotlin.jvm.JvmSynthetic")
            }
        }

        total {
            xml {
                // Consumed by the coverage workflow; the HTML is for a person.
                onCheck = false
            }
            html {
                onCheck = false
            }
        }
    }
}
