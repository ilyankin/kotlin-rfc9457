package io.github.ilyankin.rfc9457.ktor.validation

import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlin.reflect.KProperty1

/**
 * The character separating a JSON Pointer (RFC 6901) from its message inside one `RequestValidation`
 * reason string. That reason string is the only channel the plugin exposes for a validator to say
 * anything at all. `U+0000` never appears in ordinary validation text, so it survives round-tripping
 * intact.
 *
 * Written as an escape, never as the byte itself: a literal `U+0000` in a source file makes git and
 * every grep treat that file as binary.
 */
private const val POINTER_SEPARATOR: Char = '\u0000'

/**
 * Builds a [ValidationResult.Invalid] that [requestValidation] decodes into one `errors[]` entry
 * carrying both [pointer] and [detail].
 *
 * A plain `ValidationResult.Invalid("text")`, written without this helper, still works. It just
 * decodes to a `detail`-only entry, with no `pointer`.
 *
 * @param pointer a JSON Pointer (RFC 6901), typically its URI fragment form, e.g. `"#/age"`,
 *   identifying the invalid member of the request body. Written to the wire exactly as given.
 * @param detail human-readable explanation of what is wrong with [pointer].
 * @throws IllegalArgumentException if [pointer] or [detail] contains `U+0000`.
 * @see io.github.ilyankin.rfc9457.ktor.validation.requestValidation
 */
public fun invalidField(
    pointer: String,
    detail: String,
): ValidationResult.Invalid = ValidationResult.Invalid(encode(pointer, detail))

/**
 * Builds a [ValidationResult.Invalid] for [property] of the request body, deriving its pointer with
 * [jsonPointer] rather than taking one written by hand. Renaming the property is then a compile
 * error, not a pointer that quietly names a member that is gone.
 *
 * For a nested member, or one that `@SerialName` renames, pass `jsonPointer<T>(...)`'s result to the
 * [String] overload instead — [jsonPointer] explains why the property reference cannot cover those.
 *
 * @param property the invalid member of the request body, referenced on [T].
 * @param detail human-readable explanation of what is wrong with [property].
 * @throws IllegalArgumentException if [T]'s serialized form has no member under the property's name,
 *   or if [detail] contains `U+0000`.
 * @sample io.github.ilyankin.rfc9457.samples.typedPointerSample
 */
public inline fun <reified T> invalidField(
    property: KProperty1<T, *>,
    detail: String,
): ValidationResult.Invalid = invalidField(jsonPointer(property), detail)

/**
 * Builds one [ValidationResult.Invalid] carrying one `errors[]` entry per pair in [fields]. Use this
 * for a validator that finds more than one invalid member in the same request body, RFC 9457's own
 * recommended shape for multi-field validation.
 *
 * @param fields pointer-to-detail pairs, in the same sense as [invalidField]'s parameters.
 * @throws IllegalArgumentException if [fields] is empty, or if any pointer or detail in it contains
 *   `U+0000`.
 * @see io.github.ilyankin.rfc9457.ktor.validation.requestValidation
 */
public fun invalidFields(vararg fields: Pair<String, String>): ValidationResult.Invalid =
    invalidFields(fields.asList())

/**
 * The [List] form of [invalidFields], for a validator that accumulates its findings before returning
 * rather than knowing them all at the call site.
 *
 * @param fields pointer-to-detail pairs, in the same sense as [invalidField]'s parameters.
 * @throws IllegalArgumentException if [fields] is empty, or if any pointer or detail in it contains
 *   `U+0000`.
 * @see io.github.ilyankin.rfc9457.ktor.validation.requestValidation
 */
public fun invalidFields(fields: List<Pair<String, String>>): ValidationResult.Invalid {
    // An empty `Invalid` still fails the request, but produces `"errors": []` — a refusal that names
    // no reason. A validator with nothing to report should return `ValidationResult.Valid`.
    require(fields.isNotEmpty()) { "fields must not be empty; return ValidationResult.Valid instead" }
    return ValidationResult.Invalid(fields.map { (pointer, detail) -> encode(pointer, detail) })
}

private fun encode(
    pointer: String,
    detail: String,
): String {
    pointer.requireNoSeparator("pointer")
    detail.requireNoSeparator("detail")
    return "$pointer$POINTER_SEPARATOR$detail"
}

/** Reports the offending index rather than the string: echoing it back would put `U+0000` in a log. */
private fun String.requireNoSeparator(role: String) {
    val at = indexOf(POINTER_SEPARATOR)
    require(at < 0) { "$role must not contain U+0000, found one at index $at" }
}

/**
 * One decoded `RequestValidation` reason.
 *
 * @property pointer `null` when the reason was not produced by [invalidField]/[invalidFields].
 * @property detail human-readable explanation of what is wrong, same as passed to [invalidField].
 */
public data class ValidationReason(
    public val pointer: String?,
    public val detail: String,
)

/**
 * Decodes one `RequestValidationException` reason. This is what [requestValidation] does internally
 * for every entry of `errors[]`. It is exposed so a mapping that needs a shape [requestValidation]
 * cannot produce — a typed `errors[]` element, differently named members, an extra member per entry —
 * can still read [ValidationReason.pointer]/[ValidationReason.detail] back out of a reason built by
 * [invalidField]/[invalidFields], instead of re-inventing the encoding.
 *
 * @see io.github.ilyankin.rfc9457.ktor.validation.requestValidation
 * @sample io.github.ilyankin.rfc9457.samples.customErrorEntrySample
 */
public fun decodeValidationReason(reason: String): ValidationReason {
    val separatorIndex = reason.indexOf(POINTER_SEPARATOR)
    return if (separatorIndex >= 0) {
        ValidationReason(
            pointer = reason.substring(0, separatorIndex),
            detail = reason.substring(separatorIndex + 1),
        )
    } else {
        ValidationReason(pointer = null, detail = reason)
    }
}
