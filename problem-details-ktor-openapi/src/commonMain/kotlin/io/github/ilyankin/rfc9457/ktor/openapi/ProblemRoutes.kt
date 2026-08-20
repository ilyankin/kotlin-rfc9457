package io.github.ilyankin.rfc9457.ktor.openapi

import io.github.ilyankin.rfc9457.ktor.ProblemDetailsCatalog
import io.ktor.openapi.Response
import io.ktor.server.routing.Route
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

/**
 * Documents [catalog]'s universally-applicable responses on this route and everything below it.
 *
 * Ktor merges route metadata from the routing root downwards, so a call inside `routing { }` covers
 * every endpoint in the application, and a call on a `route("/api")` covers that subtree. Metadata
 * added deeper merges with this rather than replacing it.
 *
 * Which responses those are, and why domain types are excluded, is [problemsFrom]'s business.
 *
 * This wraps `Route.describe`, which Ktor marks experimental; callers need no opt-in of their own,
 * but the underlying API may change within a 3.x release.
 *
 * @return this route, for chaining.
 * @sample io.github.ilyankin.rfc9457.samples.problemRoutesSample
 */
@OptIn(ExperimentalKtorApi::class)
public fun Route.problemResponses(
    catalog: ProblemDetailsCatalog,
    configure: Response.Builder.() -> Unit = {},
): Route = describe { responses { problemsFrom(catalog, configure) } }
