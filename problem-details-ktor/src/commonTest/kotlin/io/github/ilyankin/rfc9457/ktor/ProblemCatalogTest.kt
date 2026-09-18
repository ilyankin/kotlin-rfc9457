package io.github.ilyankin.rfc9457.ktor

import io.github.ilyankin.rfc9457.ProblemType
import io.github.ilyankin.rfc9457.problem
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
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

        "a built catalog rejects later exception mappings" {
            val catalog = problemCatalog { }

            val error = shouldThrow<IllegalStateException> {
                catalog.map<InsufficientFunds> { _, _ -> DeclaredOutOfCredit.problem() }
            }

            error.message.orEmpty() shouldContain "already built"
        }

        "a built catalog rejects every non-inline mutation entry point" {
            val catalog = problemCatalog { }

            shouldThrow<IllegalStateException> { catalog.onUnmapped { _, _ -> DeclaredOutOfCredit.problem() } }
            shouldThrow<IllegalStateException> { catalog.customize { _, problem -> problem } }
            shouldThrow<IllegalStateException> {
                catalog.forStatusCode(HttpStatusCode.BadRequest) { DeclaredOutOfCredit.problem() }
            }
            shouldThrow<IllegalStateException> { catalog.standardStatusCodes() }
        }

        "reported catalog collections cannot mutate a built catalog" {
            val catalog =
                problemCatalog {
                    map<InsufficientFunds>(DeclaredOutOfCredit)
                    forStatusCode(HttpStatusCode.BadRequest) { DeclaredOutOfCredit.problem() }
                }

            runCatching { (catalog.problemTypes as? MutableList)?.clear() }
            runCatching { (catalog.statusCodes as? MutableSet)?.clear() }

            catalog.problemTypes shouldBe listOf(DeclaredOutOfCredit)
            catalog.statusCodes shouldBe setOf(HttpStatusCode.BadRequest)
        }
    })
