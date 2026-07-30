@file:OptIn(ExperimentalJsExport::class)

package probe

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.cldr.skeletons.format
import dev.carcara.kotlinx.locale.datetime.cldr.skeletons.skeletonPatternOrNull
import kotlinx.datetime.LocalDateTime

/**
 * Skeleton formatting: the matcher, the skeleton tables and the pattern tables
 * it scores against.
 *
 * Paired with `probe-datetime-full`, so the difference between the two rows is
 * what opting into skeletons costs and nothing else.
 */
@JsExport
public fun probe(iso: String, tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    val moment = LocalDateTime.parse(iso)
    return listOf(
        moment.date.format("yMMMd", locale),
        moment.date.format("MMMEd", locale),
        moment.time.format("jm", locale),
        moment.format("yMMMdjms", locale),
        moment.date.format("yQQQQ", locale),
        skeletonPatternOrNull("yMMMMd", locale).orEmpty(),
    ).joinToString(" ")
}
