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

package dev.carcara.kotlinx.locale.codegen.pipeline

/**
 * The separators every payload record is built from.
 *
 * They live here rather than beside the emitters because this module is the one
 * that takes records apart and puts them back together. The emitters and the
 * runtime decoders both read the same three characters, and a codec that
 * invented a fourth would be inventing a format the runtime cannot read.
 */
public object RecordFormat {

    /** Between fields of one record. Field 0 is the parent tag when the shape is sparse. */
    public const val FIELD: String = ""

    /** Between entries within a field. */
    public const val ENTRY: String = ""

    /** Between an entry's key and its value. */
    public const val KEY: String = ""

    /**
     * Characters a codec may spend on its own encoding.
     *
     * Everything below U+0020 except the three separators above, plus U+007F.
     * CLDR text never contains them, which [PayloadCodecValidator] checks rather
     * than assumes. U+0000 is excluded: it costs two bytes in the modified UTF-8
     * of a class file and one in every other encoding, so a codec that reached
     * for it would pay for the privilege on the JVM and Android alone.
     */
    public val AVAILABLE_CONTROL_CHARS: List<Char> =
        ((0x01..0x1F).map(Int::toChar) + '')
            .filterNot { it.toString() in setOf(FIELD, ENTRY, KEY) }
}
