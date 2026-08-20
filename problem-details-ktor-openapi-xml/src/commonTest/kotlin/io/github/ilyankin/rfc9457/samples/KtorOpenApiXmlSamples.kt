package io.github.ilyankin.rfc9457.samples

import io.github.ilyankin.rfc9457.ProblemType
import io.github.ilyankin.rfc9457.ktor.openapi.problemResponse
import io.github.ilyankin.rfc9457.ktor.openapi.xml.problemXmlContent
import io.ktor.server.application.Application
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

/** @see io.github.ilyankin.rfc9457.ktor.openapi.xml.problemXmlContent */
@OptIn(ExperimentalKtorApi::class)
internal fun Application.problemXmlContentSample() {
    routing {
        get("/orders/{id}") {
            call.respondText("an order")
        }.describe {
            responses {
                // One response, both media types: the application negotiates between them.
                problemResponse(OutOfCredit) { problemXmlContent() }
            }
        }
    }
}
