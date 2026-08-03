package dev.carcara.kotlinx.locale.codegen

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

private val documentBuilderFactory: DocumentBuilderFactory = DocumentBuilderFactory.newInstance().apply {
    isValidating = false
    isNamespaceAware = false
    // LDML files reference a DTD via a relative path; never fetch or validate it.
    setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
}

fun parseXml(file: File): Document {
    val builder = documentBuilderFactory.newDocumentBuilder()
    builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
    return builder.parse(file)
}

fun parseXml(stream: java.io.InputStream): Document {
    val builder = documentBuilderFactory.newDocumentBuilder()
    builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
    return builder.parse(stream)
}

/**
 * The child elements CLDR means, which excludes the ones it marks unready.
 *
 * The draft filter lives here rather than at each of the thirty-odd read sites,
 * and that is a deliberate difference from how `alt` is handled. An `alt` value
 * is sometimes the one wanted, so each site decides; a draft value never is, so
 * deciding once is both safer and impossible to forget. Use
 * [childElementsIncludingDrafts] to see everything.
 */
fun Element.childElements(name: String? = null): List<Element> = childElementsIncludingDrafts(name).filterNot(Element::isUnreadyDraft)

fun Element.childElementsIncludingDrafts(name: String? = null): List<Element> {
    val result = ArrayList<Element>()
    var node = firstChild
    while (node != null) {
        if (node is Element && (name == null || node.tagName == name)) result.add(node)
        node = node.nextSibling
    }
    return result
}

fun Element.child(name: String, attr: Pair<String, String>? = null): Element? =
    childElements(name).firstOrNull { attr == null || it.getAttribute(attr.first) == attr.second }

/**
 * The draft statuses that are not ready to be used.
 *
 * CLDR ranks a value `unconfirmed`, `provisional`, `contributed` or `approved`,
 * the last being the absence of the attribute. Everything from `contributed` up
 * is production data; the two below it are work in progress that survived into
 * the release.
 */
private val UNREADY_DRAFT = setOf("provisional", "unconfirmed")

/**
 * Whether CLDR wrote this value down without meaning it to be used yet.
 *
 * The same kind of filter as the `alt` skips beside it: both mark a value that
 * is present in the file and is not the answer. Reading them anyway is not a
 * harmless extra, it is worse than having nothing, because a locale with an
 * unconfirmed value stops inheriting the reviewed one from its parent.
 *
 * The threshold is `contributed`, which is ICU's, and it was checked against
 * ICU's own bundles rather than assumed: locales whose interval patterns are all
 * `contributed` keep them in `icu4c/source/data/locales`, and locales whose
 * patterns are all `unconfirmed` have no `intervalFormats` block at all. Moving
 * this to approved-only would silently change output in every domain, so change
 * it only with the same evidence.
 *
 * LDML allows the attribute on a container, applying to everything under it. No
 * container carries one in release-48-2, which [checkNoContainerDrafts] holds to.
 */
fun Element.isUnreadyDraft(): Boolean = getAttribute("draft") in UNREADY_DRAFT

/**
 * Fails if any element carrying an unready draft status has element children.
 *
 * [childElements] drops such an element and everything under it, which is right
 * when the attribute sits on a leaf and is what CLDR does today. LDML allows it
 * on a container, where it would mean the container's descendants are unready
 * rather than the container itself, and the two readings differ. Rather than
 * implement a rule nothing exercises, this asserts the shape the data actually
 * has, so a future release that changes it stops generation instead of quietly
 * dropping a whole block.
 */
fun checkNoContainerDrafts(root: Element, source: String) {
    val stack = ArrayDeque(listOf(root))
    while (stack.isNotEmpty()) {
        val element = stack.removeLast()
        val children = element.childElementsIncludingDrafts()
        check(!element.isUnreadyDraft() || children.isEmpty()) {
            "$source: <${element.tagName}> carries draft=\"${element.getAttribute("draft")}\" and has " +
                "${children.size} child elements. LDML allows this and nothing here implements it; " +
                "see Xml.kt isUnreadyDraft."
        }
        stack.addAll(children)
    }
}

/** Walks a path of (name[, attr=value]) steps from this element. */
fun Element.path(vararg steps: Any): Element? {
    var current: Element? = this
    for (step in steps) {
        current = when (step) {
            is String -> current?.child(step)
            is Pair<*, *> -> {
                val (name, attr) = step
                @Suppress("UNCHECKED_CAST")
                current?.child(name as String, attr as Pair<String, String>)
            }
            else -> error("bad step $step")
        }
        if (current == null) return null
    }
    return current
}

fun step(name: String, attrName: String, attrValue: String): Pair<String, Pair<String, String>> = name to (attrName to attrValue)
