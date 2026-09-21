package io.github.ilyankin.rfc9457.ktor.openapi

/**
 * Marks API that depends on Ktor's experimental route-description API.
 *
 * Such declarations may need a source- or binary-incompatible change when Ktor stabilizes or
 * replaces the underlying API. Opting in accepts that narrower risk without weakening the stability
 * contract of the other problem-details modules.
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API wraps Ktor's experimental route-description API and may change with it.",
)
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
public annotation class ExperimentalProblemDetailsOpenApi
