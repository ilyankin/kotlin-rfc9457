package io.github.ilyankin.rfc9457

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/**
 * Scopes the builder DSL, so that a `problem { }` nested inside another cannot silently set members
 * on the outer builder.
 */
@DslMarker
public annotation class ProblemDsl

/**
 * Builds a [Problem]. Obtain one through [problem], which also supplies the `Json` that encodes the
 * typed payloads handed to [extension] and [extensions]. That defaults to [ProblemJson] unless the
 * caller overrides it.
 */
@ProblemDsl
public class ProblemBuilder
    @PublishedApi
    internal constructor(
        private val json: Json = ProblemJson,
    ) {
        /**
         * The problem type URI (§3.1). Defaults to [Problem.ABOUT_BLANK], which is what an absent
         * `type` member means, so leaving it alone and setting it explicitly produce the same
         * document. The `type(ProblemType)` overload sets it together with a type's title and status.
         */
        public var type: String = Problem.ABOUT_BLANK

        /**
         * The HTTP status code (§3.1). Advisory: the responder must make it agree with the actual
         * response status, and nothing here checks that. `problem-details-ktor` fills it in from the
         * response.
         */
        public var status: Int? = null

        /** Short human-readable summary of the problem type (§3.1). It stays the same across occurrences. */
        public var title: String? = null

        /**
         * A human-readable explanation of this occurrence (§3.1). Its job is helping the client
         * correct the problem, and §3.1 asks consumers to leave it unparsed. Machine-readable data
         * belongs in an extension member.
         */
        public var detail: String? = null

        /**
         * URI reference identifying this specific occurrence (§3.1). Need not be dereferenceable.
         * `problem-details-ktor` fills it from the request path unless it is set here.
         */
        public var instance: String? = null

        private val extensions: MutableMap<String, ProblemValue> = LinkedHashMap()

        /** Applies a [ProblemType]'s type URI, title and status in one call. */
        public fun type(type: ProblemType) {
            this.type = type.typeUri
            this.title = type.title
            this.status = type.status
        }

        /**
         * Adds one extension member (§3.2), written as a sibling of the standard members, not nested
         * under an `extensions` key.
         *
         * Setting the same [name] twice throws. A problem document gets assembled in one place, and
         * a silent overwrite there is far likelier to be a bug than something you meant to do.
         *
         * @throws IllegalArgumentException if [name] was already set on this builder.
         */
        public fun extension(
            name: String,
            value: ProblemValue,
        ): Unit = put(name, value)

        /** Adds a string extension member, written quoted. `"30"` stays a string, not the number 30. */
        public fun extension(
            name: String,
            value: String,
        ): Unit = put(name, ProblemPrimitive(value))

        /** Adds an integer extension member. */
        public fun extension(
            name: String,
            value: Int,
        ): Unit = put(name, ProblemPrimitive(value))

        /** Adds a 64-bit integer extension member. */
        public fun extension(
            name: String,
            value: Long,
        ): Unit = put(name, ProblemPrimitive(value))

        /**
         * Adds a floating-point extension member.
         *
         * @throws IllegalArgumentException if [value] is NaN or infinite. Neither has a JSON
         *   representation (RFC 8259 §6).
         */
        public fun extension(
            name: String,
            value: Double,
        ): Unit = put(name, ProblemPrimitive(value))

        /** Adds a boolean extension member. */
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
            put(name, json.encodeToProblemValue(serializer, value))
        }

        /**
         * Spreads [value]'s own properties into the problem as sibling extension members (§3.2). This
         * is the typed producing edge, and [value] must serialize to a JSON object.
         *
         * A property named like one of the five §3.1 members gets rejected here, at call time, so the
         * error can name the offending type. The standard member already has its own property on
         * [ProblemBuilder], and the wire format has no room for both.
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

/**
 * Adds one extension member holding an arbitrary `@Serializable` value, with the serializer resolved
 * from [T].
 *
 * @sample io.github.ilyankin.rfc9457.samples.buildProblemWithTypedExtension
 */
public inline fun <reified T> ProblemBuilder.extension(
    name: String,
    value: T,
): Unit = extension(name, value, serializer())

/**
 * Spreads [value]'s own properties into the problem as sibling extension members, with the
 * serializer resolved from [T].
 *
 * @throws IllegalArgumentException if [T] does not serialize to a JSON object, or if one of its
 *   properties is named like a standard RFC 9457 member.
 */
public inline fun <reified T> ProblemBuilder.extensions(value: T): Unit = extensions(value, serializer())

/**
 * Entry point for the builder DSL.
 *
 * [json] encodes every typed payload added inside [block]. Pass the application's own instance when
 * its `serializersModule` or naming strategy matters; see [ProblemJson].
 *
 * @sample io.github.ilyankin.rfc9457.samples.buildProblem
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457#section-3.1">RFC 9457 §3.1, Members of a Problem Details Object</a>
 */
public inline fun problem(
    json: Json = ProblemJson,
    block: ProblemBuilder.() -> Unit,
): Problem = ProblemBuilder(json).apply(block).build()
