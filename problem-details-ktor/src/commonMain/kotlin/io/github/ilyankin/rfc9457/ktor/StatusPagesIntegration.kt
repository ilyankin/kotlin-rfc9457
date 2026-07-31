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
 */
public fun StatusPagesConfig.problemDetails(configure: ProblemDetailsCatalog.() -> Unit) {
    val catalog = ProblemDetailsCatalog().apply(configure)

    // Registered *before* the catalog's own entries, and the order matters for exactly one class.
    // StatusPages resolves handlers by nearest parent class, so for every other class registration
    // order is irrelevant — but `StatusPagesConfig.exceptions` is a map keyed by class, so two
    // registrations of Throwable itself collide and the later one wins. Registering this one first
    // lets `map<Throwable>` replace it, instead of being silently swallowed by it.
    exception<Throwable> { call, cause ->
        // Cancellation is not a fault to report. Ktor cancels the call's coroutine when the client
        // disconnects, so answering it would mean writing a document nobody is left to read — and
        // logging a stack trace for every dropped connection. Rethrowing hands it back to the
        // engine, which classifies it as transport noise (see `logFailure` in DefaultEnginePipeline,
        // where CancellationException and IOException go to `debug`). TimeoutCancellationException
        // is the one cancellation that *is* a status (504); `defaultExceptionStatusCode` is what
        // tells the two apart, and it is the same table `unmapped` already trusts.
        //
        // A `map<Throwable>` entry replaces this handler wholesale — including this guard.
        if (cause is CancellationException && defaultExceptionStatusCode(cause) == null) throw cause

        val problem = catalog.unmapped(call, cause)
        // The cause is logged in full server-side; only a terse document reaches the client (§5).
        // The severity follows the status rather than being a flat `error`: a 4xx means the client
        // sent something wrong, which is ordinary traffic and not a server fault. This mirrors how
        // Ktor logs the same exceptions — BadRequestException and NotFoundException land in the
        // `debug` branch of its own `logFailure`.
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
