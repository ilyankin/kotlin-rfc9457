package io.github.ilyankin.rfc9457.ktor.validation

import io.github.ilyankin.rfc9457.ProblemArray
import io.github.ilyankin.rfc9457.ProblemObject
import io.github.ilyankin.rfc9457.ProblemPrimitive
import io.github.ilyankin.rfc9457.ProblemType
import io.github.ilyankin.rfc9457.ktor.ProblemDetailsCatalog
import io.github.ilyankin.rfc9457.problem
import io.ktor.server.plugins.requestvalidation.RequestValidationException

/**
 * Maps Ktor's `RequestValidation` plugin failures to [type], carrying every validation reason as one
 * entry of an `errors[]` extension array. This is the multi-error pattern RFC 9457 itself recommends
 * for field-level validation failures.
 *
 * A reason produced by [invalidField]/[invalidFields] decodes into `{"detail": ..., "pointer": ...}`,
 * RFC 9457's own example member names. Any other reason, a plain `ValidationResult.Invalid("text")`
 * or the byte-array `validate { }` overload with no field to point at, decodes into `{"detail": ...}`
 * alone, without `pointer`.
 *
 * The entry shape is fixed. For a different one — other member names, extra members, a typed object —
 * write the `RequestValidationException` mapping directly and read each reason with
 * [decodeValidationReason].
 *
 * @param type the problem type this catalog answers `RequestValidationException` with. No default:
 *   the type URI is application-specific.
 * @sample io.github.ilyankin.rfc9457.samples.requestValidationSample
 */
public fun ProblemDetailsCatalog.requestValidation(type: ProblemType) {
    map<RequestValidationException> { _, cause ->
        type.problem().copy(extensions = mapOf("errors" to cause.reasons.toErrorsArray()))
    }
}

private fun List<String>.toErrorsArray(): ProblemArray =
    ProblemArray(
        map { reason ->
            val (pointer, detail) = decodeValidationReason(reason)
            val members: Map<String, ProblemPrimitive> =
                if (pointer != null) {
                    mapOf("detail" to ProblemPrimitive(detail), "pointer" to ProblemPrimitive(pointer))
                } else {
                    mapOf("detail" to ProblemPrimitive(detail))
                }
            ProblemObject(members)
        },
    )
