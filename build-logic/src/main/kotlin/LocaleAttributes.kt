import org.gradle.api.attributes.Attribute

/**
 * The attributes that let one project consume another's build output without
 * either of them reading the other's state.
 *
 * Sharing through a dependency configuration is the Isolated-Projects-safe way
 * to move data between projects: the producer declares what it offers, the
 * consumer declares what it wants, and the task dependency falls out of the
 * data flow rather than being asserted with a task path.
 */
object LocaleAttributes {

    /** Marks an artifact as something other than a normal library jar. */
    val KIND: Attribute<String> = Attribute.of("dev.carcara.locale.kind", String::class.java)

    /** One probe's measured bundle size, as a TSV row. */
    const val SIZE_REPORT: String = "size-report"
}
