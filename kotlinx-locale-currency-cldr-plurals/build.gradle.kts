// "2 US dollars": the currency names that agree with a count, in their own
// artifact.
//
// 42,712 count-keyed names across the 213 locales that declare them, which is
// several times every other currency table put together. Symbols, display names,
// patterns and parsing all work without them, so this is worth asking for
// deliberately.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The count-less display name is the third step of the fallback
            // chain, and it lives in the currency binding's own table.
            api(project(":kotlinx-locale-currency-cldr-full"))
            // The plural rules that choose which of the forms a number takes.
            api(project(":kotlinx-locale-number-cldr-full"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(project(":conformance-test-suite"))
        }
    }
}
