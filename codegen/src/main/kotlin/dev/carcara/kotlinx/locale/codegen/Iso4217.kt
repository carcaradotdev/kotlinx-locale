package dev.carcara.kotlinx.locale.codegen

/**
 * One active ISO 4217 currency, aggregated across the per-country entries of the
 * official "list one" published by SIX (the ISO 4217 maintenance agency). The XML
 * snapshot is vendored under codegen resources so generation stays reproducible.
 */
class Iso4217Currency(
    val code: String,
    val numericCode: Int?,
    /** ISO minor units; -1 when the standard lists "N.A." (metals, special codes). */
    val minorUnits: Int,
    val name: String,
)

class Iso4217Data(
    /** The Pblshd date of the vendored list, e.g. 2026-01-01. */
    val published: String,
    /** Active currencies sorted by alphabetic code. */
    val currencies: List<Iso4217Currency>,
)

fun parseIso4217(): Iso4217Data {
    val stream = Iso4217Data::class.java.getResourceAsStream("/iso4217/list-one.xml")
        ?: error("iso4217/list-one.xml is missing from codegen resources")
    val root = stream.use(::parseXml).documentElement
    val published = root.getAttribute("Pblshd")
    check(published.isNotEmpty()) { "list-one.xml: missing Pblshd attribute" }

    val byCode = LinkedHashMap<String, Iso4217Currency>()
    val table = root.child("CcyTbl") ?: error("list-one.xml: missing CcyTbl")
    for (entry in table.childElements("CcyNtry")) {
        // Entries without a Ccy element are territories with no universal currency.
        val code = entry.child("Ccy")?.textContent?.trim() ?: continue
        val numeric = entry.child("CcyNbr")?.textContent?.trim()?.toIntOrNull()
        val minorUnits = entry.child("CcyMnrUnts")?.textContent?.trim()?.toIntOrNull() ?: -1
        val name = entry.child("CcyNm")?.textContent?.trim().orEmpty()
        val existing = byCode[code]
        if (existing == null) {
            byCode[code] = Iso4217Currency(code, numeric, minorUnits, name)
        } else {
            check(existing.numericCode == numeric && existing.minorUnits == minorUnits) {
                "list-one.xml: conflicting entries for $code"
            }
        }
    }
    check(byCode.size > 150) { "list-one.xml: implausibly few currencies (${byCode.size})" }
    return Iso4217Data(published, byCode.values.sortedBy(Iso4217Currency::code))
}
