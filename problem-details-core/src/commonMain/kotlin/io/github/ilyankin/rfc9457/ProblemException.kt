package io.github.ilyankin.rfc9457

/**
 * A [Problem] that can be thrown.
 *
 * Domain code can raise a problem document without depending on a web framework. This type lives in
 * `problem-details-core`, and `problem-details-ktor` answers a thrown one with the document it
 * carries. Build one from a declared problem type with [exception], or wrap a document directly.
 *
 * **Carries a [Problem] instead of being one.** Making [Problem] an interface would let this
 * implement it, at the cost of the model's value semantics (`copy`, `equals`, destructuring) and of
 * the single concrete type both codecs serialize. Nothing in the library substitutes an exception
 * where a document is expected, so that trade buys nothing.
 *
 * This class is final. [ProblemType] already declares a problem type once, and a subclass would
 * restate its `typeUri`, `title` and `status` alongside it. Catch this type and switch on
 * [Problem.type] instead of defining an exception class per problem type.
 *
 * Reach for this when the document gets assembled at the throw site, from data only that site has.
 * To turn an existing exception type into a problem, map it in the `problem-details-ktor` catalog.
 * That leaves the exception itself free of any dependency on this library.
 *
 * @property problem the document this exception carries.
 * @param cause the underlying failure, if any. Chained through [Throwable.cause], and logged
 *   server-side when `problem-details-ktor` answers the exception, since it would otherwise be
 *   dropped without a trace.
 * @see exception
 */
public class ProblemException(
    public val problem: Problem,
    cause: Throwable? = null,
) : RuntimeException(problemMessage(problem), cause)

/**
 * The exception message. It carries status, type, and title when present, never `detail`.
 *
 * `type` and `title` identify the problem type and stay constant across occurrences (§3.1), which is
 * what makes the line worth grepping. `detail` describes one occurrence, is unbounded, and already
 * travels in the response document.
 *
 * A second reason to keep it short and type-shaped: with no `StatusPages` handler installed, Ktor's
 * engine puts `Throwable.message` into the response body (`handleFailure` → `tryRespondError`). Every
 * member used here is client-facing already, so nothing leaks. Still, the message should read as an
 * identifier, not a second copy of the document.
 */
private fun problemMessage(problem: Problem): String =
    buildString {
        problem.status?.let {
            append(it)
            append(' ')
        }
        append(problem.type)
        problem.title?.let {
            append(": ")
            append(it)
        }
    }

/**
 * Raises this problem type as a [ProblemException].
 *
 * The throwing twin of [ProblemType.problem], with the same per-occurrence parameters. `typeUri`,
 * `title` and `status` come from the type and cannot be overridden per call, because §3.1 asks
 * `title` to stay constant across occurrences.
 *
 * For a problem that needs extension members, build the document and wrap it directly:
 * `throw ProblemException(problem { type(OutOfCredit); extension("balance", 30) })`.
 *
 * @param detail human-readable explanation of this occurrence.
 * @param instance URI reference identifying this occurrence. Left unset, `problem-details-ktor`
 *   fills it from the request path.
 * @param cause the underlying failure. Chained and logged, never exposed to the client.
 * @sample io.github.ilyankin.rfc9457.samples.throwProblemForOutOfCredit
 */
public fun ProblemType.exception(
    detail: String? = null,
    instance: String? = null,
    cause: Throwable? = null,
): ProblemException = ProblemException(problem(detail, instance), cause)
