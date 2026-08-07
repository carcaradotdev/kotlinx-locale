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
 * Marks APIs that exist so that kotlinx-locale formatter modules (datetime,
 * and in the future currency and others) can share the locale infrastructure.
 * These APIs have no compatibility guarantees for general use.
 */
@RequiresOptIn(
    message = "This is an internal kotlinx-locale API for formatter modules. " +
        "It can change without warning in any release.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY,
    // A constructor, so a public type can be built only by the modules that own
    // its invariants: FormattedNumber's operands are trustworthy exactly because
    // the formatter that printed the digits is what constructed it.
    AnnotationTarget.CONSTRUCTOR,
)
public annotation class InternalKotlinxLocaleApi
