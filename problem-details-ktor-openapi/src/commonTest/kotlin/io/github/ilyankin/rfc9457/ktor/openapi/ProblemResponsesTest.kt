package io.github.ilyankin.rfc9457.ktor.openapi

import io.github.ilyankin.rfc9457.ktor.ProblemContentTypes
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.Operation

class ProblemResponsesTest :
    StringSpec({

        "a problem type becomes a response at its own status" {
            val operation = Operation.build { responses { problemResponse(OutOfCredit) } }

            val response =
                operation.responses
                    .shouldNotBeNull()
                    .responses
                    .shouldNotBeNull()
                    .getValue(403)
                    .valueOrNull()
                    .shouldNotBeNull()
            response.description shouldContain "You do not have enough credit."
        }

        "the body is application/problem+json and nothing else" {
            val operation = Operation.build { responses { problemResponse(OutOfCredit) } }

            val content =
                operation.responses
                    .shouldNotBeNull()
                    .responses
                    .shouldNotBeNull()
                    .getValue(403)
                    .valueOrNull()
                    .shouldNotBeNull()
                    .content
                    .shouldNotBeNull()
            content.keys shouldBe setOf(ProblemContentTypes.Json)
            content
                .getValue(ProblemContentTypes.Json)
                .schema
                .shouldNotBeNull()
                .valueOrNull()
                .shouldNotBeNull()
                .title shouldBe "ProblemDetails"
        }

        "a bare status code answers at that code, described by its reason phrase" {
            val operation =
                Operation.build { responses { problemResponse(HttpStatusCode.NotFound) } }

            operation.responses
                .shouldNotBeNull()
                .responses
                .shouldNotBeNull()
                .getValue(404)
                .valueOrNull()
                .shouldNotBeNull()
                .description shouldBe HttpStatusCode.NotFound.description
        }

        "problemDefault fills the catch-all slot" {
            val operation = Operation.build { responses { problemDefault() } }

            val default =
                operation.responses
                    .shouldNotBeNull()
                    .default
                    .shouldNotBeNull()
                    .valueOrNull()
                    .shouldNotBeNull()
            default.content.shouldNotBeNull().keys shouldBe setOf(ProblemContentTypes.Json)
        }

        "two types on one status collapse into one response naming both" {
            val operation =
                Operation.build {
                    responses {
                        problemResponse(OutOfCredit)
                        problemResponse(AccountLocked)
                    }
                }

            val responses =
                operation.responses
                    .shouldNotBeNull()
                    .responses
                    .shouldNotBeNull()
            responses.keys shouldBe setOf(403)

            val response = responses.getValue(403).valueOrNull().shouldNotBeNull()
            response.description shouldContain "You do not have enough credit."
            response.description shouldContain "This account is locked."
        }

        // A root `problemResponses(catalog)` and a leaf naming the same code both write a
        // description into the one builder Ktor keeps per status; without the guard the reader
        // would be told "Not Found Not Found".
        "a description already written is not written again" {
            val operation =
                Operation.build {
                    responses {
                        problemResponse(HttpStatusCode.NotFound)
                        problemResponse(HttpStatusCode.NotFound)
                    }
                }

            operation.responses
                .shouldNotBeNull()
                .responses
                .shouldNotBeNull()
                .getValue(404)
                .valueOrNull()
                .shouldNotBeNull()
                .description shouldBe HttpStatusCode.NotFound.description
        }

        "each colliding type contributes an example keyed by its type URI" {
            val operation =
                Operation.build {
                    responses {
                        problemResponse(OutOfCredit)
                        problemResponse(AccountLocked)
                    }
                }

            val examples =
                operation.responses
                    .shouldNotBeNull()
                    .responses
                    .shouldNotBeNull()
                    .getValue(403)
                    .valueOrNull()
                    .shouldNotBeNull()
                    .content
                    .shouldNotBeNull()
                    .getValue(ProblemContentTypes.Json)
                    .examples
                    .shouldNotBeNull()
            examples.keys shouldBe
                setOf(
                    "https://example.com/probs/out-of-credit",
                    "https://example.com/probs/account-locked",
                )
        }

        "an explicit description replaces the default one, in every overload" {
            val operation =
                Operation.build {
                    responses {
                        problemResponse(OutOfCredit, description = "Top up first.")
                        problemResponse(HttpStatusCode.NotFound, description = "No such order.")
                        problemDefault(description = "Something else went wrong.")
                    }
                }

            val responses = operation.responses.shouldNotBeNull()
            val byCode = responses.responses.shouldNotBeNull()

            byCode
                .getValue(403)
                .valueOrNull()
                .shouldNotBeNull()
                .description shouldBe "Top up first."
            byCode
                .getValue(404)
                .valueOrNull()
                .shouldNotBeNull()
                .description shouldBe "No such order."
            responses.default
                .shouldNotBeNull()
                .valueOrNull()
                .shouldNotBeNull()
                .description shouldBe "Something else went wrong."
        }

        "the errors schema reaches the response through the schema parameter" {
            val operation =
                Operation.build {
                    responses {
                        problemResponse(OutOfCredit, schema = ProblemSchemas.problemWithErrors)
                        problemDefault(schema = ProblemSchemas.problemWithErrors)
                    }
                }

            val responses = operation.responses.shouldNotBeNull()

            responses.responses
                .shouldNotBeNull()
                .getValue(403)
                .valueOrNull()
                .shouldNotBeNull()
                .content
                .shouldNotBeNull()
                .getValue(ProblemContentTypes.Json)
                .schema
                .shouldNotBeNull()
                .valueOrNull()
                .shouldNotBeNull()
                .title shouldBe "ProblemDetailsWithErrors"

            responses.default
                .shouldNotBeNull()
                .valueOrNull()
                .shouldNotBeNull()
                .content
                .shouldNotBeNull()
                .getValue(ProblemContentTypes.Json)
                .schema
                .shouldNotBeNull()
                .valueOrNull()
                .shouldNotBeNull()
                .title shouldBe "ProblemDetailsWithErrors"
        }

        "configure runs against the same response builder" {
            val operation =
                Operation.build {
                    responses {
                        problemResponse(OutOfCredit) {
                            headers { header("Retry-After") { description = "Seconds to wait." } }
                        }
                    }
                }

            operation.responses
                .shouldNotBeNull()
                .responses
                .shouldNotBeNull()
                .getValue(403)
                .valueOrNull()
                .shouldNotBeNull()
                .headers
                .shouldNotBeNull()
                .keys shouldBe setOf("Retry-After")
        }
    })
