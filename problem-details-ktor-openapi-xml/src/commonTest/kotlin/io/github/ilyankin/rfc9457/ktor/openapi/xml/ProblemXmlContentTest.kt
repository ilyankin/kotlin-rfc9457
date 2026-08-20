package io.github.ilyankin.rfc9457.ktor.openapi.xml

import io.github.ilyankin.rfc9457.ProblemType
import io.github.ilyankin.rfc9457.ktor.ProblemContentTypes
import io.github.ilyankin.rfc9457.ktor.openapi.ProblemSchemas
import io.github.ilyankin.rfc9457.ktor.openapi.problemResponse
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.openapi.Operation

private object OutOfCredit : ProblemType {
    override val typeUri: String = "https://example.com/probs/out-of-credit"
    override val title: String = "You do not have enough credit."
    override val status: Int = 403
}

class ProblemXmlContentTest :
    StringSpec({

        "both media types are documented for the one response" {
            val operation =
                Operation.build { responses { problemResponse(OutOfCredit) { problemXmlContent() } } }

            operation.responses.shouldNotBeNull().responses.shouldNotBeNull()
                .getValue(403).valueOrNull().shouldNotBeNull().content.shouldNotBeNull()
                .keys shouldBe setOf(ProblemContentTypes.Json, ProblemContentTypes.Xml)
        }

        "the XML schema carries Appendix B's element name and namespace" {
            val operation =
                Operation.build { responses { problemResponse(OutOfCredit) { problemXmlContent() } } }

            val xml =
                operation.responses.shouldNotBeNull().responses.shouldNotBeNull()
                    .getValue(403).valueOrNull().shouldNotBeNull()
                    .content.shouldNotBeNull().getValue(ProblemContentTypes.Xml)
                    .schema.shouldNotBeNull().valueOrNull().shouldNotBeNull()
                    .xml.shouldNotBeNull()

            xml.name shouldBe "problem"
            xml.namespace shouldBe "urn:ietf:rfc:7807"
        }

        "the errors variant can be documented in XML too, under its own component title" {
            val operation =
                Operation.build {
                    responses {
                        problemResponse(OutOfCredit, schema = ProblemSchemas.problemWithErrors) {
                            problemXmlContent(ProblemSchemas.problemWithErrors)
                        }
                    }
                }

            operation.responses.shouldNotBeNull().responses.shouldNotBeNull()
                .getValue(403).valueOrNull().shouldNotBeNull()
                .content.shouldNotBeNull().getValue(ProblemContentTypes.Xml)
                .schema.shouldNotBeNull().valueOrNull().shouldNotBeNull()
                .title shouldBe "ProblemDetailsWithErrorsXml"
        }
    })
