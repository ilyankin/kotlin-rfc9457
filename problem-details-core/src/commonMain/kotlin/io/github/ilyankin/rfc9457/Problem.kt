package io.github.ilyankin.rfc9457

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmField

/**
 * An RFC 9457 problem detail — the document carried by `application/problem+json` and
 * `application/problem+xml`.
 *
 * The five members RFC 9457 §3.1 defines are named properties. Everything else is a §3.2 extension
 * member, lives in [extensions], and is written as a *sibling* of the standard members by both
 * codecs — never nested under an `extensions` key.
 *
 * Extension member names are validated only against [RESERVED_MEMBERS], which would produce a
 * document with a duplicated key. RFC §3.2's further guidance — start with a letter, use only
 * `[A-Za-z0-9_]`, three characters or longer — is `SHOULD`, and a name that ignores it still yields
 * a valid document (it merely travels less well through the XML format), so it is documented here
 * rather than enforced. The rule is: enforce what breaks the wire, document what is inadvisable.
 *
 * @property type URI reference identifying the problem *type*. Per §3.1 a consumer MUST treat this,
 *   not [title], as the primary identifier of what went wrong, and an absent `type` means
 *   [ABOUT_BLANK] — which is why this property defaults to it rather than being nullable. The RFC
 *   recommends an absolute URI: a relative reference resolves against the document's base URI
 *   (RFC 3986 §5) and therefore means different things depending on where the document was fetched
 *   from.
 * @property status the HTTP status code the origin server generated. §3.1 calls it **advisory
 *   only**: a generator MUST make it agree with the real response status, but a consumer should key
 *   off the actual HTTP status, because §5 notes the two can legitimately disagree once an
 *   intermediary has rewritten the response without touching the body. Its purpose is recovering
 *   the original status after the document has passed through caches, logs or proxies. The value
 *   is deliberately unvalidated — §3.1 pins the meaning, not a numeric range; agreeing with the
 *   real response status is the responder's job, not this type's.
 * @property title short human-readable summary **of the type**, not of this occurrence: §3.1 says it
 *   SHOULD stay the same across occurrences, localization aside.
 * @property detail human-readable explanation of *this* occurrence. §3.1 asks that it help the
 *   client correct the problem rather than carry debugging information, and that consumers SHOULD
 *   NOT parse it programmatically — anything machine-readable belongs in an extension member.
 * @property instance URI reference identifying this specific occurrence. It need not be
 *   dereferenceable; when it is not, treat it as an opaque server-assigned identifier.
 * @property extensions the §3.2 extension members, keyed by member name.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457">RFC 9457, Problem Details for HTTP APIs</a>
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
        // Strict on write: an extension member named like a standard one (§3.1) would serialize to
        // a document with a duplicated key, which is why this throws rather than warns. Reading is
        // deliberately lenient instead — see ProblemSerializer.
        // RESERVED_MEMBERS resolves unqualified: companion members are in scope inside the class.
        val reserved = extensions.keys intersect RESERVED_MEMBERS
        require(reserved.isEmpty()) {
            "Extension member(s) $reserved collide with reserved RFC 9457 members; " +
                "standard members must be set through the corresponding property"
        }
    }

    public companion object {
        /**
         * The RFC's built-in problem type, and the sole initial entry in the "HTTP Problem Types"
         * IANA registry (§4.2.1): "this problem has no more specific semantics beyond the HTTP
         * status code itself". §3.1 makes it the meaning of an absent `type` member, so emitting it
         * explicitly and omitting `type` are equivalent to a conforming consumer.
         */
        public const val ABOUT_BLANK: String = "about:blank"

        /**
         * The five member names RFC 9457 §3.1 defines. An extension member may not reuse any of
         * them. Public because both codecs need it and the XML one lives in another module.
         */
        public val RESERVED_MEMBERS: Set<String> =
            setOf("type", "status", "title", "detail", "instance")

        /**
         * How deeply an extension value may nest before either codec refuses it.
         *
         * Both codecs walk the [ProblemValue] tree recursively in both directions, so an unbounded
         * document exhausts the call stack — a `StackOverflowError`, which is an `Error` rather than
         * an `Exception` and therefore escapes ordinary handling. Since problem documents arrive
         * over the network, the limit is enforced rather than merely documented: past it, both
         * codecs throw `SerializationException`.
         *
         * It lives here, in core, so the JSON and XML codecs cannot drift apart about what they will
         * accept — a document one of them emits must be one the other can read.
         *
         * 64 is far beyond any real document: §3.1 defines five flat members, and the deepest
         * example the RFC publishes nests three levels. For calibration, Jackson's equivalent
         * (`StreamReadConstraints.maxNestingDepth`, added in 2.15 in response to CVE-2025-52999)
         * defaults to 1000 and is proposed to drop to 500, while the JDK's XML parser has capped
         * `jdk.xml.maxElementDepth` at 100 since JDK 25. kotlinx.serialization offers no such
         * setting at all — `JsonConfiguration` has no depth or size parameter — which is why this
         * has to be the library's own guard rather than something a caller can configure.
         *
         * Deliberately not a codec parameter. Adding an optional `maxDepth` argument to an *existing*
         * entry point would change its JVM signature and break precompiled callers — but that is the
         * only door 1.0 closes. A new overload, or an entry point taking a configuration object built
         * from this library's own types, stays binary-compatible and can be introduced whenever a
         * real need appears. What 1.0 freezes is the default, not the possibility; do not read this
         * paragraph as a refusal of a later, well-motivated request.
         *
         * Deliberately not `const`, unlike the wire constants above it, and the distinction is worth
         * keeping: a `const val` is inlined into every consumer's bytecode. This value is shared
         * across two separately published artifacts on purpose, so inlining it would let them drift
         * — `problem-details-xml` compiled against one release would keep enforcing that release's
         * number while a newer core enforced another, which is the exact divergence the shared
         * constant exists to prevent. [ABOUT_BLANK] and the XML names may be `const` because the RFC
         * fixes them forever; this one is a tuning decision that has to stay readable at runtime.
         *
         * `@JvmField` keeps it a plain static read from Java (`Problem.MAX_NESTING_DEPTH`) rather
         * than `Problem.Companion.getMAX_NESTING_DEPTH()`, without restoring the inlining.
         */
        @JvmField
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
