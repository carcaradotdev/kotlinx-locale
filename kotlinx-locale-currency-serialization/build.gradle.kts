plugins {
    id("kotlinx-locale-multiplatform")
    // See kotlinx-locale-serialization: hand-written serializers need no
    // compiler plugin, the tests that exercise them from a generated one do.
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-currency-core"))
            api(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
