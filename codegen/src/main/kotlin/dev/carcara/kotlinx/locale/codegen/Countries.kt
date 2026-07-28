package dev.carcara.kotlinx.locale.codegen

import java.io.File

/** One ISO 3166-1 country: both alpha codes, the numeric code and its English name. */
class CountryInfo(val alpha2: String, val alpha3: String, val numeric: Int, val englishName: String)

/**
 * The alpha-2 region codes CLDR considers `regular` (common/validity/region.xml).
 * Ranges are written with `~` over the last character: `AL~M` means AL, AM.
 */
fun parseRegularRegions(cldrDir: File): Set<String> {
    val root = parseXml(cldrDir.resolve("common/validity/region.xml")).documentElement
    val idValidity = root.child("idValidity") ?: error("region.xml: missing idValidity")
    val result = LinkedHashSet<String>()
    for (idEl in idValidity.childElements("id")) {
        if (idEl.getAttribute("type") != "region" || idEl.getAttribute("idStatus") != "regular") continue
        for (token in idEl.textContent.trim().split(Regex("\\s+"))) {
            val tilde = token.indexOf('~')
            if (tilde < 0) {
                result.add(token)
                continue
            }
            val start = token.substring(0, tilde)
            val endSuffix = token.substring(tilde + 1)
            check(endSuffix.length == 1) { "region.xml: unsupported range '$token'" }
            val prefix = start.dropLast(1)
            for (last in start.last()..endSuffix.single()) result.add(prefix + last)
        }
    }
    check(result.size > 200) { "region.xml: implausibly few regular regions (${result.size})" }
    return result
}

/**
 * The ISO 3166-1 country codes: CLDR-regular alpha-2 regions that ISO assigned
 * both an alpha-3 and a numeric code. This drops CLDR-only regions: macroregions,
 * exceptionally reserved codes like AC or IC, and user-assigned codes like XK —
 * the latter via the numeric range, since ISO reserves 900-999 for user
 * assignment (CLDR maps XK to 983).
 */
fun countryTerritoryCodes(regularRegions: Set<String>, supplemental: SupplementalData): List<TerritoryCodes> {
    val codes = regularRegions.sorted().mapNotNull { alpha2 ->
        if (alpha2.length != 2 || !alpha2.all { it in 'A'..'Z' }) return@mapNotNull null
        supplemental.territoryCodes[alpha2]
            ?.takeIf { it.alpha3 != null && it.numeric != null && it.numeric < 900 }
    }
    check(codes.size in 240..260) { "implausible country count: ${codes.size}" }
    return codes
}

fun buildCountryList(codes: List<TerritoryCodes>, englishNameFor: (String) -> String?): List<CountryInfo> = codes.map { territory ->
    CountryInfo(
        alpha2 = territory.alpha2,
        alpha3 = territory.alpha3!!,
        numeric = territory.numeric!!,
        englishName = englishNameFor(territory.alpha2)
            ?: error("no English display name for territory ${territory.alpha2}"),
    )
}
