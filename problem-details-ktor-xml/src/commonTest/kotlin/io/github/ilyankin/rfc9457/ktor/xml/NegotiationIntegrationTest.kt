package io.github.ilyankin.rfc9457.ktor.xml

import io.github.ilyankin.rfc9457.ktor.problemDetails
import io.github.ilyankin.rfc9457.ktor.problemJson
import io.github.ilyankin.rfc9457.xml.ProblemXml
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication

private fun ApplicationTestBuilder.problemApp(acceptPlainJson: Boolean = true) {
    install(ContentNegotiation) {
        // Order matters and is asserted below: JSON first, so an absent or wildcard Accept resolves
        // to JSON. The split moved this guarantee from library-internal to the call site, which is
        // why both orderings appear in this spec.
        problemJson(acceptPlainJson)
        problemXml()
    }
    install(StatusPages) { problemDetails { } }
    routing { get("/boom") { throw IllegalStateException("x") } }
}

/**
 * The only module that can see both codecs, so the only place the two can be negotiated against each
 * other. Every case asserts the response's **content type**, not just its body: the 2026-07-31 review
 * found that a converter echoing back the type it was matched under passed every body-only assertion.
 */
class NegotiationIntegrationTest :
    StringSpec({

        "problem+json is served when explicitly requested" {
            testApplication {
                problemApp()
                val response =
                    client.get("/boom") {
                        header(HttpHeaders.Accept, "application/problem+json")
                    }
                response.status shouldBe HttpStatusCode.InternalServerError
                ContentType.parse(response.headers[HttpHeaders.ContentType]!!).withoutParameters() shouldBe
                    ContentType.parse("application/problem+json")
                response.bodyAsText() shouldContain "\"status\":500"
            }
        }

        "problem+xml is served when explicitly requested" {
            testApplication {
                problemApp()
                val response =
                    client.get("/boom") {
                        header(HttpHeaders.Accept, "application/problem+xml")
                    }
                ContentType.parse(response.headers[HttpHeaders.ContentType]!!).withoutParameters() shouldBe
                    ContentType.parse("application/problem+xml")
                val body = response.bodyAsText()
                body shouldContain "<problem xmlns=\"urn:ietf:rfc:7807\">"
                // Parsed, not string-matched, so the served bytes are proven to be a document this
                // library can read back, not merely one that looks right.
                ProblemXml.decodeFromString(body).status shouldBe 500
            }
        }

        "plain application/json is accepted by default" {
            testApplication {
                problemApp()
                val response = client.get("/boom") { header(HttpHeaders.Accept, "application/json") }
                response.bodyAsText() shouldContain "\"status\":500"
                // Still labelled problem+json, never the type it was matched under.
                ContentType.parse(response.headers[HttpHeaders.ContentType]!!).withoutParameters() shouldBe
                    ContentType.parse("application/problem+json")
            }
        }

        "with acceptPlainJson = false, plain application/json gets no problem+json body" {
            testApplication {
                problemApp(acceptPlainJson = false)
                val response = client.get("/boom") { header(HttpHeaders.Accept, "application/json") }
                response.headers[HttpHeaders.ContentType].orEmpty().contains("problem+json") shouldBe false
            }
        }

        "a wildcard Accept resolves to JSON, because JSON is registered first" {
            testApplication {
                problemApp()
                val response = client.get("/boom") { header(HttpHeaders.Accept, "*/*") }
                ContentType.parse(response.headers[HttpHeaders.ContentType]!!).withoutParameters() shouldBe
                    ContentType.parse("application/problem+json")
            }
        }

        "an absent Accept resolves to JSON" {
            testApplication {
                problemApp()
                val response = client.get("/boom")
                response.bodyAsText() shouldContain "\"status\":500"
                ContentType.parse(response.headers[HttpHeaders.ContentType]!!).withoutParameters() shouldBe
                    ContentType.parse("application/problem+json")
            }
        }

        "quality values are honoured, since ContentNegotiation does the matching" {
            testApplication {
                problemApp()
                val response =
                    client.get("/boom") {
                        header(
                            HttpHeaders.Accept,
                            "application/problem+json;q=0.3, application/problem+xml;q=0.9",
                        )
                    }
                ContentType.parse(response.headers[HttpHeaders.ContentType]!!).withoutParameters() shouldBe
                    ContentType.parse("application/problem+xml")
            }
        }

        "the converters ignore values that are not Problems" {
            testApplication {
                install(ContentNegotiation) {
                    problemJson()
                    problemXml()
                }
                routing { get("/plain") { call.respondText("not a problem") } }
                client.get("/plain").bodyAsText() shouldBe "not a problem"
            }
        }

        // The single-function shape guaranteed JSON-first internally; with the split, the guarantee
        // moved to the call site. This makes that a documented consequence, not a trap.
        "registering XML first reverses the wildcard tie" {
            testApplication {
                install(ContentNegotiation) {
                    problemXml()
                    problemJson()
                }
                install(StatusPages) { problemDetails { } }
                routing { get("/boom") { throw IllegalStateException("x") } }
                val response = client.get("/boom") { header(HttpHeaders.Accept, "*/*") }
                ContentType.parse(response.headers[HttpHeaders.ContentType]!!).withoutParameters() shouldBe
                    ContentType.parse("application/problem+xml")
            }
        }
    })
