package io.github.ilyankin.rfc9457.ktor

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ProblemType
import io.github.ilyankin.rfc9457.problem
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.engine.defaultExceptionStatusCode
import kotlin.reflect.KClass

/**
 * A declarative catalog of exception-to-problem and status-to-problem mappings.
 *
 * Every entry becomes exactly one registration on Ktor's own `StatusPages` configuration; dispatch,
 * including nearest-parent-class resolution, stays `StatusPages`'s job. Obtain one through
 * [problemDetails].
 */
public class ProblemDetailsCatalog internal constructor() {
    internal val exceptionMappings: MutableMap<KClass<out Throwable>, (ApplicationCall, Throwable) -> Problem> =
        LinkedHashMap()

    internal val statusMappings: MutableMap<HttpStatusCode, (ApplicationCall) -> Problem> = LinkedHashMap()

    /**
     * The problem produced for an exception no mapping covers.
     *
     * `about:blank` with no `detail` by default — RFC 9457 §5 warns that a problem document reaches
     * the client, so it must not carry debugging information like a file path or SQL fragment.
     *
     * The *status* isn't a flat 500, though: this handler backs the catch-all `exception<Throwable>`
     * registration, which would otherwise short-circuit `defaultExceptionStatusCode` and turn, say,
     * a `NotFoundException` into a 500 just because this library was installed. Consulting that
     * table first keeps Ktor's own answers intact.
     */
    internal var unmapped: (ApplicationCall, Throwable) -> Problem = { _, cause ->
        Problem.blank(defaultExceptionStatusCode(cause) ?: HttpStatusCode.InternalServerError)
    }

    /**
     * Maps [T] to a problem built from the call and the exception.
     *
     * Mapping `Throwable` itself is legal and replaces the built-in catch-all wholesale, cancellation
     * guard included — a handler registered that way must rethrow `CancellationException` itself.
     * Prefer [onUnmapped], which keeps the guard.
     */
    public inline fun <reified T : Throwable> map(noinline toProblem: (ApplicationCall, T) -> Problem) {
        @Suppress("UNCHECKED_CAST")
        addMapping(T::class, toProblem as (ApplicationCall, Throwable) -> Problem)
    }

    /**
     * The non-inline half of [map]. Exists so that only this signature, and not the backing map,
     * is pinned into the public ABI by the inline call sites.
     */
    @PublishedApi
    internal fun addMapping(
        klass: KClass<out Throwable>,
        toProblem: (ApplicationCall, Throwable) -> Problem,
    ) {
        exceptionMappings[klass] = toProblem
    }

    /**
     * Maps [T] to [type], taking `detail` from the exception message by default.
     *
     * `title` is always [ProblemType.title] — §3.1 says it SHOULD stay constant across occurrences
     * of a type, so there's no per-call override. Localization, the one exception the RFC allows,
     * is out of scope for v1.
     */
    public inline fun <reified T : Throwable> map(
        type: ProblemType,
        // Not named `detail`: inside the builder lambda that name would collide with
        // ProblemBuilder.detail and fail to resolve as a function call.
        noinline detailFrom: (T) -> String? = { it.message },
    ) {
        map<T> { _, cause -> type.problem(detail = detailFrom(cause)) }
    }

    /**
     * Replaces the fallback described on [unmapped].
     *
     * The safe way to change catch-all behaviour: unlike `map<Throwable>` it only supplies the
     * document, so the surrounding handler still rethrows cancellation and still picks log severity
     * from the resulting status.
     */
    public fun onUnmapped(handler: (ApplicationCall, Throwable) -> Problem) {
        unmapped = handler
    }

    /**
     * Registers a problem body for one status code.
     *
     * Opt-in per code on purpose: `StatusPages`' `status` hook fires for *any* outgoing response
     * carrying that code, including a body the application built deliberately, and would replace it.
     */
    public fun forStatusCode(
        status: HttpStatusCode,
        provider: (ApplicationCall) -> Problem,
    ) {
        statusMappings[status] = provider
    }

    /**
     * Covers the codes Ktor itself generates without an explicit body: 404, 405, 406, 415.
     *
     * Still an explicit opt-in rather than a blanket 4xx/5xx registration — see [forStatusCode].
     */
    public fun standardStatusCodes() {
        listOf(
            HttpStatusCode.NotFound,
            HttpStatusCode.MethodNotAllowed,
            HttpStatusCode.NotAcceptable,
            HttpStatusCode.UnsupportedMediaType,
        ).forEach { code -> forStatusCode(code) { Problem.blank(code) } }
    }
}
