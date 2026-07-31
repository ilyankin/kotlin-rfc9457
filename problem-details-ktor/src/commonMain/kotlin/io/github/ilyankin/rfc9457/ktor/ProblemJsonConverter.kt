package io.github.ilyankin.rfc9457.ktor

import io.github.ilyankin.rfc9457.Problem
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.serialization.ContentConverter
import io.ktor.server.plugins.contentnegotiation.ContentNegotiationConfig
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.readRemaining
import kotlinx.io.readString
import kotlinx.serialization.json.Json

/**
 * Reads and writes `application/problem+json` bodies for `ContentNegotiation`.
 *
 * Thin by design — the encoding lives in `ProblemSerializer` in core, this only delegates.
 * Registering it is a convenience, not a correctness requirement: an application's pre-existing
 * `json()` converter produces the same bytes, since [Problem] carries `ProblemSerializer` as its
 * own serializer.
 *
 * [json] governs formatting only (indentation and the like); the document's shape comes from the
 * serializer attached to [Problem].
 */
public class ProblemJsonConverter(
    private val json: Json = Json,
) : ContentConverter {
    /**
     * Writes [value] as `application/problem+json`, or `null` if it isn't a [Problem] — the
     * interface's way of saying "not mine", so `ContentNegotiation` tries the next converter.
     *
     * The response is always labelled `application/problem+json`, regardless of the [contentType]
     * this converter was matched under — echoing back `application/json` (see `acceptPlainJson`)
     * would strip the only wire-level marker that the body is a problem document. RFC 9457 §3
     * permits the override.
     */
    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?,
    ): OutgoingContent? {
        if (value !is Problem) return null
        return TextContent(
            json.encodeToString(Problem.serializer(), value),
            ProblemContentTypes.Json.withCharset(charset),
        )
    }

    /**
     * Reads a [Problem] from the request body, or returns `null` if the receiving type is anything
     * else, leaving the body to the next converter.
     *
     * @throws kotlinx.serialization.SerializationException if the body is not a valid problem
     *   document. `ContentNegotiation` turns that into a 400, which the catalog then answers with a
     *   problem document of its own.
     */
    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel,
    ): Any? {
        if (typeInfo.type != Problem::class) return null
        return json.decodeFromString(Problem.serializer(), content.readRemaining().readString())
    }
}

/**
 * Registers the `application/problem+json` converter on Ktor's own `ContentNegotiation`.
 *
 * **Call this before `problemXml()` if you use both** — registration order breaks the tie when
 * `Accept` is absent or a wildcard, and JSON should win it: RFC 9457 treats JSON as the canonical
 * serialization and the Appendix B XML form as an equivalent alternative. Everything about `Accept`
 * matching, quality values included, stays `ContentNegotiation`'s job; this only writes the two
 * `register` calls a developer would otherwise write by hand.
 *
 * [acceptPlainJson] additionally serves problem documents to clients asking for plain
 * `application/json` — strictly a client should ask for `application/problem+json`, but plenty of
 * tooling only ever sends `application/json`, and §3 lets a server use the problem format unasked.
 * The response is still labelled `application/problem+json` either way, since that's the only thing
 * marking the body as a problem document; the flag only widens which requests get one. Turn it off
 * to answer such clients with 406 instead.
 *
 * [json] governs formatting only; the document's shape comes from the serializer attached to
 * [Problem].
 */
public fun ContentNegotiationConfig.problemJson(
    acceptPlainJson: Boolean = true,
    json: Json = Json,
) {
    val converter = ProblemJsonConverter(json)
    register(ProblemContentTypes.Json, converter)
    if (acceptPlainJson) {
        register(ContentType.Application.Json, converter)
    }
}
