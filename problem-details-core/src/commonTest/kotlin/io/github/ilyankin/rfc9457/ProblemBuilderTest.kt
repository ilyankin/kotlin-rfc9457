package io.github.ilyankin.rfc9457

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.Serializable

@Serializable
private data class OutOfCreditDetails(
    val balance: Int,
    val accounts: List<String>,
)

@Serializable
private data class HasReservedField(
    val status: Int,
)

private object OutOfCreditType : ProblemType {
    override val typeUri: String = "https://example.com/probs/out-of-credit"
    override val title: String = "You do not have enough credit."
    override val status: Int = 403
}

class ProblemBuilderTest :
    StringSpec({

        "the builder sets standard members" {
            val problem =
                problem {
                    type = "https://example.net/validation-error"
                    status = 422
                    title = "Your request is not valid."
                    detail = "one occurrence"
                    instance = "/account/12345/msgs/abc"
                }
            problem.status shouldBe 422
            problem.instance shouldBe "/account/12345/msgs/abc"
        }

        "type(ProblemType) fills type, title and status together" {
            val problem = problem { type(OutOfCreditType) }
            problem.type shouldBe OutOfCreditType.typeUri
            problem.title shouldBe OutOfCreditType.title
            problem.status shouldBe 403
        }

        "extensions(obj) spreads the object's fields as sibling members" {
            val problem =
                problem {
                    type(OutOfCreditType)
                    extensions(OutOfCreditDetails(balance = 30, accounts = listOf("/account/12345")))
                }
            problem.extensions.keys shouldBe setOf("balance", "accounts")
            problem.extensions["balance"]?.int shouldBe 30
            problem.extensions["accounts"]?.problemArray?.map { it.string } shouldBe listOf("/account/12345")
        }

        "extension(name, value) accepts primitives" {
            val problem =
                problem {
                    extension("traceId", "abc-123")
                    extension("retries", 2)
                    extension("final", true)
                }
            problem.extensions["traceId"]?.string shouldBe "abc-123"
            problem.extensions["retries"]?.int shouldBe 2
            problem.extensions["final"]?.boolean shouldBe true
        }

        "extension(name, value) accepts a serializable value" {
            val problem =
                problem {
                    extension("details", OutOfCreditDetails(balance = 5, accounts = emptyList()))
                }
            problem.extensions["details"]
                ?.problemObject
                ?.get("balance")
                ?.int shouldBe 5
        }

        "a duplicate extension key throws instead of silently keeping the last" {
            val error =
                shouldThrow<IllegalArgumentException> {
                    problem {
                        extension("balance", 30)
                        extension("balance", 40)
                    }
                }
            error.message!! shouldContain "balance"
        }

        "extensions(obj) with a reserved field name names both the field and the type" {
            val error =
                shouldThrow<IllegalArgumentException> {
                    problem { extensions(HasReservedField(status = 400)) }
                }
            error.message!! shouldContain "status"
            error.message!! shouldContain "HasReservedField"
        }

        "extensions(value) rejects a value that is not a JSON object" {
            shouldThrow<IllegalArgumentException> {
                problem { extensions(listOf(1, 2, 3)) }
            }
        }
    })
