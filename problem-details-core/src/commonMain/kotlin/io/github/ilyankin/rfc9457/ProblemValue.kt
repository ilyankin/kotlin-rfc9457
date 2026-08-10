package io.github.ilyankin.rfc9457

/**
 * A value of an RFC 9457 extension member (§3.2).
 *
 * A problem type MAY define extra, type-specific members beyond the five standard ones, and §3.2
 * puts no constraint on their JSON type. That is why this is a full value tree instead of, say,
 * `Map<String, String>`. It mirrors `kotlinx.serialization.json.JsonElement`: scalars keep their wire
 * literal verbatim, so a number survives both codecs byte for byte.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457#section-3.2">RFC 9457 §3.2, Extension Members</a>
 */
public sealed interface ProblemValue

/**
 * A scalar extension value: a string, a number, or a boolean.
 *
 * The wire literal stays verbatim instead of getting parsed into a Kotlin type. That is what lets a
 * number survive both codecs byte for byte: `1.50` gets written back as `1.50`, not `1.5`.
 *
 * @property content the literal exactly as it appears on the wire, with the quotes of a JSON string
 *   already removed.
 * @property isString whether the value must be written as a quoted string. `content` is `30` for both
 *   the string `"30"` and the number `30`; this flag is the only thing telling them apart.
 */
public class ProblemPrimitive internal constructor(
    public val content: String,
    public val isString: Boolean,
) : ProblemValue {
    /**
     * Two primitives are equal when the literal and the string flag both match. That keeps the string
     * `"30"` and the number `30` apart, even though both share a [ProblemPrimitive.content] of `30`.
     */
    override fun equals(other: Any?): Boolean = other is ProblemPrimitive && content == other.content && isString == other.isString

    /** Derived from both properties, consistently with [equals]. */
    override fun hashCode(): Int = 31 * content.hashCode() + isString.hashCode()

    /** The value as it would be written to a JSON document, quoted when [ProblemPrimitive.isString]. */
    override fun toString(): String = if (isString) "\"$content\"" else content
}

/** A string extension value. It is written quoted, so `ProblemPrimitive("30")` is not the number 30. */
public fun ProblemPrimitive(value: String): ProblemPrimitive = ProblemPrimitive(value, isString = true)

/** An integer extension value. */
public fun ProblemPrimitive(value: Int): ProblemPrimitive = ProblemPrimitive(value.toString(), isString = false)

/**
 * A 64-bit integer extension value.
 *
 * JSON itself has one number type and no precision guarantee (RFC 8259 §6). A consumer parsing into
 * a `double` loses exactness past 2^53, so send an identifier that large as a string instead.
 */
public fun ProblemPrimitive(value: Long): ProblemPrimitive = ProblemPrimitive(value.toString(), isString = false)

/**
 * A floating-point extension value.
 *
 * [value] must be finite. A JSON number (RFC 8259 §6) cannot represent NaN or Infinity, and writing
 * one would emit a literal that conforming parsers reject. This guard sits here, at the producing
 * edge. [problemLiteral] stays the unguarded escape hatch for codecs.
 *
 * @throws IllegalArgumentException if [value] is NaN or infinite.
 */
public fun ProblemPrimitive(value: Double): ProblemPrimitive {
    require(value.isFinite()) {
        "NaN and Infinity have no JSON representation; a Double extension value must be finite, was $value"
    }
    return ProblemPrimitive(value.toString(), isString = false)
}

/** A boolean extension value, written as the unquoted literal `true` or `false`. */
public fun ProblemPrimitive(value: Boolean): ProblemPrimitive = ProblemPrimitive(value.toString(), isString = false)

/**
 * Builds a scalar from an already-encoded wire literal.
 *
 * Codecs use this because they already know how the value was encoded and must not re-encode it.
 * Application code should reach for the typed [ProblemPrimitive] factories.
 */
public fun problemLiteral(
    content: String,
    isString: Boolean,
): ProblemPrimitive = ProblemPrimitive(content, isString)

/**
 * The explicit JSON `null` of an extension member.
 *
 * Appendix B defines nothing for XML `null`, so the XML codec writes it as an empty element, which
 * reads back as the empty string. This asymmetry is documented and tested on purpose.
 */
public data object ProblemNull : ProblemValue

/**
 * A JSON array as an extension value. Implements `List<ProblemValue>` by delegation, so it can be
 * iterated and indexed directly.
 *
 * In XML this becomes a sequence of children all named `i` (Appendix B), where an empty array is
 * indistinguishable from an empty object and from `null`; all three read back as the empty string.
 * See the `problem-details-xml` module documentation.
 */
public class ProblemArray(
    private val items: List<ProblemValue>,
) : ProblemValue,
    List<ProblemValue> by items {
    /**
     * Compares the contained values against any `List`, not only another [ProblemArray]. `List`
     * equality requires that of a class implementing it, and it keeps `ProblemArray(items) == items`
     * true.
     */
    override fun equals(other: Any?): Boolean = items == other

    /** The contained list's own hash, so an equal plain `List` hashes the same. */
    override fun hashCode(): Int = items.hashCode()

    /** The array as it would be written to a JSON document. */
    override fun toString(): String = items.joinToString(separator = ",", prefix = "[", postfix = "]")
}

/**
 * A JSON object as an extension value. Implements `Map<String, ProblemValue>` by delegation, so
 * members can be read with `[]` and iterated directly.
 *
 * This is the nesting an extension member is allowed, and the reason both codecs are bounded by
 * [Problem.MAX_NESTING_DEPTH].
 */
public class ProblemObject(
    entries: Map<String, ProblemValue>,
) : ProblemValue,
    Map<String, ProblemValue> by entries {
    private val members: Map<String, ProblemValue> = entries

    /**
     * Compares the contained members against any `Map`, not only another [ProblemObject], for the
     * same reason [ProblemArray.equals] compares against any `List`.
     */
    override fun equals(other: Any?): Boolean = members == other

    /** The contained map's own hash, so an equal plain `Map` hashes the same. */
    override fun hashCode(): Int = members.hashCode()

    /** The object as it would be written to a JSON document. */
    override fun toString(): String = members.entries.joinToString(separator = ",", prefix = "{", postfix = "}") { (k, v) -> "\"$k\":$v" }
}

/*
 * Accessors come in pairs because two independent things can go wrong when reading an extension
 * member, and each pair handles them at different points.
 *
 *     problem.extensions["account"]   // ProblemValue?, null when the member is absent
 *         ?.problemObject             // throws when it is present but is not an object
 *
 * The `?.` covers absence. Choosing the strict accessor over the lenient one covers a wrong type.
 */

private fun ProblemValue.requirePrimitive(): ProblemPrimitive =
    this as? ProblemPrimitive ?: throw IllegalArgumentException("Expected a primitive, was ${this::class.simpleName}")

/**
 * This value as a scalar.
 *
 * The strict half of the pair, for a consumer that minted the problem type and wants a shape
 * mismatch to surface. A consumer reading someone else's problem type should prefer
 * [primitiveOrNull], since §3 treats a wrong-typed member as absent instead of failing the whole
 * document.
 *
 * @throws IllegalArgumentException if this value is an array, an object or [ProblemNull].
 * @see primitiveOrNull
 */
public val ProblemValue.primitive: ProblemPrimitive get() = requirePrimitive()

/**
 * This value as a scalar, or `null` if it is an array, an object or [ProblemNull].
 *
 * Combined with `extensions[name]?.`, this collapses three different situations into the same
 * `null`: an absent member, an explicit JSON null, and a wrong type. Use `name in
 * problem.extensions` to tell absence apart from the other two.
 */
public val ProblemValue.primitiveOrNull: ProblemPrimitive? get() = this as? ProblemPrimitive

/**
 * The scalar's wire literal as text, with the quotes of a JSON string already removed.
 *
 * This does not require the value to be a JSON string: the number `30` reads as `"30"`. Consult
 * [ProblemPrimitive.isString] when the distinction matters.
 *
 * @throws IllegalArgumentException if this value is not a scalar.
 * @see stringOrNull
 */
public val ProblemValue.string: String get() = requirePrimitive().content

/**
 * The scalar's wire literal as text, or `null` if this value is not a scalar.
 *
 * @see string
 */
public val ProblemValue.stringOrNull: String? get() = primitiveOrNull?.content

/**
 * The scalar parsed as an [Int].
 *
 * Reads the wire literal, not the JSON type, so a quoted `"30"` also returns `30`. This matches
 * `JsonPrimitive.int` in kotlinx.serialization.
 *
 * @throws IllegalArgumentException if this value is not a scalar.
 * @throws NumberFormatException if the literal is not an integer, or does not fit in an [Int].
 * @see intOrNull
 */
public val ProblemValue.int: Int get() = string.toInt()

/** The scalar parsed as an [Int], or `null` if this value is not a scalar or does not parse. */
public val ProblemValue.intOrNull: Int? get() = stringOrNull?.toIntOrNull()

/**
 * The scalar parsed as a [Long]. Reads the wire literal, so a quoted `"30"` also returns `30`.
 *
 * @throws IllegalArgumentException if this value is not a scalar.
 * @throws NumberFormatException if the literal is not an integer, or does not fit in a [Long].
 * @see longOrNull
 */
public val ProblemValue.long: Long get() = string.toLong()

/** The scalar parsed as a [Long], or `null` if this value is not a scalar or does not parse. */
public val ProblemValue.longOrNull: Long? get() = stringOrNull?.toLongOrNull()

/**
 * The scalar parsed as a [Double]. Reads the wire literal, so a quoted `"1.5"` also returns `1.5`.
 *
 * @throws IllegalArgumentException if this value is not a scalar.
 * @throws NumberFormatException if the literal is not a number.
 * @see doubleOrNull
 */
public val ProblemValue.double: Double get() = string.toDouble()

/** The scalar parsed as a [Double], or `null` if this value is not a scalar or does not parse. */
public val ProblemValue.doubleOrNull: Double? get() = stringOrNull?.toDoubleOrNull()

/**
 * The scalar parsed as a [Boolean].
 *
 * Only the literals `true` and `false` are accepted; `"TRUE"` and `1` both throw.
 *
 * @throws IllegalArgumentException if this value is not a scalar, or the literal is neither `true`
 *   nor `false`.
 * @see booleanOrNull
 */
public val ProblemValue.boolean: Boolean get() = string.toBooleanStrict()

/** The scalar parsed as a [Boolean], or `null` if this value is not a scalar or is not `true`/`false`. */
public val ProblemValue.booleanOrNull: Boolean? get() = stringOrNull?.toBooleanStrictOrNull()

/**
 * This value as an array.
 *
 * @throws IllegalArgumentException if this value is not an array.
 * @see problemArrayOrNull
 */
public val ProblemValue.problemArray: ProblemArray
    get() = this as? ProblemArray ?: throw IllegalArgumentException("Expected an array, was ${this::class.simpleName}")

/** This value as an array, or `null` if it is anything else. */
public val ProblemValue.problemArrayOrNull: ProblemArray? get() = this as? ProblemArray

/**
 * This value as an object.
 *
 * @throws IllegalArgumentException if this value is not an object.
 * @see problemObjectOrNull
 */
public val ProblemValue.problemObject: ProblemObject
    get() = this as? ProblemObject ?: throw IllegalArgumentException("Expected an object, was ${this::class.simpleName}")

/** This value as an object, or `null` if it is anything else. */
public val ProblemValue.problemObjectOrNull: ProblemObject? get() = this as? ProblemObject
