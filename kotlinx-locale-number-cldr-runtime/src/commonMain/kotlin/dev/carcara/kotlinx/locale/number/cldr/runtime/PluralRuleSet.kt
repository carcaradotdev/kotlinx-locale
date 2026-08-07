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

package dev.carcara.kotlinx.locale.number.cldr.runtime

import dev.carcara.kotlinx.locale.InternalKotlinxLocaleApi
import dev.carcara.kotlinx.locale.number.FormattedNumber
import dev.carcara.kotlinx.locale.number.PluralCategory

/**
 * One CLDR plural rule set: ordered `category: condition` pairs, first match
 * wins, `other` when nothing matches.
 *
 * The conditions are parsed from CLDR's own syntax at runtime rather than
 * compiled to Kotlin by the emitter. Three reasons, in order of weight.
 *
 * Size. Every cardinal condition in CLDR 48.2 is about three thousand
 * characters and every ordinal one about a thousand, covering all 1122 locales,
 * against an evaluator of roughly a hundred and fifty lines. Compiled Kotlin
 * would be one function per rule set plus a dispatch map holding references to
 * all of them, so dead-code elimination could drop none of it either way and the
 * code would be strictly larger for the same reach.
 *
 * Testability. The grammar is small and closed, and CLDR ships `@integer` and
 * `@decimal` sample lists next to every rule. Parsed at runtime those samples
 * become a fixture that runs the shipped evaluator on every target. Compiled by
 * the emitter, the only thing they could check is a generator-side
 * reimplementation that never ships.
 *
 * Narrowing. The rules ride in the bundle as text, so the Gradle plugin carries
 * them with no second emitter.
 *
 * The grammar, in full:
 *
 * ```
 * condition     = and_condition ('or' and_condition)*
 * and_condition = relation ('and' relation)*
 * relation      = expr ('=' | '!=') range_list
 * expr          = operand ('%' value)?
 * operand       = 'n' | 'i' | 'v' | 'w' | 'f' | 't' | 'e' | 'c'
 * range_list    = (range | value) (',' (range | value))*
 * range         = value '..' value
 * ```
 *
 * A range never matches a non-integer operand value, per UTS #35, which is why
 * `n = 2..4` is false for `2.5` rather than true.
 */
@InternalKotlinxLocaleApi
public class PluralRuleSet private constructor(private val rules: List<Rule>) {

    /** The category [number] falls into. */
    public fun select(number: FormattedNumber): PluralCategory {
        for (rule in rules) {
            if (rule.condition.matches(number)) return rule.category
        }
        return PluralCategory.OTHER
    }

    private class Rule(val category: PluralCategory, val condition: Condition)

    public companion object {

        /** Every locale has `other`, and a rule set that declares only it needs no evaluation. */
        public val OtherOnly: PluralRuleSet = PluralRuleSet(emptyList())

        /**
         * Reads the encoded form: `category:condition` pairs joined by `;`, with
         * CLDR's own condition syntax kept verbatim.
         *
         * Keeping CLDR's syntax rather than re-encoding it means the runtime
         * parser is a parser for a documented public grammar, and a reviewer can
         * diff a generated table against `plurals.xml` by eye.
         */
        public fun parse(encoded: String): PluralRuleSet {
            if (encoded.isBlank()) return OtherOnly
            val rules = ArrayList<Rule>()
            for (entry in encoded.split(';')) {
                if (entry.isBlank()) continue
                val separator = entry.indexOf(':')
                require(separator > 0) { "a plural rule is 'category:condition', not '$entry'" }
                val category = PluralCategory.forCldrNameOrNull(entry.substring(0, separator).trim())
                    ?: error("unknown plural category in '$entry'")
                val condition = entry.substring(separator + 1).trim()
                // `other` never carries a condition; it is the fallthrough.
                if (category == PluralCategory.OTHER || condition.isEmpty()) continue
                rules += Rule(category, parseCondition(condition))
            }
            return PluralRuleSet(rules)
        }
    }
}

private fun interface Condition {
    fun matches(number: FormattedNumber): Boolean
}

private fun parseCondition(text: String): Condition {
    val alternatives = text.split(" or ").map(::parseAndCondition)
    if (alternatives.size == 1) return alternatives[0]
    return Condition { number -> alternatives.any { it.matches(number) } }
}

private fun parseAndCondition(text: String): Condition {
    val terms = text.split(" and ").map(::parseRelation)
    if (terms.size == 1) return terms[0]
    return Condition { number -> terms.all { it.matches(number) } }
}

private fun parseRelation(text: String): Condition {
    val trimmed = text.trim()
    val negated: Boolean
    val operatorIndex: Int
    val notEquals = trimmed.indexOf("!=")
    if (notEquals >= 0) {
        negated = true
        operatorIndex = notEquals
    } else {
        negated = false
        operatorIndex = trimmed.indexOf('=')
        require(operatorIndex > 0) { "a plural relation needs = or !=, got '$trimmed'" }
    }
    val expression = parseExpression(trimmed.substring(0, operatorIndex).trim())
    val ranges = parseRanges(trimmed.substring(operatorIndex + if (negated) 2 else 1).trim())

    return Condition { number ->
        val value = expression.evaluate(number)
        // A range only ever matches an integral operand value, so a fractional
        // one fails every relation rather than matching by truncation.
        val matched = value != null && ranges.any { it.contains(value) }
        matched != negated
    }
}

private fun interface Expression {
    /** The operand's value, or `null` when it is not integral and so cannot match a range. */
    fun evaluate(number: FormattedNumber): Long?
}

private fun parseExpression(text: String): Expression {
    val modulus = text.indexOf('%')
    if (modulus < 0) return operandOf(text.trim())
    val operand = operandOf(text.substring(0, modulus).trim())
    val divisor = text.substring(modulus + 1).trim().toLong()
    return Expression { number -> operand.evaluate(number)?.let { it % divisor } }
}

private fun operandOf(name: String): Expression = when (name) {
    // n is the absolute value, which is numeric rather than textual: `n = 1`
    // matches 1.0, because one point zero is one. What it cannot match is
    // anything with a non-zero fraction, so `f` and not `v` is the test. Amharic
    // is the locale that makes the difference visible, since its `one` rule is
    // `i = 0 or n = 1` and CLDR's own samples put 1.0 in it.
    "n" -> Expression { number -> if (number.f == 0L) number.i else null }
    "i" -> Expression { number -> number.i }
    "v" -> Expression { number -> number.v.toLong() }
    "w" -> Expression { number -> number.w.toLong() }
    "f" -> Expression { number -> number.f }
    "t" -> Expression { number -> number.t }
    "c", "e" -> Expression { number -> number.c.toLong() }
    else -> error("unknown plural operand '$name'")
}

private class LongRange(val from: Long, val to: Long) {
    fun contains(value: Long): Boolean = value in from..to
}

private fun parseRanges(text: String): List<LongRange> = text.split(',').map { part ->
    val trimmed = part.trim()
    val dots = trimmed.indexOf("..")
    if (dots < 0) {
        val value = trimmed.toLong()
        LongRange(value, value)
    } else {
        LongRange(trimmed.substring(0, dots).trim().toLong(), trimmed.substring(dots + 2).trim().toLong())
    }
}
