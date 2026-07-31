package io.github.ilyankin.rfc9457

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Pins the difference between the strict accessors and their `*OrNull` twins.
 *
 * The pair exists because reading an extension member can fail two independent ways — the member is
 * *absent*, or it is present with the *wrong* JSON type — and only the second one is what the pair
 * chooses between. The first is handled by `?.` on the map lookup, whichever half is used.
 */
class ProblemValueAccessorTest :
    StringSpec({

        val account = ProblemObject(mapOf("balance" to ProblemPrimitive(30)))

        val problem =
            Problem(
                type = "https://example.com/probs/out-of-credit",
                extensions =
                    mapOf(
                        "account" to account,
                        "balance" to ProblemPrimitive(30),
                        "cancelled" to ProblemNull,
                    ),
            )

        "both halves agree when the member is present and has the expected shape" {
            problem.extensions["account"]?.problemObject shouldBe account
            problem.extensions["account"]?.problemObjectOrNull shouldBe account
        }

        "they differ in exactly one case: present, but another shape" {
            problem.extensions["balance"]?.problemObjectOrNull shouldBe null

            val failure =
                shouldThrow<IllegalArgumentException> {
                    problem.extensions["balance"]?.problemObject
                }
            failure.message shouldBe "Expected an object, was ProblemPrimitive"
        }

        "the safe call covers absence, so even the strict half yields null for a missing member" {
            // `?.` short-circuits before the accessor runs — nothing to throw about, the member simply
            // is not there. This is why `problemObject` still needs a `?`, and why needing one says
            // nothing about which half was picked.
            problem.extensions["quota"]?.problemObject shouldBe null
            problem.extensions["quota"]?.problemObjectOrNull shouldBe null
        }

        "absent, explicitly null and wrong-typed collapse into one null through the lenient half" {
            problem.extensions["quota"]?.problemObjectOrNull shouldBe null // absent
            problem.extensions["cancelled"]?.problemObjectOrNull shouldBe null // JSON null
            problem.extensions["balance"]?.problemObjectOrNull shouldBe null // present, wrong shape

            // Membership is what separates the three when the difference matters.
            ("quota" in problem.extensions) shouldBe false
            ("cancelled" in problem.extensions) shouldBe true
            problem.extensions["cancelled"] shouldBe ProblemNull
        }

        "the same split applies to arrays and to scalars" {
            problem.extensions["account"]?.problemArrayOrNull shouldBe null
            shouldThrow<IllegalArgumentException> { problem.extensions["account"]?.problemArray }

            problem.extensions["balance"]?.int shouldBe 30
            problem.extensions["balance"]?.intOrNull shouldBe 30
            problem.extensions["account"]?.intOrNull shouldBe null
            shouldThrow<IllegalArgumentException> { problem.extensions["account"]?.int }
        }

        "primitive and primitiveOrNull complete the pair for scalars themselves" {
            problem.extensions["balance"]?.primitive shouldBe ProblemPrimitive(30)
            problem.extensions["balance"]?.primitiveOrNull shouldBe ProblemPrimitive(30)
            problem.extensions["account"]?.primitiveOrNull shouldBe null
            shouldThrow<IllegalArgumentException> { problem.extensions["account"]?.primitive }
        }

        "a wrong shape and unparseable text fail as different exceptions" {
            // Not a scalar at all: the cast fails.
            shouldThrow<IllegalArgumentException> { ProblemArray(emptyList()).int }
            // A scalar whose text is not a number: the parse fails. NumberFormatException is itself an
            // IllegalArgumentException, so a caller that does not care can catch just the one.
            shouldThrow<NumberFormatException> { ProblemPrimitive("plenty").int }
            ProblemPrimitive("plenty").intOrNull shouldBe null
        }

        "the scalar accessors read the wire literal, not the JSON type" {
            // A quoted "30" is a string on the wire, yet `int` parses it — matching
            // kotlinx.serialization's own JsonPrimitive.int, which also ignores `isString`. Only
            // isString distinguishes the two, and the codecs keep it intact in both directions.
            ProblemPrimitive("30").int shouldBe 30
            ProblemPrimitive("30").isString shouldBe true
            ProblemPrimitive(30).string shouldBe "30"
            ProblemPrimitive(30).isString shouldBe false
        }

        "ProblemNull is a value, not an absence: every strict accessor rejects it" {
            shouldThrow<IllegalArgumentException> { ProblemNull.string }
            shouldThrow<IllegalArgumentException> { ProblemNull.problemObject }
            ProblemNull.stringOrNull shouldBe null
            ProblemNull.problemObjectOrNull shouldBe null
        }
    })
