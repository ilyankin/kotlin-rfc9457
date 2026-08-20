package io.github.ilyankin.rfc9457.ktor

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.log
import io.ktor.server.engine.defaultExceptionStatusCode
import io.ktor.server.plugins.statuspages.StatusPagesConfig
import kotlin.coroutines.cancellation.CancellationException

/**
 * Builds a [ProblemDetailsCatalog] as a standalone value.
 *
 * Use this when something besides `StatusPages` needs the catalog — documenting it, for instance.
 * `install(StatusPages) { problemDetails { } }` remains the shorter form when nothing does.
 */
public fun problemCatalog(configure: ProblemDetailsCatalog.() -> Unit): ProblemDetailsCatalog =
    ProblemDetailsCatalog().apply(configure)

/**
 * Registers a [ProblemDetailsCatalog] against Ktor's own `StatusPages` configuration.
 *
 * Each catalog entry becomes one `exception(...)`/`status(...)` registration and nothing more: the
 * library generates the calls a developer would otherwise hand-write, so dispatch stays Ktor's.
 *
 * @sample io.github.ilyankin.rfc9457.samples.problemDetailsSample
 */
public fun StatusPagesConfig.problemDetails(configure: ProblemDetailsCatalog.() -> Unit) {
    problemDetails(problemCatalog(configure))
}

/**
 * Registers an already-built [catalog] against Ktor's own `StatusPages` configuration.
 *
 * The counterpart of [problemCatalog]: build the catalog once, install it here and hand the same
 * value to whatever else needs to read it.
 */
public fun StatusPagesConfig.problemDetails(catalog: ProblemDetailsCatalog) {
    // `StatusPagesConfig.exceptions` is keyed by class, so two Throwable registrations collide and
    // the later one wins. Registered first, before the catalog's, so `map<Throwable>` can replace it.
    exception<Throwable> { call, cause ->
        // A plain cancellation is a client disconnect: answering it writes a document nobody reads.
        // Rethrowing hands it back to the engine as transport noise. TimeoutCancellationException is
        // the one cancellation that is a real status (504), and `defaultExceptionStatusCode`, the
        // table `unmapped` also trusts, is what tells the two apart.
        if (cause is CancellationException && defaultExceptionStatusCode(cause) == null) throw cause

        val problem = catalog.unmapped(call, cause)
        // Full cause server-side, terse document to the client (§5). Severity follows the status: a
        // 4xx is the client's fault, so it goes to `debug`, not `error`.
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
