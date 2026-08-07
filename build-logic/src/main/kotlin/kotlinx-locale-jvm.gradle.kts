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

/**
 * A plain JVM module: the code generator, the emitters it publishes, and the
 * CLDR bundle.
 *
 * These are not multiplatform and are never consumed from common code, so they
 * skip the target matrix, the ABI dumps and the Android setup that
 * `kotlinx-locale-multiplatform` brings.
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("kotlinx-locale-ktlint")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
