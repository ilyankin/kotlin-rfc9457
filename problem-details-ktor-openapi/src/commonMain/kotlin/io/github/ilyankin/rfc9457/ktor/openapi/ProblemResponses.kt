package io.github.ilyankin.rfc9457.ktor.openapi

import io.github.ilyankin.rfc9457.ProblemType
import io.github.ilyankin.rfc9457.ktor.ProblemContentTypes
import io.github.ilyankin.rfc9457.ktor.ProblemDetailsCatalog
import io.github.ilyankin.rfc9457.ktor.httpStatus
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.ExampleObject
import io.ktor.openapi.GenericElement
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.Response
import io.ktor.openapi.Responses

/**
 * Appends [addition] to a description that another call may already have written.
 *
 * `Response.Builder.description` is a plain `String` defaulting to `""`, and Ktor accumulates
 * repeated registrations for one status code into a single builder. Assigning would therefore drop
 * the first of two problem types sharing a status.
 */
private fun Response.Builder.appendDescription(addition: String) {
    description =
        when {
            description.isBlank() -> addition
            addition in description -> description
            else -> "$description $addition"
        }
}

/**
 * Writes the problem document as the body of this response.
 *
 * Always through the `application/problem+json` key, never through `Response.Builder.schema`: that
 * property's setter writes to the negotiation defaults, which is how an implementation ends up
 * documenting a problem document as `application/json`.
 */
private fun Response.Builder.problemBody(schema: JsonSchema) {
    ProblemContentTypes.Json { this.schema = schema }
}

/**
 * Documents [type] as a possible outcome of this operation.
 *
 * The status comes from [ProblemType.status] and, unless [description] says otherwise, the
 * description from [ProblemType.title]. Declaring two types that share a status yields one response
 * describing both, with an example per type keyed by its type URI — OpenAPI allows only one response
 * object per code.
 *
 * @param schema the document shape; pass [ProblemSchemas.problemWithErrors] for a response carrying
 *   per-field `errors`.
 * @sample io.github.ilyankin.rfc9457.samples.problemResponseSample
 */
public fun Responses.Builder.problemResponse(
    type: ProblemType,
    description: String? = null,
    schema: JsonSchema = ProblemSchemas.problem,
    configure: Response.Builder.() -> Unit = {},
) {
    type.httpStatus {
        appendDescription(description ?: type.title)
        problemBody(schema)
        ProblemContentTypes.Json {
            example(
                type.typeUri,
                ExampleObject(
                    summary = type.title,
                    value =
                        GenericElement(
                            listOf(
                                "type" to GenericElement(type.typeUri),
                                "title" to GenericElement(type.title),
                                "status" to GenericElement(type.status),
                            ),
                        ),
                ),
            )
        }
        configure()
    }
}

/**
 * Documents [status] as producing a problem document, without naming a problem type.
 *
 * For a status a server emits with a blank document — the codes `standardStatusCodes()` registers,
 * for instance.
 */
public fun Responses.Builder.problemResponse(
    status: HttpStatusCode,
    description: String? = null,
    schema: JsonSchema = ProblemSchemas.problem,
    configure: Response.Builder.() -> Unit = {},
) {
    status {
        appendDescription(description ?: status.description)
        problemBody(schema)
        configure()
    }
}

/**
 * Documents the catch-all `default` response as a problem document.
 *
 * The counterpart of `onUnmapped`: every status not listed explicitly still answers with a problem
 * document, which is exactly what `problemDetails { }` guarantees.
 */
public fun Responses.Builder.problemDefault(
    description: String? = null,
    schema: JsonSchema = ProblemSchemas.problem,
    configure: Response.Builder.() -> Unit = {},
) {
    default {
        appendDescription(description ?: "An unexpected failure, as an RFC 9457 problem document.")
        problemBody(schema)
        configure()
    }
}

/**
 * Documents the part of [catalog] that is true of every operation.
 *
 * That means the catch-all — every unmapped failure still answers with a problem document — and one
 * response per status registered through `forStatusCode`, `standardStatusCodes()` included. Those
 * hold wherever this is applied, so applying it at the routing root documents an entire application
 * honestly.
 *
 * Types declared through `map<T>(type)` are deliberately left out. A catalog is installed once for
 * the whole application and records no route, so attaching them everywhere would document
 * `GET /health` as returning 403 and make client generators emit handling for it. Name them where
 * they occur with [problemResponse], or, if a subtree really can produce all of them, iterate
 * `catalog.problemTypes` yourself.
 *
 * A mapping written as a lambda contributes nothing beyond the catch-all: it produces its document
 * at call time and has no type to read.
 *
 * @sample io.github.ilyankin.rfc9457.samples.problemsFromCatalogSample
 */
public fun Responses.Builder.problemsFrom(
    catalog: ProblemDetailsCatalog,
    configure: Response.Builder.() -> Unit = {},
) {
    problemDefault(configure = configure)
    catalog.statusCodes.forEach { status -> problemResponse(status, configure = configure) }
}
