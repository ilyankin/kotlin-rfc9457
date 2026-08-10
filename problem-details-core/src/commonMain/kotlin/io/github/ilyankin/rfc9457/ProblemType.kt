package io.github.ilyankin.rfc9457

/**
 * A problem type carrying the three things §4 asks a type author to define: a type URI, a title, and
 * the HTTP status code to use with it. Implementing this interface states those three once, instead
 * of restating them at every throw site.
 *
 * [ProblemType.title] describes the type, so it is not overridable per problem. §3.1 asks it to stay
 * constant except for localization, which is out of scope for v1. Only the `detail` and `instance` of
 * [ProblemType.problem] vary per occurrence.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457#section-4">RFC 9457 §4, Defining New Problem Types</a>
 */
public interface ProblemType {
    /**
     * The URI reference identifying this problem type: the document's `type` member.
     *
     * Named `typeUri`, not `type`, so that implementing this interface as an `enum class` or a
     * `sealed` hierarchy does not collide with a `type` in the implementor's own vocabulary. §3.1
     * recommends an absolute URI. A relative one resolves against the document's base URI, so its
     * meaning shifts depending on where the document was fetched.
     */
    public val typeUri: String

    /** Short human-readable summary of this type, constant across occurrences (§3.1). */
    public val title: String

    /**
     * The HTTP status code to respond with for this type.
     */
    public val status: Int
}

/**
 * Builds a [Problem] for this type. `detail` and `instance` are the only per-occurrence fields.
 *
 * @sample io.github.ilyankin.rfc9457.samples.problemFromProblemType
 */
public fun ProblemType.problem(
    detail: String? = null,
    instance: String? = null,
): Problem = Problem(type = typeUri, status = status, title = title, detail = detail, instance = instance)
