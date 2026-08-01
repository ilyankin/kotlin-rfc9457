package io.github.ilyankin.rfc9457.ktor

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.github.ilyankin.rfc9457.ProblemException
import io.github.ilyankin.rfc9457.problem
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.routing.get
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger
import ch.qos.logback.classic.Logger as LogbackLogger

private val loggerSeq = AtomicInteger()

/**
 * A logger of its own, wired to a list appender and detached from the root one.
 *
 * Handed to the application through `environment { log = … }`, so this records exactly what
 * `call.application.log` received — no global state, and specs stay independent under parallel runs.
 */
private class CapturedLog {
    private val appender = ListAppender<ILoggingEvent>().apply { start() }

    val logger: LogbackLogger =
        (LoggerFactory.getLogger("problem-details-capture-${loggerSeq.incrementAndGet()}") as LogbackLogger)
            .apply {
                level = Level.TRACE
                isAdditive = false
                addAppender(appender)
            }

    /** Events carrying a throwable — the startup chatter Ktor emits carries none. */
    val withCause: List<ILoggingEvent> get() = appender.list.filter { it.throwableProxy != null }
}

private fun ApplicationTestBuilder.installProblemJsonForCapture() {
    install(ContentNegotiation) {
        register(ContentType.parse("application/problem+json"), ProblemJsonConverter())
    }
}

class ProblemLoggingTest :
    StringSpec({

        "a ProblemException carrying a 5xx logs its cause at ERROR" {
            val capture = CapturedLog()
            testApplication {
                environment { log = capture.logger }
                installProblemJsonForCapture()
                install(StatusPages) { problemDetails { } }
                routing {
                    get("/boom") {
                        throw ProblemException(
                            problem { status = 500 },
                            cause = IllegalStateException("jdbc://secret@host/db"),
                        )
                    }
                }
                client.get("/boom").status shouldBe HttpStatusCode.InternalServerError
            }

            val event = capture.withCause.single()
            event.level shouldBe Level.ERROR
            event.throwableProxy.className shouldBe "java.lang.IllegalStateException"
            event.throwableProxy.message shouldBe "jdbc://secret@host/db"
        }

        "a ProblemException carrying a 4xx logs its cause at DEBUG" {
            val capture = CapturedLog()
            testApplication {
                environment { log = capture.logger }
                installProblemJsonForCapture()
                install(StatusPages) { problemDetails { } }
                routing {
                    get("/pay") {
                        throw ProblemException(
                            problem { status = 403 },
                            cause = IllegalArgumentException("balance too low"),
                        )
                    }
                }
                client.get("/pay").status shouldBe HttpStatusCode.Forbidden
            }

            // A 4xx is the client's fault; only the operator troubleshooting one asks for it.
            val event = capture.withCause.single()
            event.level shouldBe Level.DEBUG
            event.throwableProxy.className shouldBe "java.lang.IllegalArgumentException"
        }

        "a ProblemException carrying no status logs at ERROR, like the 500 it answers with" {
            val capture = CapturedLog()
            testApplication {
                environment { log = capture.logger }
                installProblemJsonForCapture()
                install(StatusPages) { problemDetails { } }
                routing {
                    get("/boom") {
                        throw ProblemException(
                            problem { title = "Something went wrong." },
                            cause = IllegalStateException("no status on the document"),
                        )
                    }
                }
                client.get("/boom").status shouldBe HttpStatusCode.InternalServerError
            }

            capture.withCause.single().level shouldBe Level.ERROR
        }

        "a ProblemException with no cause logs nothing" {
            val capture = CapturedLog()
            testApplication {
                environment { log = capture.logger }
                installProblemJsonForCapture()
                install(StatusPages) { problemDetails { } }
                routing { get("/boom") { throw ProblemException(problem { status = 500 }) } }
                client.get("/boom").status shouldBe HttpStatusCode.InternalServerError
            }

            // The document already describes the outcome completely; there is no root cause to keep.
            capture.withCause.shouldBeEmpty()
        }

        "an unmapped exception is logged in full at ERROR even though the document says nothing" {
            val capture = CapturedLog()
            testApplication {
                environment { log = capture.logger }
                installProblemJsonForCapture()
                install(StatusPages) { problemDetails { } }
                routing { get("/boom") { throw IllegalStateException("jdbc://secret@host/db") } }
                client.get("/boom").status shouldBe HttpStatusCode.InternalServerError
            }

            // §5 keeps the message out of the response, so the log is the only place it survives.
            val event = capture.withCause.single()
            event.level shouldBe Level.ERROR
            event.throwableProxy.message shouldBe "jdbc://secret@host/db"
        }

        "an unmapped exception that resolves to a 4xx is logged at DEBUG" {
            val capture = CapturedLog()
            testApplication {
                environment { log = capture.logger }
                installProblemJsonForCapture()
                install(StatusPages) { problemDetails { } }
                routing { get("/x") { throw NotFoundException("nope") } }
                client.get("/x").status shouldBe HttpStatusCode.NotFound
            }

            capture.withCause.single().level shouldBe Level.DEBUG
        }
    })
