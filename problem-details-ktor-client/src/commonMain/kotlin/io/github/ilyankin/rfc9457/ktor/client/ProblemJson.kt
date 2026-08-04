package io.github.ilyankin.rfc9457.ktor.client

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ProblemException
import io.ktor.client.plugins.HttpCallValidatorConfig
import io.ktor.client.plugins.ResponseException
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Turns a recognized `application/problem+json` response into the same [ProblemException] that
 * application code throws on the server side and `problem-details-ktor` answers.
 *
 * JSON only, by artifact: decoding the Appendix B XML form is planned as its own module, exactly as
 * `problemXml()` is on the server, so a client that never speaks XML never resolves an XML parser.
 *
 * Ktor's own default response validation always runs first and throws `ClientRequestException`/
 * `ServerResponseException` for a non-2xx status before this handler gets a chance to run — so this
 * cannot replace that exception by validating the response early; it has to intercept the exception
 * Ktor already threw and decide whether to replace it. `expectSuccess` must be `true` for that
 * exception to exist in the first place; `HttpClientConfig.expectSuccess` defaults to `false`, and
 * a client left at that default can still opt in per request: `client.get(url) { expectSuccess = true }`.
 *
 * `application/problem+json` and a plain `application/json` (only matched when [acceptPlainJson] is
 * `true`) are trusted differently on a decode failure: a `problem+json`-labeled body that fails to
 * parse is a broken promise and propagates as [SerializationException]; a plain-JSON-labeled body
 * was never a promise about shape, so a decode failure there falls back silently to Ktor's own
 * exception, unchanged.
 *
 * Every status Ktor raises a `ResponseException` for qualifies, redirects included — a 3xx answered
 * with a problem document is replaced just like a 4xx or 5xx.
 *
 * Ktor runs exception handlers in reverse registration order, so a handler the application registers
 * *after* this one runs *before* it and may keep this one from ever seeing the exception.
 *
 * @param acceptPlainJson also matches responses whose `Content-Type` is plain `application/json`.
 *   It reads that response header and says nothing about the `Accept` header — this module never
 *   touches outgoing requests. Defaults to `false`, unlike `problem-details-ktor`'s own
 *   `acceptPlainJson = true`, because it governs bodies from servers this project does not control
 *   rather than this project's own well-formed output.
 * @param json governs parsing only; the document's shape comes from the serializer attached to
 *   [Problem].
 * @sample io.github.ilyankin.rfc9457.samples.problemJsonSample
 */
public fun HttpCallValidatorConfig.problemJson(
    acceptPlainJson: Boolean = false,
    json: Json = Json,
) {
    handleResponseExceptionWithRequest { cause, _ ->
        if (cause !is ResponseException) return@handleResponseExceptionWithRequest

        val contentType = cause.response.contentType()?.withoutParameters()
        val strictMatch = contentType == ContentType.Application.ProblemJson
        val lenientMatch = acceptPlainJson && contentType == ContentType.Application.Json
        if (!strictMatch && !lenientMatch) return@handleResponseExceptionWithRequest

        val text = cause.response.bodyAsText()
        val problem =
            try {
                json.decodeFromString(Problem.serializer(), text)
            } catch (e: SerializationException) {
                if (strictMatch) throw e
                return@handleResponseExceptionWithRequest
            }
        throw ProblemException(problem)
    }
}
