// The person name contract: the value type, the option enums and the source
// interface. No data and no algorithm, so a consumer can depend on the shape
// without pulling in either.
plugins {
    id("kotlinx-locale-multiplatform")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kotlinx-locale-core"))
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
