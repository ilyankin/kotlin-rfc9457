package io.github.ilyankin.rfc9457.ktor.client

import io.github.ilyankin.rfc9457.ProblemException
import io.github.ilyankin.rfc9457.samples.problemJsonSample
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/*
 * The sample in `samples/` is inlined into the API documentation by `@sample`. Compiling it already
 * rules out referring to an API that no longer exists. Running it here catches wiring that compiles
 * but does not behave the way the surrounding prose claims.
 */
class SamplesTest :
    StringSpec({

        "problemJsonSample turns a problem+json error into ProblemException" {
            val client =
                HttpClient(MockEngine) {
                    problemJsonSample()
                    engine {
                        addHandler {
                            respond(
                                """{"type":"about:blank","status":404,"title":"Not Found"}""",
                                HttpStatusCode.NotFound,
                                headersOf(HttpHeaders.ContentType, "application/problem+json"),
                            )
                        }
                    }
                }

            val exception = shouldThrow<ProblemException> { client.get("http://example.test") }
            exception.problem.status shouldBe 404
        }
    })
