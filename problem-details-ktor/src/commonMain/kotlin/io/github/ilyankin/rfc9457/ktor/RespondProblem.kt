package io.github.ilyankin.rfc9457.ktor

import io.github.ilyankin.rfc9457.Problem
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.request.path
import io.ktor.server.request.uri
import io.ktor.server.response.respond

/**
 * Responds with [problem], the problem itself deciding the HTTP status.
 *
 * A `null` status is treated as 500 and written back into the body, so the response never disagrees
 * with its own document. RFC 9457 §3.1 calls the member advisory only, but still requires
 * generators to make it match the actual HTTP status.
 *
 * @sample io.github.ilyankin.rfc9457.samples.respondProblemSample
 */
public suspend fun ApplicationCall.respondProblem(problem: Problem) {
    val status = problem.status?.let { HttpStatusCode.fromValue(it) } ?: HttpStatusCode.InternalServerError
    respondProblem(status, problem)
}

/**
 * Responds with [problem] at [status], the *response* deciding the status.
 *
 * The form the `StatusPages` status path needs, where the status is already chosen and only the
 * body is being swapped in. A provider that hardcoded a different `status` gets corrected here
 * instead of shipping the self-contradictory document §3.1 forbids.
 *
 * [Problem.instance] is filled from the request path when left unset. §3.1 defines the member as
 * identifying this occurrence, which the path does at no cost. An instance the caller already set
 * is never overwritten.
 *
 * **The query string is deliberately excluded** (`path()`, not `uri()`). A problem document gets
 * logged and forwarded on both ends, and §5 warns against putting anything in one the recipient
 * shouldn't have. Query strings carry tokens and search terms often enough that echoing them by
 * default is the wrong trade. Set [Problem.instance] explicitly for the full target.
 */
public suspend fun ApplicationCall.respondProblem(
    status: HttpStatusCode,
    problem: Problem,
) {
    // Both flags mirror the engine's own `tryRespondError` guard: `isCommitted` means headers are
    // already gone, `isSent` that the whole body is.
    if (response.isCommitted || response.isSent) {
        // Nothing left to write. Throwing here would replace a partially sent response with an
        // exception the engine can no longer report, so a warning is the honest outcome.
        application.log.warn(
            "Response already committed; not writing a problem document for ${request.uri} $status",
        )
        return
    }
    respond(status, problem.copy(status = status.value, instance = problem.instance ?: request.path()))
}
