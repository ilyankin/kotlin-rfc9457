package io.github.ilyankin.rfc9457

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmField

/**
 * An RFC 9457 problem detail. This is the document carried by `application/problem+json` and
 * `application/problem+xml`.
 *
 * The five members §3.1 defines are named properties here. Anything else becomes a §3.2 extension
 * member. It lives in [Problem.extensions], and both codecs place it as a sibling of the standard
 * members, not inside an `extensions` key.
 *
 * Extension names are checked against [RESERVED_MEMBERS] alone, since reusing one of those five would
 * produce a document with a duplicate key. §3.2 also suggests starting with a letter, using only
 * `[A-Za-z0-9_]`, and staying at least three characters long. That guidance is a `SHOULD` a valid
 * document can ignore, so it stays documented here instead of enforced.
 *
 * @property type URI reference identifying the problem type. §3.1 treats this as the primary
 *   identifier of what went wrong, ahead of [Problem.title]. An absent `type` means [ABOUT_BLANK],
 *   which is why this has a default value instead of being nullable. Use an absolute URI where you
 *   can. A relative one resolves against the document's base URI, so its meaning shifts depending on
 *   where the document was fetched.
 * @property status the HTTP status code the origin server generated. §3.1 treats this as advisory
 *   only. Generators must make it agree with the real response status, but §5 still allows the two to
 *   diverge once an intermediary rewrites a response without touching its body, so consumers should
 *   key off the real status. §3.1 fixes what this member means without saying which values are legal,
 *   so this stays unchecked too.
 * @property title a short human-readable summary of the type. §3.1 asks it to stay constant across
 *   occurrences, localization aside.
 * @property detail a human-readable explanation of this occurrence. It exists to help the client
 *   correct the problem, not to carry debugging information, and §3.1 asks consumers to leave it
 *   unparsed. Machine-readable data belongs in an extension member.
 * @property instance URI reference identifying this specific occurrence. It need not be
 *   dereferenceable. Treat it as an opaque server-assigned identifier when it isn't.
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

    /** Holds what RFC 9457 fixes on the wire, the nesting limit both codecs share, and the `about:blank` factory. */
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
         * Both codecs walk the [ProblemValue] tree recursively, so an unbounded document exhausts the
         * call stack. A `StackOverflowError` is an `Error`, and that escapes ordinary handling. Past
         * this depth, both codecs throw `SerializationException`. One shared constant keeps them from
         * drifting apart on what they accept.
         */
        @JvmField
        // This stays a `val`, not a `const val`. A `const val` inlines into every consumer's
        // bytecode, and this value is shared across two separately published artifacts, so inlining
        // would let a problem-details-xml built against one release keep enforcing that release's
        // number against a newer core. That is the exact drift a shared constant exists to prevent.
        // `@JvmField` keeps Java's access at `Problem.MAX_NESTING_DEPTH` instead of
        // `Problem.Companion.getMAX_NESTING_DEPTH()`, without bringing inlining back.
        //
        // For calibration: Jackson defaults `StreamReadConstraints.maxNestingDepth` to 1000. The
        // JDK's XML parser caps `jdk.xml.maxElementDepth` at 100. kotlinx.serialization carries no
        // such setting at all, so this library needs its own guard.
        public val MAX_NESTING_DEPTH: Int = 64

        /**
         * The `about:blank` problem for [status]. [title] SHOULD be the status code's standard
         * reason phrase (§4.2.1, e.g. `"Not Found"` for 404). This module carries no reason-phrase
         * table, so the caller passes one in. `problem-details-ktor` adds an overload that fills it
         * from `HttpStatusCode.description`.
         */
        public fun blank(
            status: Int,
            title: String,
        ): Problem = Problem(type = ABOUT_BLANK, status = status, title = title)
    }
}
