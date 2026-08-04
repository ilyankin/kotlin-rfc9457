package io.github.ilyankin.rfc9457.xml

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ProblemArray
import io.github.ilyankin.rfc9457.ProblemObject
import io.github.ilyankin.rfc9457.ProblemValue
import io.github.ilyankin.rfc9457.problemLiteral
import kotlinx.serialization.SerializationException
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.xmlStreaming

/**
 * Always the **generic** reader, never the default entry point.
 *
 * xmlutil's `core-jdk` module documents that its mere presence on the classpath switches the default
 * to the JDK's parser, which resolves through `ServiceLoader` to whatever StAX implementation happens
 * to be installed — with that implementation's own defaults for DTDs and external entities. Naming
 * the generic reader makes three things true at once: a document arriving from the network cannot
 * pull in a DTD or an external entity (this is xmlutil's own documented remedy), behaviour does not
 * depend on the consumer's unrelated dependencies, and every target parses identically. Pinned by
 * the XXE test.
 *
 * `expandEntities` is left at its default of `false` — stated because the safety of this function
 * rests on it, and a future overload that flips the default must not pass silently.
 */
private fun readerFor(document: String): XmlReader = xmlStreaming.newGenericReader(document)

/**
 * The five entities every XML processor must recognise without a DTD (XML 1.0 §4.6).
 *
 * Owning the table is what keeps entity handling independent of what a document declares about
 * itself. These five expand to fixed text and resolve nothing, so honouring them by name is safe —
 * and necessary, since this module's own writer escapes `<`, `&` and `>` into exactly these.
 */
private val PREDEFINED_ENTITIES =
    mapOf(
        "lt" to "<",
        "gt" to ">",
        "amp" to "&",
        "quot" to "\"",
        "apos" to "'",
    )

/** An intermediate node: XML gives us names and text, and types are inferred afterwards. */
private sealed interface Node {
    data class Text(
        val value: String,
    ) : Node

    data class Children(
        val entries: List<Pair<String, Node>>,
    ) : Node
}

internal fun readProblem(document: String): Problem {
    val reader = readerFor(document)
    try {
        while (reader.hasNext()) {
            if (reader.next() == EventType.START_ELEMENT) break
        }
        // The root is checked, unlike the members inside it. §3's leniency is about *members* —
        // ignore a wrong-typed one, ignore an unrecognised extension — not about accepting any
        // document at all as a problem document. Without this, `<foo/>` parsed to a valid-looking
        // `about:blank` Problem, turning "this is not a problem document" into silent data loss.
        if (reader.localName != ProblemXml.ROOT_ELEMENT || reader.isForeignNamespace) {
            throw SerializationException(
                "Not an RFC 9457 problem document: expected a root <${ProblemXml.ROOT_ELEMENT}> " +
                    "element in namespace '${ProblemXml.NAMESPACE}', found " +
                    "<${reader.localName}> in '${reader.namespaceURI}'",
            )
        }
        return reader.readChildren(depth = 0).toProblem()
    } catch (cause: XmlException) {
        // Wrapped for the reason writeProblem wraps its own: xmlutil is an `implementation`
        // dependency, so none of its types may reach a caller. Chained, so nothing is lost.
        throw SerializationException("Malformed problem document: ${cause.message}", cause)
    } catch (cause: IllegalStateException) {
        // Text before the root element, or a lone `<`, leaves KtXmlReader.next as a bare
        // IllegalStateException instead of an XmlException, so the clause above never sees it.
        // Enumerated rather than caught broadly, as on the write path, and safe to catch here
        // because nothing in this module raises one of its own — the root refusal above is a
        // SerializationException, which is an IllegalArgumentException.
        throw SerializationException("Malformed problem document: ${cause.message}", cause)
    } finally {
        reader.close()
    }
}

/**
 * Appendix B's schema admits exactly one namespace, so anything else is foreign. An *absent*
 * namespace is tolerated rather than rejected: some producers omit the declaration, the element name
 * is unambiguous without it, and this matches how the member loop below already treats children.
 */
private val XmlReader.isForeignNamespace: Boolean
    get() = namespaceURI.isNotEmpty() && namespaceURI != ProblemXml.NAMESPACE

/**
 * Reads the children of the element the reader is currently positioned on.
 *
 * Members outside the document's namespace are skipped: Appendix B's schema admits only the one
 * namespace, so foreign content is never legitimate, and §3 forbids failing over it.
 *
 * **Attributes are ignored, deliberately.** Appendix B's RELAX NG admits `attribute * { text }`, so
 * an attribute is valid; what it never supplies is a mapping from one into the JSON data model,
 * whose convention it states over elements alone. Honouring an attribute would mean inventing that
 * mapping and freezing an element-versus-attribute collision policy the RFC does not specify. The
 * writer emits none, so nothing this module produces loses anything. Pinned by test.
 */
private fun XmlReader.readChildren(depth: Int): Node.Children {
    // The guard belongs in the recursion itself, because this is where the stack is spent: xmlutil's
    // reader is a pull parser and never recurses per element, so the only unbounded recursion in the
    // parse path is ours. See Problem.MAX_NESTING_DEPTH.
    if (depth > Problem.MAX_NESTING_DEPTH) {
        throw SerializationException(
            "Problem document nests deeper than ${Problem.MAX_NESTING_DEPTH} levels",
        )
    }
    val entries = mutableListOf<Pair<String, Node>>()
    val content = StringBuilder()
    while (hasNext()) {
        when (next()) {
            EventType.START_ELEMENT -> {
                // Both are read before recursing: the reader has moved on by the time it returns.
                val name = localName
                val foreign = isForeignNamespace
                val child = readChildren(depth + 1)
                if (!foreign) entries += name to child
            }

            // IGNORABLE_WHITESPACE belongs here despite the name. xmlutil has no schema and cannot
            // know an element's content model, so it uses that event for any run of text that
            // happens to be *entirely* whitespace — including the whole content of `<detail> </detail>`.
            // Inter-element whitespace still ends up discarded, because `content` is only ever read
            // for an element that turned out to have no children.
            EventType.TEXT, EventType.CDSECT, EventType.IGNORABLE_WHITESPACE -> {
                content.append(text)
            }

            // Note this deliberately does not consult `XmlReader.isKnownEntity`, which is also true
            // for anything the document's own internal DTD subset declared — and a document arriving
            // from the network is exactly where an attacker puts that, whether to reach a file (XXE)
            // or merely to expand (billion laughs). Anything outside the table contributes nothing.
            EventType.ENTITY_REF -> {
                PREDEFINED_ENTITIES[localName]?.let(content::append)
            }

            EventType.END_ELEMENT -> {
                return if (entries.isEmpty()) {
                    Node.Children(listOf("" to Node.Text(content.toString())))
                } else {
                    Node.Children(entries)
                }
            }

            else -> {
                Unit
            }
        }
    }
    return Node.Children(entries)
}

/** A leaf node carries a single unnamed text entry; anything else has real children. */
private val Node.Children.textOrNull: String?
    get() = entries.singleOrNull()?.takeIf { it.first.isEmpty() }?.let { (it.second as Node.Text).value }

private fun Node.Children.toProblem(): Problem {
    val members = entries.filter { it.first.isNotEmpty() }

    fun text(name: String): String? = members.lastOrNull { it.first == name }?.second?.let { (it as? Node.Children)?.textOrNull }

    return Problem(
        type = text("type") ?: Problem.ABOUT_BLANK,
        // §3 leniency: a status that is not an integer is ignored as if absent.
        status = text("status")?.toIntOrNull(),
        title = text("title"),
        detail = text("detail"),
        instance = text("instance"),
        extensions =
            members
                .filterNot { it.first in Problem.RESERVED_MEMBERS }
                .associate { (name, node) -> name to (node as Node.Children).toProblemValue() },
    )
}

private fun Node.Children.toProblemValue(): ProblemValue {
    val text = textOrNull
    if (text != null) return widen(text)

    // Appendix B: "elements containing only one or more child elements named i ... are considered
    // arrays"; anything else with children is an object. Mixed content is not valid Appendix B, and
    // reading it as an object discards less than dropping the member.
    val allItems = entries.isNotEmpty() && entries.all { it.first == ProblemXml.ARRAY_ITEM_ELEMENT }
    return if (allItems) {
        ProblemArray(entries.map { (_, node) -> (node as Node.Children).toProblemValue() })
    } else {
        ProblemObject(entries.associate { (name, node) -> name to (node as Node.Children).toProblemValue() })
    }
}

/**
 * XML carries no type information for extension members, so a parse must decide. Text that is
 * exactly a JSON number or boolean literal becomes a non-string scalar; everything else stays a
 * string. The documented cost: a genuinely-string extension reading `30` comes back as a number.
 */
private fun widen(text: String): ProblemValue =
    when {
        text == "true" || text == "false" -> problemLiteral(text, isString = false)
        text.isJsonNumberLiteral() -> problemLiteral(text, isString = false)
        else -> problemLiteral(text, isString = true)
    }

private val JSON_NUMBER = Regex("""-?(0|[1-9]\d*)(\.\d+)?([eE][-+]?\d+)?""")

private fun String.isJsonNumberLiteral(): Boolean = isNotEmpty() && matches(JSON_NUMBER)
