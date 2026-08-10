package io.github.ilyankin.rfc9457.xml

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ProblemArray
import io.github.ilyankin.rfc9457.ProblemNull
import io.github.ilyankin.rfc9457.ProblemObject
import io.github.ilyankin.rfc9457.ProblemPrimitive
import io.github.ilyankin.rfc9457.ProblemValue
import kotlinx.serialization.SerializationException
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.XmlWriter
import nl.adaptivity.xmlutil.core.KtXmlWriter

/**
 * Written by hand, not by `XmlDeclMode.Charset`, which would be the obvious way.
 *
 * `KtXmlWriter` hardcodes apostrophes around both pseudo-attributes and exposes nothing to change
 * them with. XML 1.0 §2.8 permits either quote character, so that is conformant. It simply is not the
 * one RFC 9457 Appendix B prints, and the conformance test compares against the RFC's published
 * example verbatim. Emitting the declaration here also keeps an xmlutil release that changes its
 * quoting from breaking that test over something that carries no meaning.
 */
private const val XML_DECLARATION = """<?xml version="1.0" encoding="UTF-8"?>"""

/**
 * Everything this codec refuses leaves as a `SerializationException`, xmlutil's own refusals
 * included.
 *
 * xmlutil is an `implementation` dependency precisely so a caller never needs it on their own
 * classpath, and a thrown type belongs to that contract as much as a signature does. Unwrapped, an
 * invalid character would surface as xmlutil's `IllegalArgumentException`.
 *
 * **The first `catch` is load-bearing.** `SerializationException` extends `IllegalArgumentException`,
 * so without it every guard reachable from here (depth, name, text) would be caught by the second
 * arm and wrapped inside itself. The `IndexOutOfBoundsException` arm is a backstop for what an
 * xmlutil upgrade might add; [requireWritableText] already stops the one input known to reach it.
 */
internal fun writeProblem(problem: Problem): String =
    try {
        writeProblemDocument(problem)
    } catch (cause: SerializationException) {
        throw cause
    } catch (cause: IllegalArgumentException) {
        throw SerializationException("Problem cannot be written as XML: ${cause.message}", cause)
    } catch (cause: IndexOutOfBoundsException) {
        throw SerializationException("Problem cannot be written as XML: ${cause.message}", cause)
    }

private fun writeProblemDocument(problem: Problem): String =
    buildString {
        append(XML_DECLARATION)

        // Every element, including extensions, lives in the one problem namespace: Appendix B's
        // schema "explicitly only allows elements from the one namespace used in the XML format".
        // isRepairNamespaces = true is what collapses those namespace arguments into a single
        // `xmlns` on the root and leaves the children bare.
        //
        // KtXmlWriter is constructed directly because xmlutil offers no generic writer factory to
        // match `newGenericReader`: every `newWriter` overload is either experimental or documented
        // as returning a potentially platform-specific writer. Naming the platform-independent
        // implementation keeps the bytes identical on every target.
        val writer: XmlWriter =
            KtXmlWriter(
                this,
                isRepairNamespaces = true,
                xmlDeclMode = XmlDeclMode.None,
            ).apply {
                // Off, so an empty element is `<nothing/>`, not xmlutil's default `<nothing />`.
                // Both are the same element to any parser, but the spaced form surprises readers and
                // diff tools for no gain.
                addTrailingSpaceBeforeEnd = false
            }
        writer.use { writer ->
            writer.startTag(ProblemXml.NAMESPACE, ProblemXml.ROOT_ELEMENT, "")

            // Appendix B's interleave makes the five standard members order-free, but the
            // conformance test compares byte for byte against the RFC's example, which uses this
            // order.
            writer.textElement("type", problem.type)
            problem.title?.let { writer.textElement("title", it) }
            problem.detail?.let { writer.textElement("detail", it) }
            problem.status?.let { writer.textElement("status", it.toString()) }
            problem.instance?.let { writer.textElement("instance", it) }

            problem.extensions.forEach { (name, value) -> writer.writeValue(name, value, depth = 1) }

            writer.endTag(ProblemXml.NAMESPACE, ProblemXml.ROOT_ELEMENT, "")
        }
    }

/**
 * The single funnel for every piece of caller-supplied text: the five standard members and every
 * scalar extension value. That is why [requireWritableText] sits here and nowhere else. Names get
 * checked in [writeValue] instead; the ones reaching this function are either RFC-fixed literals or
 * have already been vetted there.
 */
private fun XmlWriter.textElement(
    name: String,
    content: String,
) {
    requireWritableText(name, content)
    startTag(ProblemXml.NAMESPACE, name, "")
    text(content)
    endTag(ProblemXml.NAMESPACE, name, "")
}

private fun XmlWriter.writeValue(
    name: String,
    value: ProblemValue,
    depth: Int,
) {
    // Bounded on the way out as well as in. Reading caps at the same limit, so a Problem deep enough
    // to overflow here can only come from the caller's own code. A document this codec could not
    // read back should not be one it emits.
    if (depth > Problem.MAX_NESTING_DEPTH) {
        throw SerializationException(
            "Problem extensions nest deeper than ${Problem.MAX_NESTING_DEPTH} levels",
        )
    }
    // The single funnel for every element name this codec emits: top-level extension members, the
    // keys of a nested object, and the RFC's own `i` for array items. This check lives here, not in
    // [textElement], so the opening tag of an array or an object is covered too.
    requireXmlName(name)
    when (value) {
        is ProblemPrimitive -> {
            textElement(name, value.content)
        }

        // Appendix B has no representation for null, nor for an empty collection. All three become
        // an empty element, and the reader therefore returns them as the empty string. This loss is
        // a property of the format. Tests pin it down instead of hiding it.
        ProblemNull -> {
            textElement(name, "")
        }

        is ProblemArray -> {
            startTag(ProblemXml.NAMESPACE, name, "")
            value.forEach { item -> writeValue(ProblemXml.ARRAY_ITEM_ELEMENT, item, depth + 1) }
            endTag(ProblemXml.NAMESPACE, name, "")
        }

        is ProblemObject -> {
            startTag(ProblemXml.NAMESPACE, name, "")
            value.forEach { (childName, childValue) -> writeValue(childName, childValue, depth + 1) }
            endTag(ProblemXml.NAMESPACE, name, "")
        }
    }
}
