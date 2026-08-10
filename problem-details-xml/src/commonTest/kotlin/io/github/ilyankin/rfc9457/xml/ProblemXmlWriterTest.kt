package io.github.ilyankin.rfc9457.xml

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ProblemArray
import io.github.ilyankin.rfc9457.ProblemNull
import io.github.ilyankin.rfc9457.ProblemObject
import io.github.ilyankin.rfc9457.ProblemPrimitive
import io.github.ilyankin.rfc9457.ProblemValue
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.SerializationException

class ProblemXmlWriterTest :
    StringSpec({

        "the Appendix B example is reproduced exactly" {
            ProblemXml.encodeToString(XmlFixtures.outOfCreditProblem()) shouldBe
                XmlFixtures.OUT_OF_CREDIT_XML
        }

        "the root element is problem and the namespace is the 7807 one" {
            val xml = ProblemXml.encodeToString(Problem.blank(404, "Not Found"))
            xml shouldContain "<problem xmlns=\"urn:ietf:rfc:7807\">"
            xml shouldContain "</problem>"
        }

        "the namespace is never the 9457 one" {
            ProblemXml.encodeToString(Problem.blank(404, "Not Found")) shouldContain "urn:ietf:rfc:7807"
            ProblemXml.NAMESPACE shouldBe "urn:ietf:rfc:7807"
        }

        "status is written as element text" {
            ProblemXml.encodeToString(Problem.blank(404, "Not Found")) shouldContain "<status>404</status>"
        }

        "an integer extension is not decimalised" {
            ProblemXml.encodeToString(Problem(extensions = mapOf("balance" to ProblemPrimitive(30)))) shouldContain
                "<balance>30</balance>"
        }

        "a list extension uses i children" {
            val problem =
                Problem(
                    extensions =
                        mapOf(
                            "accounts" to ProblemArray(listOf(ProblemPrimitive("/a"), ProblemPrimitive("/b"))),
                        ),
                )
            ProblemXml.encodeToString(problem) shouldContain "<accounts><i>/a</i><i>/b</i></accounts>"
        }

        "a nested object extension becomes named child elements" {
            val problem =
                Problem(
                    extensions =
                        mapOf(
                            "errors" to
                                ProblemArray(
                                    listOf(
                                        ProblemObject(
                                            mapOf(
                                                "detail" to ProblemPrimitive("must be a positive integer"),
                                                "pointer" to ProblemPrimitive("#/age"),
                                            ),
                                        ),
                                    ),
                                ),
                        ),
                )
            ProblemXml.encodeToString(problem) shouldContain
                "<errors><i><detail>must be a positive integer</detail><pointer>#/age</pointer></i></errors>"
        }

        "null and empty collections collapse to an empty element" {
            val problem =
                Problem(
                    extensions =
                        mapOf(
                            "nothing" to ProblemNull,
                            "emptyList" to ProblemArray(emptyList()),
                            "emptyObject" to ProblemObject(emptyMap()),
                        ),
                )
            // Unspaced because the writer sets addTrailingSpaceBeforeEnd = false; xmlutil's default
            // would be `<nothing />`. Indistinguishable to a parser (the reader test pins that both
            // forms parse), so this only fixes which one we emit.
            val xml = ProblemXml.encodeToString(problem)
            xml shouldContain "<nothing/>"
            xml shouldContain "<emptyList/>"
            xml shouldContain "<emptyObject/>"
        }

        "writing past the depth limit fails with an exception, not a StackOverflowError" {
            // Bounded on the way out too. Such a Problem can only come from the caller's own code
            // now that reading caps at the same limit. A document this codec could not read back
            // should not be one it emits.
            var value: ProblemValue = ProblemPrimitive("leaf")
            repeat(Problem.MAX_NESTING_DEPTH + 5) { value = ProblemObject(mapOf("a" to value)) }
            shouldThrow<SerializationException> {
                ProblemXml.encodeToString(Problem(extensions = mapOf("deep" to value)))
            }
        }

        "text is escaped" {
            ProblemXml.encodeToString(Problem(detail = "a < b & c")) shouldContain
                "<detail>a &lt; b &amp; c</detail>"
        }
    })
