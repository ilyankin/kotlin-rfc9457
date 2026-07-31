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
 * Emits the canonical serialization of `application/problem+json`: §3.2 extension members written as
 * siblings of the §3.1 standard ones. Reading follows §3's tolerance rules.
 *
 * Being the type's *own* serializer is a correctness requirement, not a convenience. An application
 * that already registered a plain `json()` converter would otherwise win the match for
 * `application/json` and silently emit `{"extensions": {...}}` — nested, and invalid per the RFC.
 * Attached to the type, that path produces identical bytes to this one.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457#section-3">RFC 9457 §3, The Problem Details JSON Object</a>
 */
public object ProblemSerializer : KSerializer<Problem> {
    /**
     * Describes the five standard members only. Extension members are runtime-determined and cannot
     * appear in a static descriptor, which is why both directions go through kotlinx's JSON-specific
     * encoder and decoder rather than a format-agnostic one.
     */
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("io.github.ilyankin.rfc9457.Problem") {
            element<String>("type")
            element<Int>("status", isOptional = true)
            element<String>("title", isOptional = true)
            element<String>("detail", isOptional = true)
            element<String>("instance", isOptional = true)
        }

    /**
     * Writes the problem as a flat JSON object: the standard members that are set, then every
     * extension member as a sibling.
     *
     * @throws kotlinx.serialization.SerializationException if [encoder] is not a JSON encoder — for
     *   `application/problem+xml` use `ProblemXml` from the `problem-details-xml` module.
     */
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

    /**
     * Reads a flat problem document: recognised members become properties, everything else becomes
     * an extension member.
     *
     * Lenient in the two ways RFC 9457 §3 requires. A standard member of the wrong JSON type is
     * dropped as if absent rather than failing the document, and an unrecognised member is kept in
     * `extensions` rather than rejected.
     *
     * @throws kotlinx.serialization.SerializationException if [decoder] is not a JSON decoder, or if
     *   the document is well-formed JSON but not an object — §3 defines a problem detail as a JSON
     *   object, so an array or a bare string is not one.
     */
    override fun deserialize(decoder: Decoder): Problem {
        val jsonDecoder =
            decoder as? JsonDecoder ?: throw SerializationException(
                "Problem can only be deserialized from JSON. For application/problem+xml use " +
                    "ProblemXml from the problem-details-xml module.",
            )
        // §3 defines a problem detail as a JSON object, so a well-formed document of any other
        // shape is not a problem document at all and §3's leniency does not reach it. Thrown as a
        // SerializationException, not the IllegalArgumentException JsonElement.jsonObject would leak.
        val obj =
            jsonDecoder.decodeJsonElement() as? JsonObject
                ?: throw SerializationException("Expected a JSON object: an RFC 9457 problem document is one (§3)")
        // Keeping unrecognized members rather than dropping them satisfies §3.2's "consumers MUST
        // ignore" and goes one better: a proxy or a logger can re-emit the document intact.
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
     * §3, stated for the whole document: *"if a member is present but its value doesn't match the
     * type specified, the implementation MUST ignore that member (as if it were absent) rather than
     * reject the whole document."* So `"status": "403"` — a string where §3.1 requires a number —
     * yields `status = null` and a document that still parses.
     *
     * Both helpers return `null` for anything that is not the expected JSON type, an explicit `null`
     * included.
     */
    private fun JsonObject.text(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.number(name: String): String? =
        (this[name] as? JsonPrimitive)?.takeIf { !it.isString && it.content != "null" }?.content
}
