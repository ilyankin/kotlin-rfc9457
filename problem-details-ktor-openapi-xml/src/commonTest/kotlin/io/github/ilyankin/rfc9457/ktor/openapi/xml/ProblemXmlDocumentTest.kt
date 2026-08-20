@file:OptIn(ExperimentalKtorApi::class)

package io.github.ilyankin.rfc9457.ktor.openapi.xml

import io.github.ilyankin.rfc9457.ProblemType
import io.github.ilyankin.rfc9457.ktor.openapi.problemResponse
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.openapi.plus
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private object Negotiated : ProblemType {
    override val typeUri: String = "https://example.com/probs/negotiated"
    override val title: String = "Negotiated failure."
    override val status: Int = 403
}

// Two endpoints on purpose: one documenting both formats, one documenting JSON alone. They compete
// for `components/schemas` entries, which is where a shared title would silently lose the XML object.
private fun Application.documentedApi() {
    routing {
        get("/both") { call.respondText("both") }
            .describe { responses { problemResponse(Negotiated) { problemXmlContent() } } }

        get("/json-only") { call.respondText("json") }
            .describe { responses { problemResponse(Negotiated) } }

        get("/docs.json") {
            val doc =
                OpenApiDoc(info = OpenApiInfo("Test API", "1.0")) +
                    call.application.routingRoot.descendants()
            call.respondText(Json.encodeToString(doc), ContentType.Application.Json)
        }.hide()
    }
}

class ProblemXmlDocumentTest :
    StringSpec({

        "the XML variant hoists into its own component, so the JSON one is not overwritten" {
            testApplication {
                application { documentedApi() }

                val doc = Json.parseToJsonElement(client.get("/docs.json").bodyAsText()).jsonObject
                val schemas = doc["components"]!!.jsonObject["schemas"]!!.jsonObject

                schemas.keys shouldBe setOf("ProblemDetails", "ProblemDetailsXml")
            }
        }

        "the hoisted XML component keeps Appendix B's element name and namespace" {
            testApplication {
                application { documentedApi() }

                val doc = Json.parseToJsonElement(client.get("/docs.json").bodyAsText()).jsonObject
                val xml =
                    doc["components"]!!.jsonObject["schemas"]!!.jsonObject["ProblemDetailsXml"]!!
                        .jsonObject["xml"]!!.jsonObject

                xml["name"]!!.jsonPrimitive.content shouldBe "problem"
                xml["namespace"]!!.jsonPrimitive.content shouldBe "urn:ietf:rfc:7807"
            }
        }

        "the JSON component stays free of XML metadata" {
            testApplication {
                application { documentedApi() }

                val doc = Json.parseToJsonElement(client.get("/docs.json").bodyAsText()).jsonObject

                doc["components"]!!.jsonObject["schemas"]!!.jsonObject["ProblemDetails"]!!
                    .jsonObject["xml"] shouldBe null
            }
        }

        "each media type references its own component" {
            testApplication {
                application { documentedApi() }

                val doc = Json.parseToJsonElement(client.get("/docs.json").bodyAsText()).jsonObject
                val content =
                    doc["paths"]!!.jsonObject["/both"]!!.jsonObject["get"]!!
                        .jsonObject["responses"]!!.jsonObject["403"]!!
                        .jsonObject["content"]!!.jsonObject

                content["application/problem+json"]!!.jsonObject["schema"]!!
                    .jsonObject["\$ref"]!!.jsonPrimitive.content shouldBe
                    "#/components/schemas/ProblemDetails"
                content["application/problem+xml"]!!.jsonObject["schema"]!!
                    .jsonObject["\$ref"]!!.jsonPrimitive.content shouldBe
                    "#/components/schemas/ProblemDetailsXml"
            }
        }
    })
