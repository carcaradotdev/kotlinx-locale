package dev.carcara.kotlinx.locale.datetime

/**
 * Which of CLDR's two naming contexts a month, weekday or quarter name is wanted
 * in.
 *
 * [FORMAT] is the form that goes inside a date, and [STANDALONE] the form that
 * stands on its own: a calendar column header, a month picker, a chart axis. In
 * many languages the two differ by grammatical case — Czech July is `července`
 * in a date and `červenec` alone, Croatian `srpnja` and `srpanj` — and it is not
 * only case. Croatian writes its stand-alone narrow months as `7.`, a number.
 *
 * 283 of CLDR 48.2's 1122 locales distinguish the two somewhere. The other 838
 * answer identically, which is what a source with no stand-alone table falls
 * back to and what CLDR root's own alias says.
 *
 * This is a second axis rather than more [TextStyle] entries because CLDR models
 * it as one: context times width. Collapsing them would give six constants now
 * and eight when the short weekday width lands, and adding an entry to a public
 * enum breaks every exhaustive `when` a consumer wrote.
 */
public enum class NameContext { FORMAT, STANDALONE }
