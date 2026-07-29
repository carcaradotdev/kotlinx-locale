package dev.carcara.kotlinx.locale.gradle

import dev.carcara.kotlinx.locale.LocaleRef
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import javax.inject.Inject

/**
 * What to generate.
 *
 * ```
 * kotlinxLocale {
 *     locales(Pt.BR, En.US)
 *     fallback(En.US)
 *     packageName = "com.example.locale"
 *
 *     country { names = true }
 *     currency { names = true; formats = true }
 *     datetime { patterns = true }
 * }
 * ```
 *
 * Locales can be given as [LocaleRef] values from `kotlinx-locale-types` or as
 * tags. The refs are worth preferring: a typo in a tag does not throw, it
 * quietly generates data for one locale fewer than intended, and this is a build
 * script so nothing will fail at runtime either.
 */
abstract class KotlinxLocaleExtension @Inject constructor(objects: ObjectFactory) {

    /** The locales to generate, as canonical BCP 47 tags. */
    @get:Input
    abstract val locales: SetProperty<String>

    /**
     * The locale that answers for anything not generated.
     *
     * Required, and required to be one of [locales]. Without it a narrowed
     * source would return nothing for an unlisted locale, and a date has no code
     * to degrade to the way a country or a currency does, so "no data" would
     * surface as an ISO 8601 timestamp in the middle of a translated screen.
     */
    @get:Input
    abstract val fallbackLocale: Property<String>

    /** The package the generated sources go into. */
    @get:Input
    abstract val packageName: Property<String>

    /**
     * The prefix on the generated source objects, so `Generated` yields
     * `GeneratedCountryNames`.
     *
     * Configurable because a project may want more than one set: a narrow
     * default and a full one behind a lazy load.
     */
    @get:Input
    abstract val objectPrefix: Property<String>

    @get:Nested
    val country: CountryFeatures = objects.newInstance(CountryFeatures::class.java)

    @get:Nested
    val currency: CurrencyFeatures = objects.newInstance(CurrencyFeatures::class.java)

    @get:Nested
    val datetime: DateTimeFeatures = objects.newInstance(DateTimeFeatures::class.java)

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

    /** True when nothing at all was asked for, which is worth failing on rather than generating an empty source set. */
    internal fun generatesNothing(): Boolean =
        !country.names.get() && !currency.names.get() && !currency.formats.get() && !datetime.patterns.get()
}

abstract class CountryFeatures {
    /** Localized country names, behind `Country.displayName`. */
    @get:Input
    abstract val names: Property<Boolean>
}

abstract class CurrencyFeatures {
    /** Localized currency symbols and display names. */
    @get:Input
    abstract val names: Property<Boolean>

    /**
     * Number patterns, for `CurrencyAmount.format` and `parseFormatted`.
     *
     * Implies [names], because a pattern substitutes the symbol into itself.
     */
    @get:Input
    abstract val formats: Property<Boolean>
}

abstract class DateTimeFeatures {
    /** Date and time patterns plus month and weekday names. */
    @get:Input
    abstract val patterns: Property<Boolean>
}
