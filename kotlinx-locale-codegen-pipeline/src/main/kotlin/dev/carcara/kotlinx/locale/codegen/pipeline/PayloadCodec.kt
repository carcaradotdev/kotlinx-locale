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

package dev.carcara.kotlinx.locale.codegen.pipeline

/**
 * How a record's fields are laid out, which is all a codec needs to know about
 * the table it is rewriting.
 *
 * Deliberately not `BundleSection`. A codec has no business knowing which CLDR
 * section it is looking at, or whether narrowing may drop rows; it needs to know
 * where the fields are. Keeping the vocabulary this small is what lets a codec
 * be tested against three hand-written records instead of a CLDR checkout.
 */
public class PayloadShape(public val sparseFields: Int = 0) {

    /** True when field 0 is the parent tag rather than data. */
    public val isSparse: Boolean get() = sparseFields > 0

    override fun toString(): String = if (isSparse) "sparse($sparseFields)" else "resolved"

    public companion object {

        /** A record that holds everything its locale needs. */
        public val Resolved: PayloadShape = PayloadShape()
    }
}

/**
 * What a codec produces for one table.
 *
 * [sharedTables] is the part that makes a codec worth having: anything the
 * records used to repeat and now name once. The emitter writes those as their
 * own constants, and a codec that fills this in has to come with a runtime
 * decoder that reads them back, or the generated sources will not answer.
 */
public class EncodedPayloads(public val payloadByTag: Map<String, String>, public val sharedTables: List<String> = emptyList()) {

    override fun toString(): String = "EncodedPayloads(${payloadByTag.size} records, ${sharedTables.size} shared tables)"

    public companion object
}

/**
 * A reversible rewrite of one table's payloads.
 *
 * A codec is the whole extension point. It sees a table's records and gives back
 * records the runtime can still answer from, plus whatever it hoisted out of
 * them. It does not write files, does not know what a Kotlin constant is, and
 * cannot see the other tables, which is what keeps one measurable on its own.
 *
 * [decode] exists for [PayloadCodecValidator] rather than for the runtime, which
 * reads the encoded form directly. It is how a codec proves it did not lose
 * anything, and it is not optional: three separate encodings in this repository
 * looked correct and were not, and each was caught by decoding rather than by
 * reading the encoder.
 */
public interface PayloadCodec {

    /**
     * Stable name, written into the generated source.
     *
     * The runtime dispatches on it, so changing the id of a codec that is
     * already shipping is changing the format.
     */
    public val id: String

    public fun encode(shape: PayloadShape, payloads: Map<String, String>): EncodedPayloads

    public fun decode(shape: PayloadShape, encoded: EncodedPayloads): Map<String, String>

    public companion object {

        /**
         * The codec that changes nothing, and the default everywhere.
         *
         * Having an identity in the chain means the pipeline can be wired in
         * and proven inert before any codec is written, and means turning a
         * codec off is a value rather than a branch.
         */
        public val Identity: PayloadCodec = IdentityCodec
    }
}

private object IdentityCodec : PayloadCodec {

    override val id: String get() = "identity"

    override fun encode(shape: PayloadShape, payloads: Map<String, String>): EncodedPayloads = EncodedPayloads(payloads)

    override fun decode(shape: PayloadShape, encoded: EncodedPayloads): Map<String, String> = encoded.payloadByTag

    override fun toString(): String = id
}
