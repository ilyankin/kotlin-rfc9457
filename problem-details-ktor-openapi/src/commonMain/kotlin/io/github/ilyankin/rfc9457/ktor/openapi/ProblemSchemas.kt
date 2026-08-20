package io.github.ilyankin.rfc9457.ktor.openapi

import io.ktor.openapi.AdditionalProperties
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.JsonType
import io.ktor.openapi.ReferenceOr

private fun uriReference(description: String): ReferenceOr<JsonSchema> =
    ReferenceOr.Value(JsonSchema(type = JsonType.STRING, format = "uri-reference", description = description))

private fun string(description: String): ReferenceOr<JsonSchema> =
    ReferenceOr.Value(JsonSchema(type = JsonType.STRING, description = description))

private val standardMembers: Map<String, ReferenceOr<JsonSchema>> =
    mapOf(
        "type" to uriReference("A URI reference identifying the problem type. Absent means about:blank."),
        "title" to string("A short, human-readable summary of the problem type."),
        "status" to
            ReferenceOr.Value(
                JsonSchema(
                    type = JsonType.INTEGER,
                    minimum = 100.0,
                    maximum = 599.0,
                    description = "The HTTP status code generated for this occurrence of the problem.",
                ),
            ),
        "detail" to string("A human-readable explanation specific to this occurrence of the problem."),
        "instance" to uriReference("A URI reference identifying this specific occurrence of the problem."),
    )

/**
 * The JSON Schemas this module writes into an OpenAPI document.
 *
 * Both are written by hand rather than inferred. `ProblemSerializer`'s descriptor marks `type`
 * non-optional and cannot describe extension members at all — they are runtime-determined — so an
 * inferred schema would require a member RFC 9457 §3.1 makes optional and would omit the sibling
 * extensions of §3.2 entirely.
 *
 * Each schema's non-null `title` is load-bearing: Ktor lifts a titled schema into
 * `components/schemas` and rewrites every use to a `$ref`. Clearing it inlines the whole schema at
 * every response instead. Through Ktor 3.5.2 the lifting skips `default` responses (KTOR-9657, fixed
 * in 3.6.0), so a catch-all body stays inline whatever its title.
 */
public object ProblemSchemas {
    /**
     * The `application/problem+json` document of RFC 9457 §3.
     *
     * Every standard member is optional and arbitrary extension members are admitted as siblings,
     * which is what `additionalProperties` records.
     */
    public val problem: JsonSchema =
        JsonSchema(
            type = JsonType.OBJECT,
            title = "ProblemDetails",
            description = "An RFC 9457 problem details document.",
            properties = standardMembers,
            additionalProperties = AdditionalProperties.Allowed(true),
        )

    /**
     * [problem] plus the `errors` array of RFC 9457's own multi-error example.
     *
     * Standalone rather than an `allOf` reference to [problem]: Ktor registers a component only for
     * a titled schema it meets by value, so a reference would dangle whenever a document uses this
     * variant alone.
     */
    public val problemWithErrors: JsonSchema =
        JsonSchema(
            type = JsonType.OBJECT,
            title = "ProblemDetailsWithErrors",
            description = "An RFC 9457 problem details document carrying per-field errors.",
            properties =
                standardMembers +
                    (
                        "errors" to
                            ReferenceOr.Value(
                                JsonSchema(
                                    type = JsonType.ARRAY,
                                    description = "One entry per failed member.",
                                    items =
                                        ReferenceOr.Value(
                                            JsonSchema(
                                                type = JsonType.OBJECT,
                                                properties =
                                                    mapOf(
                                                        "detail" to string("What is wrong with this member."),
                                                        "pointer" to
                                                            string("An RFC 6901 JSON Pointer to the member."),
                                                    ),
                                            ),
                                        ),
                                ),
                            )
                    ),
            additionalProperties = AdditionalProperties.Allowed(true),
        )
}
