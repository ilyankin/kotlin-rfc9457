package io.github.ilyankin.rfc9457.ktor.validation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class InvalidFieldTest :
    StringSpec({

        "invalidField round-trips pointer and detail through decodeValidationReason" {
            val result = invalidField("#/age", "must be a positive integer")
            result.reasons shouldBe listOf("#/age\u0000must be a positive integer")

            val decoded = decodeValidationReason(result.reasons.single())
            decoded.pointer shouldBe "#/age"
            decoded.detail shouldBe "must be a positive integer"
        }

        "invalidFields encodes every pair, in order" {
            val result =
                invalidFields(
                    "#/age" to "must be a positive integer",
                    "#/profile/color" to "must be 'green', 'red' or 'blue'",
                )
            val decoded = result.reasons.map(::decodeValidationReason)
            decoded.map { it.pointer } shouldBe listOf("#/age", "#/profile/color")
            decoded.map { it.detail } shouldBe
                listOf("must be a positive integer", "must be 'green', 'red' or 'blue'")
        }

        "the list overload produces the same reasons as the vararg one" {
            val fields =
                listOf(
                    "#/age" to "must be a positive integer",
                    "#/profile/color" to "must be 'green', 'red' or 'blue'",
                )
            invalidFields(fields).reasons shouldBe invalidFields(*fields.toTypedArray()).reasons
        }

        "invalidFields rejects an empty list in either form" {
            shouldThrow<IllegalArgumentException> { invalidFields() }
            shouldThrow<IllegalArgumentException> { invalidFields(emptyList()) }
        }

        "invalidField rejects a pointer containing U+0000" {
            shouldThrow<IllegalArgumentException> { invalidField("#/a\u0000ge", "detail") }
        }

        "invalidField rejects a detail containing U+0000" {
            shouldThrow<IllegalArgumentException> { invalidField("#/age", "bad\u0000detail") }
        }

        "decodeValidationReason treats a reason with no separator as detail-only" {
            val decoded = decodeValidationReason("a plain validation message")
            decoded.pointer.shouldBeNull()
            decoded.detail shouldBe "a plain validation message"
        }
    })
