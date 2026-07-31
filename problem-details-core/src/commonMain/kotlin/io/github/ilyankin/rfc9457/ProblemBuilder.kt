package io.github.ilyankin.rfc9457

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

@DslMarker
public annotation class ProblemDsl

/**
 * Builds a [Problem]. Obtain one through [problem].
 *
 * [json] encodes the typed payloads handed to [extension] and [extensions]; it defaults to
 * [ProblemJson] and is worth overriding when the application's own kotlinx configuration matters —
 * see [ProblemJson].
 */
@ProblemDsl
public class ProblemBuilder
    @PublishedApi
    internal constructor(
        private val json: Json = ProblemJson,
    ) {
        public var type: String = Problem.ABOUT_BLANK
        public var status: Int? = null
        public var title: String? = null
        public var detail: String? = null
        public var instance: String? = null

        private val extensions: MutableMap<String, ProblemValue> = LinkedHashMap()

        /** Applies a [ProblemType]'s type URI, title and status in one call. */
        public fun type(type: ProblemType) {
            this.type = type.typeUri
            this.title = type.title
            this.status = type.status
        }

        public fun extension(
            name: String,
            value: ProblemValue,
        ): Unit = put(name, value)

        public fun extension(
            name: String,
            value: String,
        ): Unit = put(name, ProblemPrimitive(value))

        public fun extension(
            name: String,
            value: Int,
        ): Unit = put(name, ProblemPrimitive(value))

        public fun extension(
            name: String,
            value: Long,
        ): Unit = put(name, ProblemPrimitive(value))

        public fun extension(
            name: String,
            value: Double,
        ): Unit = put(name, ProblemPrimitive(value))

        public fun extension(
            name: String,
            value: Boolean,
        ): Unit = put(name, ProblemPrimitive(value))

        /** Adds one extension member holding an arbitrary serializable value. */
        public fun <T> extension(
            name: String,
            value: T,
            serializer: SerializationStrategy<T>,
        ) {
            put(name, json.encodeToJsonElement(serializer, value).toProblemValue())
        }

        /**
         * Spreads [value]'s own properties into the problem as sibling extension members (RFC 9457
         * §3.2) — the typed producing edge. [value] must serialize to a JSON object.
         *
         * A property named like one of the five §3.1 standard members is rejected here rather than at
         * [build], so the error names the offending type and can be acted on: the standard member has a
         * property of its own on [ProblemBuilder], and the wire format has no room for both.
         */
        public fun <T> extensions(
            value: T,
            serializer: SerializationStrategy<T>,
        ) {
            val encoded = json.encodeToJsonElement(serializer, value)
            val obj =
                encoded as? JsonObject
                    ?: throw IllegalArgumentException(
                        "extensions(...) requires a value that serializes to a JSON object, " +
                            "but ${serializer.descriptor.serialName} serialized to ${encoded::class.simpleName}",
                    )
            obj.forEach { (name, element) ->
                require(name !in Problem.RESERVED_MEMBERS) {
                    "Property '$name' of ${serializer.descriptor.serialName} collides with the reserved " +
                        "RFC 9457 member '$name'; rename the property or set the standard member directly"
                }
                put(name, element.toProblemValue())
            }
        }

        private fun put(
            name: String,
            value: ProblemValue,
        ) {
            require(extensions.put(name, value) == null) {
                "Duplicate extension member '$name'; it was already set on this problem"
            }
        }

        @PublishedApi
        internal fun build(): Problem = Problem(type, status, title, detail, instance, extensions.toMap())
    }

public inline fun <reified T> ProblemBuilder.extension(
    name: String,
    value: T,
): Unit = extension(name, value, serializer())

public inline fun <reified T> ProblemBuilder.extensions(value: T): Unit = extensions(value, serializer())

/**
 * Entry point for the builder DSL.
 *
 * [json] encodes every typed payload added inside [block] — pass the application's own instance when
 * its `serializersModule` or naming strategy matters. See [ProblemJson].
 */
public inline fun problem(
    json: Json = ProblemJson,
    block: ProblemBuilder.() -> Unit,
): Problem = ProblemBuilder(json).apply(block).build()
