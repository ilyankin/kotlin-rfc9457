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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private fun Application.documentedApi() {
    routing {
        problemResponses(problemCatalog { standardStatusCodes() })

        route("/orders") {
            get("/{id}") { call.respondText("an order") }
        }

        get("/docs.json") {
            val doc =
                OpenApiDoc(info = OpenApiInfo("Test API", "1.0")) +
                    call.application.routingRoot.descendants()
            call.respondText(Json.encodeToString(doc), ContentType.Application.Json)
        }.hide()
    }
}

class ProblemRoutesTest :
    StringSpec({

        "a describe at the routing root reaches a nested endpoint" {
            testApplication {
                application { documentedApi() }

                val doc = Json.parseToJsonElement(client.get("/docs.json").bodyAsText()).jsonObject
                val responses =
                    doc["paths"]!!
                        .jsonObject["/orders/{id}"]!!
                        .jsonObject["get"]!!
                        .jsonObject["responses"]!!
                        .jsonObject

                responses.keys shouldBe setOf("default", "404", "405", "406", "415")
            }
        }

        "the schema is hoisted into components and referenced" {
            testApplication {
                application { documentedApi() }

                val doc = Json.parseToJsonElement(client.get("/docs.json").bodyAsText()).jsonObject

                doc["components"]!!.jsonObject["schemas"]!!.jsonObject["ProblemDetails"] shouldNotBe null

                val schema =
                    doc["paths"]!!
                        .jsonObject["/orders/{id}"]!!
                        .jsonObject["get"]!!
                        .jsonObject["responses"]!!
                        .jsonObject["404"]!!
                        .jsonObject["content"]!!
                        .jsonObject["application/problem+json"]!!
                        .jsonObject["schema"]!!
                        .jsonObject
                schema["\$ref"]!!.jsonPrimitive.content shouldBe "#/components/schemas/ProblemDetails"
            }
        }

        "the content type is application/problem+json, not application/json" {
            testApplication {
                application { documentedApi() }

                val doc = Json.parseToJsonElement(client.get("/docs.json").bodyAsText()).jsonObject
                val content =
                    doc["paths"]!!
                        .jsonObject["/orders/{id}"]!!
                        .jsonObject["get"]!!
                        .jsonObject["responses"]!!
                        .jsonObject["404"]!!
                        .jsonObject["content"]!!
                        .jsonObject

                content.keys shouldBe setOf("application/problem+json")
            }
        }

        "a leaf describe merges with the root one rather than replacing it" {
            testApplication {
                application {
                    routing {
                        problemResponses(problemCatalog { standardStatusCodes() })

                        get("/orders") { call.respondText("orders") }
                            .describe { responses { problemResponse(HttpStatusCode.Conflict) } }

                        get("/docs.json") {
                            val doc =
                                OpenApiDoc(info = OpenApiInfo("Test API", "1.0")) +
                                    call.application.routingRoot.descendants()
                            call.respondText(Json.encodeToString(doc), ContentType.Application.Json)
                        }.hide()
                    }
                }

                val doc = Json.parseToJsonElement(client.get("/docs.json").bodyAsText()).jsonObject
                val responses =
                    doc["paths"]!!
                        .jsonObject["/orders"]!!
                        .jsonObject["get"]!!
                        .jsonObject["responses"]!!
                        .jsonObject

                responses.keys shouldBe setOf("default", "404", "405", "406", "415", "409")
            }
        }
    })
