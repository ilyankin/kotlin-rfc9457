package io.github.ilyankin.rfc9457.ktor.openapi

import io.github.ilyankin.rfc9457.ProblemType
import io.github.ilyankin.rfc9457.ktor.ProblemContentTypes
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.Operation

private object OutOfCredit : ProblemType {
    override val typeUri: String = "https://example.com/probs/out-of-credit"
    override val title: String = "You do not have enough credit."
    override val status: Int = 403
}

private object AccountLocked : ProblemType {
    override val typeUri: String = "https://example.com/probs/account-locked"
    override val title: String = "This account is locked."
    override val status: Int = 403
}

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

        "a bare status code becomes a response with the problem schema" {
            val operation =
                Operation.build { responses { problemResponse(HttpStatusCode.NotFound) } }

            val response =
                operation.responses
                    .shouldNotBeNull()
                    .responses
                    .shouldNotBeNull()
                    .getValue(404)
                    .valueOrNull()
                    .shouldNotBeNull()
            response.content.shouldNotBeNull().keys shouldBe setOf(ProblemContentTypes.Json)
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

        "an explicit description replaces the type's title" {
            val operation =
                Operation.build {
                    responses { problemResponse(OutOfCredit, description = "Top up first.") }
                }

            operation.responses
                .shouldNotBeNull()
                .responses
                .shouldNotBeNull()
                .getValue(403)
                .valueOrNull()
                .shouldNotBeNull()
                .description shouldBe "Top up first."
        }

        "the errors schema reaches the response through the schema parameter" {
            val operation =
                Operation.build {
                    responses {
                        problemResponse(OutOfCredit, schema = ProblemSchemas.problemWithErrors)
                    }
                }

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
