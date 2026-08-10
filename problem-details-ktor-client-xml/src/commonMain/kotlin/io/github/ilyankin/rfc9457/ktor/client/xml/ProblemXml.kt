package io.github.ilyankin.rfc9457.ktor.client.xml

import io.github.ilyankin.rfc9457.ProblemException
import io.github.ilyankin.rfc9457.ktor.client.problemJson
import io.github.ilyankin.rfc9457.xml.ProblemXml
import io.ktor.client.plugins.HttpCallValidatorConfig
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerializationException

/**
 * Turns a recognized `application/problem+xml` response into the same [ProblemException] that
 * application code throws on the server side and `problem-details-ktor` answers. This is the XML
 * twin of [problemJson].
 *
 * Both may be registered, in either order: each returns untouched anything the other matches, so
 * neither can shadow the other. That is unlike the server's `ContentNegotiation` pair, where
 * registration order decides which format an absent or wildcard `Accept` resolves to.
 *
 * The `expectSuccess` requirement, the treatment of redirects, and the reverse order Ktor runs
 * exception handlers in are all as described on [problemJson]. Only the two rules below are specific
 * to XML.
 *
 * **A body labeled `application/problem+xml` that fails to decode propagates as
 * [SerializationException]**, not falling back to Ktor's own exception. The label is an unambiguous
 * claim about the body's shape, and swallowing a broken one would hide a server bug. There is no
 * lenient counterpart to [problemJson]'s `acceptPlainJson`; plain `application/xml` never matches, so
 * unlike the JSON side there is only this one rule. XML has no equivalent of the convention that
 * makes tooling label everything `application/json`, and an application's own XML dialect is not
 * this library's to claim.
 *
 * The body is read with the response's declared charset, falling back to UTF-8. A document's own
 * `encoding` pseudo-attribute is not consulted, since by then the bytes are already text. RFC 7303
 * makes the transport's charset authoritative for `+xml` media types, so this is the order the
 * specification asks for.
 *
 * @sample io.github.ilyankin.rfc9457.samples.problemXmlSample
 */
public fun HttpCallValidatorConfig.problemXml() {
    handleResponseExceptionWithRequest { cause, _ ->
        if (cause !is ResponseException) return@handleResponseExceptionWithRequest
        if (cause.response.contentType()?.withoutParameters() != ContentType.Application.ProblemXml) {
            return@handleResponseExceptionWithRequest
        }

        throw ProblemException(ProblemXml.decodeFromString(cause.response.bodyAsText()))
    }
}
