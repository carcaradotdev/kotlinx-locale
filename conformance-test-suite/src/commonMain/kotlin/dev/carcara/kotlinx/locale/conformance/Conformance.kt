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

package dev.carcara.kotlinx.locale.conformance

/**
 * How strictly a source is held to the ICU fixtures.
 *
 * The distinction is not strictness for its own sake. A CLDR-backed source is a
 * second encoding of the data ICU encodes, so any disagreement is a bug in one
 * of them and the suite should say so. A platform-backed source reads whatever
 * the host shipped, which moves with OS versions and cannot be pinned to a
 * fixture without the test becoming a report on the CI image.
 *
 * This module is not published. The tiers exist so that the two implementations
 * of each contract in this repository can share one suite, not as a compliance
 * kit for sources outside it.
 */
public enum class ConformanceTier {

    /**
     * Every name and symbol must equal ICU's, byte for byte once the no-break
     * space variants that point releases disagree on are normalized.
     *
     * For the sources compiled from CLDR.
     */
    EXACT,

    /**
     * Answers must be well-shaped — non-blank, the right number of them, round
     * tripping where the API promises it — but need not match ICU.
     *
     * For platform sources, where the data belongs to the host.
     */
    BEHAVIOURAL,
}

/**
 * Normalizes the no-break space variants that ICU and CLDR point releases
 * disagree on: U+00A0 NO-BREAK SPACE and U+202F NARROW NO-BREAK SPACE.
 *
 * Public rather than internal because the ICU comparisons that use it live in
 * the module that owns each fixture, not here. The shared module carries the
 * contract every source owes; the fixtures and the comparisons against them sit
 * next to the one source they describe, so that a module's test binary links its
 * own goldens and nobody else's.
 */
public fun String.normalizedSpaces(): String = replace('\u00A0', ' ').replace('\u202F', ' ')
