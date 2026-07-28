package dev.carcara.kotlinx.locale.codegen

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader

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

fun Element.childElements(name: String? = null): List<Element> {
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

fun step(name: String, attrName: String, attrValue: String): Pair<String, Pair<String, String>> =
    name to (attrName to attrValue)
