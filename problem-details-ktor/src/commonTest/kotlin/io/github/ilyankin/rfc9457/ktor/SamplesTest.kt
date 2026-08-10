package io.github.ilyankin.rfc9457.ktor

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.samples.problemDetailsSample
import io.github.ilyankin.rfc9457.samples.respondProblemSample
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json

/*
 * The samples in `samples/` are inlined into the API documentation by `@sample`. Compiling them
 * already rules out referring to an API that no longer exists. Running them here catches wiring
 * that compiles but does not behave the way the surrounding prose claims.
 */
class SamplesTest :
    StringSpec({

        "respondProblemSample answers with a problem document at the problem's own status" {
            testApplication {
                application {
                    install(ContentNegotiation) { problemJson() }
                    respondProblemSample()
                }

                val response = client.get("/orders/42")
                response.status shouldBe HttpStatusCode.NotFound

                val body = Json.decodeFromString<Problem>(response.bodyAsText())
                body.status shouldBe 404
                // Autofilled from the request path, which is the behavior the sample demonstrates.
                body.instance shouldBe "/orders/42"
            }
        }

        "problemDetailsSample turns Ktor's own 404 into a problem document" {
            testApplication {
                application { problemDetailsSample() }

                val response = client.get("/nothing-is-routed-here")
                response.status shouldBe HttpStatusCode.NotFound
                Json.decodeFromString<Problem>(response.bodyAsText()).status shouldBe 404
            }
        }
    })
