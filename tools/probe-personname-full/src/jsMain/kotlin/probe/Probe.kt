@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.personname.PersonName
import dev.carcara.kotlinx.locale.personname.PersonNameUsage
import dev.carcara.kotlinx.locale.personname.cldr.personNameFormat

/** Both paths a name is used through: written out, and reduced to initials. */
@JsExport
public fun probe(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    val name = PersonName(given = "Iris", surname = "Adler", locale = locale)
    return personNameFormat(name, locale = locale) +
        " " + personNameFormat(name, usage = PersonNameUsage.MONOGRAM, locale = locale)
}
