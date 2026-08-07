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

// The half of code generation that a user's build can run: emitters plus the
// reader for the pre-resolved CLDR bundle. Nothing here clones a repository or
// parses XML, so it is safe on a build classpath.
plugins {
    id("kotlinx-locale-jvm")
    id("kotlinx-locale-publish")
}

kotlin {
    explicitApi()
}

dependencies {
    testImplementation(libs.kotlin.test)
}

tasks.test {
    // LicenseHeaderTest compares the emitted header against the LICENSE file, so
    // it needs to know where the root of the checkout is.
    systemProperty("kotlinx.locale.rootDir", rootDir.absolutePath)
}
