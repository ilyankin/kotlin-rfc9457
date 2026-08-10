package io.github.ilyankin.rfc9457.xml

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ProblemArray
import io.github.ilyankin.rfc9457.ProblemObject
import io.github.ilyankin.rfc9457.ProblemPrimitive
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * What survives an XML round trip, what does not, and how much malformedness the reader tolerates.
 *
 * Every case here is specified behaviour, not a defect. Either it follows from Appendix B's mapping,
 * or it is a leniency RFC 9457 §3 asks for. Pinned so that a later refactor has to change them
 * deliberately; several of these used to be accidents of the parse loop.
 */
class ProblemXmlFidelityTest :
    StringSpec({

        // ---- Whitespace-only text is content, not decoration ---------------------------------

        "a member whose text is only whitespace survives a round trip" {
            // Dropping xmlutil's IGNORABLE_WHITESPACE event lost the entire content of
            // `<detail> </detail>`: a single space came back as the empty string.
            listOf(" ", "\t", "\n", "   ").forEach { text ->
                val problem = Problem(detail = text)
                ProblemXml.decodeFromString(ProblemXml.encodeToString(problem)) shouldBe problem
            }
        }

        "padded text keeps its padding, as it always did" {
            val problem = Problem(detail = "  hi  ", title = "a\tb")
            ProblemXml.decodeFromString(ProblemXml.encodeToString(problem)) shouldBe problem
        }

        "an indented document still parses to the same Problem as its compact form" {
            // The other side of the same change: inter-element whitespace must keep being discarded.
            // It is, because `content` is only ever consulted for an element that turned out to have
            // no children.
            val indented =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <problem xmlns="urn:ietf:rfc:7807">
                  <type>https://example.com/probs/out-of-credit</type>
                  <title>You do not have enough credit.</title>
                  <detail>Your current balance is 30, but that costs 50.</detail>
                  <instance>https://example.net/account/12345/msgs/abc</instance>
                  <balance>30</balance>
                  <accounts>
                    <i>https://example.net/account/12345</i>
                    <i>https://example.net/account/67890</i>
                  </accounts>
                </problem>
                """.trimIndent()
            ProblemXml.decodeFromString(indented) shouldBe XmlFixtures.outOfCreditProblem()
        }

        // ---- Losses that follow from Appendix B ----------------------------------------------

        "an object whose keys are all i comes back as an array" {
            // An object that happens to use `i` as its only key is written exactly like an array,
            // so it cannot be told apart on the way back. Changing this would mean departing from
            // Appendix B, not fixing a bug. It is the third specified loss, alongside the
            // null/empty-collection collapse and number widening.
            val original = Problem(extensions = mapOf("x" to ProblemObject(mapOf("i" to ProblemPrimitive("a")))))
            val back = ProblemXml.decodeFromString(ProblemXml.encodeToString(original))
            back.extensions["x"] shouldBe ProblemArray(listOf(ProblemPrimitive("a")))
        }

        "an extension named i at the top level is not affected" {
            // The array rule applies to an element's children, and the standard members are read by
            // name, not by shape, so a top-level `i` is an ordinary extension member.
            val original = Problem(extensions = mapOf("i" to ProblemPrimitive("a")))
            ProblemXml.decodeFromString(ProblemXml.encodeToString(original)) shouldBe original
        }

        // ---- Reader leniency: decisions, not accidents ----------------------------------------

        "the last of several same-named members wins" {
            // RFC 9457 never contemplates a duplicate, so any rule is arbitrary; last-wins is what a
            // streaming reader does naturally. Pinned for both kinds of member so the two cannot
            // drift apart.
            ProblemXml
                .decodeFromString(
                    """<problem xmlns="urn:ietf:rfc:7807"><detail>a</detail><detail>b</detail></problem>""",
                ).detail shouldBe "b"

            ProblemXml
                .decodeFromString(
                    """<problem xmlns="urn:ietf:rfc:7807"><ext>a</ext><ext>b</ext></problem>""",
                ).extensions["ext"] shouldBe ProblemPrimitive("b")
        }

        "content after the closing tag is ignored" {
            // The root element is already checked, which is the identity question §3 cares about.
            // Policing what follows would add a second rule that buys nothing: the document element
            // has been read in full by then.
            ProblemXml
                .decodeFromString(
                    """<problem xmlns="urn:ietf:rfc:7807"><status>404</status></problem><junk/>""",
                ).status shouldBe 404
        }

        "an XML 1.1 declaration parses" {
            // xmlutil's call, not this codec's. Rejecting it here would be stricter than the parser
            // underneath, and Appendix B says nothing about the XML version.
            ProblemXml
                .decodeFromString(
                    """<?xml version="1.1" encoding="UTF-8"?>""" +
                        """<problem xmlns="urn:ietf:rfc:7807"><status>404</status></problem>""",
                ).status shouldBe 404
        }
    })
