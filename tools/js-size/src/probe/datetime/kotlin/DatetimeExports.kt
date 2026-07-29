package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.FormatStyle
import dev.carcara.kotlinx.locale.datetime.TextStyle
import dev.carcara.kotlinx.locale.datetime.displayName
import dev.carcara.kotlinx.locale.datetime.format
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month

/**
 * Touches every public declaration of `dev.carcara:kotlinx-locale-datetime`.
 * Every format style is exercised, which pulls in the CLDR pattern, month, day
 * and day-period tables.
 *
 * Note that this scenario also drags in the parts of `kotlinx-datetime` that the
 * formatter signatures expose (`LocalDate`, `LocalTime`, `LocalDateTime`,
 * `Month`, `DayOfWeek`), because a consumer cannot call the API without them.
 */
@JsExport
fun datetimeSurface(tag: String, year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): String {
    val locale = Locale.forLanguageTag(tag)
    val date = LocalDate(year, month, day)
    val time = LocalTime(hour, minute, second)
    val dateTime = LocalDateTime(date, time)

    return buildString {
        for (style in FormatStyle.entries) {
            append(style.name)
            append(style.ordinal)
            append(FormatStyle.valueOf(style.name).ordinal)
            append(date.format(style, locale))
            append(time.format(style, locale))
            append(dateTime.format(style, locale))
            for (timeStyle in FormatStyle.entries) {
                append(dateTime.format(style, timeStyle, locale))
            }
        }
        for (style in TextStyle.entries) {
            append(style.name)
            append(style.ordinal)
            append(TextStyle.valueOf(style.name).ordinal)
            for (value in Month.entries) {
                append(value.displayName(style, locale))
            }
            for (value in DayOfWeek.entries) {
                append(value.displayName(style, locale))
            }
        }
    }
}
