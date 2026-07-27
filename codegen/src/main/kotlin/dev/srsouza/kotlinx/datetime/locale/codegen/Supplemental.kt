package dev.srsouza.kotlinx.datetime.locale.codegen

import java.io.File

class SupplementalData(
    /** child locale id -> explicit parent locale id (component-less parentLocales only). */
    val parentOverrides: Map<String, String>,
    /** numbering system id -> its ten digits (numeric systems only). */
    val numberingSystemDigits: Map<String, String>,
)

fun parseSupplemental(cldrDir: File): SupplementalData {
    val supplementalDir = cldrDir.resolve("common/supplemental")

    val parentOverrides = LinkedHashMap<String, String>()
    val supplementalData = parseXml(supplementalDir.resolve("supplementalData.xml")).documentElement
    for (block in supplementalData.childElements("parentLocales")) {
        if (block.hasAttribute("component")) continue
        for (rule in block.childElements("parentLocale")) {
            val parent = rule.getAttribute("parent")
            for (child in rule.getAttribute("locales").split(' ')) {
                if (child.isNotBlank()) parentOverrides[child] = parent
            }
        }
    }

    val digits = LinkedHashMap<String, String>()
    val numberingSystems = parseXml(supplementalDir.resolve("numberingSystems.xml")).documentElement
    numberingSystems.child("numberingSystems")?.let { container ->
        for (system in container.childElements("numberingSystem")) {
            if (system.getAttribute("type") != "numeric") continue
            val id = system.getAttribute("id")
            val systemDigits = system.getAttribute("digits")
            if (id.isNotEmpty() && systemDigits.isNotEmpty()) digits[id] = systemDigits
        }
    }
    check("latn" in digits) { "numberingSystems.xml did not provide latn digits" }

    return SupplementalData(parentOverrides, digits)
}
