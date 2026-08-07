@file:OptIn(InternalKotlinxLocaleApi::class)

package dev.carcara.kotlinx.locale.number.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.internal.ENTRY_SEPARATOR
import dev.carcara.kotlinx.locale.internal.FIELD_SEPARATOR
import dev.carcara.kotlinx.locale.internal.KEY_SEPARATOR
import dev.carcara.kotlinx.locale.number.NumberSymbols
import dev.carcara.kotlinx.locale.number.PluralCategory

/** U+2212, which is what CLDR's negative ordinal rules emit rather than an ASCII hyphen. */
private const val MINUS_SIGN = '−'

/**
 * The `digits-ordinal` rule closure for one locale.
 *
 * CLDR writes ordinal forms as rules rather than as a table, in its own
 * rule-based number format syntax. This is a deliberately small evaluator for
 * the part of that syntax the ordinal rule sets actually use, which is bounded
 * and checked: a census of the transitive closure of every `OrdinalRules`
 * grouping in CLDR 48.2 finds rule selection by base value, the `-x` negative
 * rule, `=#,##0=` decimal substitution, `=%name=` and `=%%name=` ruleset
 * substitution, `→→` remainder substitution and `$(ordinal,…)$` plural
 * substitution, and nothing else. Generation fails if a future release
 * introduces a construct outside that set, because silent mis-rendering is the
 * failure this has to be designed against.
 *
 * The full rule-based formatter, which ICU implements in several thousand lines,
 * also spells numbers out in words. That is out of scope and not needed here.
 */
@InternalKotlinxLocaleApi
public class OrdinalRuleClosure(encoded: String) {

    private val ruleSets: Map<String, RbnfRuleSet>
    private val entryPoint: String

    init {
        val sets = LinkedHashMap<String, RbnfRuleSet>()
        var first = ""
        for (block in encoded.split(FIELD_SEPARATOR)) {
            if (block.isEmpty()) continue
            val name = block.substringBefore(ENTRY_SEPARATOR)
            val rules = block.substringAfter(ENTRY_SEPARATOR, "")
            if (first.isEmpty()) first = name
            sets[name] = RbnfRuleSet(rules)
        }
        ruleSets = sets
        entryPoint = first
    }

    public val isEmpty: Boolean get() = ruleSets.isEmpty()

    /** [value] as an ordinal, or `null` when this closure carries no usable rule. */
    public fun format(value: Long, symbols: NumberSymbols, selectOrdinalCategory: (Long) -> PluralCategory): String? {
        if (ruleSets.isEmpty()) return null
        return apply(entryPoint, value, symbols, selectOrdinalCategory, depth = 0)
    }

    private fun apply(
        ruleSetName: String,
        value: Long,
        symbols: NumberSymbols,
        selectOrdinalCategory: (Long) -> PluralCategory,
        depth: Int,
    ): String? {
        if (depth > 8) return null
        val ruleSet = ruleSets[ruleSetName] ?: return null
        if (value < 0) {
            val negative = ruleSet.negativeRule
                ?: return MINUS_SIGN + (apply(ruleSetName, -value, symbols, selectOrdinalCategory, depth + 1) ?: return null)
            return render(negative, ruleSetName, -value, -value, symbols, selectOrdinalCategory, depth)
        }
        val rule = ruleSet.ruleFor(value) ?: return null
        return render(rule.body, ruleSetName, value, value % maxOf(rule.divisor, 1L), symbols, selectOrdinalCategory, depth)
    }

    private fun render(
        body: String,
        ruleSetName: String,
        value: Long,
        remainder: Long,
        symbols: NumberSymbols,
        selectOrdinalCategory: (Long) -> PluralCategory,
        depth: Int,
    ): String? = buildString {
        var index = 0
        while (index < body.length) {
            when {
                body.startsWith("$(", index) -> {
                    val close = body.indexOf(")$", index)
                    if (close < 0) return null
                    append(pluralChoice(body.substring(index + 2, close), value, selectOrdinalCategory) ?: return null)
                    index = close + 2
                }
                body[index] == '=' -> {
                    val close = body.indexOf('=', index + 1)
                    if (close < 0) return null
                    val token = body.substring(index + 1, close)
                    append(substitute(token, value, symbols, selectOrdinalCategory, depth) ?: return null)
                    index = close + 1
                }
                body[index] == '→' -> {
                    var close = index + 1
                    while (close < body.length && body[close] != '→') close++
                    if (close >= body.length) return null
                    val token = body.substring(index + 1, close)
                    val target = if (token.isEmpty()) ruleSetName else token
                    append(substitute(target, remainder, symbols, selectOrdinalCategory, depth) ?: return null)
                    index = close + 1
                }
                else -> {
                    append(body[index])
                    index++
                }
            }
        }
    }

    private fun substitute(
        token: String,
        value: Long,
        symbols: NumberSymbols,
        selectOrdinalCategory: (Long) -> PluralCategory,
        depth: Int,
    ): String? = when {
        // A decimal pattern rather than a rule set name: write the digits.
        token.isEmpty() || token.first() in "#0" -> formatDigits(value, token.ifEmpty { "#,##0" }, symbols)
        token.startsWith("%%") -> apply(token.removePrefix("%%"), value, symbols, selectOrdinalCategory, depth + 1)
        token.startsWith("%") -> apply(token.removePrefix("%"), value, symbols, selectOrdinalCategory, depth + 1)
        else -> apply(token, value, symbols, selectOrdinalCategory, depth + 1)
    }

    private fun formatDigits(value: Long, pattern: String, symbols: NumberSymbols): String {
        val parsed = NumberPattern.parse(pattern)
        return renderNumber(
            value = dev.carcara.kotlinx.locale.number.Decimal.of(value),
            pattern = parsed,
            symbols = symbols,
        ).text
    }

    /** `one{st}two{nd}few{rd}other{th}` against the value's ordinal plural category. */
    private fun pluralChoice(body: String, value: Long, selectOrdinalCategory: (Long) -> PluralCategory): String? {
        val arguments = body.substringAfter(',', "")
        if (arguments.isEmpty()) return null
        val choices = LinkedHashMap<String, String>()
        var index = 0
        while (index < arguments.length) {
            val open = arguments.indexOf('{', index)
            if (open < 0) break
            val close = arguments.indexOf('}', open)
            if (close < 0) break
            choices[arguments.substring(index, open).trim()] = arguments.substring(open + 1, close)
            index = close + 1
        }
        val category = selectOrdinalCategory(value)
        return choices[category.cldrName] ?: choices["other"]
    }

    public companion object
}

private class RbnfRule(val base: Long, val divisor: Long, val body: String)

private class RbnfRuleSet(encoded: String) {

    private val rules: List<RbnfRule>
    val negativeRule: String?

    init {
        var negative: String? = null
        val parsed = ArrayList<RbnfRule>()
        for (entry in encoded.split(ENTRY_SEPARATOR)) {
            if (entry.isEmpty()) continue
            val key = entry.substringBefore(KEY_SEPARATOR)
            val body = unescapeLeadingQuote(entry.substringAfter(KEY_SEPARATOR, ""))
            if (key == "-x") {
                negative = body
                continue
            }
            val base = key.toLongOrNull() ?: continue
            parsed += RbnfRule(base, divisorFor(base), body)
        }
        rules = parsed.sortedBy(RbnfRule::base)
        negativeRule = negative
    }

    /** The rule with the greatest base value not above [value]. */
    fun ruleFor(value: Long): RbnfRule? {
        var found: RbnfRule? = null
        for (rule in rules) {
            if (rule.base > value) break
            found = rule
        }
        return found ?: rules.firstOrNull()
    }
}

/**
 * The largest power of ten not above [base], which is what a remainder
 * substitution divides by.
 *
 * So a rule keyed 20 takes the value modulo 10, which is how Spanish reaches its
 * `1` rule from 21.
 */
private fun divisorFor(base: Long): Long {
    if (base < 10) return 1L
    var divisor = 1L
    while (divisor * 10 <= base) divisor *= 10
    return divisor
}

/**
 * Strips the leading apostrophe the rule syntax uses to protect a leading
 * character, so Azerbaijani's `''inci` yields the apostrophe its ordinals are
 * written with.
 */
private fun unescapeLeadingQuote(body: String): String = if (body.startsWith('\'')) body.substring(1) else body
