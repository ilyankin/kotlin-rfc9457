package io.github.ilyankin.rfc9457

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private object OutOfCredit : ProblemType {
    override val typeUri: String = "https://example.com/probs/out-of-credit"
    override val title: String = "You do not have enough credit."
    override val status: Int = 403
}

class ProblemTest :
    StringSpec({

        "type defaults to about:blank" {
            Problem().type shouldBe "about:blank"
            Problem.ABOUT_BLANK shouldBe "about:blank"
        }

        "blank sets type, status and the given title" {
            val problem = Problem.blank(404, "Not Found")
            problem.type shouldBe "about:blank"
            problem.status shouldBe 404
            problem.title shouldBe "Not Found"
            problem.detail shouldBe null
        }

        "a ProblemType produces type, title and status, with detail per occurrence" {
            val problem = OutOfCredit.problem(detail = "Your current balance is 30, but that costs 50.")
            problem.type shouldBe "https://example.com/probs/out-of-credit"
            problem.title shouldBe "You do not have enough credit."
            problem.status shouldBe 403
            problem.detail shouldBe "Your current balance is 30, but that costs 50."
        }

        "an extension member colliding with a reserved name is rejected" {
            val error =
                shouldThrow<IllegalArgumentException> {
                    Problem(extensions = mapOf("title" to ProblemPrimitive("nope")))
                }
            error.message!! shouldContain "title"
        }

        "every reserved name is guarded" {
            listOf("type", "status", "title", "detail", "instance").forEach { name ->
                shouldThrow<IllegalArgumentException> {
                    Problem(extensions = mapOf(name to ProblemPrimitive("x")))
                }
            }
        }

        "the guard also fires through copy" {
            val problem = Problem(status = 400)
            shouldThrow<IllegalArgumentException> {
                problem.copy(extensions = mapOf("status" to ProblemPrimitive(400)))
            }
        }

        "reserved names are matched case-sensitively, so Title is a legal extension" {
            val problem = Problem(extensions = mapOf("Title" to ProblemPrimitive("odd but legal")))
            problem.extensions.keys shouldBe setOf("Title")
        }

        "a non-reserved extension is kept as given" {
            val problem = Problem(extensions = mapOf("balance" to ProblemPrimitive(30)))
            problem.extensions["balance"]?.int shouldBe 30
        }
    })
