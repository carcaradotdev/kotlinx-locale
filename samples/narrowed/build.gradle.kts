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

// What a consumer writes to ship three locales instead of 1121.
plugins {
    kotlin("jvm") version "2.4.0"
    id("dev.carcara.kotlinx-locale") version "0.1.0-SNAPSHOT"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // The contract and the entry sets. Note what is absent: no -cldr-full artifact,
    // because the data comes from the generator instead.
    implementation("dev.carcara:kotlinx-locale-country-core:0.1.0-SNAPSHOT")
    implementation("dev.carcara:kotlinx-locale-country-cldr-runtime:0.1.0-SNAPSHOT")
    implementation("dev.carcara:kotlinx-locale-currency-core:0.1.0-SNAPSHOT")
    implementation("dev.carcara:kotlinx-locale-currency-cldr-runtime:0.1.0-SNAPSHOT")
    // The plural rules that pick which spelling of a currency name a count takes.
    implementation("dev.carcara:kotlinx-locale-number-core:0.1.0-SNAPSHOT")
    implementation("dev.carcara:kotlinx-locale-number-cldr-runtime:0.1.0-SNAPSHOT")
    implementation("dev.carcara:kotlinx-locale-datetime-core:0.1.0-SNAPSHOT")
    implementation("dev.carcara:kotlinx-locale-datetime-cldr-runtime:0.1.0-SNAPSHOT")

    testImplementation(kotlin("test"))
}

kotlinxLocale {
    // PT.BR, EN and JA would be the type-checked form; tags are used here so the
    // sample reads without the catalog import.
    locales("pt-BR", "en", "ja")
    fallback("en")

    packageName = "com.example.locale"

    country { names = true }
    currency { names = true; formats = true; pluralNames = true }
    datetime { patterns = true }
}

tasks.test {
    useJUnitPlatform()
}
