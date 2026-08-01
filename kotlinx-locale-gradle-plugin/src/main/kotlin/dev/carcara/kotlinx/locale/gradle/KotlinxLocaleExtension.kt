package dev.carcara.kotlinx.locale.gradle

import dev.carcara.kotlinx.locale.LocaleRef
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

/**
 * What to generate.
 *
 * ```
 * kotlinxLocale {
 *     locales(PT.BR, EN.US, JA)
 *     fallback(EN.US)
 *     packageName = "com.example.locale"
 *
 *     country { names = true }
 *     currency { names = true; formats = true }
 *     datetime { patterns = true; skeletons = true }
 * }
 * ```
 *
 * Locales can be given as [LocaleRef] values from `kotlinx-locale-types` or as
 * tags. The refs are worth preferring: a typo in a tag does not throw, it
 * quietly generates data for one locale fewer than intended, and this is a build
 * script so nothing will fail at runtime either.
 *
 * A flag turns on a [LocaleFeature], which carries the set of tables generating
 * it needs. Turning one on can therefore pull in a table another flag also
 * names, and that is deliberate: it is what makes a half-configured source set
 * impossible to express.
 */
abstract class KotlinxLocaleExtension @Inject constructor(objects: ObjectFactory) {

    /** The locales to generate, as canonical BCP 47 tags. */
    abstract val locales: SetProperty<String>

    /**
     * The locale that answers for anything not generated.
     *
     * Required, and required to be one of [locales]. Without it a narrowed
     * source would return nothing for an unlisted locale, and a date has no code
     * to degrade to the way a country or a currency does, so "no data" would
     * surface as an ISO 8601 timestamp in the middle of a translated screen.
     */
    abstract val fallbackLocale: Property<String>

    /** The package the generated sources go into. */
    abstract val packageName: Property<String>

    /**
     * The prefix on the generated source objects, so `Generated` yields
     * `GeneratedCountryNames`.
     *
     * Configurable because a project may want more than one set: a narrow
     * default and a full one behind a lazy load.
     */
    abstract val objectPrefix: Property<String>

    val country: CountryFeatures = objects.newInstance(CountryFeatures::class.java)

    val currency: CurrencyFeatures = objects.newInstance(CurrencyFeatures::class.java)

    val datetime: DateTimeFeatures = objects.newInstance(DateTimeFeatures::class.java)

    private val blocks: List<FeatureBlock> get() = listOf(country, currency, datetime)

    /** Adds locales by reference, which is the form the compiler checks. */
    fun locales(vararg refs: LocaleRef) {
        locales.addAll(refs.map { it.tag })
    }

    /** Adds locales by tag, for a set read from a file or built at configuration time. */
    fun locales(vararg tags: String) {
        locales.addAll(tags.toList())
    }

    fun fallback(ref: LocaleRef) {
        fallbackLocale.set(ref.tag)
    }

    fun fallback(tag: String) {
        fallbackLocale.set(tag)
    }

    fun country(action: Action<CountryFeatures>) {
        action.execute(country)
    }

    fun currency(action: Action<CurrencyFeatures>) {
        action.execute(currency)
    }

    fun datetime(action: Action<DateTimeFeatures>) {
        action.execute(datetime)
    }

    /**
     * Everything asked for, in a stable order so the task's input hash does not
     * depend on the order the DSL happened to be written in.
     */
    internal fun requestedFeatures(): Set<LocaleFeature> = blocks
        .flatMap { block -> block.enabled.filterValues { it.get() }.keys }
        .toSortedSet(compareBy(LocaleFeature::ordinal))

    /** True when nothing at all was asked for, which is worth failing on rather than generating an empty source set. */
    internal fun generatesNothing(): Boolean = requestedFeatures().isEmpty()
}

/**
 * One `kotlinxLocale { }` sub-block.
 *
 * The flags register themselves as they are declared, so the conventions, the
 * "generates nothing" check and the task input read the block rather than each
 * carrying a hand-maintained copy of the same list.
 */
abstract class FeatureBlock(private val objects: ObjectFactory) {

    internal val enabled: MutableMap<LocaleFeature, Property<Boolean>> = LinkedHashMap()

    protected fun flag(feature: LocaleFeature): Property<Boolean> = objects.property(Boolean::class.java).also {
        it.convention(false)
        enabled[feature] = it
    }
}

abstract class CountryFeatures @Inject constructor(objects: ObjectFactory) : FeatureBlock(objects) {

    /** Localized country names, behind `Country.displayName`. */
    val names: Property<Boolean> = flag(LocaleFeature.COUNTRY_NAMES)
}

abstract class CurrencyFeatures @Inject constructor(objects: ObjectFactory) : FeatureBlock(objects) {

    /** Localized currency symbols and display names. */
    val names: Property<Boolean> = flag(LocaleFeature.CURRENCY_NAMES)

    /**
     * Number patterns, for `CurrencyAmount.format` and `parseFormatted`.
     *
     * Generates the name tables as well, because a pattern substitutes the
     * symbol into itself and a pattern without one would render a hole.
     */
    val formats: Property<Boolean> = flag(LocaleFeature.CURRENCY_FORMATS)
}

abstract class DateTimeFeatures @Inject constructor(objects: ObjectFactory) : FeatureBlock(objects) {

    /** Date and time patterns plus month and weekday names. */
    val patterns: Property<Boolean> = flag(LocaleFeature.DATETIME_PATTERNS)

    /**
     * Skeleton formatting: `format(date, "yMMMd", locale)` and the pattern
     * behind it.
     *
     * Generates the pattern tables as well, because matching a skeleton scores
     * against the locale's standard date and time patterns and rendering the
     * winner needs its month and weekday names. Worth asking for deliberately:
     * across all locales the tables are the larger half of the datetime data,
     * which is why the shipped build puts them in their own artifact.
     */
    val skeletons: Property<Boolean> = flag(LocaleFeature.DATETIME_SKELETONS)
}
