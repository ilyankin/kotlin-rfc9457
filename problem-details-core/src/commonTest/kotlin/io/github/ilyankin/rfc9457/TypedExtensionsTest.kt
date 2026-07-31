package io.github.ilyankin.rfc9457

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Serializable

@Serializable
private data class Credit(
    val balance: Int,
    val accounts: List<String>,
)

@Serializable
private data class ValidationError(
    val detail: String,
    val pointer: String,
)

class TypedExtensionsTest :
    StringSpec({

        "extensions(obj) and extensionsAs<T>() are inverses" {
            val details = Credit(balance = 30, accounts = listOf("/account/12345", "/account/67890"))
            val problem = problem { extensions(details) }
            problem.extensionsAs<Credit>() shouldBe details
        }

        "extensionsAs tolerates extension members the target type does not declare" {
            val problem =
                problem {
                    extensions(Credit(balance = 30, accounts = emptyList()))
                    extension("traceId", "abc-123")
                }
            problem.extensionsAs<Credit>() shouldBe Credit(balance = 30, accounts = emptyList())
        }

        "extension<T>(name) decodes a single member" {
            val problem =
                problem {
                    extension(
                        "errors",
                        listOf(
                            ValidationError("must be a positive integer", "#/age"),
                            ValidationError("must be 'green', 'red' or 'blue'", "#/profile/color"),
                        ),
                    )
                }
            problem.extension<List<ValidationError>>("errors") shouldBe
                listOf(
                    ValidationError("must be a positive integer", "#/age"),
                    ValidationError("must be 'green', 'red' or 'blue'", "#/profile/color"),
                )
        }

        "extension<T>(name) returns null for an absent member" {
            Problem().extension<String>("nope") shouldBe null
        }
    })
