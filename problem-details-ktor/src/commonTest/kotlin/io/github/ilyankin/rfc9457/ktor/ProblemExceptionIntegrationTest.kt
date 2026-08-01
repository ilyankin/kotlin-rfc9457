package io.github.ilyankin.rfc9457.ktor

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ProblemException
import io.github.ilyankin.rfc9457.ProblemType
import io.github.ilyankin.rfc9457.exception
import io.github.ilyankin.rfc9457.problem
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json

private object ThrownOutOfCredit : ProblemType {
    override val typeUri: String = "https://example.com/probs/out-of-credit"
    override val title: String = "You do not have enough credit."
    override val status: Int = 403
}

private fun ApplicationTestBuilder.installProblemJsonOnly() {
    install(ContentNegotiation) {
        register(ContentType.parse("application/problem+json"), ProblemJsonConverter())
    }
}

class ProblemExceptionIntegrationTest :
    StringSpec({

        "a thrown ProblemException answers with the document it carries" {
            testApplication {
                installProblemJsonOnly()
                install(StatusPages) { problemDetails { } }
                routing {
                    get("/pay") {
                        throw ThrownOutOfCredit.exception(
                            detail = "Your current balance is 30, but that costs 50.",
                        )
                    }
                }

                val response = client.get("/pay")
                response.status shouldBe HttpStatusCode.Forbidden

                val body = Json.decodeFromString<Problem>(response.bodyAsText())
                body.type shouldBe "https://example.com/probs/out-of-credit"
                body.title shouldBe "You do not have enough credit."
                body.status shouldBe 403
                body.detail shouldBe "Your current balance is 30, but that costs 50."
            }
        }

        "instance is filled from the request path when the thrower left it unset" {
            testApplication {
                installProblemJsonOnly()
                install(StatusPages) { problemDetails { } }
                routing { get("/accounts/12345/pay") { throw ThrownOutOfCredit.exception() } }

                val body = Json.decodeFromString<Problem>(client.get("/accounts/12345/pay").bodyAsText())
                body.instance shouldBe "/accounts/12345/pay"
            }
        }

        "an instance set by the thrower survives" {
            testApplication {
                installProblemJsonOnly()
                install(StatusPages) { problemDetails { } }
                routing {
                    get("/accounts/12345/pay") {
                        throw ThrownOutOfCredit.exception(instance = "urn:account:12345")
                    }
                }

                val body = Json.decodeFromString<Problem>(client.get("/accounts/12345/pay").bodyAsText())
                body.instance shouldBe "urn:account:12345"
            }
        }

        "a user's map<ProblemException> replaces the built-in entry" {
            testApplication {
                installProblemJsonOnly()
                install(StatusPages) {
                    problemDetails {
                        map<ProblemException> { _, _ -> Problem.blank(HttpStatusCode.Conflict) }
                    }
                }
                routing { get("/pay") { throw ThrownOutOfCredit.exception() } }

                client.get("/pay").status shouldBe HttpStatusCode.Conflict
            }
        }

        "ProblemException wins over a user's map<Throwable>" {
            testApplication {
                installProblemJsonOnly()
                install(StatusPages) {
                    problemDetails {
                        map<Throwable> { _, _ -> Problem.blank(HttpStatusCode.Conflict) }
                    }
                }
                routing { get("/pay") { throw ThrownOutOfCredit.exception() } }

                // Nearest-parent resolution: distance 0 to ProblemException beats the Throwable entry.
                client.get("/pay").status shouldBe HttpStatusCode.Forbidden
            }
        }

        "a ProblemException carrying 404 is not also rewritten by the status hook" {
            testApplication {
                installProblemJsonOnly()
                install(StatusPages) { problemDetails { standardStatusCodes() } }
                routing {
                    get("/orders/42") {
                        throw ProblemException(
                            problem {
                                status = 404
                                title = "Order not found."
                                detail = "No order 42 exists."
                            },
                        )
                    }
                }

                val response = client.get("/orders/42")
                response.status shouldBe HttpStatusCode.NotFound

                // Ktor sets `statusPageMarker` before invoking an exception handler, so
                // `standardStatusCodes()` does not get to replace this body with a bare about:blank.
                val body = Json.decodeFromString<Problem>(response.bodyAsText())
                body.title shouldBe "Order not found."
                body.detail shouldBe "No order 42 exists."
            }
        }

        "a problem carrying no status responds 500 and says 500 in the document" {
            testApplication {
                installProblemJsonOnly()
                install(StatusPages) { problemDetails { } }
                routing {
                    get("/pay") { throw ProblemException(problem { title = "Something went wrong." }) }
                }

                val response = client.get("/pay")
                response.status shouldBe HttpStatusCode.InternalServerError
                Json.decodeFromString<Problem>(response.bodyAsText()).status shouldBe 500
            }
        }
    })
