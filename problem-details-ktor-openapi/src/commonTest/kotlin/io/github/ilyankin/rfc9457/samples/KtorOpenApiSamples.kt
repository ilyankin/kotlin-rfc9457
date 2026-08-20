package io.github.ilyankin.rfc9457.samples

import io.github.ilyankin.rfc9457.ProblemType
import io.github.ilyankin.rfc9457.ktor.openapi.problemResponse
import io.github.ilyankin.rfc9457.ktor.openapi.problemResponses
import io.github.ilyankin.rfc9457.ktor.openapi.problemsFrom
import io.github.ilyankin.rfc9457.ktor.problemCatalog
import io.github.ilyankin.rfc9457.ktor.problemDetails
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.describe
import io.ktor.server.routing.routing
import io.ktor.utils.io.ExperimentalKtorApi

private object OutOfCredit : ProblemType {
    override val typeUri: String = "https://example.com/probs/out-of-credit"
    override val title: String = "You do not have enough credit."
    override val status: Int = 403
}

/** @see io.github.ilyankin.rfc9457.ktor.openapi.problemResponse */
@OptIn(ExperimentalKtorApi::class)
internal fun Application.problemResponseSample() {
    routing {
        get("/orders/{id}") {
            call.respondText("an order")
        }.describe {
            summary = "Fetch one order"
            responses {
                problemResponse(OutOfCredit)
            }
        }
    }
}

/** @see io.github.ilyankin.rfc9457.ktor.openapi.problemsFrom */
@OptIn(ExperimentalKtorApi::class)
internal fun Application.problemsFromCatalogSample() {
    val catalog =
        problemCatalog {
            standardStatusCodes()
            map<IllegalStateException>(OutOfCredit)
        }

    install(StatusPages) { problemDetails(catalog) }

    routing {
        describe {
            // The catch-all and the four standard status codes: true of every route below.
            responses { problemsFrom(catalog) }
        }

        get("/orders/{id}") {
            call.respondText("an order")
        }.describe {
            // `OutOfCredit` is named here, where it can actually occur, rather than everywhere.
            responses { problemResponse(OutOfCredit) }
        }
    }
}

/** @see io.github.ilyankin.rfc9457.ktor.openapi.problemResponses */
@OptIn(ExperimentalKtorApi::class)
internal fun Application.problemRoutesSample() {
    val catalog = problemCatalog { standardStatusCodes() }

    install(StatusPages) { problemDetails(catalog) }

    routing {
        // One call at the root: every endpoint below now documents the catch-all and the catalog's
        // status codes, without a line per route.
        problemResponses(catalog)

        get("/orders/{id}") {
            call.respondText("an order")
        }.describe {
            responses { problemResponse(OutOfCredit) }
        }
    }
}
