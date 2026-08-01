package dev.carcara.kotlinx.locale.phone.metadata.runtime

/**
 * A regular expression over digits, evaluated identically on every target.
 *
 * This is the piece that makes a multiplatform phone library possible at all.
 * libphonenumber decides whether a number is valid by matching it against a
 * regular expression, and Kotlin's [Regex] is a facade over a different engine
 * on every target: `java.util.regex` on the JVM and Android, `RegExp` on JS and
 * Wasm, and a bundled implementation on Native. They agree on the common cases
 * and diverge at the edges, which for a validation library means a number that
 * is valid on a phone and invalid in a browser. That is not a difference a
 * caller can be asked to reason about.
 *
 * So this evaluates the patterns itself. The whole of libphonenumber's metadata
 * uses six constructs:
 *
 * ```
 * 7          a literal digit, or one of * and #
 * \d         any digit
 * [0-24-6]   a class, with ranges
 * (?:…)      a group, capturing when written (…)
 * a|b        alternation
 * x{2}       bounded repetition, or {2,4}, or x? for {0,1}
 * $          the end of the input, only in the national-prefix rules
 * ```
 *
 * No backreferences, no lookaround, no unbounded quantifiers, no dot. Every
 * input is at most a few dozen digits and every repetition is bounded, so a
 * plain backtracking matcher is both correct and fast enough, with none of the
 * pathological blowup an unbounded engine has to defend against.
 *
 * The subset is checked rather than assumed: `:codegen` fails the build if a
 * libphonenumber release starts using anything else, naming the pattern.
 */
public class DigitPattern private constructor(
    private val root: Node,
    /** How many capturing groups the pattern declares. */
    public val groupCount: Int,
) {

    /** True when [input] matches this pattern in its entirety. */
    public fun matches(input: CharSequence): Boolean = run(root, input, 0, IntArray(groupCount * 2) { -1 }) { it == input.length }

    /**
     * The length of the shortest prefix of [input] this pattern matches, or -1.
     *
     * Prefix rather than whole, because that is what stripping a national prefix
     * needs: the pattern describes the prefix and the rest of the number is what
     * survives it.
     */
    public fun prefixLength(input: CharSequence): Int {
        var result = -1
        run(root, input, 0, IntArray(groupCount * 2) { -1 }) { end ->
            result = end
            true
        }
        return result
    }

    /**
     * The capture groups from matching [input] whole, or `null` when it does not
     * match.
     *
     * Index 0 is the first group, the way `$1` reads in a format string, rather
     * than the whole match. A group that took part in no alternative is `null`.
     */
    public fun capture(input: CharSequence): List<String?>? {
        val slots = IntArray(groupCount * 2) { -1 }
        if (!run(root, input, 0, slots) { it == input.length }) return null
        return List(groupCount) { index ->
            val start = slots[index * 2]
            val end = slots[index * 2 + 1]
            if (start < 0 || end < 0) null else input.substring(start, end)
        }
    }

    /**
     * The capture groups from matching a prefix of [input], with the offset the
     * match ended at.
     *
     * What the national-prefix rules need: they match the front of a number and
     * the transform rule rewrites it from the groups.
     */
    public fun capturePrefix(input: CharSequence): Pair<List<String?>, Int>? {
        val slots = IntArray(groupCount * 2) { -1 }
        var end = -1
        if (!run(root, input, 0, slots) {
                end = it
                true
            }
        ) {
            return null
        }
        val groups = List(groupCount) { index ->
            val start = slots[index * 2]
            val stop = slots[index * 2 + 1]
            if (start < 0 || stop < 0) null else input.substring(start, stop)
        }
        return groups to end
    }

    private fun run(node: Node, input: CharSequence, pos: Int, slots: IntArray, cont: (Int) -> Boolean): Boolean = when (node) {
        is Node.Char -> pos < input.length && node.accepts(input[pos]) && cont(pos + 1)
        is Node.End -> pos == input.length && cont(pos)
        is Node.Sequence -> runSequence(node.parts, 0, input, pos, slots, cont)
        is Node.Alternation -> node.branches.any { run(it, input, pos, slots, cont) }
        is Node.Group -> {
            if (node.index < 0) {
                run(node.body, input, pos, slots) { cont(it) }
            } else {
                // Saved and restored so a failed alternative does not leave
                // its capture behind for the branch that follows it.
                val savedStart = slots[node.index * 2]
                val savedEnd = slots[node.index * 2 + 1]
                val matched = run(node.body, input, pos, slots) { end ->
                    slots[node.index * 2] = pos
                    slots[node.index * 2 + 1] = end
                    cont(end)
                }
                if (!matched) {
                    slots[node.index * 2] = savedStart
                    slots[node.index * 2 + 1] = savedEnd
                }
                matched
            }
        }
        is Node.Repeat -> runRepeat(node, input, pos, slots, 0, cont)
    }

    private fun runSequence(
        parts: List<Node>,
        index: Int,
        input: CharSequence,
        pos: Int,
        slots: IntArray,
        cont: (Int) -> Boolean,
    ): Boolean = if (index == parts.size) {
        cont(pos)
    } else {
        run(parts[index], input, pos, slots) { next -> runSequence(parts, index + 1, input, next, slots, cont) }
    }

    private fun runRepeat(node: Node.Repeat, input: CharSequence, pos: Int, slots: IntArray, done: Int, cont: (Int) -> Boolean): Boolean {
        // Greedy, like every engine libphonenumber's patterns were written
        // against: try one more repetition before accepting what we have.
        if (done < node.max &&
            run(node.body, input, pos, slots) { next ->
                // A body that matched nothing would repeat forever; the metadata
                // has no such pattern, and this is what keeps that true.
                next > pos && runRepeat(node, input, next, slots, done + 1, cont)
            }
        ) {
            return true
        }
        return done >= node.min && cont(pos)
    }

    public companion object {

        /** [pattern] compiled, or an error naming what it could not read. */
        public fun parse(pattern: String): DigitPattern {
            val parser = PatternParser(pattern)
            val root = parser.parseAlternation()
            require(parser.atEnd) { "unexpected '${pattern[parser.position]}' at ${parser.position} in '$pattern'" }
            return DigitPattern(root, parser.groupCount)
        }
    }
}

internal sealed class Node {

    /** One input character, as the set of characters that satisfy it. */
    class Char(private val allowed: ULong, private val extra: Set<kotlin.Char>) : Node() {

        fun accepts(ch: kotlin.Char): Boolean {
            val offset = ch.code - '0'.code
            return if (offset in 0..9) (allowed shr offset) and 1uL == 1uL else ch in extra
        }

        companion object {
            val Digit: Char = Char(0x3FFuL, emptySet())
            fun of(ch: kotlin.Char): Char {
                val offset = ch.code - '0'.code
                return if (offset in 0..9) Char(1uL shl offset, emptySet()) else Char(0uL, setOf(ch))
            }

            fun ofSet(digits: Set<Int>, others: Set<kotlin.Char>): Char {
                var mask = 0uL
                for (digit in digits) mask = mask or (1uL shl digit)
                return Char(mask, others)
            }
        }
    }

    /** The end of the input. */
    object End : Node()

    class Sequence(val parts: List<Node>) : Node()

    class Alternation(val branches: List<Node>) : Node()

    class Group(val body: Node, val index: Int) : Node()

    class Repeat(val body: Node, val min: Int, val max: Int) : Node()
}

private class PatternParser(private val source: String) {

    var position: Int = 0
        private set
    var groupCount: Int = 0
        private set

    val atEnd: Boolean get() = position >= source.length

    fun parseAlternation(): Node {
        val branches = ArrayList<Node>(1)
        branches += parseSequence()
        while (!atEnd && source[position] == '|') {
            position++
            branches += parseSequence()
        }
        return if (branches.size == 1) branches[0] else Node.Alternation(branches)
    }

    private fun parseSequence(): Node {
        val parts = ArrayList<Node>()
        while (!atEnd && source[position] != '|' && source[position] != ')') {
            parts += parseQuantified()
        }
        return if (parts.size == 1) parts[0] else Node.Sequence(parts)
    }

    private fun parseQuantified(): Node {
        val atom = parseAtom()
        if (atEnd) return atom
        return when (source[position]) {
            '?' -> {
                position++
                Node.Repeat(atom, 0, 1)
            }
            '{' -> {
                val close = source.indexOf('}', position)
                require(close > 0) { "unterminated quantifier at $position in '$source'" }
                val body = source.substring(position + 1, close)
                position = close + 1
                val comma = body.indexOf(',')
                if (comma < 0) {
                    val n = body.toInt()
                    Node.Repeat(atom, n, n)
                } else {
                    val min = body.substring(0, comma).toInt()
                    val max = body.substring(comma + 1).toInt()
                    Node.Repeat(atom, min, max)
                }
            }
            else -> atom
        }
    }

    private fun parseAtom(): Node = when {
        source.startsWith("(?:", position) -> {
            position += 3
            val body = parseAlternation()
            expect(')')
            Node.Group(body, index = -1)
        }
        source[position] == '(' -> {
            position++
            val index = groupCount++
            val body = parseAlternation()
            expect(')')
            Node.Group(body, index)
        }
        source[position] == '[' -> parseClass()
        source[position] == '\\' -> {
            require(source.getOrNull(position + 1) == 'd') {
                "only \\d is supported, found '\\${source.getOrNull(position + 1)}' in '$source'"
            }
            position += 2
            Node.Char.Digit
        }
        source[position] == '$' -> {
            position++
            Node.End
        }
        else -> Node.Char.of(source[position++])
    }

    private fun parseClass(): Node {
        position++
        val digits = HashSet<Int>()
        val others = HashSet<Char>()
        while (!atEnd && source[position] != ']') {
            val ch = source[position]
            // A `-` between two characters is a range; one at either end of the
            // class is the character itself.
            if (source.getOrNull(position + 1) == '-' && source.getOrNull(position + 2)?.let { it != ']' } == true) {
                val last = source[position + 2]
                for (code in ch.code..last.code) {
                    val offset = code - '0'.code
                    if (offset in 0..9) digits += offset else others += code.toChar()
                }
                position += 3
                continue
            }
            val offset = ch.code - '0'.code
            if (offset in 0..9) digits += offset else others += ch
            position++
        }
        expect(']')
        return Node.Char.ofSet(digits, others)
    }

    private fun expect(ch: Char) {
        require(!atEnd && source[position] == ch) { "expected '$ch' at $position in '$source'" }
        position++
    }
}
