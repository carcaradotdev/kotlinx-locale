package dev.carcara.kotlinx.locale

/**
 * The tags a formatter module should try, most specific first, when looking up
 * locale-keyed data for this locale. The list never contains `root`; callers
 * fall back to their bundled root data when nothing matches.
 */
@InternalKotlinxLocaleApi
public fun Locale.dataLookupTags(): List<String> = buildList {
    fun addTag(vararg parts: String?) {
        val tag = parts.filterNotNull().joinToString("-")
        if (tag.isNotEmpty() && tag !in this) add(tag)
    }
    addTag(language, script, region, variant)
    addTag(language, script, region)
    addTag(language, region, variant)
    addTag(language, region)
    addTag(language, script)
    addTag(language, variant)
    addTag(language)
}
