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

import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * ktlint, with generated sources excluded.
 *
 * Applied by each module through the convention plugin it already uses, rather
 * than pushed onto every project from the root with `subprojects {}`. The root
 * cannot configure another project without reading its state, which is what
 * Isolated Projects forbids and what makes parallel configuration possible.
 */
plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

configure<KtlintExtension> {
    filter {
        // Generated sources carry a marker header and are not hand-formatted.
        // Matching on the header rather than on a path list means a new
        // generated file cannot quietly start being linted, or stop being.
        exclude { entry -> !entry.isDirectory && isGeneratedSource(entry.file) }
    }
}
