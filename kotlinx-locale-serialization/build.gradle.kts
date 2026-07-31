plugins {
    id("kotlinx-locale-multiplatform")
    // The serializers here are hand-written, so nothing in `commonMain` needs a
    // compiler plugin — only the `kotlinx-serialization-core` runtime. The tests
    // need it, because what they check is these serializers being used the way a
    // consumer uses them: from inside a plugin-generated serializer.
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-core"))
            // api, not implementation: KSerializer<Locale> is the return type of
            // everything this module declares, so a consumer cannot name one
            // without kotlinx-serialization-core on its own compile classpath.
            api(libs.kotlinx.serialization.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
