package dev.carcara.kotlinx.locale.datetime.cldr

import dev.carcara.kotlinx.locale.datetime.DateTimeFormatSource
import dev.carcara.kotlinx.locale.datetime.cldr.format.PayloadDateTimeFormats
import dev.carcara.kotlinx.locale.datetime.cldr.internal.data.localeDataRegistry

/**
 * The date and time patterns CLDR ships, compiled into this artifact.
 *
 * All this object contributes is the table. The pattern parser and formatter
 * live in `kotlinx-locale-datetime-cldr-format`, which is also what a build that
 * generated a narrowed table binds to, so a narrowed build renders identically
 * to a full one for the locales it kept.
 */
public object CldrDateTime : DateTimeFormatSource by PayloadDateTimeFormats(localeDataRegistry)
