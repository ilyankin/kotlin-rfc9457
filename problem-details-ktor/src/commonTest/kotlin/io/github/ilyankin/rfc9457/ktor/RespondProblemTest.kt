package io.github.ilyankin.rfc9457.ktor

import io.github.ilyankin.rfc9457.Problem
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiationConfig
import io.ktor.server.routing.get
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json

class RespondProblemTest :
    StringSpec({

        "the HTTP status comes from the problem, and instance is auto-filled" {
            testApplication {
                install(ContentNegotiation) { problemDetailsFormatsForTest() }
                routing {
                    get("/pay") { call.respondProblem(Problem(status = 403, title = "Forbidden")) }
                }
                val response = client.get("/pay")
                response.status shouldBe HttpStatusCode.Forbidden
                val body = Json.decodeFromString<Problem>(response.bodyAsText())
                body.status shouldBe 403
                body.instance shouldBe "/pay"
            }
        }

        "the auto-filled instance omits the query string" {
            testApplication {
                install(ContentNegotiation) { problemDetailsFormatsForTest() }
                routing { get("/pay") { call.respondProblem(Problem(status = 403)) } }
                // A problem document is read by the client and logged on both ends; §5 warns against
                // putting anything in one the recipient should not have, and query strings routinely
                // carry tokens, so instance is the path, never the full request target.
                val body =
                    Json.decodeFromString<Problem>(
                        client.get("/pay?api_key=s3cret&debug=true").bodyAsText(),
                    )
                body.instance shouldBe "/pay"
            }
        }

        "an explicit instance is not overwritten" {
            testApplication {
                install(ContentNegotiation) { problemDetailsFormatsForTest() }
                routing {
                    get("/pay") {
                        call.respondProblem(Problem(status = 403, instance = "/account/1/msgs/abc"))
                    }
                }
                Json.decodeFromString<Problem>(client.get("/pay").bodyAsText()).instance shouldBe
                    "/account/1/msgs/abc"
            }
        }

        "a null status falls back to 500 and is written back into the body" {
            testApplication {
                install(ContentNegotiation) { problemDetailsFormatsForTest() }
                routing { get("/boom") { call.respondProblem(Problem(title = "no status")) } }
                val response = client.get("/boom")
                response.status shouldBe HttpStatusCode.InternalServerError
                Json.decodeFromString<Problem>(response.bodyAsText()).status shouldBe 500
            }
        }

        "the two-argument form lets the response status win over the body's" {
            testApplication {
                install(ContentNegotiation) { problemDetailsFormatsForTest() }
                routing {
                    get("/x") {
                        call.respondProblem(HttpStatusCode.NotFound, Problem(status = 418, title = "wrong"))
                    }
                }
                val response = client.get("/x")
                response.status shouldBe HttpStatusCode.NotFound
                Json.decodeFromString<Problem>(response.bodyAsText()).status shouldBe 404
            }
        }

        "blank(HttpStatusCode) uses the reason phrase as the title" {
            Problem.blank(HttpStatusCode.NotFound) shouldBe Problem.blank(404, "Not Found")
        }

        "the content type is application/problem+json" {
            testApplication {
                install(ContentNegotiation) { problemDetailsFormatsForTest() }
                routing { get("/x") { call.respondProblem(Problem(status = 400)) } }
                val response = client.get("/x")
                ContentType.parse(response.headers["Content-Type"]!!).withoutParameters() shouldBe
                    ContentType.parse("application/problem+json")
            }
        }
    })

/**
 * Stands in for `problemJson()`, which Task 12 adds. Registering the converter by hand keeps this
 * spec independent of the registration helper it does not test.
 */
private fun ContentNegotiationConfig.problemDetailsFormatsForTest() {
    register(ContentType.parse("application/problem+json"), ProblemJsonConverter())
}
