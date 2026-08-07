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

import dev.carcara.kotlinx.locale.conformance.ConformanceTier
import dev.carcara.kotlinx.locale.conformance.assertConformsToDateTimeFormats
import dev.carcara.kotlinx.locale.datetime.cldr.CldrDateTime
import kotlin.test.Test

/**
 * Month and weekday names are held to ICU exactly. The patterns behind the
 * formatted output are cross-checked separately in [IcuGoldenTest], which can
 * reach the tables directly because it lives in the module that owns them.
 */
class CldrDateTimeConformanceTest {

    @Test
    fun conformsExactly() = CldrDateTime.assertConformsToDateTimeFormats(ConformanceTier.EXACT)
}
