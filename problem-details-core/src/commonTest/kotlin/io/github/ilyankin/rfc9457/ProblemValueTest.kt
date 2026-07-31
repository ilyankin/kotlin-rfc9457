package io.github.ilyankin.rfc9457

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class ProblemValueTest :
    StringSpec({

        "a numeric literal keeps its exact text and is not a string" {
            val value = ProblemPrimitive(30)
            value.content shouldBe "30"
            value.isString shouldBe false
        }

        "a high-precision decimal literal is kept verbatim" {
            val literal = "3.141592653589793238462643383279"
            val value = problemLiteral(literal, isString = false)
            value.content shouldBe literal
            value.isString shouldBe false
        }

        "a string primitive is marked as a string" {
            val value = ProblemPrimitive("30")
            value.content shouldBe "30"
            value.isString shouldBe true
        }

        "a number and a same-text string are different values" {
            ProblemPrimitive(30) shouldBe ProblemPrimitive("30", isString = false)
            (ProblemPrimitive(30) == ProblemPrimitive("30")) shouldBe false
        }

        "ProblemArray behaves as a List" {
            val array = ProblemArray(listOf(ProblemPrimitive("a"), ProblemPrimitive("b")))
            array.size shouldBe 2
            array[0] shouldBe ProblemPrimitive("a")
            array.map { it.string } shouldBe listOf("a", "b")
            array shouldBe listOf(ProblemPrimitive("a"), ProblemPrimitive("b"))
        }

        "ProblemObject behaves as a Map" {
            val obj = ProblemObject(mapOf("balance" to ProblemPrimitive(30)))
            obj["balance"]?.int shouldBe 30
            obj.keys shouldBe setOf("balance")
            obj shouldBe mapOf("balance" to ProblemPrimitive(30))
        }

        "a non-finite Double is rejected: it has no JSON representation" {
            // RFC 8259 §6 numbers cannot express NaN or Infinity; emitting one would produce a
            // literal conforming parsers reject, so the producing edge refuses it instead.
            shouldThrow<IllegalArgumentException> { ProblemPrimitive(Double.NaN) }
            shouldThrow<IllegalArgumentException> { ProblemPrimitive(Double.POSITIVE_INFINITY) }
            shouldThrow<IllegalArgumentException> { ProblemPrimitive(Double.NEGATIVE_INFINITY) }
        }

        "typed accessors read through, OrNull variants tolerate the wrong shape" {
            ProblemPrimitive(true).boolean shouldBe true
            ProblemPrimitive(7L).long shouldBe 7L
            ProblemNull.intOrNull shouldBe null
            ProblemArray(emptyList()).stringOrNull shouldBe null
            shouldThrow<IllegalArgumentException> { ProblemNull.string }
        }
    })
