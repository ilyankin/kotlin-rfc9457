package io.github.ilyankin.rfc9457.xml

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ProblemArray
import io.github.ilyankin.rfc9457.ProblemPrimitive
import io.github.ilyankin.rfc9457.problemArray
import io.github.ilyankin.rfc9457.problemObject
import io.github.ilyankin.rfc9457.string
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.SerializationException
import nl.adaptivity.xmlutil.XmlException

private fun xml(body: String): String = """<?xml version="1.0" encoding="UTF-8"?><problem xmlns="urn:ietf:rfc:7807">$body</problem>"""

class ProblemXmlReaderTest :
    StringSpec({

        "the Appendix B example parses to the expected Problem" {
            ProblemXml.decodeFromString(XmlFixtures.OUT_OF_CREDIT_XML) shouldBe
                XmlFixtures.outOfCreditProblem()
        }

        "standard members are typed by the RFC, so status is a number and title a string" {
            val problem = ProblemXml.decodeFromString(xml("<status>404</status><title>404</title>"))
            problem.status shouldBe 404
            problem.title shouldBe "404"
        }

        "an absent type defaults to about:blank" {
            ProblemXml.decodeFromString(xml("<status>404</status>")).type shouldBe "about:blank"
        }

        "a non-integer status is ignored as if absent" {
            ProblemXml.decodeFromString(xml("<status>not-a-number</status>")).status shouldBe null
        }

        "an extension whose text is a number literal parses as a number" {
            val value = ProblemXml.decodeFromString(xml("<balance>30</balance>")).extensions["balance"]!!
            value shouldBe ProblemPrimitive(30)
        }

        "an extension whose text is a boolean literal parses as a boolean" {
            ProblemXml.decodeFromString(xml("<flag>true</flag>")).extensions["flag"] shouldBe
                ProblemPrimitive(true)
        }

        "an extension whose text is not a literal parses as a string" {
            ProblemXml.decodeFromString(xml("<note>hello</note>")).extensions["note"] shouldBe
                ProblemPrimitive("hello")
        }

        "children all named i are an array" {
            val value =
                ProblemXml
                    .decodeFromString(xml("<accounts><i>/a</i><i>/b</i></accounts>"))
                    .extensions["accounts"]!!
            value.problemArray.map { it.string } shouldBe listOf("/a", "/b")
        }

        "a single i child is a one-element array, per Appendix B's one-or-more wording" {
            val value =
                ProblemXml
                    .decodeFromString(xml("<accounts><i>/a</i></accounts>"))
                    .extensions["accounts"]!!
            value shouldBe ProblemArray(listOf(ProblemPrimitive("/a")))
        }

        "children with other names are an object" {
            val value =
                ProblemXml
                    .decodeFromString(xml("<err><detail>d</detail><pointer>#/a</pointer></err>"))
                    .extensions["err"]!!
            value.problemObject.keys shouldBe setOf("detail", "pointer")
        }

        "mixed children are read as an object rather than dropped" {
            val value =
                ProblemXml
                    .decodeFromString(xml("<mix><i>1</i><named>2</named></mix>"))
                    .extensions["mix"]!!
            value.problemObject.keys shouldBe setOf("i", "named")
        }

        "objects nested inside an i array parse" {
            val value =
                ProblemXml
                    .decodeFromString(
                        xml("<errors><i><detail>d</detail><pointer>#/age</pointer></i></errors>"),
                    ).extensions["errors"]!!
            value.problemArray.size shouldBe 1
            value.problemArray[0]
                .problemObject["pointer"]
                ?.string shouldBe "#/age"
        }

        "an empty element is the empty string — null and empty collections do not survive" {
            ProblemXml.decodeFromString(xml("<nothing></nothing>")).extensions["nothing"] shouldBe
                ProblemPrimitive("")
            ProblemXml.decodeFromString(xml("<nothing/>")).extensions["nothing"] shouldBe
                ProblemPrimitive("")
        }

        "a string extension whose text reads as a number comes back widened — documented loss" {
            ProblemXml.decodeFromString(xml("<code>30</code>")).extensions["code"] shouldBe
                ProblemPrimitive(30)
        }

        "a child in a foreign namespace is ignored rather than fatal" {
            val document =
                """<?xml version="1.0" encoding="UTF-8"?>""" +
                    """<problem xmlns="urn:ietf:rfc:7807" xmlns:x="urn:example:other">""" +
                    """<status>400</status><x:sneaky>nope</x:sneaky></problem>"""
            val problem = ProblemXml.decodeFromString(document)
            problem.status shouldBe 400
            problem.extensions.keys shouldBe emptySet()
        }

        "attributes are ignored wherever they appear" {
            // Appendix B's RELAX NG admits `attribute * { text }`, so an attribute is *valid* — but
            // the prose defines the mapping over elements only ("elements containing a child or
            // children represent an object, except elements containing only children named i, which
            // are arrays"). An attribute has no member to map onto, so honouring one would mean
            // inventing semantics the RFC withholds, and freezing a collision policy it never
            // specifies. Microsoft's ProblemDetailsWrapper — the most widely deployed
            // implementation of this appendix — drops them the same way. This module's writer never
            // emits an attribute, so nothing it produces is lost either.
            //
            // Pinned because today the behaviour is a *consequence* of never reading attributes
            // rather than a decision, and a refactor could start mapping them without noticing.
            ProblemXml.decodeFromString(xml("""<balance currency="EUR">30</balance>""")).extensions shouldBe
                mapOf("balance" to ProblemPrimitive(30))

            // No ambiguity is introduced: where an attribute and a child element share a name, the
            // element is the only thing read, so there is nothing to arbitrate.
            ProblemXml
                .decodeFromString(xml("""<err code="1"><code>2</code></err>"""))
                .extensions["err"]
                ?.problemObject
                ?.get("code")
                ?.string shouldBe "2"
        }

        "an attribute on the root or on a standard member changes nothing" {
            val document =
                """<?xml version="1.0" encoding="UTF-8"?>""" +
                    """<problem xmlns="urn:ietf:rfc:7807" foo="bar">""" +
                    """<detail xml:lang="en">hi</detail><status>404</status></problem>"""
            val problem = ProblemXml.decodeFromString(document)
            problem.detail shouldBe "hi"
            problem.status shouldBe 404
            problem.extensions.keys shouldBe emptySet()
        }

        "escaped text is unescaped" {
            ProblemXml.decodeFromString(xml("<detail>a &lt; b &amp; c</detail>")).detail shouldBe "a < b & c"
        }

        "text needing escapes survives a writer-to-reader round trip" {
            // The half the Appendix B fixture cannot cover: its example contains no character that
            // needs escaping, so nothing else would notice if the writer escaped and the reader
            // then dropped the result. It did — the five predefined entities arrive as ENTITY_REF
            // events, which the XXE guard was discarding along with everything else.
            val original = Problem(detail = """1 < 2 && "x" > 'y'""", title = "a & b")
            ProblemXml.decodeFromString(ProblemXml.encodeToString(original)) shouldBe original
        }

        "an entity declared by the document's own DTD is not expanded either" {
            // Narrower than the XXE case below and worth pinning separately: this entity resolves
            // to nothing outside the document, so a parser may well consider it "known". It is
            // still attacker-supplied, and unbounded expansion of self-referencing entities is the
            // billion-laughs attack. Only the five XML-predefined names are honoured.
            val document =
                """<?xml version="1.0" encoding="UTF-8"?>""" +
                    """<!DOCTYPE problem [<!ENTITY inert "expanded">]>""" +
                    """<problem xmlns="urn:ietf:rfc:7807"><detail>&inert;</detail></problem>"""
            val detail = runCatching { ProblemXml.decodeFromString(document).detail }.getOrNull()
            detail.orEmpty() shouldNotContain "expanded"
        }

        "a malformed document fails with SerializationException, not xmlutil's own type" {
            // xmlutil is an `implementation` dependency so that none of its types reach callers; a
            // thrown type is part of that contract too. Catching XmlException would otherwise force
            // consumers to add xmlutil to their own build.
            val failure =
                shouldThrow<SerializationException> {
                    ProblemXml.decodeFromString("<problem xmlns='urn:ietf:rfc:7807'")
                }
            failure.cause.shouldBeInstanceOf<XmlException>()
        }

        "an empty document fails the same way" {
            shouldThrow<SerializationException> { ProblemXml.decodeFromString("") }
        }

        "a document that is not markup at all fails the same way" {
            // Regression guard: xmlutil reports this one as a bare IllegalStateException rather than
            // an XmlException, so it used to escape the wrapper and reach callers untranslated.
            shouldThrow<SerializationException> { ProblemXml.decodeFromString("not xml at all") }
            shouldThrow<SerializationException> { ProblemXml.decodeFromString("<") }
        }

        "a root element that is not <problem> is rejected" {
            // RFC §3's leniency covers members, not the identity of the document. Accepting this
            // used to yield a valid-looking empty `about:blank` Problem — silent data loss.
            shouldThrow<SerializationException> {
                ProblemXml.decodeFromString("""<foo xmlns="urn:ietf:rfc:7807"/>""")
            }
        }

        "a <problem> root in a foreign namespace is rejected" {
            shouldThrow<SerializationException> {
                ProblemXml.decodeFromString("""<problem xmlns="urn:ietf:rfc:9457"/>""")
            }
        }

        "a <problem> root with no namespace at all is accepted" {
            // Tolerated, not rejected: the element name is unambiguous, some producers omit the
            // declaration, and this matches how member elements already treat an absent namespace.
            ProblemXml.decodeFromString("<problem><status>404</status></problem>").status shouldBe 404
        }

        "nesting past the depth limit fails with an exception, not a StackOverflowError" {
            // The recursion is entirely ours — xmlutil's reader is a pull parser and never recurses
            // per element — so nothing below this library can bound it. Unbounded, a ~70 KB document
            // exhausted the stack; StackOverflowError is an Error, so it slips past ordinary
            // handling entirely.
            val tooDeep = Problem.MAX_NESTING_DEPTH + 5
            val document =
                buildString {
                    append("""<problem xmlns="urn:ietf:rfc:7807">""")
                    repeat(tooDeep) { append("<a>") }
                    repeat(tooDeep) { append("</a>") }
                    append("</problem>")
                }
            shouldThrow<SerializationException> { ProblemXml.decodeFromString(document) }
        }

        "nesting up to the depth limit still parses" {
            val document =
                buildString {
                    append("""<problem xmlns="urn:ietf:rfc:7807"><deep>""")
                    // <deep> is level 1, so this reaches exactly MAX_NESTING_DEPTH.
                    repeat(Problem.MAX_NESTING_DEPTH - 1) { append("<a>") }
                    append("leaf")
                    repeat(Problem.MAX_NESTING_DEPTH - 1) { append("</a>") }
                    append("</deep></problem>")
                }
            ProblemXml.decodeFromString(document).extensions.keys shouldBe setOf("deep")
        }

        // This is the test that turns "The parser implementation is pinned explicitly" from a
        // convention into a property. It fails if the codec is ever routed through a default entry
        // point that consults platform parsers, or if an xmlutil upgrade changes the generic reader.
        "an external entity is never resolved" {
            val document =
                """<?xml version="1.0" encoding="UTF-8"?>""" +
                    """<!DOCTYPE problem [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>""" +
                    """<problem xmlns="urn:ietf:rfc:7807"><detail>&xxe;</detail></problem>"""

            // Either outcome is acceptable and both are safe: the parser may reject the DTD outright,
            // or it may parse and leave the entity unexpanded. What must never happen is file content
            // reaching `detail`. Assert on the content, not on which of the two paths was taken.
            val detail = runCatching { ProblemXml.decodeFromString(document).detail }.getOrNull()
            detail?.shouldNotContain("root:")
        }
    })
