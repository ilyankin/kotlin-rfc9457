package io.github.ilyankin.rfc9457.ktor.xml

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ktor.ProblemContentTypes
import io.github.ilyankin.rfc9457.xml.ProblemXml
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.withCharset
import io.ktor.serialization.ContentConverter
import io.ktor.server.plugins.contentnegotiation.ContentNegotiationConfig
import io.ktor.util.reflect.TypeInfo
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.readRemaining
import kotlinx.io.readString

/**
 * Reads and writes `application/problem+xml` bodies for `ContentNegotiation`.
 *
 * The same shape as `ProblemJsonConverter`, with `ProblemXml` in place of `Json`, and thin for the
 * same reason: the encoding lives in `problem-details-xml`, so this only delegates.
 *
 * It lives in this module rather than beside its JSON twin so that `problem-details-ktor` never
 * depends on `problem-details-xml`. That edge is the whole point of the split — an application
 * without the XML dependency gets a compile error at the [problemXml] call site rather than a
 * runtime `NoClassDefFoundError`.
 *
 * Unlike the JSON side there is no `Json` parameter to accept: `ProblemXml` deliberately exposes no
 * configuration, because pinning the parser implementation (which is what keeps external entities
 * unresolved) and accepting a caller's parser are mutually exclusive.
 */
public class ProblemXmlConverter : ContentConverter {
    /**
     * Writes [value] as `application/problem+xml`, or returns `null` if it is not a [Problem] — the
     * interface's way of saying "not mine", which lets `ContentNegotiation` try the next converter.
     *
     * As on the JSON side, the [contentType] this converter was matched under is ignored and the
     * response is labelled `application/problem+xml`: the media type is the only wire-level marker
     * identifying a problem document. Today the two always coincide, since there is no
     * `acceptPlainXml` flag; writing rather than echoing is pinned by a test so that adding one
     * later cannot silently reintroduce the bug the JSON converter shipped with.
     *
     * **The response is always UTF-8**, whatever [charset] was negotiated. An XML document states its
     * own encoding in-band and `ProblemXml` writes that declaration as a literal `encoding="UTF-8"`,
     * so honouring a negotiated `ISO-8859-1` would put latin-1 bytes inside a document claiming to be
     * UTF-8 — wrong for anything reading the bytes without the HTTP header, which is any parser
     * handed a saved file or a queued message.
     */
    override suspend fun serialize(
        contentType: ContentType,
        charset: Charset,
        typeInfo: TypeInfo,
        value: Any?,
    ): OutgoingContent? {
        if (value !is Problem) return null
        return TextContent(
            ProblemXml.encodeToString(value),
            ProblemContentTypes.Xml.withCharset(Charsets.UTF_8),
        )
    }

    /**
     * Reads a [Problem] from the request body, or returns `null` if the receiving type is anything
     * else, leaving the body to the next converter.
     *
     * **The body is decoded as UTF-8**, whatever [charset] the request declared, and a document's own
     * `encoding` pseudo-attribute is likewise not honoured — by the time the codec runs the bytes are
     * already text. Stated rather than left implicit: it matches `ProblemJsonConverter`, so the two
     * formats never disagree about the same request.
     *
     * @throws kotlinx.serialization.SerializationException if the body is not a valid problem
     *   document. xmlutil's own `XmlException` is wrapped rather than propagated, so catching a
     *   parse failure never requires xmlutil on the caller's classpath.
     */
    override suspend fun deserialize(
        charset: Charset,
        typeInfo: TypeInfo,
        content: ByteReadChannel,
    ): Any? {
        if (typeInfo.type != Problem::class) return null
        return ProblemXml.decodeFromString(content.readRemaining().readString())
    }
}

/**
 * Registers the `application/problem+xml` converter on Ktor's own `ContentNegotiation`.
 *
 * **Call this after `problemJson()`.** Registration order decides which format an absent or wildcard
 * `Accept` resolves to, and JSON is meant to win that tie; registering XML first silently reverses
 * it, which `NegotiationIntegrationTest` pins in both directions. Nothing else in a typical
 * application registers `application/problem+xml`, so unlike the JSON side there is no converter race
 * to worry about here.
 *
 * There is no `acceptPlainXml` counterpart to `problemJson`'s `acceptPlainJson`: a client sending
 * `Accept: application/xml` is asking for that application's own XML dialect, and answering every
 * such request with a problem document would take over a media type this library does not own. The
 * JSON flag exists because tooling overwhelmingly sends `application/json` and nothing else; no
 * equivalent convention exists on the XML side.
 */
public fun ContentNegotiationConfig.problemXml() {
    register(ProblemContentTypes.Xml, ProblemXmlConverter())
}
