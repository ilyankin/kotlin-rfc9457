package io.github.ilyankin.rfc9457.ktor

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
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication

/**
 * Short on purpose. Its real job is to prove the module split: this spec compiles and passes with no
 * XML codec anywhere on the classpath, which is exactly what a JSON-only application gets.
 */
class JsonOnlyNegotiationTest :
    StringSpec({

        "problemJson alone serves problem+json" {
            testApplication {
                install(ContentNegotiation) { problemJson() }
                install(StatusPages) { problemDetails { } }
                routing { get("/boom") { throw IllegalStateException("x") } }
                val response =
                    client.get("/boom") {
                        header(HttpHeaders.Accept, "application/problem+json")
                    }
                ContentType.parse(response.headers[HttpHeaders.ContentType]!!).withoutParameters() shouldBe
                    ContentType.parse("application/problem+json")
                response.bodyAsText() shouldContain "\"status\":500"
            }
        }

        "plain application/json is accepted by default" {
            testApplication {
                install(ContentNegotiation) { problemJson() }
                install(StatusPages) { problemDetails { } }
                routing { get("/boom") { throw IllegalStateException("x") } }
                client
                    .get("/boom") { header(HttpHeaders.Accept, "application/json") }
                    .bodyAsText() shouldContain "\"status\":500"
            }
        }

        "a client asking for plain application/json is still answered with problem+json" {
            testApplication {
                install(ContentNegotiation) { problemJson() }
                install(StatusPages) { problemDetails { } }
                routing { get("/boom") { throw IllegalStateException("x") } }
                val response =
                    client.get("/boom") {
                        header(HttpHeaders.Accept, "application/json")
                    }
                // acceptPlainJson widens *which* requests get a problem document, never how one is
                // labelled. Answering `application/json` would strip the only wire-level marker that
                // says "this is a problem document"; RFC 9457 §3 explicitly permits replying with
                // problem+json to a client that did not ask for it (HTTP §12.5.1).
                ContentType.parse(response.headers[HttpHeaders.ContentType]!!).withoutParameters() shouldBe
                    ContentType.parse("application/problem+json")
            }
        }

        "with no Accept header the JSON registration still wins" {
            testApplication {
                install(ContentNegotiation) { problemJson() }
                install(StatusPages) { problemDetails { } }
                routing { get("/boom") { throw IllegalStateException("x") } }
                val response = client.get("/boom")
                ContentType.parse(response.headers[HttpHeaders.ContentType]!!).withoutParameters() shouldBe
                    ContentType.parse("application/problem+json")
            }
        }

        "with acceptPlainJson = false, a plain application/json client gets 406 and no body" {
            testApplication {
                install(ContentNegotiation) { problemJson(acceptPlainJson = false) }
                install(StatusPages) { problemDetails { } }
                routing { get("/boom") { throw IllegalStateException("x") } }
                val response = client.get("/boom") { header(HttpHeaders.Accept, "application/json") }

                response.headers[HttpHeaders.ContentType].orEmpty().contains("json") shouldBe false
                // What happens instead is Ktor's own behaviour, not this library's, and is pinned here
                // because it is what a user turning the flag off actually sees: no converter matches the
                // Accept header, so ContentNegotiation answers 406 with an empty body — the diagnosis of
                // the failed problem response is lost along with it. That trade is the reason
                // acceptPlainJson defaults to true.
                response.status shouldBe HttpStatusCode.NotAcceptable
                response.bodyAsText() shouldBe ""
            }
        }
    })
