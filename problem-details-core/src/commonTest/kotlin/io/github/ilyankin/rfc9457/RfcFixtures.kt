package io.github.ilyankin.rfc9457

/**
 * Reference documents taken verbatim from RFC 9457's own examples, so the codec tests check
 * conformance to the standard, not internal round-trip consistency.
 *
 * The JSON and XML `out-of-credit` examples are different problem instances. The JSON one uses
 * relative URIs; the XML one uses absolute URIs. They stay separate fixtures for that reason.
 */
internal object RfcFixtures {
    /** RFC 9457 §3.2, first example. */
    val OUT_OF_CREDIT_JSON: String =
        """
        {
          "type": "https://example.com/probs/out-of-credit",
          "title": "You do not have enough credit.",
          "detail": "Your current balance is 30, but that costs 50.",
          "instance": "/account/12345/msgs/abc",
          "balance": 30,
          "accounts": ["/account/12345", "/account/67890"]
        }
        """.trimIndent()

    fun outOfCreditProblem(): Problem =
        Problem(
            type = "https://example.com/probs/out-of-credit",
            title = "You do not have enough credit.",
            detail = "Your current balance is 30, but that costs 50.",
            instance = "/account/12345/msgs/abc",
            extensions =
                mapOf(
                    "balance" to ProblemPrimitive(30),
                    "accounts" to
                        ProblemArray(
                            listOf(ProblemPrimitive("/account/12345"), ProblemPrimitive("/account/67890")),
                        ),
                ),
        )

    /** RFC 9457 §3.2, validation-errors example. JSON only; the RFC publishes no XML form. */
    val VALIDATION_ERRORS_JSON: String =
        """
        {
          "type": "https://example.net/validation-error",
          "title": "Your request is not valid.",
          "errors": [
            { "detail": "must be a positive integer", "pointer": "#/age" },
            { "detail": "must be 'green', 'red' or 'blue'", "pointer": "#/profile/color" }
          ]
        }
        """.trimIndent()

    fun validationErrorsProblem(): Problem =
        Problem(
            type = "https://example.net/validation-error",
            title = "Your request is not valid.",
            extensions =
                mapOf(
                    "errors" to
                        ProblemArray(
                            listOf(
                                ProblemObject(
                                    mapOf(
                                        "detail" to ProblemPrimitive("must be a positive integer"),
                                        "pointer" to ProblemPrimitive("#/age"),
                                    ),
                                ),
                                ProblemObject(
                                    mapOf(
                                        "detail" to ProblemPrimitive("must be 'green', 'red' or 'blue'"),
                                        "pointer" to ProblemPrimitive("#/profile/color"),
                                    ),
                                ),
                            ),
                        ),
                ),
        )
}
