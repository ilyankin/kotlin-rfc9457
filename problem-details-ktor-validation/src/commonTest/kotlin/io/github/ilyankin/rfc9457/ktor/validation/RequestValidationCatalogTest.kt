package io.github.ilyankin.rfc9457.ktor.validation

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ProblemArray
import io.github.ilyankin.rfc9457.ProblemObject
import io.github.ilyankin.rfc9457.ProblemPrimitive
import io.github.ilyankin.rfc9457.ProblemType
import io.github.ilyankin.rfc9457.ktor.problemDetails
import io.github.ilyankin.rfc9457.ktor.problemJson
import io.github.ilyankin.rfc9457.problemArray
import io.github.ilyankin.rfc9457.problemObject
import io.github.ilyankin.rfc9457.stringOrNull
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respondText
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json

/** The type of the RFC's own validation-errors example (`docs/rfc9457-spec.md`, §3 examples). */
private object ValidationError : ProblemType {
    override val typeUri: String = "https://example.net/validation-error"
    override val title: String = "Your request is not valid."
    override val status: Int = 422
}

private fun ApplicationTestBuilder.installValidation(validate: suspend (String) -> ValidationResult) {
    install(RequestValidation) { validate<String>(validate) }
    install(ContentNegotiation) { problemJson() }
    install(StatusPages) { problemDetails { requestValidation(ValidationError) } }
    routing { post("/x") { call.respondText(call.receive<String>()) } }
}

class RequestValidationCatalogTest :
    StringSpec({

        "a two-field failure reproduces the RFC's own validation-errors example" {
            testApplication {
                installValidation {
                    invalidFields(
                        "#/age" to "must be a positive integer",
                        "#/profile/color" to "must be 'green', 'red' or 'blue'",
                    )
                }

                val response = client.post("/x") { setBody("") }
                response.status shouldBe HttpStatusCode.UnprocessableEntity

                // Two members the RFC's printed example does not show: `status`, which a
                // `ProblemType` always carries, and `instance`, which `respondProblem` fills from
                // the request path. Everything else is the example verbatim.
                Json.decodeFromString<Problem>(response.bodyAsText()) shouldBe
                    Problem(
                        type = "https://example.net/validation-error",
                        status = 422,
                        title = "Your request is not valid.",
                        instance = "/x",
                        extensions =
                            mapOf(
                                "errors" to
                                    ProblemArray(
                                        listOf(
                                            ProblemObject(
                                                mapOf(
                                                    "detail" to ProblemPrimitive("must be a positive integer"),
                                                    "pointer" to ProblemPrimitive("#/age"),
                                                ),
                                            ),
                                            ProblemObject(
                                                mapOf(
                                                    "detail" to ProblemPrimitive("must be 'green', 'red' or 'blue'"),
                                                    "pointer" to ProblemPrimitive("#/profile/color"),
                                                ),
                                            ),
                                        ),
                                    ),
                            ),
                    )
            }
        }

        "a plain ValidationResult.Invalid reason degrades to detail only, no pointer" {
            testApplication {
                installValidation { ValidationResult.Invalid("body must not be empty") }

                val response = client.post("/x") { setBody("") }
                val body = Json.decodeFromString<Problem>(response.bodyAsText())
                val error =
                    body.extensions
                        .getValue("errors")
                        .problemArray
                        .single()
                        .problemObject
                error.getValue("detail").stringOrNull shouldBe "body must not be empty"
                ("pointer" in error) shouldBe false
            }
        }

        "a single invalidField still produces a one-entry errors[] array" {
            testApplication {
                installValidation { invalidField("#/age", "must be a positive integer") }

                val body =
                    Json.decodeFromString<Problem>(client.post("/x") { setBody("") }.bodyAsText())
                body.extensions.getValue("errors").problemArray shouldHaveSize 1
            }
        }

        "a valid body is not touched" {
            testApplication {
                installValidation { ValidationResult.Valid }
                client.post("/x") { setBody("ok") }.status shouldBe HttpStatusCode.OK
            }
        }
    })
