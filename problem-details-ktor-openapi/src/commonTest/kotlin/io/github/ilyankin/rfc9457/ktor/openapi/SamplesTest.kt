package io.github.ilyankin.rfc9457.ktor.openapi

import io.github.ilyankin.rfc9457.ktor.ProblemContentTypes
import io.github.ilyankin.rfc9457.samples.problemResponseSample
import io.github.ilyankin.rfc9457.samples.problemRoutesSample
import io.github.ilyankin.rfc9457.samples.problemsFromCatalogSample
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.server.application.Application
import io.ktor.server.routing.openapi.mapToPathItems
import io.ktor.server.routing.routingRoot
import io.ktor.server.testing.testApplication

class SamplesTest :
    StringSpec({

        "problemResponseSample documents the type's status as a problem body" {
            testApplication {
                lateinit var app: Application
                application {
                    app = this
                    problemResponseSample()
                }
                startApplication()

                val pathItems = app.routingRoot.descendants().mapToPathItems()

                val response =
                    pathItems
                        .getValue("/orders/{id}")
                        .get
                        .shouldNotBeNull()
                        .responses
                        .shouldNotBeNull()
                        .responses
                        .shouldNotBeNull()
                        .getValue(403)
                        .valueOrNull()
                        .shouldNotBeNull()

                response.description shouldBe "You do not have enough credit."
                response.content.shouldNotBeNull().keys shouldBe setOf(ProblemContentTypes.Json)
            }
        }

        "problemsFromCatalogSample documents the catalog universally and the type only where it occurs" {
            testApplication {
                lateinit var app: Application
                application {
                    app = this
                    problemsFromCatalogSample()
                }
                startApplication()

                val responses =
                    app.routingRoot
                        .descendants()
                        .mapToPathItems()
                        .getValue("/orders/{id}")
                        .get
                        .shouldNotBeNull()
                        .responses
                        .shouldNotBeNull()

                responses.default.shouldNotBeNull()
                responses.responses.shouldNotBeNull().keys shouldBe setOf(403, 404, 405, 406, 415)
            }
        }

        "problemRoutesSample reaches the nested endpoint from one call at the root" {
            testApplication {
                lateinit var app: Application
                application {
                    app = this
                    problemRoutesSample()
                }
                startApplication()

                val responses =
                    app.routingRoot
                        .descendants()
                        .mapToPathItems()
                        .getValue("/orders/{id}")
                        .get
                        .shouldNotBeNull()
                        .responses
                        .shouldNotBeNull()

                responses.default.shouldNotBeNull()
                responses.responses.shouldNotBeNull().keys shouldBe setOf(403, 404, 405, 406, 415)
            }
        }
    })
