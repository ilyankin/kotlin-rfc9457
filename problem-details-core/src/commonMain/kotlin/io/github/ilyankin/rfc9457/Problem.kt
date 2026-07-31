package io.github.ilyankin.rfc9457

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmField

/**
 * An RFC 9457 problem detail — the document carried by `application/problem+json` and
 * `application/problem+xml`.
 *
 * The five members §3.1 defines are named properties. Everything else is a §3.2 extension member,
 * lives in [Problem.extensions], and is written as a *sibling* of the standard members by both
 * codecs — never nested under an `extensions` key.
 *
 * Extension names are checked against [RESERVED_MEMBERS] alone, because reusing one would produce a
 * document with a duplicated key. §3.2's remaining guidance — start with a letter, use only
 * `[A-Za-z0-9_]`, three characters or longer — is a `SHOULD` that a valid document may ignore, so it
 * is documented rather than enforced.
 *
 * @property type URI reference identifying the problem *type*. §3.1 makes this, not [Problem.title],
 *   the primary identifier of what went wrong, and an absent `type` means [ABOUT_BLANK] — hence the
 *   default rather than a nullable. Prefer an absolute URI: a relative one resolves against the
 *   document's base URI, so it means different things depending on where the document was fetched.
 * @property status the HTTP status code the origin server generated — **advisory only** (§3.1).
 *   Generators MUST make it agree with the real response status, but consumers should key off that
 *   status instead, since §5 allows the two to diverge once an intermediary has rewritten a response
 *   without touching its body. Deliberately not range-checked: §3.1 fixes what the member means, not
 *   which values are legal.
 * @property title short human-readable summary **of the type**, which §3.1 asks to stay constant
 *   across occurrences, localization aside.
 * @property detail human-readable explanation of *this* occurrence, meant to help the client correct
 *   it rather than to carry debugging information. §3.1 asks consumers not to parse it — anything
 *   machine-readable belongs in an extension member.
 * @property instance URI reference identifying this specific occurrence. Need not be
 *   dereferenceable; when it is not, treat it as an opaque server-assigned identifier.
 * @property extensions the §3.2 extension members, keyed by member name.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457#section-3">RFC 9457 §3, The Problem Details JSON Object</a>
 */
@Serializable(with = ProblemSerializer::class)
public data class Problem(
    public val type: String = ABOUT_BLANK,
    public val status: Int? = null,
    public val title: String? = null,
    public val detail: String? = null,
    public val instance: String? = null,
    public val extensions: Map<String, ProblemValue> = emptyMap(),
) {
    init {
        val reserved = extensions.keys intersect RESERVED_MEMBERS
        require(reserved.isEmpty()) {
            "Extension member(s) $reserved collide with reserved RFC 9457 members; " +
                "standard members must be set through the corresponding property"
        }
    }

    /** The wire constants RFC 9457 fixes, the limit both codecs share, and the `about:blank` factory. */
    public companion object {
        /**
         * The RFC's built-in problem type, and the sole initial entry in the "HTTP Problem Types"
         * registry (§4.2.1): "this problem has no more specific semantics beyond the HTTP status
         * code itself". §3.1 makes it the meaning of an absent `type`, so emitting it explicitly and
         * omitting the member are equivalent to a conforming consumer.
         */
        public const val ABOUT_BLANK: String = "about:blank"

        /**
         * The five member names §3.1 defines, which no extension member may reuse.
         */
        public val RESERVED_MEMBERS: Set<String> =
            setOf("type", "status", "title", "detail", "instance")

        /**
         * How deeply an extension value may nest before either codec refuses it.
         *
         * Both codecs walk the [ProblemValue] tree recursively, so an unbounded document exhausts
         * the call stack — and a `StackOverflowError` is an `Error`, which escapes ordinary
         * handling. Past this depth they throw `SerializationException` instead. One shared
         * constant, so the two codecs cannot drift apart about what they accept.
         */
        @JvmField
        // Deliberately not `const`, unlike the wire constants above. A `const val` is inlined into
        // every consumer's bytecode, and this value is shared across two separately published
        // artifacts — inlining would let a problem-details-xml built against one release keep
        // enforcing that release's number against a newer core, the exact drift one shared constant
        // exists to prevent. `@JvmField` keeps Java's access at `Problem.MAX_NESTING_DEPTH` rather
        // than `Problem.Companion.getMAX_NESTING_DEPTH()`, without restoring the inlining.
        //
        // For calibration: Jackson's `StreamReadConstraints.maxNestingDepth` defaults to 1000, the
        // JDK's XML parser caps `jdk.xml.maxElementDepth` at 100, and kotlinx.serialization has no
        // such setting at all — which is why this has to be the library's own guard.
        public val MAX_NESTING_DEPTH: Int = 64

        /**
         * The `about:blank` problem for [status]. [title] SHOULD be the status code's standard
         * reason phrase (§4.2.1, e.g. `"Not Found"` for 404); this module has no reason-phrase
         * table, so it is passed in. `problem-details-ktor` adds an overload that fills it from
         * `HttpStatusCode.description`.
         */
        public fun blank(
            status: Int,
            title: String,
        ): Problem = Problem(type = ABOUT_BLANK, status = status, title = title)
    }
}
