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

package dev.carcara.kotlinx.locale.test

import kotlin.contracts.contract

/**
 * kotlin-test's assertions, reimplemented so that kotlin-test itself does not
 * have to be on the classpath.
 *
 * Same names, same parameter order, same defaults, same message format. That is
 * the whole design goal: the reason this file exists is a Wasm entry-point
 * collision, not a dissatisfaction with the assertions, so the migration off
 * kotlin-test should not change what any test says.
 *
 * Everything throws [AssertionError], which is what `assertFailsWith<AssertionError>`
 * in the conformance meta-tests expects and what every test framework treats as
 * a failure rather than an error.
 */

/** Fails with [message], or a default, when [expected] and [actual] differ. */
public fun <T> assertEquals(expected: T, actual: T, message: String? = null) {
    if (expected != actual) {
        fail(messagePrefix(message) + "expected:<${expected.display()}> but was:<${actual.display()}>")
    }
}

/** Fails when [illegal] and [actual] are equal. */
public fun <T> assertNotEquals(illegal: T, actual: T, message: String? = null) {
    if (illegal == actual) {
        fail(messagePrefix(message) + "illegal value:<${illegal.display()}>")
    }
}

/**
 * Fails when [actual] is not `true`.
 *
 * The contract is not decoration. `assertTrue(text != null && text.isNotEmpty())`
 * followed by a line that uses `text` as non-null is a shape several suites here
 * are written in, and it only compiles because returning normally implies the
 * condition held.
 */
@OptIn(kotlin.contracts.ExperimentalContracts::class)
public fun assertTrue(actual: Boolean, message: String? = null) {
    contract { returns() implies actual }
    if (!actual) fail(messagePrefix(message) + "expected the value to be true")
}

/**
 * Fails when [block] does not return `true`.
 *
 * The lazy-message form: the message is built only on failure, which matters
 * where composing it means formatting a few thousand mismatches.
 */
public fun assertTrue(message: String? = null, block: () -> Boolean) {
    assertTrue(block(), message)
}

/** Fails when [actual] is not `false`. Contract as in [assertTrue], negated. */
@OptIn(kotlin.contracts.ExperimentalContracts::class)
public fun assertFalse(actual: Boolean, message: String? = null) {
    contract { returns() implies !actual }
    if (actual) fail(messagePrefix(message) + "expected the value to be false")
}

/**
 * Fails when [actual] is null, and returns it as non-null otherwise.
 *
 * The contract is what lets a caller keep using the value afterwards without
 * repeating the null check, which is how kotlin-test's version reads and how
 * several suites here are written.
 */
@OptIn(kotlin.contracts.ExperimentalContracts::class)
public fun <T : Any> assertNotNull(actual: T?, message: String? = null): T {
    contract { returns() implies (actual != null) }
    if (actual == null) fail(messagePrefix(message) + "expected the value to not be null")
    return actual
}

/** Fails when [actual] is not null. */
public fun assertNull(actual: Any?, message: String? = null) {
    if (actual != null) fail(messagePrefix(message) + "expected the value to be null, but was:<${actual.display()}>")
}

/** Fails when [charSequence] does not contain [other]. */
public fun assertContains(charSequence: CharSequence, other: CharSequence, ignoreCase: Boolean = false, message: String? = null) {
    if (!charSequence.contains(other, ignoreCase)) {
        fail(messagePrefix(message) + "expected the char sequence to contain:<$other>, but was:<$charSequence>")
    }
}

/** Fails when [iterable] does not contain [element]. */
public fun <T> assertContains(iterable: Iterable<T>, element: T, message: String? = null) {
    if (!iterable.contains(element)) {
        fail(messagePrefix(message) + "expected the collection to contain:<${element.display()}>")
    }
}

/**
 * Runs [block] and returns the [T] it threw, failing when it threw something
 * else or nothing at all.
 */
public inline fun <reified T : Throwable> assertFailsWith(message: String? = null, block: () -> Unit): T {
    try {
        block()
    } catch (thrown: Throwable) {
        if (thrown is T) return thrown
        // An AssertionError from inside the block is a failed assertion, not the
        // exception under test, and saying so is more useful than "expected T".
        throw AssertionError(
            messagePrefix(message) + "expected an exception of type ${T::class.simpleName} " +
                "but was ${thrown::class.simpleName}: ${thrown.message}",
            thrown,
        )
    }
    fail(messagePrefix(message) + "expected an exception of type ${T::class.simpleName} to be thrown, but nothing was")
}

/** Fails unconditionally. */
public fun fail(message: String? = null): Nothing = throw AssertionError(message ?: "failed")

@PublishedApi
internal fun messagePrefix(message: String?): String = if (message.isNullOrEmpty()) "" else "$message. "

/**
 * A value as it should read in a failure.
 *
 * Strings are quoted, because the difference between a trailing space and no
 * trailing space is the whole content of a formatting mismatch and is invisible
 * without them. Null prints as `null` rather than as an empty string.
 */
private fun Any?.display(): String = when (this) {
    null -> "null"
    is String -> "\"$this\""
    else -> toString()
}
