package io.github.ilyankin.rfc9457.ktor.validation

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ktor.problemJson
import io.github.ilyankin.rfc9457.problemArray
import io.github.ilyankin.rfc9457.problemObject
import io.github.ilyankin.rfc9457.samples.customErrorEntrySample
import io.github.ilyankin.rfc9457.samples.requestValidationSample
import io.github.ilyankin.rfc9457.samples.typedInvalidFieldSample
import io.github.ilyankin.rfc9457.samples.typedPointerSample
import io.github.ilyankin.rfc9457.stringOrNull
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json

class SamplesTest :
    StringSpec({

        "requestValidationSample answers a blank body with a two-field errors[] array" {
            testApplication {
                application {
                    install(ContentNegotiation) { problemJson() }
                    requestValidationSample()
                }

                val response = client.post("/customers") { setBody("") }
                response.status shouldBe HttpStatusCode.UnprocessableEntity

                val body = Json.decodeFromString<Problem>(response.bodyAsText())
                body.type shouldBe "https://example.com/probs/validation-error"
                body.extensions.getValue("errors").problemArray shouldHaveSize 2
            }
        }

        "requestValidationSample lets a non-blank body through" {
            testApplication {
                application {
                    install(ContentNegotiation) { problemJson() }
                    requestValidationSample()
                }

                client.post("/customers") { setBody("not blank") }.status shouldBe HttpStatusCode.OK
            }
        }

        "customErrorEntrySample builds a typed errors[] entry from decodeValidationReason" {
            testApplication {
                application {
                    install(ContentNegotiation) { problemJson() }
                    customErrorEntrySample()
                }

                val body =
                    Json.decodeFromString<Problem>(client.post("/customers") { setBody("") }.bodyAsText())
                val errors = body.extensions.getValue("errors").problemArray
                errors shouldHaveSize 2
                val first = errors[0].problemObject
                first.getValue("code").stringOrNull shouldBe "VALIDATION_FAILED"
                first.getValue("field").stringOrNull shouldBe "#/age"
                first.getValue("message").stringOrNull shouldBe "must be a positive integer"
            }
        }

        "typedInvalidFieldSample derives #/age from the property reference" {
            testApplication {
                application {
                    // `problemJson()` deserializes nothing but `Problem`, so the sample's own DTO
                    // needs an ordinary converter beside it.
                    install(ContentNegotiation) {
                        problemJson()
                        json()
                    }
                    typedInvalidFieldSample()
                }

                val response =
                    client.post("/customers") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"age":-1,"profile":{"color":"green"},"email_address":"a@b.example"}""")
                    }
                response.status shouldBe HttpStatusCode.UnprocessableEntity

                val body = Json.decodeFromString<Problem>(response.bodyAsText())
                val error = body.extensions.getValue("errors").problemArray.single().problemObject
                error.getValue("pointer").stringOrNull shouldBe "#/age"
                error.getValue("detail").stringOrNull shouldBe "must be a positive integer"
            }
        }

        "typedPointerSample points at a plain, a nested and a renamed member" {
            testApplication {
                application {
                    install(ContentNegotiation) {
                        problemJson()
                        json()
                    }
                    typedPointerSample()
                }

                val response =
                    client.post("/customers") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"age":0,"profile":{"color":"puce"},"email_address":"nope"}""")
                    }
                response.status shouldBe HttpStatusCode.UnprocessableEntity

                val body = Json.decodeFromString<Problem>(response.bodyAsText())
                val errors = body.extensions.getValue("errors").problemArray
                errors shouldHaveSize 3
                errors.map { it.problemObject.getValue("pointer").stringOrNull } shouldBe
                    listOf("#/age", "#/profile/color", "#/email_address")
            }
        }

        "typedPointerSample lets a valid customer through" {
            testApplication {
                application {
                    install(ContentNegotiation) {
                        problemJson()
                        json()
                    }
                    typedPointerSample()
                }

                client
                    .post("/customers") {
                        contentType(ContentType.Application.Json)
                        setBody("""{"age":30,"profile":{"color":"green"},"email_address":"a@b.example"}""")
                    }.status shouldBe HttpStatusCode.OK
            }
        }
    })
