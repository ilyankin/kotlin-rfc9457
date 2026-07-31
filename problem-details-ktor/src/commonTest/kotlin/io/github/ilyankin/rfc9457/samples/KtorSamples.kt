package io.github.ilyankin.rfc9457.samples

import io.github.ilyankin.rfc9457.ktor.problemDetails
import io.github.ilyankin.rfc9457.ktor.problemJson
import io.github.ilyankin.rfc9457.ktor.respondProblem
import io.github.ilyankin.rfc9457.problem
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

internal class OrderNotFound(
    val id: String,
) : RuntimeException("No order $id")

/** @see io.github.ilyankin.rfc9457.ktor.respondProblem */
internal fun Application.respondProblemSample() {
    routing {
        get("/orders/{id}") {
            // `status` decides the HTTP status, and `instance` is filled from the request path.
            call.respondProblem(
                problem {
                    status = 404
                    title = "Order not found."
                    detail = "No order with that id exists."
                },
            )
        }
    }
}

/** @see io.github.ilyankin.rfc9457.ktor.problemDetails */
internal fun Application.problemDetailsSample() {
    install(ContentNegotiation) { problemJson() }

    install(StatusPages) {
        problemDetails {
            // 404, 405 and the rest of Ktor's own status responses become problem documents.
            standardStatusCodes()

            map<OrderNotFound> { _, cause ->
                problem {
                    type = "https://example.com/probs/order-not-found"
                    status = 404
                    title = "Order not found."
                    detail = "No order ${cause.id} exists."
                }
            }

            // Anything not mapped above, including exceptions thrown by other plugins.
            onUnmapped { _, _ ->
                problem {
                    status = 500
                    title = "Internal Server Error"
                }
            }
        }
    }
}
