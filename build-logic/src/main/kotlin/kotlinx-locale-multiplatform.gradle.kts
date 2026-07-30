import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

/**
 * What a published multiplatform library module adds to the shared setup in
 * `kotlinx-locale-multiplatform-base`: publication and ABI validation.
 *
 * The two are the same decision. An artifact that reaches Maven Central has a
 * public ABI other people compile against, so it gets a committed dump; a module
 * that never leaves this build has no such obligation and applies the base
 * plugin instead.
 */
plugins {
    id("kotlinx-locale-multiplatform-base")
    // Applied again so this script gets the `kotlin` accessor. Applying a plugin
    // twice is a no-op.
    id("org.jetbrains.kotlin.multiplatform")
    `maven-publish`
}

kotlin {
    // Dumps the public ABI of every target to api/<module>.klib.api and
    // api/jvm/<module>.api. `checkKotlinAbi` compares the code against those
    // files; `updateKotlinAbi` rewrites them.
    @OptIn(ExperimentalAbiValidation::class)
    abiValidation()
}

// The plugin wires checkKotlinAbi into `check` by default; detach it again. A
// complete comparison needs a klib for every target and only a macOS host can
// compile all of them, so `check` on Linux or Windows would quietly compare a
// subset of the ABI and report success. Run `./gradlew checkKotlinAbi` on a Mac
// instead.
//
// This is afterEvaluate rather than a lazy Provider chain because KGP adds the
// dependency from its own afterEvaluate and offers no lazy hook to intercept it,
// which is the third-party-bridge exception to the no-afterEvaluate rule. It was
// gradle.projectsEvaluated before, which registered a build-scoped callback that
// then mutated this project: the same job, but an Isolated Projects violation.
@OptIn(ExperimentalAbiValidation::class)
val abiCheckTaskName = kotlin.abiValidation.checkTaskProvider.name
afterEvaluate {
    tasks.named("check") {
        setDependsOn(dependsOn.filterNot { it is TaskProvider<*> && it.name == abiCheckTaskName })
    }
}
