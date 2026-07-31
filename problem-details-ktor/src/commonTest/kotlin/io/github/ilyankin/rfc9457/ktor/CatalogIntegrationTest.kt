package io.github.ilyankin.rfc9457.ktor

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ProblemType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

private object OutOfCredit : ProblemType {
    override val typeUri: String = "https://example.com/probs/out-of-credit"
    override val title: String = "You do not have enough credit."
    override val status: Int = 403
}

private open class DomainException(
    message: String,
) : RuntimeException(message)

private class SpecificException(
    message: String,
) : DomainException(message)

private fun ApplicationTestBuilder.installProblemFormats() {
    install(ContentNegotiation) {
        register(ContentType.parse("application/problem+json"), ProblemJsonConverter())
    }
}

class CatalogIntegrationTest :
    StringSpec({

        "a mapped exception becomes its problem type" {
            testApplication {
                installProblemFormats()
                install(StatusPages) {
                    problemDetails { map<DomainException>(OutOfCredit) }
                }
                routing { get("/pay") { throw DomainException("balance too low") } }

                val response = client.get("/pay")
                response.status shouldBe HttpStatusCode.Forbidden
                val body = Json.decodeFromString<Problem>(response.bodyAsText())
                body.type shouldBe OutOfCredit.typeUri
                body.title shouldBe OutOfCredit.title
                body.detail shouldBe "balance too low"
            }
        }

        "nearest-parent-class resolution still applies" {
            testApplication {
                installProblemFormats()
                install(StatusPages) {
                    problemDetails { map<DomainException>(OutOfCredit) }
                }
                routing { get("/pay") { throw SpecificException("subclass") } }

                Json.decodeFromString<Problem>(client.get("/pay").bodyAsText()).type shouldBe
                    OutOfCredit.typeUri
            }
        }

        "an unmapped exception never leaks the exception message" {
            testApplication {
                installProblemFormats()
                install(StatusPages) { problemDetails { } }
                routing { get("/boom") { throw IllegalStateException("jdbc://secret@host/db") } }

                val response = client.get("/boom")
                response.status shouldBe HttpStatusCode.InternalServerError
                val text = response.bodyAsText()
                text shouldNotContain "secret"
                Json.decodeFromString<Problem>(text).detail shouldBe null
            }
        }

        "Ktor's own NotFoundException still yields 404, not 500" {
            testApplication {
                installProblemFormats()
                install(StatusPages) { problemDetails { } }
                routing { get("/x") { throw NotFoundException("nope") } }

                val response = client.get("/x")
                response.status shouldBe HttpStatusCode.NotFound
                Json.decodeFromString<Problem>(response.bodyAsText()).status shouldBe 404
            }
        }

        "Ktor's own BadRequestException still yields 400" {
            testApplication {
                installProblemFormats()
                install(StatusPages) { problemDetails { } }
                routing { get("/x") { throw BadRequestException("nope") } }
                client.get("/x").status shouldBe HttpStatusCode.BadRequest
            }
        }

        "Ktor's own UnsupportedMediaTypeException still yields 415" {
            testApplication {
                installProblemFormats()
                install(StatusPages) { problemDetails { } }
                routing { get("/x") { throw UnsupportedMediaTypeException(ContentType.Text.Plain) } }
                client.get("/x").status shouldBe HttpStatusCode.UnsupportedMediaType
            }
        }

        "a map<Throwable> entry replaces the built-in catch-all rather than being swallowed by it" {
            testApplication {
                installProblemFormats()
                install(StatusPages) {
                    problemDetails {
                        map<Throwable> { _, _ -> Problem(status = 599, title = "user catch-all") }
                    }
                }
                routing { get("/boom") { throw IllegalStateException("x") } }

                // StatusPagesConfig.exceptions is a map keyed by class, so registering Throwable twice
                // is last-one-wins rather than nearest-parent-class. The library registers its own
                // catch-all first precisely so this entry is the survivor.
                val response = client.get("/boom")
                response.status.value shouldBe 599
                Json.decodeFromString<Problem>(response.bodyAsText()).title shouldBe "user catch-all"
            }
        }

        "cancellation is handed back to the engine instead of being answered" {
            testApplication {
                installProblemFormats()
                install(StatusPages) { problemDetails { } }
                routing { get("/gone") { throw CancellationException("client went away") } }

                // Ktor cancels the call's coroutine when the client disconnects. Turning that into a
                // 500 would write a document to a socket nobody is reading and log a stack trace per
                // dropped connection, so the catch-all rethrows instead.
                // What comes back is then the engine's business, not this library's — under
                // `testApplication` that is Ktor's development-mode error page. In production a real
                // disconnect means nothing is written at all. Either way the assertion is the same and
                // is the one thing this library controls: no problem document was produced.
                val response = client.get("/gone")
                response.headers[HttpHeaders.ContentType].orEmpty() shouldNotContain "problem+json"
                response.bodyAsText() shouldNotContain "about:blank"
            }
        }

        "a timeout is a cancellation that still becomes a status" {
            testApplication {
                installProblemFormats()
                install(StatusPages) { problemDetails { } }
                routing { get("/slow") { withTimeout(1.milliseconds) { delay(1_000.milliseconds) } } }

                // TimeoutCancellationException is a CancellationException, but defaultExceptionStatusCode
                // maps it to 504 — which is what separates it from a disconnect in the guard.
                client.get("/slow").status shouldBe HttpStatusCode.GatewayTimeout
            }
        }

        "onUnmapped can be overridden" {
            testApplication {
                installProblemFormats()
                install(StatusPages) {
                    problemDetails {
                        onUnmapped { _, _ -> Problem(status = 503, title = "Service Unavailable") }
                    }
                }
                routing { get("/x") { throw IllegalStateException("x") } }
                client.get("/x").status shouldBe HttpStatusCode.ServiceUnavailable
            }
        }

        "a registered status code gets a problem body" {
            testApplication {
                installProblemFormats()
                install(StatusPages) {
                    problemDetails { standardStatusCodes() }
                }
                routing { }
                val response = client.get("/nothing-here")
                response.status shouldBe HttpStatusCode.NotFound
                Json.decodeFromString<Problem>(response.bodyAsText()).status shouldBe 404
            }
        }

        "an unregistered status code keeps the application's own body" {
            testApplication {
                installProblemFormats()
                install(StatusPages) {
                    problemDetails {
                        forStatusCode(HttpStatusCode.NotFound) {
                            Problem.blank(HttpStatusCode.NotFound)
                        }
                    }
                }
                routing {
                    get("/teapot") { call.respondText("my own body", status = HttpStatusCode.Conflict) }
                }
                val response = client.get("/teapot")
                response.status shouldBe HttpStatusCode.Conflict
                response.bodyAsText() shouldBe "my own body"
            }
        }
    })
