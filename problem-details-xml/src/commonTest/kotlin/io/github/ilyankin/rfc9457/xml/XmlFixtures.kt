package io.github.ilyankin.rfc9457.xml

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ProblemArray
import io.github.ilyankin.rfc9457.ProblemPrimitive

internal object XmlFixtures {
    /**
     * RFC 9457 Appendix B's example, with insignificant inter-element whitespace removed because
     * the writer emits none. The absolute URIs matter: this is a different problem instance from the
     * JSON `out-of-credit` example, which uses relative ones.
     */
    val OUT_OF_CREDIT_XML: String =
        """<?xml version="1.0" encoding="UTF-8"?>""" +
            """<problem xmlns="urn:ietf:rfc:7807">""" +
            """<type>https://example.com/probs/out-of-credit</type>""" +
            """<title>You do not have enough credit.</title>""" +
            """<detail>Your current balance is 30, but that costs 50.</detail>""" +
            """<instance>https://example.net/account/12345/msgs/abc</instance>""" +
            """<balance>30</balance>""" +
            """<accounts><i>https://example.net/account/12345</i>""" +
            """<i>https://example.net/account/67890</i></accounts>""" +
            """</problem>"""

    fun outOfCreditProblem(): Problem =
        Problem(
            type = "https://example.com/probs/out-of-credit",
            title = "You do not have enough credit.",
            detail = "Your current balance is 30, but that costs 50.",
            instance = "https://example.net/account/12345/msgs/abc",
            extensions =
                mapOf(
                    "balance" to ProblemPrimitive(30),
                    "accounts" to
                        ProblemArray(
                            listOf(
                                ProblemPrimitive("https://example.net/account/12345"),
                                ProblemPrimitive("https://example.net/account/67890"),
                            ),
                        ),
                ),
        )
}
