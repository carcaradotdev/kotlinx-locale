/*
 * Copyright 2026 Carcara.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    val number: NumberFeatures = objects.newInstance(NumberFeatures::class.java)

    val language: LanguageFeatures = objects.newInstance(LanguageFeatures::class.java)

    val timezone: TimeZoneFeatures = objects.newInstance(TimeZoneFeatures::class.java)

    val personName: PersonNameFeatures = objects.newInstance(PersonNameFeatures::class.java)

    private val blocks: List<FeatureBlock>
        get() = listOf(country, currency, datetime, number, language, timezone, personName)

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

    fun personName(action: Action<PersonNameFeatures>) {
        action.execute(personName)
    }

    fun number(action: Action<NumberFeatures>) {
        action.execute(number)
    }

    fun language(action: Action<LanguageFeatures>) {
        action.execute(language)
    }

    fun timezone(action: Action<TimeZoneFeatures>) {
        action.execute(timezone)
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

    /**
     * Compact money: `${'$'}1.2M`.
     *
     * Generates the name, pattern and plural tables it needs, so it is enough on
     * its own.
     */
    val compact: Property<Boolean> = flag(LocaleFeature.CURRENCY_COMPACT)

    /**
     * Currency names that agree with a count, behind `CurrencyAmount.formatPluralName`
     * and `Currency.pluralName`.
     *
     * Generates the display-name and plural tables it needs, so it is enough on
     * its own.
     */
    val pluralNames: Property<Boolean> = flag(LocaleFeature.CURRENCY_PLURAL_NAMES)
}

abstract class LanguageFeatures @Inject constructor(objects: ObjectFactory) : FeatureBlock(objects) {

    /**
     * Language, script and region names, behind `Locale.displayName`.
     *
     * The largest table in the library across all locales, and the one the
     * Gradle plugin pays for most: a language picker needs a handful of names,
     * not eleven hundred locales' worth.
     */
    val names: Property<Boolean> = flag(LocaleFeature.LANGUAGE_NAMES)
}

abstract class TimeZoneFeatures @Inject constructor(objects: ObjectFactory) : FeatureBlock(objects) {

    /** The localized GMT format: `GMT-08:00` in the locale's own word and digits. */
    val formats: Property<Boolean> = flag(LocaleFeature.TIMEZONE_FORMATS)

    /** Zone and metazone display names: `Pacific Standard Time`, `PT`. */
    val names: Property<Boolean> = flag(LocaleFeature.TIMEZONE_NAMES)

    /**
     * Exemplar cities, for the generic location format.
     *
     * The largest zone table by a wide margin. Without it the location format
     * uses the identifier's last part, which is the fallback UTS #35 itself
     * prescribes.
     */
    val exemplarCities: Property<Boolean> = flag(LocaleFeature.TIMEZONE_EXEMPLAR_CITIES)
}

abstract class NumberFeatures @Inject constructor(objects: ObjectFactory) : FeatureBlock(objects) {

    /** Number symbols and the decimal and percent patterns. */
    val formats: Property<Boolean> = flag(LocaleFeature.NUMBER_FORMATS)

    /**
     * Compact notation: `1.2K` and `1.2 thousand`.
     *
     * Generates the plural rules its patterns are keyed by, so there is no way
     * to ask for compact and get the wrong plural form.
     */
    val compact: Property<Boolean> = flag(LocaleFeature.NUMBER_COMPACT)

    /**
     * CLDR plural rules, for choosing between translated strings.
     *
     * Carried whole rather than narrowed: four kilobytes covers every locale in
     * CLDR, so dropping rows would save nothing and would turn an unlisted
     * locale into wrong grammar rather than an error.
     */
    val plurals: Property<Boolean> = flag(LocaleFeature.NUMBER_PLURALS)

    /** Ordinal forms: `1st`, `1.`, `1º`. Generates the plural rules eight of the rule sets read. */
    val ordinals: Property<Boolean> = flag(LocaleFeature.NUMBER_ORDINALS)
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

    /**
     * Date and time intervals: `Jul 18 – 22, 2026`, with the parts both ends
     * share written once.
     *
     * Generates the skeleton tables as well, because an interval is a split of
     * the pattern the matcher picks for the requested skeleton.
     */
    val intervals: Property<Boolean> = flag(LocaleFeature.DATETIME_INTERVALS)

    /**
     * Stand-alone month, weekday and quarter names: `červenec` where a date
     * would read `července`.
     *
     * Twelve thousand characters across every locale, because the table stores
     * only where a locale differs from its format names and 838 of them do not.
     */
    val standalone: Property<Boolean> = flag(LocaleFeature.DATETIME_STANDALONE)

    /**
     * Relative wording: `3 days ago`, `yesterday`, `za 3 dny`.
     *
     * Generates the plural rules that choose the wording and the number tables
     * that render its count, so the four Czech forms come out right rather than
     * defaulting to one of them.
     */
    val relativeTime: Property<Boolean> = flag(LocaleFeature.DATETIME_RELATIVE_TIME)

    /**
     * Duration wording: `2 hours`, `2 hr`, `2h`, `2 Stunden`.
     *
     * The measurement form, not the `h:mm` of `durationPattern`, which rides
     * along with the date patterns. Generates the plural rules and number tables
     * alongside, the way [relativeTime] does.
     */
    val durationUnits: Property<Boolean> = flag(LocaleFeature.DATETIME_DURATION_UNITS)
}

/** Person name formatting, and the initials derived from it. */
abstract class PersonNameFeatures @Inject constructor(objects: ObjectFactory) : FeatureBlock(objects) {

    /**
     * Writing a person's name the way a locale writes one, and its initials.
     *
     * One table, so this costs what the patterns weigh and nothing else. The
     * order a name is written in depends on the name's locale as well as the
     * reader's, so generating a narrow set of locales narrows who can be read
     * to, not whose names can be written.
     */
    val formats: Property<Boolean> = flag(LocaleFeature.PERSONNAME_FORMATS)
}
