package io.github.ilyankin.rfc9457.xml

import io.github.ilyankin.rfc9457.Problem

/**
 * The `application/problem+xml` codec, per RFC 9457 Appendix B.
 *
 * Hand-written over xmlutil's plain `XmlReader`/`XmlWriter`, not driven by a serialization framework.
 * Extension members produce element names that are only known at runtime, and a serial descriptor
 * cannot express that.
 */
public object ProblemXml {
    /**
     * RFC 9457 deliberately kept RFC 7807's namespace, because the wire format did not change.
     * Emitting `urn:ietf:rfc:9457` is a conformance bug that round-trips happily against itself,
     * and one that shipped implementations have made repeatedly.
     */
    public const val NAMESPACE: String = "urn:ietf:rfc:7807"

    /**
     * The name Appendix B gives the document element. The reader rejects a document rooted at
     * anything else. §3's leniency covers members; it says nothing about accepting any XML document
     * as a problem document.
     */
    public const val ROOT_ELEMENT: String = "problem"

    /** Appendix B: an element whose children are all named `i` is an array. */
    public const val ARRAY_ITEM_ELEMENT: String = "i"

    /**
     * The media type as a plain `String`, since this module must not depend on Ktor.
     *
     * `problem-details-ktor` exposes the same value as a Ktor `ContentType`, `ProblemContentTypes.Xml`,
     * which is what registration code should use. Neither is more canonical than the other; they
     * differ only in which module can name the type.
     */
    public const val MEDIA_TYPE: String = "application/problem+xml"

    /**
     * Writes [problem] as an `application/problem+xml` document, always in UTF-8.
     *
     * Not every [Problem] can be written. Appendix B puts each extension member in an element name,
     * and XML constrains both names and character content in ways JSON does not. A problem this
     * library encodes to JSON without complaint may be refused here. That asymmetry is deliberate.
     * [Problem] accepts any extension name because RFC 9457 §3.2's naming rule is a `SHOULD`, and
     * enforcing it in the model would break JSON-only callers for the sake of a format they never use.
     *
     * @throws kotlinx.serialization.SerializationException if [problem] nests deeper than
     *   [Problem.MAX_NESTING_DEPTH]; if an extension member's name is not an XML `NCName`
     *   (XML 1.0 §2.3 without a colon); or if any member's text carries `U+0000` or an unpaired
     *   surrogate, neither of which XML can represent. The underlying writer's own exception, where
     *   there is one, is attached as the cause.
     */
    public fun encodeToString(problem: Problem): String = writeProblem(problem)

    /**
     * Reads an `application/problem+xml` document, tolerating the deviations RFC 9457 §3 asks a
     * consumer to tolerate: an unrecognised member, a wrong-typed one, a missing namespace.
     *
     * @throws kotlinx.serialization.SerializationException if [xml] is not a well-formed problem
     *   document: malformed XML, a root element other than `problem`, or nesting deeper than
     *   [Problem.MAX_NESTING_DEPTH]. The underlying parser's own exception is attached as the cause.
     */
    public fun decodeFromString(xml: String): Problem = readProblem(xml)
}
