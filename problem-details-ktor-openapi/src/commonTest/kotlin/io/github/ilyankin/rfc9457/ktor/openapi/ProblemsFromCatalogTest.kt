package io.github.ilyankin.rfc9457.ktor.openapi

import io.github.ilyankin.rfc9457.ProblemType
import io.github.ilyankin.rfc9457.ktor.problemCatalog
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.openapi.Operation

private object CatalogedOutOfCredit : ProblemType {
    override val typeUri: String = "https://example.com/probs/out-of-credit"
    override val title: String = "You do not have enough credit."
    override val status: Int = 403
}

private class InsufficientFunds : RuntimeException("balance too low")

class ProblemsFromCatalogTest :
    StringSpec({

        "the registered status codes become responses" {
            val catalog = problemCatalog { standardStatusCodes() }
            val operation = Operation.build { responses { problemsFrom(catalog) } }

            operation.responses
                .shouldNotBeNull()
                .responses
                .shouldNotBeNull()
                .keys shouldBe
                setOf(404, 405, 406, 415)
        }

        "the catch-all is always documented" {
            val operation =
                Operation.build { responses { problemsFrom(problemCatalog { }) } }

            operation.responses
                .shouldNotBeNull()
                .default
                .shouldNotBeNull()
                .valueOrNull()
                .shouldNotBeNull()
                .content
                .shouldNotBeNull()
                .keys.size shouldBe 1
        }

        "a domain type is not attached, because nothing knows which routes reach it" {
            val catalog = problemCatalog { map<InsufficientFunds>(CatalogedOutOfCredit) }
            val operation = Operation.build { responses { problemsFrom(catalog) } }

            operation.responses.shouldNotBeNull().responses shouldBe null
        }

        "configure reaches every derived response" {
            val catalog = problemCatalog { standardStatusCodes() }
            val operation =
                Operation.build {
                    responses {
                        problemsFrom(catalog) {
                            headers { header("X-Request-Id") { description = "Correlation id." } }
                        }
                    }
                }

            val responses =
                operation.responses
                    .shouldNotBeNull()
                    .responses
                    .shouldNotBeNull()
            responses.values.forEach { response ->
                response
                    .valueOrNull()
                    .shouldNotBeNull()
                    .headers
                    .shouldNotBeNull()
                    .keys shouldBe setOf("X-Request-Id")
            }
            operation.responses
                .shouldNotBeNull()
                .default
                .shouldNotBeNull()
                .valueOrNull()
                .shouldNotBeNull()
                .headers
                .shouldNotBeNull()
                .keys shouldBe setOf("X-Request-Id")
        }
    })
