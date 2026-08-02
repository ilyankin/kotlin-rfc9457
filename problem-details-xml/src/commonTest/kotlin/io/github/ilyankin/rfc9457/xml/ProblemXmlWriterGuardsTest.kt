package io.github.ilyankin.rfc9457.xml

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ProblemArray
import io.github.ilyankin.rfc9457.ProblemObject
import io.github.ilyankin.rfc9457.ProblemPrimitive
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private fun encode(
    name: String,
    value: String = "v",
) = ProblemXml.encodeToString(Problem(extensions = mapOf(name to ProblemPrimitive(value))))

/**
 * What the writer refuses, and with which exception type.
 *
 * All three groups exist because XML constrains things JSON does not, and because `Problem`
 * deliberately does not enforce RFC 9457 §3.2's naming `SHOULD` — so a problem that is perfectly
 * valid for `application/problem+json` can be unrepresentable as `application/problem+xml`. That
 * asymmetry is the specified behaviour; these tests pin where it surfaces.
 */
class ProblemXmlWriterGuardsTest :
    StringSpec({

        // ---- Extension member names must be XML NCNames -------------------------------------

        "an extension name that is not an XML name is rejected rather than written verbatim" {
            listOf(
                "account balance", // space
                "1abc", // starts with a digit
                "", // empty
                "x:y", // colon: would be read as a namespace prefix bound to nothing
                "xml:lang", // same, and a reserved prefix at that
                "a\"b", // quote
                "<evil>", // angle brackets
            ).forEach { name ->
                shouldThrow<SerializationException> { encode(name) }
            }
        }

        "a hostile name cannot inject elements into the document" {
            // The finding the guard exists for: written verbatim, this name closed the element it
            // was opening and opened two of its own — in the end tag as well as the start tag.
            val failure =
                shouldThrow<SerializationException> {
                    encode("a><injected>owned</injected><b")
                }
            failure.message shouldContain "cannot be an XML element name"
        }

        "the same injection is refused when the problem arrived as JSON" {
            // The realistic path: a proxy or the content-negotiation layer reads someone else's
            // JSON document and re-emits it as XML. Core still accepting the name is asserted here
            // too, so a later change to core cannot make this test pass for the wrong reason.
            val fromJson =
                Json.decodeFromString<Problem>("""{"type":"about:blank","a><evil/><b":"v"}""")
            fromJson.extensions.keys shouldBe setOf("a><evil/><b")
            shouldThrow<SerializationException> { ProblemXml.encodeToString(fromJson) }
        }

        "a name nested inside an object or an array is checked too" {
            shouldThrow<SerializationException> {
                ProblemXml.encodeToString(
                    Problem(extensions = mapOf("ok" to ProblemObject(mapOf("bad name" to ProblemPrimitive("v"))))),
                )
            }
            shouldThrow<SerializationException> {
                ProblemXml.encodeToString(
                    Problem(
                        extensions =
                            mapOf(
                                "ok" to
                                    ProblemArray(
                                        listOf(ProblemObject(mapOf("bad name" to ProblemPrimitive("v")))),
                                    ),
                            ),
                    ),
                )
            }
        }

        "a valid XML name is accepted, including ones RFC 9457 §3.2 only advises against" {
            listOf("balance", "ab", "_x", "a-b.c", "x1", "тип").forEach { name ->
                shouldNotThrowAny { encode(name) }
            }
        }

        "a non-ASCII name really is written, not merely accepted" {
            encode("тип") shouldContain "<тип>v</тип>"
        }

        // ---- Character content XML cannot carry ----------------------------------------------

        "U+0000 is rejected, in a standard member and in an extension alike" {
            // Before this check the byte went into the document, which xmlutil's own reader then
            // read back happily while every other parser rejected it.
            shouldThrow<SerializationException> {
                ProblemXml.encodeToString(Problem(detail = "a\u0000b"))
            }
            shouldThrow<SerializationException> { encode("note", "a\u0000b") }
        }

        "an unpaired surrogate is rejected rather than crashing the writer" {
            // The high half crashed xmlutil outright; the low half was refused, but as a raw
            // IllegalArgumentException.
            shouldThrow<SerializationException> {
                ProblemXml.encodeToString(Problem(detail = "\uD800"))
            }
            shouldThrow<SerializationException> {
                ProblemXml.encodeToString(Problem(detail = "\uDC00"))
            }
            shouldThrow<SerializationException> {
                ProblemXml.encodeToString(Problem(detail = "a\uD800b"))
            }
        }

        "a well-formed surrogate pair survives a round trip" {
            // The guard must reject unpaired halves without touching real astral characters.
            val problem = Problem(detail = "locked 🔒 tight")
            ProblemXml.decodeFromString(ProblemXml.encodeToString(problem)) shouldBe problem
        }

        // ---- Everything the writer refuses leaves as one exception type ----------------------

        "a character xmlutil rejects surfaces as SerializationException, not xmlutil's own type" {
            // Not one of the two cases guarded above: 0x1F is xmlutil's to reject, and the point
            // here is only that its IllegalArgumentException does not escape — catching a write
            // failure must not require xmlutil on the caller's classpath.
            val failure =
                shouldThrow<SerializationException> {
                    ProblemXml.encodeToString(Problem(detail = "a\u001Fb"))
                }
            failure.cause.shouldBeInstanceOf<IllegalArgumentException>()
        }

        "the codec's own refusals are not re-wrapped in themselves" {
            // A null cause is the observable form of the writer's catch ordering: lose it and the
            // guard's own exception gets wrapped in itself, growing the chain by a link.
            val failure = shouldThrow<SerializationException> { encode("bad name") }
            failure.cause shouldBe null
        }
    })
