# Module problem-details-ktor-validation

Ktor `RequestValidation` plugin integration: maps `RequestValidationException` to a `Problem` carrying
an `errors[]` extension array with JSON Pointer (RFC 6901) references — the multi-field validation
pattern RFC 9457 itself recommends.

`RequestValidation`'s own `ValidationResult.Invalid` carries nothing but a flat list of reason strings —
no field name, no path. [io.github.ilyankin.rfc9457.ktor.validation.invalidField] and
[io.github.ilyankin.rfc9457.ktor.validation.invalidFields] encode a JSON Pointer alongside each message
so [io.github.ilyankin.rfc9457.ktor.validation.requestValidation] can decode it back into a proper
`errors[]` entry; a reason written any other way still works, it just degrades to a `detail`-only entry
with no `pointer`.

# Package io.github.ilyankin.rfc9457.ktor.validation

| Entry point | Role |
|---|---|
| [invalidField] | Inside `validate<T> { }`: fail with one pointer + detail pair. The pointer is a string, or a property reference the pointer is derived from. |
| [invalidFields] | Inside `validate<T> { }`: fail with more than one pointer + detail pair from the same body. Takes them as `vararg` pairs or as a `List`. |
| [jsonPointer] | Builds a pointer that is checked against the body type's serial descriptor, instead of written out by hand. |
| [requestValidation] | Inside `problemDetails { }`: register the `RequestValidationException` → `Problem` mapping. |
| [decodeValidationReason] | Reads a [invalidField]/[invalidFields]-encoded reason back into pointer + detail, for a mapping that does not go through `requestValidation`. |

Published for `jvm`, `js`, `wasmJs`, `linuxX64`, `linuxArm64`, `mingwX64`, `macosArm64`, `iosArm64`
and `iosSimulatorArm64`. Depend on the plain coordinates from `commonMain` and Gradle picks the
variant.

## Usage

```kotlin
install(RequestValidation) {
    validate<Customer> { customer ->
        if (customer.age > 0) ValidationResult.Valid
        else invalidField("#/age", "must be a positive integer")
    }
}

install(StatusPages) {
    problemDetails {
        requestValidation(ValidationError)
    }
}
```

## When a plain exception is the better tool

This module exists to bridge `RequestValidation`'s reason strings, which carry nothing but text, to
`errors[]`. If you are not tied to that plugin, there is a shorter route to the same document with no
encoding in between: throw your own exception from inside `validate<T> { }` and map it with
`problem-details-ktor`'s `map<T> { }`. Ktor calls validators without a `try`, so the exception reaches
`StatusPages` untouched, carrying whatever typed payload you gave it.

```kotlin
class InvalidCustomer(val errors: List<FieldError>) : RuntimeException()

install(RequestValidation) {
    validate<Customer> { customer -> if (customer.isValid) ValidationResult.Valid else throw InvalidCustomer(...) }
}

install(StatusPages) {
    problemDetails {
        map<InvalidCustomer> { _, cause -> /* build errors[] from cause.errors, fully typed */ }
    }
}
```

What that gives up is aggregation. The plugin runs *every* validator registered for a body type and
concatenates their reasons; a thrown exception stops at the first one. With a single validator per
type — the usual arrangement — nothing is lost, since that validator already collects its own
findings before returning.

So reach for this module when you cannot leave the `ValidationResult` protocol: several validators on
one body type, validators written elsewhere, or a codebase already returning
`ValidationResult.Invalid` throughout. That is what it is good at, and it is a narrower claim than
"the way to do validation problems in Ktor".

## Pointers that cannot go stale

`"#/age"` written as a string is a claim about the request body that nothing checks. Rename the
property and the pointer keeps naming a member that is gone. [jsonPointer] resolves the path against
the body type's serial descriptor instead, so a path that no longer exists fails loudly:

```kotlin
invalidFields(
    jsonPointer(Customer::age) to "must be a positive integer",              // renaming `age` stops compiling
    jsonPointer<Customer>("profile", "color") to "must be 'green', 'red' or 'blue'",
)

invalidField(Customer::age, "must be a positive integer")                     // the same, for a single field
```

The property form is checked by the compiler; the string form is checked when the pointer is built,
segment by segment, and reports the members that do exist when one does not match. Segments are
escaped for you — `~` and `/` per RFC 6901 §3, then anything a URI fragment does not admit is
percent-encoded.

Two limits are worth knowing before you rely on the property form. A property renamed with
`@SerialName` cannot be resolved from the reference — mapping a Kotlin property to its serial name
needs `kotlin-reflect`, which this library does not use — so `jsonPointer(Customer::emailAddress)`
throws rather than guessing, and you name it as `jsonPointer<Customer>("email_address")` instead. And
a `JsonNamingStrategy` on the application's `Json` is invisible to the descriptor altogether; a
project using one should build pointers from strings and test them against a real response.

## What degrades, and how

A reason not produced by [invalidField]/[invalidFields] — a hand-written `ValidationResult.Invalid("text")`,
or the byte-array `validate { }` overload, which has no field to point at — is never dropped and never
throws. It becomes an `errors[]` entry with `detail` alone, no `pointer`.

## A custom errors[] entry

[requestValidation]'s entries are always a flat `{detail, pointer}` object, using RFC 9457's own example
member names. For anything else — other names, extra members, a typed object — bypass it and write the
`RequestValidationException` mapping directly, using [decodeValidationReason] to read what
[invalidField]/[invalidFields] encoded and
[io.github.ilyankin.rfc9457.encodeToProblemValue] (`problem-details-core`) to turn a typed value into a
[io.github.ilyankin.rfc9457.ProblemValue]:

```kotlin
@Serializable
data class ValidationErrorEntry(val code: String, val field: String? = null, val message: String)

problemDetails {
    map<RequestValidationException> { _, cause ->
        Problem(
            type = ValidationError.typeUri,
            status = ValidationError.status,
            title = ValidationError.title,
            extensions = mapOf(
                "errors" to ProblemArray(
                    cause.reasons.map { reason ->
                        val decoded = decodeValidationReason(reason)
                        Json.encodeToProblemValue(
                            ValidationErrorEntry(code = "VALIDATION_FAILED", field = decoded.pointer, message = decoded.detail),
                        )
                    },
                ),
            ),
        )
    }
}
```
