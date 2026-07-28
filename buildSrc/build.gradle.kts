plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.gradle.plugin.kotlin.multiplatform)
    implementation(libs.gradle.plugin.android.kmp.library)
    implementation(libs.gradle.plugin.ktlint)
}
