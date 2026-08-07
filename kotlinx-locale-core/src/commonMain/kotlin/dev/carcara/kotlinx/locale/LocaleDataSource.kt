/*
 * Copyright 2026 Carcara.dev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
     * Which locales resolve is a property of the installed source rather than of
     * the [Locale] type, and it stops being a fixed list the moment a build
     * narrows what it generates.
     *
     * An empty set means the source cannot enumerate what it supports, not that
     * it supports nothing. A platform source is the reason that distinction
     * exists: ECMA-402 will filter a list of locales you already have but offers
     * no way to ask for the list, so a source over `Intl` answers any lookup
     * while being unable to describe its own coverage. Treat this as a report,
     * not as a precondition; asking for a locale that is not in the set is always
     * allowed and the documented fallbacks apply.
     */
    public val supportedLocales: Set<Locale>
}
