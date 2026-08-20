@file:OptIn(ExperimentalKtorApi::class)

package io.github.ilyankin.rfc9457.ktor.openapi.xml

import io.github.ilyankin.rfc9457.ProblemType
import io.github.ilyankin.rfc9457.ktor.openapi.problemResponse
import io.github.ilyankin.rfc9457.ktor.openapi.problemResponses
import io.github.ilyankin.rfc9457.ktor.problemCatalog
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.openapi.plus
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private object Negotiated : ProblemType {
    override val typeUri: String = "https://example.com/probs/negotiated"
    override val title: String = "Negotiated failure."
    override val status: Int = 403
}

/** Serves the document these specs read. Hidden, so it never appears in what it describes. */
private fun Route.openApiDocument() {
    get("/docs.json") {
        val doc =
            OpenApiDoc(info = OpenApiInfo("Test API", "1.0")) +
                call.application.routingRoot.descendants()
        call.respondText(Json.encodeToString(doc), ContentType.Application.Json)
    }.hide()
}

// Two endpoints on purpose: one documenting both formats, one documenting JSON alone. They compete
// for `components/schemas` entries, which is where a shared title would silently lose the XML object.
private fun Application.documentedApi() {
    routing {
        get("/both") { call.respondText("both") }
            .describe { responses { problemResponse(Negotiated) { problemXmlContent() } } }

        get("/json-only") { call.respondText("json") }
            .describe { responses { problemResponse(Negotiated) } }

        openApiDocument()
    }
}

// The whole-application shape: one call at the root, every status it derives answering both formats.
private fun Application.negotiatingApi() {
    routing {
        problemResponses(problemCatalog { standardStatusCodes() }) { problemXmlContent() }

        get("/orders") { call.respondText("orders") }

        openApiDocument()
    }
}

private fun withDocument(api: Application.() -> Unit, assert: (JsonObject) -> Unit) =
    testApplication {
        application { api() }
        assert(Json.parseToJsonElement(client.get("/docs.json").bodyAsText()).jsonObject)
    }

private fun JsonObject.at(vararg path: String): JsonObject =
    path.fold(this) { node, key -> node.getValue(key).jsonObject }

class ProblemXmlDocumentTest :
    StringSpec({

        "the XML variant hoists into its own component, so the JSON one is not overwritten" {
            withDocument(Application::documentedApi) { doc ->
                doc.at("components", "schemas").keys shouldBe
                    setOf("ProblemDetails", "ProblemDetailsXml")
            }
        }

        "the hoisted XML component keeps Appendix B's element name and namespace" {
            withDocument(Application::documentedApi) { doc ->
                val xml = doc.at("components", "schemas", "ProblemDetailsXml", "xml")

                xml.getValue("name").jsonPrimitive.content shouldBe "problem"
                xml.getValue("namespace").jsonPrimitive.content shouldBe "urn:ietf:rfc:7807"
            }
        }

        "the JSON component stays free of XML metadata" {
            withDocument(Application::documentedApi) { doc ->
                doc.at("components", "schemas", "ProblemDetails")["xml"] shouldBe null
            }
        }

        "each media type references its own component" {
            withDocument(Application::documentedApi) { doc ->
                val content = doc.at("paths", "/both", "get", "responses", "403", "content")

                content
                    .at("application/problem+json", "schema")
                    .getValue("\$ref")
                    .jsonPrimitive
                    .content shouldBe "#/components/schemas/ProblemDetails"
                content
                    .at("application/problem+xml", "schema")
                    .getValue("\$ref")
                    .jsonPrimitive
                    .content shouldBe "#/components/schemas/ProblemDetailsXml"
            }
        }

        "configure carries the XML body onto every status a catalog derives" {
            withDocument(Application::negotiatingApi) { doc ->
                val responses = doc.at("paths", "/orders", "get", "responses")

                responses.keys shouldBe setOf("default", "404", "405", "406", "415")
                setOf("404", "405", "406", "415").forEach { status ->
                    responses.at(status, "content").keys shouldBe
                        setOf("application/problem+json", "application/problem+xml")
                }
            }
        }
    })
