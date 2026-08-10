package io.github.ilyankin.rfc9457.samples

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ProblemArray
import io.github.ilyankin.rfc9457.ProblemType
import io.github.ilyankin.rfc9457.encodeToProblemValue
import io.github.ilyankin.rfc9457.ktor.problemDetails
import io.github.ilyankin.rfc9457.ktor.validation.decodeValidationReason
import io.github.ilyankin.rfc9457.ktor.validation.invalidField
import io.github.ilyankin.rfc9457.ktor.validation.invalidFields
import io.github.ilyankin.rfc9457.ktor.validation.jsonPointer
import io.github.ilyankin.rfc9457.ktor.validation.requestValidation
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidation
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.requestvalidation.ValidationResult
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private object ValidationError : ProblemType {
    override val typeUri: String = "https://example.com/probs/validation-error"
    override val title: String = "Your request is not valid."
    override val status: Int = 422
}

/** @see io.github.ilyankin.rfc9457.ktor.validation.requestValidation */
internal fun Application.requestValidationSample() {
    install(RequestValidation) {
        // A real validator would parse `body` into a typed object; kept as raw text here so the
        // sample stays self-contained, with no JSON content-negotiation dependency of its own.
        validate<String> { body ->
            if (body.isBlank()) {
                invalidFields(
                    "#/age" to "must be a positive integer",
                    "#/profile/color" to "must be 'green', 'red' or 'blue'",
                )
            } else {
                ValidationResult.Valid
            }
        }
    }

    install(StatusPages) {
        problemDetails {
            requestValidation(ValidationError)
        }
    }

    routing {
        post("/customers") {
            call.respond(call.receive<String>())
        }
    }
}

@Serializable
private data class ValidationErrorEntry(
    val code: String,
    val field: String? = null,
    val message: String,
)

/** @see io.github.ilyankin.rfc9457.ktor.validation.decodeValidationReason */
internal fun Application.customErrorEntrySample() {
    install(RequestValidation) {
        validate<String> { body ->
            if (body.isBlank()) {
                invalidFields(
                    "#/age" to "must be a positive integer",
                    "#/profile/color" to "must be 'green', 'red' or 'blue'",
                )
            } else {
                ValidationResult.Valid
            }
        }
    }

    install(StatusPages) {
        problemDetails {
            // Bypasses requestValidation(): its errors[] shape is fixed to detail/pointer, renameable
            // but not restructured. decodeValidationReason() still reads the pointer/detail that
            // invalidFields() encoded. encodeToProblemValue() turns the typed entry into a ProblemValue.
            map<RequestValidationException> { _, cause ->
                Problem(
                    type = ValidationError.typeUri,
                    status = ValidationError.status,
                    title = ValidationError.title,
                    extensions =
                        mapOf(
                            "errors" to
                                ProblemArray(
                                    cause.reasons.map { reason ->
                                        val decoded = decodeValidationReason(reason)
                                        Json.encodeToProblemValue(
                                            ValidationErrorEntry(
                                                code = "VALIDATION_FAILED",
                                                field = decoded.pointer,
                                                message = decoded.detail,
                                            ),
                                        )
                                    },
                                ),
                        ),
                )
            }
        }
    }

    routing {
        post("/customers") {
            call.respond(call.receive<String>())
        }
    }
}

@Serializable
private data class Profile(
    val color: String,
)

@Serializable
private data class Customer(
    val age: Int,
    val profile: Profile,
    @SerialName("email_address") val emailAddress: String,
)

/** @see io.github.ilyankin.rfc9457.ktor.validation.invalidField */
internal fun Application.typedInvalidFieldSample() {
    install(RequestValidation) {
        // `Customer::age` rather than the string "#/age": renaming the property stops compiling here,
        // instead of leaving behind a pointer to a member that no longer exists.
        validate<Customer> { customer ->
            if (customer.age > 0) {
                ValidationResult.Valid
            } else {
                invalidField(Customer::age, "must be a positive integer")
            }
        }
    }

    install(StatusPages) {
        problemDetails {
            requestValidation(ValidationError)
        }
    }

    routing {
        post("/customers") {
            call.respond(call.receive<Customer>())
        }
    }
}

/** @see io.github.ilyankin.rfc9457.ktor.validation.jsonPointer */
internal fun Application.typedPointerSample() {
    install(RequestValidation) {
        validate<Customer> { customer ->
            val errors =
                buildList {
                    if (customer.age <= 0) {
                        add(jsonPointer(Customer::age) to "must be a positive integer")
                    }
                    // No property reference reaches a nested member, but every segment of the path is
                    // still checked against the descriptor it belongs to.
                    if (customer.profile.color !in setOf("green", "red", "blue")) {
                        add(jsonPointer<Customer>("profile", "color") to "must be 'green', 'red' or 'blue'")
                    }
                    // `@SerialName` renames this one, so the pointer has to name what the wire
                    // carries. `jsonPointer(Customer::emailAddress)` would throw rather than guess.
                    if ("@" !in customer.emailAddress) {
                        add(jsonPointer<Customer>("email_address") to "must be an email address")
                    }
                }

            if (errors.isEmpty()) ValidationResult.Valid else invalidFields(errors)
        }
    }

    install(StatusPages) {
        problemDetails {
            requestValidation(ValidationError)
        }
    }

    routing {
        post("/customers") {
            call.respond(call.receive<Customer>())
        }
    }
}
