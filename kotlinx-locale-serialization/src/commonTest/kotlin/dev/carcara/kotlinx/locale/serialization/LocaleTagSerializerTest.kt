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

package dev.carcara.kotlinx.locale.serialization

import dev.carcara.kotlinx.locale.Locale
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocaleTagSerializerTest {

    @Test
    fun writesTheCanonicalTag() {
        assertEquals("\"pt-BR\"", Json.encodeToString(LocaleTagSerializer, Locale.of("pt", region = "BR")))
        assertEquals("\"en\"", Json.encodeToString(LocaleTagSerializer, Locale.of("en")))
        assertEquals(
            "\"sr-Cyrl-BA\"",
            Json.encodeToString(LocaleTagSerializer, Locale.of("sr", script = "Cyrl", region = "BA")),
        )
    }

    @Test
    fun normalizesSubtagCaseOnTheWayOut() {
        // The instance normalizes, so the tag written is canonical whatever the
        // caller passed. Serialization inherits that rather than repeating it.
        assertEquals("\"pt-BR\"", Json.encodeToString(LocaleTagSerializer, Locale.of("PT", region = "br")))
    }

    @Test
    fun roundTripsEveryShapeOfTag() {
        val locales = listOf(
            Locale.of("en"),
            Locale.of("pt", region = "BR"),
            Locale.of("zh", script = "Hans", region = "CN"),
            Locale.of("de", region = "DE", variant = "1901"),
            Locale.of("es", region = "419"),
        )
        for (locale in locales) {
            val encoded = Json.encodeToString(LocaleTagSerializer, locale)
            assertEquals(locale, Json.decodeFromString(LocaleTagSerializer, encoded), encoded)
        }
    }

    @Test
    fun readsLenientlyBecauseForLanguageTagDoes() {
        // POSIX identifiers and Unicode extensions parse, so a tag written by
        // something other than this serializer still reads.
        assertEquals(
            Locale.of("pt", region = "BR"),
            Json.decodeFromString(LocaleTagSerializer, "\"pt_BR.UTF-8@latin\""),
        )
        assertEquals(
            Locale.of("en", region = "US"),
            Json.decodeFromString(LocaleTagSerializer, "\"en-US-u-ca-buddhist\""),
        )
    }

    @Test
    fun rejectsATagWithNoLanguageSubtag() {
        assertFailsWith<SerializationException> { Json.decodeFromString(LocaleTagSerializer, "\"\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(LocaleTagSerializer, "\"123\"") }
        assertFailsWith<SerializationException> { Json.decodeFromString(LocaleTagSerializer, "\"C\"") }
    }

    @Test
    fun worksInsideAGeneratedSerializer() {
        val preferences = Preferences(Locale.of("pt", region = "BR"), Locale.of("en"))
        val encoded = Json.encodeToString(preferences)
        assertEquals("""{"display":"pt-BR","fallback":"en"}""", encoded)
        assertEquals(preferences, Json.decodeFromString<Preferences>(encoded))
    }

    @Test
    fun worksAsACollectionElement() {
        val locales = listOf(Locale.of("pt", region = "BR"), Locale.of("ja"))
        val encoded = Json.encodeToString(ListSerializer(LocaleTagSerializer), locales)
        assertEquals("""["pt-BR","ja"]""", encoded)
        assertEquals(locales, Json.decodeFromString(ListSerializer(LocaleTagSerializer), encoded))
    }

    @Test
    fun resolvesContextually() {
        // The one-line alternative to annotating every property, for a codebase
        // that has settled on a single strategy.
        val json = Json { serializersModule = SerializersModule { contextual(LocaleTagSerializer) } }
        val request = Request(Locale.of("fr", region = "CA"))
        assertEquals("""{"locale":"fr-CA"}""", json.encodeToString(request))
        assertEquals(request, json.decodeFromString<Request>("""{"locale":"fr-CA"}"""))
    }

    @Serializable
    private data class Preferences(
        @Serializable(with = LocaleTagSerializer::class) val display: Locale,
        @Serializable(with = LocaleTagSerializer::class) val fallback: Locale,
    )

    @Serializable
    private data class Request(@Contextual val locale: Locale)
}
