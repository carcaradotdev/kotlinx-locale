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

import org.gradle.api.attributes.Attribute

/**
 * The attributes that let one project consume another's build output without
 * either of them reading the other's state.
 *
 * Sharing through a dependency configuration is the Isolated-Projects-safe way
 * to move data between projects: the producer declares what it offers, the
 * consumer declares what it wants, and the task dependency falls out of the
 * data flow rather than being asserted with a task path.
 */
object LocaleAttributes {

    /** Marks an artifact as something other than a normal library jar. */
    val KIND: Attribute<String> = Attribute.of("dev.carcara.locale.kind", String::class.java)

    /** One probe's measured bundle size, as a TSV row. */
    const val SIZE_REPORT: String = "size-report"
}
