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

// Measures what the generated data weighs, per platform, without building any
// of them. `docs/size.md` measures the other end: a real Kotlin/JS bundle after
// compression. This one measures the data, because that is the half a codec
// changes and building twenty-five native targets to see a table shrink is a
// waste of a machine.
plugins {
    id("kotlinx-locale-jvm")
}

dependencies {
    implementation(project(":kotlinx-locale-codegen-emitters"))
    testImplementation(libs.kotlin.test)
}

val mainClassFqn = "dev.carcara.kotlinx.locale.size.MainKt"
val document = rootProject.layout.projectDirectory.file("docs/data-size.md")

val dataSizeReport = tasks.register<JavaExec>("dataSizeReport") {
    group = "verification"
    description = "Measures the generated data and writes the report into build/"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = mainClassFqn
    args("report", rootDir.absolutePath, layout.buildDirectory.file("reports/size/data-size.md").get().asFile.absolutePath)
}

tasks.register<Copy>("updateDataSizeDoc") {
    group = "documentation"
    description = "Regenerates docs/data-size.md"
    dependsOn(dataSizeReport)
    from(layout.buildDirectory.file("reports/size/data-size.md"))
    into(document.asFile.parentFile)
}

// `-Pref=` picks what to compare against; the default is the branch a change is
// going to land on.
tasks.register<JavaExec>("compareDataSize") {
    group = "verification"
    description = "Prints what the generated data gained or lost against a git ref (-Pref=origin/main)"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = mainClassFqn
    args("compare", rootDir.absolutePath, providers.gradleProperty("ref").getOrElse("origin/main"))
}
