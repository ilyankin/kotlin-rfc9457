package io.github.ilyankin.rfc9457.ktor

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.engine.defaultExceptionStatusCode
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import kotlin.coroutines.cancellation.CancellationException

/**
 * Registers a [ProblemDetailsCatalog] against Ktor's own `StatusPages` configuration.
 *
 * Each catalog entry becomes one `exception(...)`/`status(...)` registration and nothing more: the
 * library generates the calls a developer would otherwise hand-write, so dispatch stays Ktor's.
 *
 * @sample io.github.ilyankin.rfc9457.samples.problemDetailsSample
 */
public fun StatusPagesConfig.problemDetails(configure: ProblemDetailsCatalog.() -> Unit) {
    val catalog = ProblemDetailsCatalog().apply(configure)

    // Registered *before* the catalog's own entries — order matters for exactly one class.
    // `StatusPagesConfig.exceptions` is a map keyed by class, so two registrations of Throwable
    // collide and the later one wins; registering this one first lets `map<Throwable>` replace it
    // instead of being silently swallowed by it.
    exception<Throwable> { call, cause ->
        // Cancellation means the client disconnected — answering it writes a document nobody reads
        // and logs a stack trace per dropped connection. Rethrowing hands it back to the engine,
        // which treats it as transport noise (`logFailure` sends CancellationException to `debug`).
        // TimeoutCancellationException is the one cancellation that *is* a real status (504);
        // `defaultExceptionStatusCode` tells the two apart — the same table `unmapped` trusts.
        // A `map<Throwable>` entry replaces this handler wholesale, guard included.
        if (cause is CancellationException && defaultExceptionStatusCode(cause) == null) throw cause

        val problem = catalog.unmapped(call, cause)
        // Full cause logged server-side; only a terse document reaches the client (§5). Severity
        // follows the status rather than a flat `error` — a 4xx is the client's fault, not the
        // server's, mirroring how Ktor's own `logFailure` sends 4xx exceptions to `debug`.
        val message = "Unhandled exception; responding with a problem document"
        if ((problem.status ?: HttpStatusCode.InternalServerError.value) >= 500) {
            call.application.log.error(message, cause)
        } else {
            call.application.log.debug(message, cause)
        }
        call.respondProblem(problem)
    }

    catalog.exceptionMappings.forEach { (klass, toProblem) ->
        exception(klass) { call, cause -> call.respondProblem(toProblem(call, cause)) }
    }

    catalog.statusMappings.forEach { (code, provider) ->
        // Explicit parameter types pick the plain (call, code) overload over the StatusContext one.
        status(code) { call: ApplicationCall, _: HttpStatusCode ->
            call.respondProblem(code, provider(call))
        }
    }
}
