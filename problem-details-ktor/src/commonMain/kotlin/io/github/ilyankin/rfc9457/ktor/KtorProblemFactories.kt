package io.github.ilyankin.rfc9457.ktor

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ProblemType
import io.ktor.http.HttpStatusCode

/**
 * The `about:blank` problem for [status], with the title taken from Ktor's own reason phrase.
 *
 * RFC 9457 §4.2.1 says `about:blank` SHOULD carry the status code's standard reason phrase as
 * `title` — `HttpStatusCode.description` is exactly that table. Core can't offer this overload
 * itself, since it must not depend on Ktor.
 *
 * The title is only as good as [status]'s `description`: for a code Ktor doesn't know, that's the
 * literal `"Unknown Status Code"`, not a real reason phrase. Construct the `HttpStatusCode` with
 * the real phrase (`HttpStatusCode(499, "Client Closed Request")`) for codes outside Ktor's table.
 */
public fun Problem.Companion.blank(status: HttpStatusCode): Problem = blank(status.value, status.description)

/**
 * An `HttpStatusCode`-typed view over [ProblemType.status], which is a plain `Int` in core.
 *
 * `HttpStatusCode.fromValue` never rejects a value — for an unknown code it synthesizes one with
 * description `"Unknown Status Code"` rather than failing. See [blank] for where that shows up.
 */
public val ProblemType.httpStatus: HttpStatusCode get() = HttpStatusCode.fromValue(status)
