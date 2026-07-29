// What a consumer writes to ship three locales instead of 1121.
plugins {
    kotlin("jvm") version "2.4.0"
    id("dev.carcara.kotlinx-locale") version "0.1.0-SNAPSHOT"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // The contract and the entry sets. Note what is absent: no -cldr artifact,
    // because the data comes from the generator instead.
    implementation("dev.carcara:kotlinx-locale-country-core:0.1.0-SNAPSHOT")
    implementation("dev.carcara:kotlinx-locale-country-cldr-format:0.1.0-SNAPSHOT")
    implementation("dev.carcara:kotlinx-locale-currency-core:0.1.0-SNAPSHOT")
    implementation("dev.carcara:kotlinx-locale-currency-cldr-format:0.1.0-SNAPSHOT")
    implementation("dev.carcara:kotlinx-locale-datetime-core:0.1.0-SNAPSHOT")
    implementation("dev.carcara:kotlinx-locale-datetime-cldr-format:0.1.0-SNAPSHOT")

    testImplementation(kotlin("test"))
}

kotlinxLocale {
    // Pt.BR and En.US would be the type-checked form; tags are used here so the
    // sample reads without the catalog import.
    locales("pt-BR", "en", "ja")
    fallback("en")

    packageName = "com.example.locale"

    country { names = true }
    currency { names = true; formats = true }
    datetime { patterns = true }
}

tasks.test {
    useJUnitPlatform()
}
