package io.github.ilyankin.rfc9457.ktor

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ProblemType
import io.ktor.http.HttpStatusCode

/**
 * The `about:blank` problem for [status], with the title taken from Ktor's own reason phrase.
 *
 * RFC 9457 §4.2.1 says a document using `about:blank` SHOULD carry the status code's standard
 * reason phrase as its `title` — `HttpStatusCode.description` is exactly that table. Core cannot
 * offer this overload, since it must not depend on Ktor, and keeping a second reason-phrase table
 * there would be worse than passing the title in.
 *
 * The title is only as good as [status]'s `description`. For a code Ktor does not know — anything
 * [httpStatus] or `HttpStatusCode.fromValue` synthesizes — that description is the literal
 * `"Unknown Status Code"`, which is not a reason phrase and does not satisfy §4.2.1. Construct the
 * `HttpStatusCode` with the real phrase (`HttpStatusCode(499, "Client Closed Request")`) when the
 * code is outside Ktor's table.
 */
public fun Problem.Companion.blank(status: HttpStatusCode): Problem = blank(status.value, status.description)

/**
 * An `HttpStatusCode`-typed view over [ProblemType.status], which is a plain `Int` in core.
 *
 * `HttpStatusCode.fromValue` never rejects a value: for one it does not know it synthesizes a code
 * whose description is the literal `"Unknown Status Code"`, so a custom status passes through rather
 * than failing here. See [blank] for the one place that placeholder is visible to clients.
 */
public val ProblemType.httpStatus: HttpStatusCode get() = HttpStatusCode.fromValue(status)
