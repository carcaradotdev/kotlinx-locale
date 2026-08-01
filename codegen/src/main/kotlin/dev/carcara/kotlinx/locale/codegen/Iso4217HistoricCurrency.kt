package dev.carcara.kotlinx.locale.codegen

/**
 * One withdrawn ISO 4217 currency, aggregated across the per-country entries of
 * the official "list three" published by SIX.
 *
 * The XML snapshot is vendored under codegen resources alongside list one, so
 * generation stays reproducible and needs no network.
 */
class Iso4217HistoricCurrency(
    val code: String,
    val numericCode: Int?,
    val name: String,
    /** The latest withdrawal SIX records for this code, as `yyyy-MM`. */
    val withdrawnOn: String,
)

/**
 * Reads list three.
 *
 * Unlike list one, this file repeats an alpha code across separate withdrawal
 * events: HRK appears for the 2015 redenomination and again for the 2023 switch
 * to the euro, and YUM appears under two different numeric codes. So this
 * aggregates by code and keeps the most recent event, where the list-one parser
 * asserts the entries agree. Asserting here would fail on real data.
 *
 * Some entries carry no `CcyNbr` at all, which is why the numeric code is
 * nullable.
 */
fun parseIso4217Historic(): List<Iso4217HistoricCurrency> {
    val stream = Iso4217HistoricCurrency::class.java.getResourceAsStream("/iso4217/list-three.xml")
        ?: error("iso4217/list-three.xml is missing from codegen resources")
    val root = stream.use(::parseXml).documentElement
    val table = root.child("HstrcCcyTbl") ?: error("list-three.xml: missing HstrcCcyTbl")

    val byCode = LinkedHashMap<String, Iso4217HistoricCurrency>()
    for (entry in table.childElements("HstrcCcyNtry")) {
        val code = entry.child("Ccy")?.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: continue
        val numeric = entry.child("CcyNbr")?.textContent?.trim()?.toIntOrNull()
        val name = entry.child("CcyNm")?.textContent?.trim().orEmpty()
        val withdrawn = entry.child("WthdrwlDt")?.textContent?.trim().orEmpty()
        val existing = byCode[code]
        if (existing == null || withdrawn > existing.withdrawnOn) {
            byCode[code] = Iso4217HistoricCurrency(code, numeric ?: existing?.numericCode, name, withdrawn)
        }
    }
    check(byCode.size > 120) { "list-three.xml: implausibly few withdrawn currencies (${byCode.size})" }
    return byCode.values.sortedBy(Iso4217HistoricCurrency::code)
}
