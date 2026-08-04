package io.github.ilyankin.rfc9457.ktor.client

import io.github.ilyankin.rfc9457.ProblemException
import io.github.ilyankin.rfc9457.int
import io.github.ilyankin.rfc9457.problemArray
import io.github.ilyankin.rfc9457.string
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.SerializationException

/**
 * RFC 9457 §3's first example. Re-declared here rather than shared: test fixtures do not cross module
 * `commonTest` source sets, so each module that wants this one keeps its own copy.
 */
private val OUT_OF_CREDIT_JSON =
    """
    {
      "type": "https://example.com/probs/out-of-credit",
      "title": "You do not have enough credit.",
      "detail": "Your current balance is 30, but that costs 50.",
      "instance": "/account/12345/msgs/abc",
      "balance": 30,
      "accounts": ["/account/12345", "/account/67890"]
    }
    """.trimIndent()

private fun problemClient(
    status: HttpStatusCode,
    contentType: String,
    body: String,
    acceptPlainJson: Boolean = false,
): HttpClient =
    HttpClient(MockEngine) {
        expectSuccess = true
        engine {
            addHandler { respond(body, status, headersOf(HttpHeaders.ContentType, contentType)) }
        }
        HttpResponseValidator {
            problemJson(acceptPlainJson = acceptPlainJson)
        }
    }

class ProblemJsonTest :
    StringSpec({

        "a strict application/problem+json error becomes ProblemException, decoded like RFC 9457's own example" {
            val client = problemClient(HttpStatusCode.Forbidden, "application/problem+json", OUT_OF_CREDIT_JSON)

            val exception = shouldThrow<ProblemException> { client.get("http://example.test") }
            exception.problem.type shouldBe "https://example.com/probs/out-of-credit"
            exception.problem.title shouldBe "You do not have enough credit."
            exception.problem.detail shouldBe "Your current balance is 30, but that costs 50."
            exception.problem.instance shouldBe "/account/12345/msgs/abc"
            // Siblings of the standard members on the wire, not nested under an "extensions" key —
            // the same shape `ProblemSerializer` produces, since this decodes through it.
            exception.problem.extensions["balance"]?.int shouldBe 30
            exception.problem.extensions["accounts"]?.problemArray?.map { it.string } shouldBe
                listOf("/account/12345", "/account/67890")
        }

        "a non-problem error response still throws Ktor's own exception" {
            val client = problemClient(HttpStatusCode.NotFound, "text/plain", "not found")

            shouldThrow<ClientRequestException> { client.get("http://example.test") }
        }

        "a success response is not touched" {
            val client =
                HttpClient(MockEngine) {
                    expectSuccess = true
                    engine {
                        addHandler { respond("ok", HttpStatusCode.OK) }
                    }
                    HttpResponseValidator { problemJson() }
                }

            client.get("http://example.test").status shouldBe HttpStatusCode.OK
        }

        "acceptPlainJson = false leaves a plain application/json error as Ktor's own exception" {
            val client =
                problemClient(
                    HttpStatusCode.NotFound,
                    "application/json",
                    """{"type":"about:blank","status":404}""",
                    acceptPlainJson = false,
                )

            shouldThrow<ClientRequestException> { client.get("http://example.test") }
        }

        "acceptPlainJson = true decodes a plain application/json error into ProblemException" {
            val client =
                problemClient(
                    HttpStatusCode.NotFound,
                    "application/json",
                    """{"type":"about:blank","status":404}""",
                    acceptPlainJson = true,
                )

            val exception = shouldThrow<ProblemException> { client.get("http://example.test") }
            exception.problem.status shouldBe 404
        }

        "acceptPlainJson = true falls back silently when the body isn't a problem document" {
            val client =
                problemClient(
                    HttpStatusCode.NotFound,
                    "application/json",
                    "[1, 2, 3]",
                    acceptPlainJson = true,
                )

            shouldThrow<ClientRequestException> { client.get("http://example.test") }
        }

        "a strict application/problem+json body that fails to decode propagates loudly" {
            val client = problemClient(HttpStatusCode.NotFound, "application/problem+json", "not json at all")

            shouldThrow<SerializationException> { client.get("http://example.test") }
        }

        "a 5xx problem response becomes ProblemException too" {
            val client =
                problemClient(
                    HttpStatusCode.ServiceUnavailable,
                    "application/problem+json",
                    """{"type":"about:blank","status":503,"title":"Service Unavailable"}""",
                )

            val exception = shouldThrow<ProblemException> { client.get("http://example.test") }
            exception.problem.status shouldBe 503
        }

        "a 3xx problem response becomes ProblemException too" {
            val client =
                HttpClient(MockEngine) {
                    expectSuccess = true
                    followRedirects = false
                    engine {
                        addHandler {
                            respond(
                                """{"type":"about:blank","status":308,"title":"Permanent Redirect"}""",
                                HttpStatusCode.PermanentRedirect,
                                headersOf(HttpHeaders.ContentType, "application/problem+json"),
                            )
                        }
                    }
                    HttpResponseValidator { problemJson() }
                }

            val exception = shouldThrow<ProblemException> { client.get("http://example.test") }
            exception.problem.status shouldBe 308
        }
    })
