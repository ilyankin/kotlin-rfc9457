package io.github.ilyankin.rfc9457.ktor.client.xml

import io.github.ilyankin.rfc9457.ProblemException
import io.github.ilyankin.rfc9457.int
import io.github.ilyankin.rfc9457.ktor.client.problemJson
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
 * RFC 9457 Appendix B's example, indentation aside. Re-declared here rather than shared: test
 * fixtures do not cross module `commonTest` source sets, so each module that wants this one keeps its
 * own copy. Unlike `problem-details-xml`'s `XmlFixtures`, which drops the inter-element whitespace to
 * match what the writer emits, this one keeps it — the read path has to cope with a hand-formatted
 * document from a server it does not control.
 */
private val OUT_OF_CREDIT_XML =
    """
    <?xml version="1.0" encoding="UTF-8"?>
    <problem xmlns="urn:ietf:rfc:7807">
        <type>https://example.com/probs/out-of-credit</type>
        <title>You do not have enough credit.</title>
        <detail>Your current balance is 30, but that costs 50.</detail>
        <instance>https://example.net/account/12345/msgs/abc</instance>
        <balance>30</balance>
        <accounts>
            <i>https://example.net/account/12345</i>
            <i>https://example.net/account/67890</i>
        </accounts>
    </problem>
    """.trimIndent()

private fun problemClient(
    status: HttpStatusCode,
    contentType: String,
    body: String,
): HttpClient =
    HttpClient(MockEngine) {
        expectSuccess = true
        engine {
            addHandler { respond(body, status, headersOf(HttpHeaders.ContentType, contentType)) }
        }
        HttpResponseValidator { problemXml() }
    }

class ProblemXmlTest :
    StringSpec({

        "an application/problem+xml error becomes ProblemException, decoded like Appendix B's own example" {
            val client = problemClient(HttpStatusCode.Forbidden, "application/problem+xml", OUT_OF_CREDIT_XML)

            val exception = shouldThrow<ProblemException> { client.get("http://example.test") }
            exception.problem.type shouldBe "https://example.com/probs/out-of-credit"
            exception.problem.title shouldBe "You do not have enough credit."
            exception.problem.detail shouldBe "Your current balance is 30, but that costs 50."
            exception.problem.instance shouldBe "https://example.net/account/12345/msgs/abc"
            // Siblings of the standard members on the wire, not nested under an "extensions" element.
            exception.problem.extensions["balance"]?.int shouldBe 30
            exception.problem.extensions["accounts"]
                ?.problemArray
                ?.map { it.string } shouldBe
                listOf("https://example.net/account/12345", "https://example.net/account/67890")
        }

        "a charset parameter on the content type does not stop the match" {
            val client =
                problemClient(
                    HttpStatusCode.NotFound,
                    "application/problem+xml; charset=utf-8",
                    OUT_OF_CREDIT_XML,
                )

            shouldThrow<ProblemException> { client.get("http://example.test") }
        }

        "a non-problem error response still throws Ktor's own exception" {
            val client = problemClient(HttpStatusCode.NotFound, "text/plain", "not found")

            shouldThrow<ClientRequestException> { client.get("http://example.test") }
        }

        "a plain application/xml error is left alone — there is no lenient mode" {
            val client = problemClient(HttpStatusCode.NotFound, "application/xml", OUT_OF_CREDIT_XML)

            shouldThrow<ClientRequestException> { client.get("http://example.test") }
        }

        "a success response is not touched" {
            val client =
                HttpClient(MockEngine) {
                    expectSuccess = true
                    engine {
                        addHandler { respond("ok", HttpStatusCode.OK) }
                    }
                    HttpResponseValidator { problemXml() }
                }

            client.get("http://example.test").status shouldBe HttpStatusCode.OK
        }

        "a body that is not XML at all propagates loudly" {
            val client = problemClient(HttpStatusCode.NotFound, "application/problem+xml", "not xml at all")

            shouldThrow<SerializationException> { client.get("http://example.test") }
        }

        "a well-formed document that is not a problem document propagates loudly" {
            val client =
                problemClient(
                    HttpStatusCode.NotFound,
                    "application/problem+xml",
                    """<?xml version="1.0" encoding="UTF-8"?><error xmlns="https://example.com/ns"/>""",
                )

            shouldThrow<SerializationException> { client.get("http://example.test") }
        }

        "a 5xx problem response becomes ProblemException too" {
            val client =
                problemClient(
                    HttpStatusCode.ServiceUnavailable,
                    "application/problem+xml",
                    """<problem xmlns="urn:ietf:rfc:7807"><status>503</status></problem>""",
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
                                """<problem xmlns="urn:ietf:rfc:7807"><status>308</status></problem>""",
                                HttpStatusCode.PermanentRedirect,
                                headersOf(HttpHeaders.ContentType, "application/problem+xml"),
                            )
                        }
                    }
                    HttpResponseValidator { problemXml() }
                }

            val exception = shouldThrow<ProblemException> { client.get("http://example.test") }
            exception.problem.status shouldBe 308
        }
    })

/**
 * The server's `problemJson()`/`problemXml()` pair is order-sensitive — registration order breaks the
 * `Accept` tie. This pair is not, because each handler returns untouched anything the other matches.
 * Pinned in both directions so that a future change which makes one of them greedier fails here.
 */
class RegistrationOrderTest :
    StringSpec({

        val xmlBody = """<problem xmlns="urn:ietf:rfc:7807"><status>404</status></problem>"""
        val jsonBody = """{"type":"about:blank","status":404}"""

        fun client(
            contentType: String,
            body: String,
            xmlFirst: Boolean,
        ): HttpClient =
            HttpClient(MockEngine) {
                expectSuccess = true
                engine {
                    addHandler {
                        respond(body, HttpStatusCode.NotFound, headersOf(HttpHeaders.ContentType, contentType))
                    }
                }
                HttpResponseValidator {
                    if (xmlFirst) {
                        problemXml()
                        problemJson()
                    } else {
                        problemJson()
                        problemXml()
                    }
                }
            }

        listOf(true, false).forEach { xmlFirst ->
            val order = if (xmlFirst) "XML registered first" else "JSON registered first"

            "$order: an XML problem response still decodes" {
                val exception =
                    shouldThrow<ProblemException> {
                        client("application/problem+xml", xmlBody, xmlFirst).get("http://example.test")
                    }
                exception.problem.status shouldBe 404
            }

            "$order: a JSON problem response still decodes" {
                val exception =
                    shouldThrow<ProblemException> {
                        client("application/problem+json", jsonBody, xmlFirst).get("http://example.test")
                    }
                exception.problem.status shouldBe 404
            }
        }
    })
