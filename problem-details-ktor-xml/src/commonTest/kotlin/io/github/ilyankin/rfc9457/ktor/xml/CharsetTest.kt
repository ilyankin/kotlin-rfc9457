package io.github.ilyankin.rfc9457.ktor.xml

import io.github.ilyankin.rfc9457.Problem
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.charset
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.charsets.name

/**
 * An XML document states its own encoding in-band, which JSON does not, so "whatever charset was
 * negotiated" is not a safe answer for this converter the way it is for `ProblemJsonConverter`.
 *
 * Before this was pinned, `Accept-Charset: ISO-8859-1` produced latin-1 bytes inside a document whose
 * declaration read `encoding="UTF-8"` — `café` arrived as `caf?` to anything parsing the bytes
 * without the HTTP header. The header and the declaration contradicted each other outright.
 */
class CharsetTest :
    StringSpec({

        val accented = "café — naïve"

        "the response is UTF-8 even when another charset was negotiated" {
            testApplication {
                install(ContentNegotiation) { problemXml() }
                routing { get("/p") { call.respond(Problem(status = 400, detail = accented)) } }

                val response =
                    client.get("/p") {
                        header(HttpHeaders.Accept, "application/problem+xml")
                        header(HttpHeaders.AcceptCharset, "ISO-8859-1")
                    }

                ContentType.parse(response.headers[HttpHeaders.ContentType]!!).charset()?.name shouldBe "UTF-8"
            }
        }

        "the bytes on the wire agree with the declaration inside them" {
            testApplication {
                install(ContentNegotiation) { problemXml() }
                routing { get("/p") { call.respond(Problem(status = 400, detail = accented)) } }

                val response =
                    client.get("/p") {
                        header(HttpHeaders.Accept, "application/problem+xml")
                        header(HttpHeaders.AcceptCharset, "ISO-8859-1")
                    }

                // Decoded as UTF-8 deliberately, not through bodyAsText(), which would consult the
                // header and so could not tell a lying header from an honest one. This asserts the
                // bytes themselves are UTF-8.
                val decoded = response.bodyAsBytes().decodeToString()
                decoded shouldStartWith """<?xml version="1.0" encoding="UTF-8"?>"""
                decoded shouldContain "<detail>$accented</detail>"
            }
        }

        "the document the client receives is one this codec can read back" {
            testApplication {
                install(ContentNegotiation) { problemXml() }
                val problem = Problem(status = 400, detail = accented)
                routing { get("/p") { call.respond(problem) } }

                val response = client.get("/p") { header(HttpHeaders.Accept, "application/problem+xml") }
                io.github.ilyankin.rfc9457.xml.ProblemXml
                    .decodeFromString(response.bodyAsText()) shouldBe problem
            }
        }

        "a request body is decoded as UTF-8, whatever charset it claims" {
            // The other half of the guarantee. `readString()` has always decoded UTF-8 and the
            // `charset` parameter has never been consulted; pinning it keeps the two formats from
            // disagreeing about the same request.
            testApplication {
                install(ContentNegotiation) { problemXml() }
                routing {
                    post("/p") { call.respondText(call.receive<Problem>().detail.orEmpty()) }
                }

                val body =
                    """<?xml version="1.0" encoding="ISO-8859-1"?>""" +
                        """<problem xmlns="urn:ietf:rfc:7807"><detail>$accented</detail></problem>"""

                val response =
                    client.post("/p") {
                        header(HttpHeaders.ContentType, "application/problem+xml; charset=ISO-8859-1")
                        setBody(body.encodeToByteArray())
                    }

                response.bodyAsText() shouldBe accented
            }
        }
    })
