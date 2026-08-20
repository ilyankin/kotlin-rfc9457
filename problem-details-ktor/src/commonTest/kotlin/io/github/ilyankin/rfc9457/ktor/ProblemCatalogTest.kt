package io.github.ilyankin.rfc9457.ktor

import io.github.ilyankin.rfc9457.ProblemType
import io.github.ilyankin.rfc9457.problem
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

private object DeclaredOutOfCredit : ProblemType {
    override val typeUri: String = "https://example.com/probs/out-of-credit"
    override val title: String = "You do not have enough credit."
    override val status: Int = 403
}

private class InsufficientFunds : RuntimeException("balance too low")

class ProblemCatalogTest :
    StringSpec({

        "a declaratively mapped type is remembered" {
            val catalog = problemCatalog { map<InsufficientFunds>(DeclaredOutOfCredit) }

            catalog.problemTypes shouldBe listOf(DeclaredOutOfCredit)
        }

        "a lambda mapping declares no type" {
            val catalog = problemCatalog { map<InsufficientFunds> { _, _ -> DeclaredOutOfCredit.problem() } }

            catalog.problemTypes.shouldBeEmpty()
        }

        "standardStatusCodes reports the four codes it registers" {
            val catalog = problemCatalog { standardStatusCodes() }

            catalog.statusCodes shouldBe
                setOf(
                    HttpStatusCode.NotFound,
                    HttpStatusCode.MethodNotAllowed,
                    HttpStatusCode.NotAcceptable,
                    HttpStatusCode.UnsupportedMediaType,
                )
        }

        "an empty catalog reports nothing" {
            val catalog = problemCatalog { }

            catalog.problemTypes.shouldBeEmpty()
            catalog.statusCodes shouldBe emptySet()
        }
    })
