package dev.carcara.kotlinx.locale.codegen

import java.io.File

class RbnfData(
    /** closure id -> the encoded rule sets, entry point first. */
    val closures: Map<String, String>,
    /** CLDR locale id -> closure id. */
    val index: Map<String, String>,
)

/** The public ordinal rule set every locale resolves through. */
private const val ENTRY_POINT = "digits-ordinal"

/**
 * The constructs the runtime evaluator understands.
 *
 * A census of the transitive closure of every `OrdinalRules` grouping in CLDR
 * 48.2 finds exactly these. The check below fails generation if a later release
 * introduces another, because the alternative is a rule that renders silently
 * wrong in one language and is noticed by nobody.
 */
private val SUPPORTED_CONSTRUCTS = Regex(
    """^(?:[^←\[\]>=→$]|=[^=]*=|→[^→]*→|\$\(ordinal,[^)]*\)\$)*$""",
)

/**
 * Reads the `digits-ordinal` rule sets out of `common/rbnf`.
 *
 * The inheritance here is not the main-file chain: RBNF exists for 91 locales
 * and the rest inherit root's rule, which appends a full stop. That is why
 * German and Czech ordinals read `1.` rather than being a gap in the data.
 */
fun parseRbnfOrdinals(cldrDir: File, localeIds: List<String>, parentOverrides: Map<String, String>): RbnfData {
    val rbnfDir = cldrDir.resolve("common/rbnf")
    check(rbnfDir.isDirectory) { "common/rbnf is missing; widen CLDR_REPO.sparsePaths" }

    val declared = HashMap<String, Map<String, Map<String, String>>>()
    for (file in rbnfDir.listFiles().orEmpty().sortedBy(File::getName)) {
        if (file.extension != "xml") continue
        declared[file.nameWithoutExtension] = parseRbnfFile(file)
    }

    val closures = LinkedHashMap<String, String>()
    val byEncoding = HashMap<String, String>()
    val index = LinkedHashMap<String, String>()

    fun closureFor(id: String): String {
        val chain = rbnfChain(id, parentOverrides).filter { it in declared }
        val ruleSets = chain.firstNotNullOfOrNull { candidate ->
            declared[candidate]?.takeIf { ENTRY_POINT in it }?.let { candidate to it }
        } ?: return ""
        val encoded = encodeClosure(ruleSets.second, ruleSets.first)
        return byEncoding.getOrPut(encoded) {
            val closureId = "r${closures.size}"
            closures[closureId] = encoded
            closureId
        }
    }

    for (id in listOf("root") + localeIds) {
        val closureId = closureFor(id)
        if (closureId.isNotEmpty()) index[id] = closureId
    }

    check(closures.isNotEmpty()) { "no digits-ordinal rule sets resolved" }
    println("[codegen] resolved ${closures.size} ordinal rule closures for ${index.size} locales")
    return RbnfData(closures, index)
}

/** ruleset name -> base value -> rule body, for one RBNF file. */
private fun parseRbnfFile(file: File): Map<String, Map<String, String>> {
    val root = parseXml(file).documentElement
    val result = LinkedHashMap<String, Map<String, String>>()
    val rbnf = root.child("rbnf") ?: return result
    for (grouping in rbnf.childElements("rulesetGrouping")) {
        if (grouping.getAttribute("type") != "OrdinalRules") continue
        for (ruleset in grouping.childElements("ruleset")) {
            val name = ruleset.getAttribute("type")
            val rules = LinkedHashMap<String, String>()
            for (rule in ruleset.childElements("rbnfrule")) {
                val body = rule.textContent.orEmpty().removeSuffix(";")
                rules[rule.getAttribute("value")] = body
            }
            result[name] = rules
        }
    }
    return result
}

/**
 * The entry point plus every rule set it reaches, encoded for the runtime.
 *
 * Private rule sets are pulled in by name, so a closure is self-contained and a
 * locale that shares one with another locale shares the whole encoding and
 * therefore the row.
 */
private fun encodeClosure(ruleSets: Map<String, Map<String, String>>, locale: String): String {
    val reached = LinkedHashSet<String>()
    val pending = ArrayDeque(listOf(ENTRY_POINT))
    while (pending.isNotEmpty()) {
        val name = pending.removeFirst()
        if (!reached.add(name)) continue
        val rules = ruleSets[name] ?: continue
        for (body in rules.values) {
            checkConstructs(body, locale, name)
            for (reference in referencedRuleSets(body)) pending.addLast(reference)
        }
    }
    return reached.filter { it in ruleSets }.joinToString(FIELD_SEPARATOR) { name ->
        val rules = ruleSets.getValue(name)
        name + LIST_SEPARATOR + rules.entries.joinToString(LIST_SEPARATOR) { (base, body) ->
            base + KEY_SEPARATOR + body
        }
    }
}

private fun referencedRuleSets(body: String): List<String> {
    val names = ArrayList<String>()
    for (match in Regex("""[=→](%%?[a-zA-Z-]+)[=→]""").findAll(body)) {
        names += match.groupValues[1].trimStart('%')
    }
    return names
}

private fun checkConstructs(body: String, locale: String, ruleSet: String) {
    check(SUPPORTED_CONSTRUCTS.matches(body)) {
        "rbnf/$locale.xml ruleset '$ruleSet' uses a construct the ordinal evaluator does not implement: '$body'. " +
            "Either extend OrdinalRuleClosure to handle it or exclude the rule set, but do not ship it unread: " +
            "an unhandled construct renders silently wrong."
    }
}

/** The RBNF lookup chain: CLDR's parent overrides, then truncation, then root. */
private fun rbnfChain(id: String, parentOverrides: Map<String, String>): List<String> {
    val chain = ArrayList<String>()
    var current: String? = id
    while (current != null && current != "root") {
        chain += current
        current = parentOverrides[current]
            ?: current.substringBeforeLast('_', "").takeIf { it.isNotEmpty() }
            ?: "root"
    }
    chain += "root"
    return chain
}
