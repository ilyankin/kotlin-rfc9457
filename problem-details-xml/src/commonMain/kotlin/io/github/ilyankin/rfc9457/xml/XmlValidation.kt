package io.github.ilyankin.rfc9457.xml

import kotlinx.serialization.SerializationException

/**
 * Rejects an extension member name that XML cannot express.
 *
 * Nothing downstream will: `KtXmlWriter.startTag` appends the name it is handed without validating
 * it, so `a><injected/><b` would be copied into the document verbatim and the result would stop
 * being XML. Names arrive unfiltered by design — [io.github.ilyankin.rfc9457.Problem] accepts any
 * string as an extension key, because RFC 9457 §3.2's naming rule is a `SHOULD` and JSON has no
 * trouble with it.
 *
 * The required production is **NCName** (Namespaces in XML 1.0): XML 1.0 §2.3's `Name` minus the
 * colon. The colon is excluded rather than allowed because every element here is written with an
 * empty prefix, so `x:y` would reach a reader as a prefix bound to nothing.
 *
 * §3.2's own advice — start with a letter, use only `[A-Za-z0-9_]`, three characters or longer — is
 * deliberately not enforced. It is a `SHOULD`, and a two-character name is legal XML.
 */
internal fun requireXmlName(name: String) {
    if (!name.isNcName()) {
        throw SerializationException(
            "Extension member '$name' cannot be an XML element name. RFC 9457 Appendix B writes " +
                "each extension member as an element, so its name must be an NCName (XML 1.0 §2.3, " +
                "without a colon). §3.2 recommends names that start with a letter and use only " +
                "[A-Za-z0-9_]. The same problem encodes to JSON without complaint.",
        )
    }
}

/**
 * Rejects the two pieces of text xmlutil 1.0.1 mishandles, before it gets the chance.
 *
 * `U+0000` is written out raw — xmlutil's own guard for it is unreachable — yielding a document no
 * conforming parser will read. An unpaired *high* surrogate crashes the writer with
 * `StringIndexOutOfBoundsException`; an unpaired *low* one is refused correctly, but as a bare
 * `IllegalArgumentException`, so it is caught here too and the message can name [member].
 *
 * Every other character XML forbids, xmlutil rejects on its own, so this is deliberately not a
 * second implementation of the XML character model. Both defects are reported upstream.
 */
internal fun requireWritableText(
    member: String,
    content: String,
) {
    var i = 0
    while (i < content.length) {
        val c = content[i]
        when {
            c == '\u0000' -> {
                throw SerializationException(
                    "Member '$member' contains U+0000 at index $i, which no XML document may carry " +
                        "in any form — not escaped, not in a CDATA section (XML 1.0 §2.2). The same " +
                        "value encodes to JSON without complaint.",
                )
            }

            c.isHighSurrogate() -> {
                if (i + 1 >= content.length || !content[i + 1].isLowSurrogate()) {
                    throw SerializationException(unpairedSurrogate(member, i))
                }
                // Skip the low half; the pair together is one character.
                i++
            }

            c.isLowSurrogate() -> {
                throw SerializationException(unpairedSurrogate(member, i))
            }
        }
        i++
    }
}

private fun unpairedSurrogate(
    member: String,
    index: Int,
): String =
    "Member '$member' contains an unpaired surrogate at index $index, which is not a character and " +
        "cannot be encoded as XML."

/**
 * NCName, evaluated over **code points** rather than `Char`s, because `NameStartChar` includes
 * `[#x10000-#xEFFFF]` — two `Char`s in Kotlin's UTF-16 strings.
 *
 * Written out from the productions rather than delegated to `java.lang.Character`, since this module
 * is `commonMain` and must behave identically on every target. An unpaired surrogate is left
 * uncombined and so fails the range test, which is the right answer.
 */
private fun String.isNcName(): Boolean {
    if (isEmpty()) return false
    var i = 0
    var isFirst = true
    while (i < length) {
        val c = this[i]
        val codePoint: Int
        if (c.isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate()) {
            codePoint = 0x10000 + ((c.code - 0xD800) shl 10) + (this[i + 1].code - 0xDC00)
            i += 2
        } else {
            codePoint = c.code
            i += 1
        }
        val ok = if (isFirst) isNcNameStartChar(codePoint) else isNcNameChar(codePoint)
        if (!ok) return false
        isFirst = false
    }
    return true
}

/** XML 1.0 §2.3 `NameStartChar`, minus `":"` — that exclusion is what makes it an *NC*Name. */
private fun isNcNameStartChar(codePoint: Int): Boolean =
    codePoint == '_'.code ||
        codePoint in 'A'.code..'Z'.code ||
        codePoint in 'a'.code..'z'.code ||
        codePoint in 0xC0..0xD6 ||
        codePoint in 0xD8..0xF6 ||
        codePoint in 0xF8..0x2FF ||
        codePoint in 0x370..0x37D ||
        codePoint in 0x37F..0x1FFF ||
        codePoint in 0x200C..0x200D ||
        codePoint in 0x2070..0x218F ||
        codePoint in 0x2C00..0x2FEF ||
        codePoint in 0x3001..0xD7FF ||
        codePoint in 0xF900..0xFDCF ||
        codePoint in 0xFDF0..0xFFFD ||
        codePoint in 0x10000..0xEFFFF

/** XML 1.0 §2.3 `NameChar`, minus `":"`. */
private fun isNcNameChar(codePoint: Int): Boolean =
    isNcNameStartChar(codePoint) ||
        codePoint == '-'.code ||
        codePoint == '.'.code ||
        codePoint in '0'.code..'9'.code ||
        codePoint == 0xB7 ||
        codePoint in 0x300..0x36F ||
        codePoint in 0x203F..0x2040
