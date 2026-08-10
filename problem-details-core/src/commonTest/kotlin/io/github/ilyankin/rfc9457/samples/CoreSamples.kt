package io.github.ilyankin.rfc9457.samples

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ProblemType
import io.github.ilyankin.rfc9457.exception
import io.github.ilyankin.rfc9457.extension
import io.github.ilyankin.rfc9457.extensionsAs
import io.github.ilyankin.rfc9457.problem
import kotlinx.serialization.Serializable

/*
 * Bodies of these functions are inlined into the API documentation by the `@sample` KDoc tag, so
 * this file is documentation that the compiler checks. Renaming an API breaks the build instead of
 * leaving a stale example behind. `SamplesTest` additionally runs them.
 *
 * Two constraints, both enforced by Dokka, not by convention: nothing here may import a test
 * framework, since Dokka analyses this directory against `commonMain`'s classpath, which has none,
 * and the directory is attached to the `commonMain` source set alone.
 */

/** @see io.github.ilyankin.rfc9457.problem */
internal fun buildProblem(): Problem =
    problem {
        type = "https://example.net/validation-error"
        status = 422
        title = "Your request is not valid."
        detail = "The 'age' field must be a positive integer."
        instance = "/account/12345/msgs/abc"

        // Written as a sibling of the standard members, not nested under an "extensions" key.
        extension("invalidField", "age")
    }

@Serializable
internal data class Account(
    val id: String,
    val balance: Int,
)

/** @see io.github.ilyankin.rfc9457.extension */
internal fun buildProblemWithTypedExtension(): Problem =
    problem {
        type = "https://example.com/probs/out-of-credit"
        status = 403
        title = "You do not have enough credit."
        detail = "Your current balance is 30, but that costs 50."

        // Serialized with kotlinx and added as one extension member named "account".
        extension("account", Account(id = "12345", balance = 30))
    }

/** @see io.github.ilyankin.rfc9457.ProblemType.problem */
internal fun problemFromProblemType(): Problem {
    // An application states a problem type's URI, title and status once, in one place.
    val outOfCredit =
        object : ProblemType {
            override val typeUri: String = "https://example.com/probs/out-of-credit"
            override val title: String = "You do not have enough credit."
            override val status: Int = 403
        }

    // Only `detail` and `instance` vary per occurrence.
    return outOfCredit.problem(detail = "Your current balance is 30, but that costs 50.")
}

@Serializable
internal data class OutOfCredit(
    val balance: Int,
    val accounts: List<String>,
)

/** @see io.github.ilyankin.rfc9457.exception */
internal fun throwProblemForOutOfCredit(): Nothing {
    val outOfCredit =
        object : ProblemType {
            override val typeUri: String = "https://example.com/probs/out-of-credit"
            override val title: String = "You do not have enough credit."
            override val status: Int = 403
        }

    // Thrown from domain code: nothing on this path depends on a web framework. The Ktor
    // integration answers it with this document, filling `instance` from the request path.
    throw outOfCredit.exception(detail = "Your current balance is 30, but that costs 50.")
}

/** @see io.github.ilyankin.rfc9457.extensionsAs */
internal fun readTypedExtensions(problem: Problem): OutOfCredit =
    // Extension members the type does not declare are ignored, as RFC 9457 §3.2 requires. That is
    // also why this reads a document produced by a newer version of the problem type.
    problem.extensionsAs<OutOfCredit>()
