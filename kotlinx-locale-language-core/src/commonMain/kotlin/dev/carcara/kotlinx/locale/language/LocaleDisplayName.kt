package dev.carcara.kotlinx.locale.language

import dev.carcara.kotlinx.locale.Locale

/**
 * [target] written for [locale], following the locale display name algorithm of
 * UTS #35 Part 2.
 *
 * The longest matching language name wins, and the subtags it did not consume
 * are then named and joined with the locale's own patterns:
 *
 * ```
 * Locale.forLanguageTag("sr-Cyrl").displayName(en)  // "Serbian (Cyrillic)"
 * Locale.forLanguageTag("en-GB").displayName(en)    // "British English"
 * Locale.forLanguageTag("en-GB").displayName(en, LanguageDisplay.STANDARD)
 *                                                   // "English (United Kingdom)"
 * ```
 *
 * The extension-subtag steps of the algorithm are not implemented, because
 * [Locale] stops parsing at a singleton subtag and so never carries one. Nor is
 * the `menu` ordering or the nested-bracket replacement.
 *
 * Falls back to the language subtag itself when the source has no name at all,
 * so this never returns nothing.
 */
public fun LanguageNameSource.displayName(
    target: Locale,
    locale: Locale = Locale.current,
    display: LanguageDisplay = LanguageDisplay.DIALECT,
    style: LanguageNameStyle = LanguageNameStyle.STANDARD,
): String {
    val patterns = displayPatternsOrNull(locale) ?: LocaleDisplayPatterns.Root

    // Step 1: longest match on the language table. Under STANDARD only the bare
    // subtag is tried, which is what turns British English into English (United
    // Kingdom).
    var base: String? = null
    var consumedScript = false
    var consumedRegion = false
    if (display == LanguageDisplay.DIALECT) {
        val script = target.script
        val region = target.region
        if (script != null && region != null) {
            base = languageNameOrNull("${target.language}_${script}_$region", style, locale)
            if (base != null) {
                consumedScript = true
                consumedRegion = true
            }
        }
        if (base == null && region != null) {
            base = languageNameOrNull("${target.language}_$region", style, locale)
            if (base != null) consumedRegion = true
        }
        if (base == null && script != null) {
            base = languageNameOrNull("${target.language}_$script", style, locale)
            if (base != null) consumedScript = true
        }
    }
    if (base == null) base = languageNameOrNull(target.language, style, locale)
    val languageName = base ?: target.language

    // Step 2: whatever the winner did not consume becomes a qualifier, and an
    // unmatched subtag contributes its own code rather than disappearing.
    val qualifiers = ArrayList<String>(3)
    target.script?.takeUnless { consumedScript }?.let { qualifiers += scriptNameOrNull(it, locale) ?: it }
    target.region?.takeUnless { consumedRegion }?.let { qualifiers += regionNameOrNull(it, locale) ?: it }
    target.variant?.let { qualifiers += it }

    // Step 3: none, one, or several joined by the separator first.
    if (qualifiers.isEmpty()) return languageName
    val joined = qualifiers.reduce { left, right ->
        patterns.localeSeparator.replace("{0}", left).replace("{1}", right)
    }
    return patterns.localePattern.replace("{0}", languageName).replace("{1}", joined)
}

/**
 * [target]'s name in its own language: `日本語`, `čeština`, `português`.
 *
 * The same call as [displayName] with the target and the display locale equal,
 * which is where CLDR keeps a native name: `cs.xml` is the file that says
 * `čeština`.
 *
 * Note that CLDR stores these as the language writes them, which in many
 * languages is lower case. A picker row that wants `Čeština` is asking for a
 * capitalization transform, which is a separate question from the name.
 */
public fun LanguageNameSource.nativeDisplayName(
    target: Locale,
    display: LanguageDisplay = LanguageDisplay.DIALECT,
    style: LanguageNameStyle = LanguageNameStyle.STANDARD,
): String = displayName(target, target, display, style)

/** The name of the language [subtag] in [locale]; falls back to the subtag. */
public fun LanguageNameSource.languageName(
    subtag: String,
    locale: Locale = Locale.current,
    style: LanguageNameStyle = LanguageNameStyle.STANDARD,
): String = languageNameOrNull(subtag, style, locale) ?: subtag

/** The name of the script [code] in [locale]; falls back to the code. */
public fun LanguageNameSource.scriptName(code: String, locale: Locale = Locale.current): String = scriptNameOrNull(code, locale) ?: code

/** The name of the region [code] in [locale]; falls back to the code. */
public fun LanguageNameSource.regionName(code: String, locale: Locale = Locale.current): String = regionNameOrNull(code, locale) ?: code
