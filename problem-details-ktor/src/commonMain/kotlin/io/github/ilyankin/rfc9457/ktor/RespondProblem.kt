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
 * A `null` status is treated as 500 and written back into the body, so a problem document never
 * ships with a `status` that disagrees with its own response: RFC 9457 §3.1 calls the member
 * advisory only and requires that generators MUST make it match the actual HTTP response status.
 */
public suspend fun ApplicationCall.respondProblem(problem: Problem) {
    val status = problem.status?.let { HttpStatusCode.fromValue(it) } ?: HttpStatusCode.InternalServerError
    respondProblem(status, problem)
}

/**
 * Responds with [problem] at [status], the *response* deciding the status.
 *
 * This is the form the `StatusPages` status path needs, where the status was already chosen by
 * whoever built the response and only the body is being swapped. A provider that hardcoded a
 * different `status` gets it corrected here rather than emitting the self-contradictory document
 * RFC §3.1 forbids.
 *
 * [Problem.instance] is filled from the request path when the problem left it unset — §3.1 defines
 * the member as identifying this specific occurrence, which the request path does at no cost to the
 * caller. An instance the caller did set is never overwritten.
 *
 * The **query string is deliberately excluded** (`path()`, not `uri()`). A problem document is
 * written for a client to read and is routinely logged and forwarded on both ends, and §5 warns
 * against putting anything into one that the recipient should not have; query strings carry API
 * keys, tokens and search terms often enough that echoing them back by default is the wrong trade.
 * A caller who wants the full target can always set [Problem.instance] explicitly.
 */
public suspend fun ApplicationCall.respondProblem(
    status: HttpStatusCode,
    problem: Problem,
) {
    // Both flags, matching the engine's own `tryRespondError` guard: `isCommitted` means the status
    // line and headers are gone, `isSent` that the whole body is.
    if (response.isCommitted || response.isSent) {
        // Nothing can be written anymore; throwing here would replace a partially sent response
        // with an exception the engine can no longer report. A warning is the honest outcome.
        application.log.warn(
            "Response already committed; not writing a problem document for {} {}",
            request.uri,
            status,
        )
        return
    }
    respond(status, problem.copy(status = status.value, instance = problem.instance ?: request.path()))
}
