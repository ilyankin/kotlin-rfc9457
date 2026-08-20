@file:OptIn(ExperimentalKtorApi::class)

package io.github.ilyankin.rfc9457.ktor.openapi

import io.github.ilyankin.rfc9457.ktor.problemCatalog
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.openapi.plus
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ExperimentalKtorApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Serves the document these specs read. Hidden, so it never appears in what it describes. */
private fun Route.openApiDocument() {
    get("/docs.json") {
        val doc =
            OpenApiDoc(info = OpenApiInfo("Test API", "1.0")) +
                call.application.routingRoot.descendants()
        call.respondText(Json.encodeToString(doc), ContentType.Application.Json)
    }.hide()
}

private fun Application.rootApi() {
    routing {
        problemResponses(problemCatalog { standardStatusCodes() })

        route("/orders") {
            get("/{id}") { call.respondText("an order") }
        }

        openApiDocument()
    }
}

private fun Application.leafDescribingApi() {
    routing {
        problemResponses(problemCatalog { standardStatusCodes() })

        get("/orders") { call.respondText("orders") }
            .describe { responses { problemResponse(HttpStatusCode.Conflict) } }

        openApiDocument()
    }
}

private fun Application.subtreeApi() {
    routing {
        route("/api") {
            problemResponses(problemCatalog { standardStatusCodes() })
            get("/orders") { call.respondText("orders") }
        }

        get("/health") { call.respondText("ok") }

        openApiDocument()
    }
}

private fun withDocument(
    api: Application.() -> Unit,
    assert: (JsonObject) -> Unit,
) = testApplication {
    application { api() }
    assert(Json.parseToJsonElement(client.get("/docs.json").bodyAsText()).jsonObject)
}

private fun JsonObject.at(vararg path: String): JsonObject =
    path.fold(this) { node, key ->
        node.getValue(key).jsonObject
    }

class ProblemRoutesTest :
    StringSpec({

        "a describe at the routing root reaches a nested endpoint" {
            withDocument(Application::rootApi) { doc ->
                doc
                    .at("paths", "/orders/{id}", "get", "responses")
                    .keys shouldBe setOf("default", "404", "405", "406", "415")
            }
        }

        "the body is a reference to one hoisted schema, keyed application/problem+json" {
            withDocument(Application::rootApi) { doc ->
                doc.at("components", "schemas")["ProblemDetails"] shouldNotBe null

                val content = doc.at("paths", "/orders/{id}", "get", "responses", "404", "content")
                content.keys shouldBe setOf("application/problem+json")
                content
                    .at("application/problem+json", "schema")[$$"$ref"]
                    ?.jsonPrimitive
                    ?.content shouldBe "#/components/schemas/ProblemDetails"
            }
        }

        "a leaf describe merges with the root one rather than replacing it" {
            withDocument(Application::leafDescribingApi) { doc ->
                doc
                    .at("paths", "/orders", "get", "responses")
                    .keys shouldBe setOf("default", "404", "405", "406", "415", "409")
            }
        }

        // The other half of the claim: a subtree call documents its subtree, and a sibling route
        // outside it stays undocumented rather than inheriting responses it cannot produce.
        "a subtree describe covers that subtree and nothing beside it" {
            withDocument(Application::subtreeApi) { doc ->
                doc
                    .at("paths", "/api/orders", "get", "responses")
                    .keys shouldBe setOf("default", "404", "405", "406", "415")

                doc.at("paths", "/health", "get")["responses"] shouldBe null
            }
        }
    })
