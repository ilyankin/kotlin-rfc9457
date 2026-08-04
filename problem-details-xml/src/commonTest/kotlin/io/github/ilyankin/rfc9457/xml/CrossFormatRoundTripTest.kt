package io.github.ilyankin.rfc9457.xml

import io.github.ilyankin.rfc9457.Problem
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/**
 * RFC 9457 §3's JSON example, restated here because core's test fixtures are not on this
 * module's test classpath. Chosen deliberately: it has no nulls, no empty collections and no
 * number-like strings, so it is expected to survive XML losslessly. The loss cases live in
 * [ProblemXmlReaderTest].
 */
private val OUT_OF_CREDIT_JSON =
    """
    {"type":"https://example.com/probs/out-of-credit","title":"You do not have enough credit.",
    "detail":"Your current balance is 30, but that costs 50.","instance":"/account/12345/msgs/abc",
    "balance":30,"accounts":["/account/12345","/account/67890"]}
    """.trimIndent().replace("\n", "")

class CrossFormatRoundTripTest :
    StringSpec({

        "JSON to Problem to XML to Problem to JSON returns the original document" {
            val json = Json
            val fromJson = json.decodeFromString<Problem>(OUT_OF_CREDIT_JSON)
            val viaXml = ProblemXml.decodeFromString(ProblemXml.encodeToString(fromJson))
            viaXml shouldBe fromJson
            json.parseToJsonElement(json.encodeToString(viaXml)) shouldBe
                json.parseToJsonElement(OUT_OF_CREDIT_JSON)
        }
    })
