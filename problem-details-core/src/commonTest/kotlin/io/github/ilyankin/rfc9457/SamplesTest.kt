package io.github.ilyankin.rfc9457

import io.github.ilyankin.rfc9457.samples.buildProblem
import io.github.ilyankin.rfc9457.samples.buildProblemWithTypedExtension
import io.github.ilyankin.rfc9457.samples.problemFromProblemType
import io.github.ilyankin.rfc9457.samples.readTypedExtensions
import io.github.ilyankin.rfc9457.samples.throwProblemForOutOfCredit
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/*
 * The samples in `samples/` are inlined into the API documentation by `@sample`. Compiling them
 * already stops them from referring to an API that no longer exists; running them here stops them
 * from being code that compiles but does not do what the surrounding prose claims.
 */
class SamplesTest :
    StringSpec({

        "buildProblem produces the document the KDoc describes" {
            val problem = buildProblem()
            problem.type shouldBe "https://example.net/validation-error"
            problem.status shouldBe 422
            problem.extensions["invalidField"]?.string shouldBe "age"
        }

        "buildProblemWithTypedExtension nests the typed payload under its member name" {
            val account = buildProblemWithTypedExtension().extensions["account"]?.problemObject
            account?.get("id")?.string shouldBe "12345"
            account?.get("balance")?.int shouldBe 30
        }

        "problemFromProblemType takes type, title and status from the type" {
            val problem = problemFromProblemType()
            problem.type shouldBe "https://example.com/probs/out-of-credit"
            problem.title shouldBe "You do not have enough credit."
            problem.status shouldBe 403
            problem.detail shouldBe "Your current balance is 30, but that costs 50."
        }

        "throwProblemForOutOfCredit raises the problem type's own document" {
            val thrown = shouldThrow<ProblemException> { throwProblemForOutOfCredit() }

            thrown.problem.type shouldBe "https://example.com/probs/out-of-credit"
            thrown.problem.title shouldBe "You do not have enough credit."
            thrown.problem.status shouldBe 403
            thrown.problem.detail shouldBe "Your current balance is 30, but that costs 50."
        }

        "readTypedExtensions ignores members the type does not declare" {
            val problem =
                problem {
                    status = 403
                    extension("balance", 30)
                    extension("accounts", ProblemArray(listOf(ProblemPrimitive("/account/12345"))))
                    // Added by a newer version of the problem type; §3.2 says a consumer must ignore it.
                    extension("unknownToUs", true)
                }

            val decoded = readTypedExtensions(problem)
            decoded.balance shouldBe 30
            decoded.accounts shouldBe listOf("/account/12345")
        }
    })
