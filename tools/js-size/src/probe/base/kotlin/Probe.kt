package probe

/**
 * The bundle entry point. It stays empty on purpose: with no modules selected
 * this file alone is the baseline, and its bundle size is the Kotlin/JS runtime
 * floor that every scenario pays regardless of which library modules are used.
 */
fun main() {
    // Nothing to run. The probe is compiled and bundled, never executed.
}
