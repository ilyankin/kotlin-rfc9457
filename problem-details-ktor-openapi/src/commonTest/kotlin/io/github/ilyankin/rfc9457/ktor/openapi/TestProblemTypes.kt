package io.github.ilyankin.rfc9457.ktor.openapi

import io.github.ilyankin.rfc9457.ProblemType

internal object OutOfCredit : ProblemType {
    override val typeUri: String = "https://example.com/probs/out-of-credit"
    override val title: String = "You do not have enough credit."
    override val status: Int = 403
}

/** Shares [OutOfCredit]'s status, which is what makes the two collapse into one response. */
internal object AccountLocked : ProblemType {
    override val typeUri: String = "https://example.com/probs/account-locked"
    override val title: String = "This account is locked."
    override val status: Int = 403
}

internal class InsufficientFunds : RuntimeException("balance too low")
