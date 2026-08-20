package io.github.ilyankin.rfc9457.ktor.openapi.xml

import io.github.ilyankin.rfc9457.ktor.ProblemContentTypes
import io.github.ilyankin.rfc9457.ktor.openapi.ProblemSchemas
import io.github.ilyankin.rfc9457.xml.ProblemXml
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.Response
import io.ktor.openapi.Xml

/**
 * Adds `application/problem+xml` beside whatever this response already documents.
 *
 * Written inside a `problemResponse`/`problemDefault` block, so one response describes both formats:
 *
 * ```kotlin
 * responses { problemResponse(OutOfCredit) { problemXmlContent() } }
 * ```
 *
 * The XML object records RFC 9457 Appendix B's root element and namespace. That namespace is
 * RFC 7807's, unchanged, because the wire format did not change between the two documents.
 *
 * The schema is retitled with an `Xml` suffix so that it becomes its own component. Ktor keys
 * `components/schemas` by title alone and the last schema registered under a title wins, so sharing
 * one with the JSON variant would let whichever route is walked last decide whether the component
 * carries this XML object at all.
 *
 * @param schema the document shape. Repeat the argument given to the enclosing call when it is not
 *   the default — this function cannot see it.
 * @sample io.github.ilyankin.rfc9457.samples.problemXmlContentSample
 */
public fun Response.Builder.problemXmlContent(schema: JsonSchema = ProblemSchemas.problem) {
    ProblemContentTypes.Xml {
        this.schema =
            schema.copy(
                title = schema.title?.let { "${it}Xml" },
                xml = Xml(name = ProblemXml.ROOT_ELEMENT, namespace = ProblemXml.NAMESPACE),
            )
    }
}
