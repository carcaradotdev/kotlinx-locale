package dev.carcara.kotlinx.locale

/**
 * A source of locale-keyed data: CLDR tables compiled into an artifact, a
 * platform API, a narrowed set generated for one build, or a composition of
 * several.
 *
 * The domain interfaces that extend this one are partial on purpose. Every
 * lookup returns `null` where the source has nothing, so that a composing
 * source can tell a miss from an answer. Code that wants a total operation
 * calls the extensions each domain's `-core` layers over its interface, which
 * supply the documented fallback.
 *
 * Implementations are stateless and safe to share.
 */
public interface LocaleDataSource {

    /**
     * The locales this source carries data for.
     *
     * Which locales resolve is a property of the installed source rather than
     * of the [Locale] type, and it stops being a fixed list the moment a build
     * narrows what it generates.
     */
    public val supportedLocales: Set<Locale>
}
