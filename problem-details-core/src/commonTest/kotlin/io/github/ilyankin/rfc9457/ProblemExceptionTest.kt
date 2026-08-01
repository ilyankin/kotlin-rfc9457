package io.github.ilyankin.rfc9457

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

private object ThrownOutOfCredit : ProblemType {
    override val typeUri: String = "https://example.com/probs/out-of-credit"
    override val title: String = "You do not have enough credit."
    override val status: Int = 403
}

class ProblemExceptionTest :
    StringSpec({

        "the carried problem is exposed unchanged" {
            val carried =
                problem {
                    type = "https://example.com/probs/out-of-credit"
                    status = 403
                }
            ProblemException(carried).problem shouldBe carried
        }

        "the message carries status, type and title" {
            val carried =
                problem {
                    type = "https://example.com/probs/out-of-credit"
                    status = 403
                    title = "You do not have enough credit."
                    detail = "Your current balance is 30, but that costs 50."
                }

            ProblemException(carried).message shouldBe
                "403 https://example.com/probs/out-of-credit: You do not have enough credit."
        }

        "the message leaves detail out" {
            val carried =
                problem {
                    status = 403
                    title = "You do not have enough credit."
                    detail = "Your current balance is 30, but that costs 50."
                }

            ProblemException(carried).message!! shouldNotContain "balance"
        }

        "a problem with neither status nor title degrades to its type alone" {
            ProblemException(Problem()).message shouldBe "about:blank"
        }

        "the cause is chained" {
            val root = IllegalStateException("the ledger is unreachable")
            ProblemException(Problem(), root).cause shouldBe root
        }

        "the cause is absent by default" {
            ProblemException(Problem()).cause shouldBe null
        }

        "exception() takes type, title and status from the problem type" {
            val thrown = ThrownOutOfCredit.exception(detail = "Your current balance is 30, but that costs 50.")

            thrown.problem.type shouldBe "https://example.com/probs/out-of-credit"
            thrown.problem.title shouldBe "You do not have enough credit."
            thrown.problem.status shouldBe 403
            thrown.problem.detail shouldBe "Your current balance is 30, but that costs 50."
            thrown.problem.instance shouldBe null
        }

        "exception() passes instance and cause through" {
            val root = IllegalStateException("the ledger is unreachable")
            val thrown = ThrownOutOfCredit.exception(instance = "/account/12345", cause = root)

            thrown.problem.instance shouldBe "/account/12345"
            thrown.problem.detail shouldBe null
            thrown.cause shouldBe root
        }
    })
