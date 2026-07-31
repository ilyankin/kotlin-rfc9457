package io.github.ilyankin.rfc9457.ktor

import io.ktor.http.ContentType

/**
 * The two media types RFC 9457 registers (§6.1): `application/problem+json` and
 * `application/problem+xml`.
 *
 * Media types only — naming [Xml] here costs nothing and does not pull the XML codec in. The
 * codec lives in `problem-details-xml` and its registration function in `problem-details-ktor-xml`;
 * this module still has no way to *write* XML, which is the point of the split.
 */
public object ProblemContentTypes {
    /** `application/problem+json` — the canonical serialization (§3). */
    public val Json: ContentType = ContentType("application", "problem+json")

    /** `application/problem+xml` — the equivalent alternative defined in Appendix B. */
    public val Xml: ContentType = ContentType("application", "problem+xml")
}
