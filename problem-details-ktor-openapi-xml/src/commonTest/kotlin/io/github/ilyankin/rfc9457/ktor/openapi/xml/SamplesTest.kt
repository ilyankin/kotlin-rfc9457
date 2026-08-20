package io.github.ilyankin.rfc9457.ktor.openapi.xml

import io.github.ilyankin.rfc9457.ktor.ProblemContentTypes
import io.github.ilyankin.rfc9457.samples.problemXmlContentSample
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.server.application.Application
import io.ktor.server.routing.openapi.mapToPathItems
import io.ktor.server.routing.routingRoot
import io.ktor.server.testing.testApplication

class SamplesTest :
    StringSpec({

        "problemXmlContentSample documents both media types on the one response" {
            testApplication {
                lateinit var app: Application
                application {
                    app = this
                    problemXmlContentSample()
                }
                startApplication()

                val content = app.routingRoot.descendants().mapToPathItems()
                    .getValue("/orders/{id}").get.shouldNotBeNull()
                    .responses.shouldNotBeNull().responses.shouldNotBeNull()
                    .getValue(403).valueOrNull().shouldNotBeNull()
                    .content.shouldNotBeNull()

                content.keys shouldBe setOf(ProblemContentTypes.Json, ProblemContentTypes.Xml)
            }
        }
    })
