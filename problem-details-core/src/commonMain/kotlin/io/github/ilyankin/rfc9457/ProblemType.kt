package io.github.ilyankin.rfc9457

/**
 * A problem type as RFC 9457 §4 asks type authors to document it: a type URI, a title, and the
 * HTTP status code to use with it — the three items the RFC lists as the minimum for defining a new
 * problem type. Implementing this interface is how an application states those three once, in one
 * place, instead of restating them at every throw site.
 *
 * [title] describes the *type*, not the occurrence, and is therefore not overridable per problem —
 * RFC §3.1 says it SHOULD stay constant for a type except for localization, which is out of scope
 * for v1. Only [ProblemType.problem]'s `detail` varies per occurrence.
 *
 * [status] is a plain `Int` because this module must not depend on Ktor.
 */
public interface ProblemType {
    public val typeUri: String
    public val title: String
    public val status: Int
}

/** Builds a [Problem] for this type. `detail` and `instance` are the only per-occurrence fields. */
public fun ProblemType.problem(
    detail: String? = null,
    instance: String? = null,
): Problem = Problem(type = typeUri, status = status, title = title, detail = detail, instance = instance)
