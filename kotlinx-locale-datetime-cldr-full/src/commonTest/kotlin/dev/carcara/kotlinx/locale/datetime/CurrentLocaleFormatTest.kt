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

package dev.carcara.kotlinx.locale.datetime

import dev.carcara.kotlinx.locale.Locale
import dev.carcara.kotlinx.locale.datetime.cldr.format
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertTrue

class CurrentLocaleFormatTest {

    @Test
    fun currentLocaleFormatsEndToEnd() {
        // Whatever the platform reports must be formattable.
        val formatted = LocalDate(2026, 7, 27).format(FormatStyle.MEDIUM, Locale.current)
        assertTrue(formatted.isNotBlank())
    }
}
