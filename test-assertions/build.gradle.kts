// The assertion vocabulary the test suites are written in.
//
// This exists because kotlin-test cannot share a Kotlin/Wasm compilation with
// TestBalloon: both emit a `startUnitTests` entry point, a Wasm module cannot
// export one name twice, and Node refuses to load the result. Kotlin/JS is
// unaffected, so the collision only shows up on the two Wasm targets.
//
// The alternative was to write every assertion in Kotest's infix style, which
// the matrix addon already brings. That is a different reading order at ten
// thousand call sites and flips the argument order of the most common one, so
// the risk is not in the idea but in the transcription. These functions carry
// kotlin-test's names, parameter order and defaults instead, and the migration
// was an import swap.
//
// No dependencies on purpose: this lands on every test compilation in the build,
// including modules whose own dependency graph it must not disturb.
plugins {
    id("kotlinx-locale-multiplatform-base")
}
