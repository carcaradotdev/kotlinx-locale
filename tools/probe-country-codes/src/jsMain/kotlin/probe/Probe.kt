@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.country.Country
import dev.carcara.kotlinx.locale.country.alpha2
import dev.carcara.kotlinx.locale.country.forAlpha2
import dev.carcara.kotlinx.locale.country.forAlpha3OrNull
import dev.carcara.kotlinx.locale.country.forNumericCodeOrNull

/** Codes and lookups, no translated text. */
@JsExport
public fun probe(code: String, alpha3: String, numeric: Int): String {
    val byCode = Country.forAlpha2(code)
    return listOf(
        byCode.alpha2,
        byCode.alpha3,
        byCode.numericCode.toString(),
        Country.forAlpha3OrNull(alpha3)?.alpha2,
        Country.forNumericCodeOrNull(numeric)?.alpha2,
        Country.entries.size.toString(),
    ).joinToString(" ")
}
