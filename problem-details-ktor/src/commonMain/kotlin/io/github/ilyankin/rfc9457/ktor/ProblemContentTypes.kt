package io.github.ilyankin.rfc9457.ktor

import io.ktor.http.ContentType

/**
 * The two media types RFC 9457 registers (§6.1): `application/problem+json` and
 * `application/problem+xml`.
 *
 * Media types only. Naming [Xml] here costs nothing and doesn't pull the XML codec in; that lives
 * in `problem-details-xml`, registered through `problem-details-ktor-xml`. This module still has no
 * way to *write* XML, which is the point of the split.
 */
public object ProblemContentTypes {
    /** `application/problem+json`: the canonical serialization (§3). */
    public val Json: ContentType = ContentType("application", "problem+json")

    /** `application/problem+xml`: the equivalent alternative defined in Appendix B. */
    public val Xml: ContentType = ContentType("application", "problem+xml")
}
