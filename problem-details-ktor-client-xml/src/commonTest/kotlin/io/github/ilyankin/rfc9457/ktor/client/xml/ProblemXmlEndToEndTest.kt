package io.github.ilyankin.rfc9457.ktor.client.xml

import io.github.ilyankin.rfc9457.ProblemException
import io.github.ilyankin.rfc9457.int
import io.github.ilyankin.rfc9457.ktor.ProblemContentTypes
import io.github.ilyankin.rfc9457.ktor.problemDetails
import io.github.ilyankin.rfc9457.ktor.problemJson
import io.github.ilyankin.rfc9457.ktor.xml.problemXml
import io.github.ilyankin.rfc9457.problem
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.github.ilyankin.rfc9457.ktor.client.problemJson as clientProblemJson

class ProblemXmlEndToEndTest :
    StringSpec({

        "decodes a Problem actually emitted by problem-details-ktor-xml" {
            testApplication {
                install(ContentNegotiation) {
                    problemJson()
                    problemXml() // registered after JSON, as its own KDoc requires
                }
                install(StatusPages) { problemDetails { } }
                routing {
                    get("/orders/{id}") {
                        throw ProblemException(
                            problem {
                                status = 404
                                title = "Order not found."
                                detail = "No order 42 exists."
                                extension("orderId", 42)
                            },
                        )
                    }
                }

                val client =
                    createClient {
                        expectSuccess = true
                        HttpResponseValidator {
                            clientProblemJson()
                            problemXml()
                        }
                    }

                val exception =
                    shouldThrow<ProblemException> {
                        client.get("/orders/42") { accept(ProblemContentTypes.Xml) }
                    }
                exception.problem.status shouldBe 404
                exception.problem.title shouldBe "Order not found."
                exception.problem.detail shouldBe "No order 42 exists."
                exception.problem.extensions["orderId"]?.int shouldBe 42
                // Autofilled from the request path by problem-details-ktor's respondProblem.
                exception.problem.instance shouldBe "/orders/42"
            }
        }
    })
