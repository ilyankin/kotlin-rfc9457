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
 * Thin by design: the encoding lives in `ProblemSerializer` in core, so this only delegates. That is
 * what makes registering it a convenience rather than a correctness requirement — an application's
 * pre-existing `json()` converter that wins the `application/json` match produces the same bytes,
 * because [Problem] carries `ProblemSerializer` as its own serializer.
 *
 * [json] governs formatting only (indentation and the like); it cannot change the document's shape,
 * since the serializer attached to [Problem] decides that.
 */
public class ProblemJsonConverter(
    private val json: Json = Json,
) : ContentConverter {
    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?,
    ): OutgoingContent? {
        // Returning null is the interface's documented way of saying "not mine"; ContentNegotiation
        // then tries the next registered converter instead of failing the response.
        if (value !is Problem) return null
        // Deliberately NOT the `contentType` this converter was matched under. When registered for
        // plain `application/json` (see acceptPlainJson) that parameter is `application/json`, and
        // labeling a problem document as ordinary JSON is exactly what makes it undetectable —
        // the media type is the only thing distinguishing it on the wire. RFC 9457 §3 sanctions
        // this: a server MAY respond with `application/problem+json` even if the client's `Accept`
        // header didn't explicitly list it (HTTP §12.5.1), so clients must be prepared for it.
        return TextContent(
            json.encodeToString(Problem.serializer(), value),
            ProblemContentTypes.Json.withCharset(charset),
        )
    }

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
 * **Call this before `problemXml()` if you use both.** Registration order is what breaks the tie
 * when `Accept` is absent or a wildcard, and JSON should win it — RFC 9457 frames JSON as the
 * canonical serialization and the Appendix B XML form as an equivalent alternative. Everything else
 * about `Accept` matching, quality values included, stays `ContentNegotiation`'s job; this function
 * only writes the two `register` calls a developer would otherwise write by hand.
 *
 * [acceptPlainJson] additionally serves problem documents to clients asking for plain
 * `application/json`. Strictly a client should ask for `application/problem+json`, but plenty of
 * tooling only ever sends `application/json`, and §3 lets a server use the problem format without
 * being asked. Note what this does *not* mean: the response is still labelled
 * `application/problem+json`, because that label is the only thing that marks the body as a problem
 * document. The flag widens which requests get one, never how one is tagged. Turn it off to answer
 * such clients with 406 instead.
 *
 * [json] governs formatting only; the document's shape comes from `ProblemSerializer`, which
 * [Problem] carries as its own serializer.
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
