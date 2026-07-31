package io.github.ilyankin.rfc9457

/**
 * A value of an RFC 9457 extension member (§3.2).
 *
 * A problem type MAY define extra, type-specific members beyond the five standard ones, and the RFC
 * puts no constraint on their JSON type — hence a full value tree rather than, say, `Map<String,
 * String>`.
 *
 * Mirrors the shape of `kotlinx.serialization.json.JsonElement` deliberately: scalars keep their
 * wire literal verbatim, so a number survives both codecs byte for byte.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457#section-3.2">RFC 9457 §3.2, Extension Members</a>
 */
public sealed interface ProblemValue

/**
 * A scalar extension value: [content] is the literal exactly as it appears on the wire (unquoted),
 * and [isString] records whether it should be written as a quoted string.
 */
public class ProblemPrimitive internal constructor(
    public val content: String,
    public val isString: Boolean,
) : ProblemValue {
    override fun equals(other: Any?): Boolean = other is ProblemPrimitive && content == other.content && isString == other.isString

    override fun hashCode(): Int = 31 * content.hashCode() + isString.hashCode()

    override fun toString(): String = if (isString) "\"$content\"" else content
}

public fun ProblemPrimitive(value: String): ProblemPrimitive = ProblemPrimitive(value, isString = true)

public fun ProblemPrimitive(value: Int): ProblemPrimitive = ProblemPrimitive(value.toString(), isString = false)

public fun ProblemPrimitive(value: Long): ProblemPrimitive = ProblemPrimitive(value.toString(), isString = false)

/**
 * [value] must be finite: a JSON number (RFC 8259 §6) cannot represent NaN or Infinity, and writing
 * one would emit a literal that conforming parsers reject. Guarded here, at the producing edge —
 * [problemLiteral] stays the unguarded escape hatch for codecs.
 */
public fun ProblemPrimitive(value: Double): ProblemPrimitive {
    require(value.isFinite()) {
        "NaN and Infinity have no JSON representation; a Double extension value must be finite, was $value"
    }
    return ProblemPrimitive(value.toString(), isString = false)
}

public fun ProblemPrimitive(value: Boolean): ProblemPrimitive = ProblemPrimitive(value.toString(), isString = false)

/**
 * Builds a scalar from an already-encoded wire literal.
 *
 * For codecs, which know how the value was encoded and must not re-encode it. Application code
 * should prefer the typed [ProblemPrimitive] factories.
 */
public fun problemLiteral(
    content: String,
    isString: Boolean,
): ProblemPrimitive = ProblemPrimitive(content, isString)

/**
 * The explicit JSON `null` of an extension member.
 *
 * Has no XML representation: Appendix B maps JSON objects to named child elements and JSON arrays to
 * children all named `i`, and defines nothing for `null`. The XML codec therefore writes it as an
 * empty element, and reading that back yields the empty string — a documented, tested asymmetry
 * rather than a bug.
 */
public data object ProblemNull : ProblemValue

public class ProblemArray(
    private val items: List<ProblemValue>,
) : ProblemValue,
    List<ProblemValue> by items {
    override fun equals(other: Any?): Boolean = items == other

    override fun hashCode(): Int = items.hashCode()

    override fun toString(): String = items.joinToString(separator = ",", prefix = "[", postfix = "]")
}

public class ProblemObject(
    entries: Map<String, ProblemValue>,
) : ProblemValue,
    Map<String, ProblemValue> by entries {
    // Not a constructor property: a `val entries` would hide the `entries` member this class
    // inherits from Map through the delegation, which the compiler rejects.
    private val members: Map<String, ProblemValue> = entries

    override fun equals(other: Any?): Boolean = members == other

    override fun hashCode(): Int = members.hashCode()

    override fun toString(): String = members.entries.joinToString(separator = ",", prefix = "{", postfix = "}") { (k, v) -> "\"$k\":$v" }
}

/*
 * Accessors come in pairs on purpose. The strict half is for a consumer that knows the problem type
 * it is reading and wants a mismatch to surface; the `*OrNull` half is what a consumer following
 * RFC 9457 §3 should reach for, since the RFC requires a member whose value is of an unexpected type
 * to be treated as absent rather than to fail the document. The library cannot pick for you: the
 * MUST applies to the five standard members, whose handling is already built into the codecs, while
 * an extension member's type is defined by whoever minted the problem type.
 *
 * Two independent things can go wrong when reading an extension member, and they are handled at
 * different points:
 *
 *     problem.extensions["account"]   // ProblemValue? — null when the member is absent
 *         ?.problemObject             // throws when it is present but is not an object
 *
 * The `?.` covers *absence*; picking `problemObject` over `problemObjectOrNull` covers a *wrong
 * type*. So the two accessors differ in exactly one situation — a member that is present and of
 * another shape — and the lenient one collapses that situation, an explicit JSON null, and an
 * absent member into the same `null`. `name in problem.extensions` tells them apart.
 *
 * The scalar accessors read the wire *literal*, not the JSON type: `int` on a quoted "30" returns
 * 30, matching `JsonPrimitive.int` in kotlinx.serialization. Consult ProblemPrimitive.isString when
 * that distinction matters.
 */

private fun ProblemValue.requirePrimitive(): ProblemPrimitive =
    this as? ProblemPrimitive ?: throw IllegalArgumentException("Expected a primitive, was ${this::class.simpleName}")

public val ProblemValue.primitive: ProblemPrimitive get() = requirePrimitive()
public val ProblemValue.primitiveOrNull: ProblemPrimitive? get() = this as? ProblemPrimitive

public val ProblemValue.string: String get() = requirePrimitive().content
public val ProblemValue.stringOrNull: String? get() = primitiveOrNull?.content

public val ProblemValue.int: Int get() = string.toInt()
public val ProblemValue.intOrNull: Int? get() = stringOrNull?.toIntOrNull()

public val ProblemValue.long: Long get() = string.toLong()
public val ProblemValue.longOrNull: Long? get() = stringOrNull?.toLongOrNull()

public val ProblemValue.double: Double get() = string.toDouble()
public val ProblemValue.doubleOrNull: Double? get() = stringOrNull?.toDoubleOrNull()

public val ProblemValue.boolean: Boolean get() = string.toBooleanStrict()
public val ProblemValue.booleanOrNull: Boolean? get() = stringOrNull?.toBooleanStrictOrNull()

public val ProblemValue.problemArray: ProblemArray
    get() = this as? ProblemArray ?: throw IllegalArgumentException("Expected an array, was ${this::class.simpleName}")
public val ProblemValue.problemArrayOrNull: ProblemArray? get() = this as? ProblemArray

public val ProblemValue.problemObject: ProblemObject
    get() = this as? ProblemObject ?: throw IllegalArgumentException("Expected an object, was ${this::class.simpleName}")
public val ProblemValue.problemObjectOrNull: ProblemObject? get() = this as? ProblemObject
