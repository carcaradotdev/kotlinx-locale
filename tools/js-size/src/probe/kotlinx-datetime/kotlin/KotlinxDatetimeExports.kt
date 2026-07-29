package probe

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.Month
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number

/**
 * Touches exactly the `kotlinx-datetime` surface that the datetime module
 * exposes and reaches internally. Its imports are `LocalDate`, `LocalTime`,
 * `LocalDateTime`, `Month`, `DayOfWeek`, `number` and `isoDayNumber`, and the
 * formatter reads year, month, day, day-of-week, day-of-year, hour, minute and
 * second off them.
 *
 * Measured on its own this is the third-party share of the datetime scenario:
 * the cost a consumer pays for the dependency rather than for this library.
 */
@JsExport
fun kotlinxDatetimeSurface(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): String {
    val date = LocalDate(year, month, day)
    val time = LocalTime(hour, minute, second)
    val dateTime = LocalDateTime(date, time)

    return buildString {
        append(date.year)
        append(date.month.name)
        append(date.day)
        append(date.dayOfWeek.name)
        append(date.dayOfYear)
        append(time.hour)
        append(time.minute)
        append(time.second)
        append(dateTime.date.year)
        append(dateTime.time.hour)
        for (value in Month.entries) {
            append(value.name)
            append(value.number)
        }
        for (value in DayOfWeek.entries) {
            append(value.name)
            append(value.isoDayNumber)
        }
    }
}
