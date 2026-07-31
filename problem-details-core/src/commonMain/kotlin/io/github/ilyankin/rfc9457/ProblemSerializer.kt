package io.github.ilyankin.rfc9457

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The JSON codec for [Problem], and [Problem]'s own serializer.
 *
 * Emits the canonical serialization of RFC 9457 — the JSON object of `application/problem+json`,
 * with §3.2 extension members written as siblings of the §3.1 standard ones. Reading follows the
 * RFC's tolerance rules; the two places that implement them are annotated below.
 *
 * Being the type's own serializer is a correctness requirement, not a convenience: an application
 * that already registered a plain `json()` converter would otherwise win the match for
 * `application/json` and emit `{"extensions": {...}}` — nested, invalid per the RFC, and silently.
 * With this serializer attached to the type, that path produces identical bytes to ours.
 *
 * Flattening arbitrary runtime-determined members requires the JSON-specific encoder, exactly as
 * kotlinx.serialization's own documentation prescribes; there is no format-agnostic equivalent.
 * XML is handled by `problem-details-xml`, which does not go through kotlinx at all.
 */
public object ProblemSerializer : KSerializer<Problem> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("io.github.ilyankin.rfc9457.Problem") {
            element<String>("type")
            element<Int>("status", isOptional = true)
            element<String>("title", isOptional = true)
            element<String>("detail", isOptional = true)
            element<String>("instance", isOptional = true)
        }

    override fun serialize(
        encoder: Encoder,
        value: Problem,
    ) {
        val jsonEncoder =
            encoder as? JsonEncoder ?: throw SerializationException(
                "Problem can only be serialized by JSON. For application/problem+xml use " +
                    "ProblemXml from the problem-details-xml module.",
            )
        jsonEncoder.encodeJsonElement(
            buildJsonObject {
                put("type", value.type)
                value.status?.let { put("status", it) }
                value.title?.let { put("title", it) }
                value.detail?.let { put("detail", it) }
                value.instance?.let { put("instance", it) }
                value.extensions.forEach { (name, extension) -> put(name, extension.toJsonElement()) }
            },
        )
    }

    override fun deserialize(decoder: Decoder): Problem {
        val jsonDecoder =
            decoder as? JsonDecoder ?: throw SerializationException(
                "Problem can only be deserialized from JSON. For application/problem+xml use " +
                    "ProblemXml from the problem-details-xml module.",
            )
        val obj = jsonDecoder.decodeJsonElement().jsonObject
        // Everything that is not one of the five §3.1 members is a §3.2 extension member. The RFC
        // requires a consumer to *ignore* extension members it does not recognize; keeping them in
        // the model satisfies that (nothing fails) and goes one better, since a proxy or a logger
        // can re-emit the document without silently dropping members it knows nothing about.
        return Problem(
            type = obj.text("type") ?: Problem.ABOUT_BLANK,
            status = obj.number("status")?.toIntOrNull(),
            title = obj.text("title"),
            detail = obj.text("detail"),
            instance = obj.text("instance"),
            extensions =
                obj
                    .filterKeys { it !in Problem.RESERVED_MEMBERS }
                    .mapValues { (_, element) -> element.toProblemValue() },
        )
    }

    /**
     * RFC 9457 §3 states the rule for the whole document: *"if a member is present but its value
     * doesn't match the type specified, the implementation MUST ignore that member (as if it were
     * absent) rather than reject the whole document."* So `"status": "403"` — a string where §3.1
     * requires a number — yields `status = null` and a document that still parses, and the same
     * goes for a `title` that arrives as an object.
     *
     * Both helpers return `null` for anything that is not the expected JSON type, which includes an
     * explicit `null`.
     */
    private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { !it.isString && it.content != "null" }?.content
}
