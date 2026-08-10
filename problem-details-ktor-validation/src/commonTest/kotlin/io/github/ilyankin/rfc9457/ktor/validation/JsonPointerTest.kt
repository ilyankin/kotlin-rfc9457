package io.github.ilyankin.rfc9457.ktor.validation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class Profile(
    val color: String,
)

@Serializable
private data class Item(
    val sku: String,
)

@Serializable
private data class Customer(
    val age: Int,
    val profile: Profile,
    val items: List<Item>,
    val labels: Map<String, String>,
    @SerialName("email_address") val emailAddress: String,
    @SerialName("a/b~c") val odd: String,
    @SerialName("full name") val spaced: String,
    @SerialName("цена") val price: String,
)

class JsonPointerTest :
    StringSpec({

        "a property reference becomes the RFC's fragment form" {
            jsonPointer(Customer::age) shouldBe "#/age"
        }

        "a path walks into a nested member" {
            jsonPointer<Customer>("profile", "color") shouldBe "#/profile/color"
        }

        "a list segment is an index, and the element type is walked into" {
            jsonPointer<Customer>("items", "0", "sku") shouldBe "#/items/0/sku"
            jsonPointer<Customer>("items", "-") shouldBe "#/items/-"
        }

        "a non-index segment under a list is rejected" {
            val thrown = shouldThrow<IllegalArgumentException> { jsonPointer<Customer>("items", "first") }
            thrown.message shouldContain "must be an index"
        }

        "any segment is accepted under a map, since keys are not known statically" {
            jsonPointer<Customer>("labels", "anything") shouldBe "#/labels/anything"
        }

        "an unknown member throws and names the members that do exist" {
            val thrown = shouldThrow<IllegalArgumentException> { jsonPointer<Customer>("nope") }
            thrown.message shouldContain "has no member"
            thrown.message shouldContain "age"
            thrown.message shouldContain "email_address"
        }

        "a property renamed by @SerialName is refused through the property reference" {
            shouldThrow<IllegalArgumentException> { jsonPointer(Customer::emailAddress) }
        }

        "the same member resolves through its serial name" {
            jsonPointer<Customer>("email_address") shouldBe "#/email_address"
        }

        "RFC 6901 escaping runs before percent-encoding" {
            jsonPointer<Customer>("a/b~c") shouldBe "#/a~1b~0c"
        }

        "a character a URI fragment does not admit is percent-encoded" {
            jsonPointer<Customer>("full name") shouldBe "#/full%20name"
        }

        "a non-ASCII member name is percent-encoded as UTF-8" {
            jsonPointer<Customer>("цена") shouldBe "#/%D1%86%D0%B5%D0%BD%D0%B0"
        }

        "descending past a primitive throws" {
            shouldThrow<IllegalArgumentException> { jsonPointer<Customer>("age", "deeper") }
        }

        "an empty path throws" {
            shouldThrow<IllegalArgumentException> { jsonPointer<Customer>() }
        }

        "invalidField takes a property reference and encodes the derived pointer" {
            val decoded = decodeValidationReason(invalidField(Customer::age, "must be positive").reasons.single())
            decoded.pointer shouldBe "#/age"
            decoded.detail shouldBe "must be positive"
        }
    })
