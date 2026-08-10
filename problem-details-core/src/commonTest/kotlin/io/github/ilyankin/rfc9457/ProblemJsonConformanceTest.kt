package io.github.ilyankin.rfc9457

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private object NonJsonEncoderStub : kotlinx.serialization.encoding.AbstractEncoder() {
    override val serializersModule: kotlinx.serialization.modules.SerializersModule =
        kotlinx.serialization.modules.EmptySerializersModule()
}

class ProblemJsonConformanceTest :
    StringSpec({

        val json = Json

        "the RFC out-of-credit example parses to the expected Problem" {
            json.decodeFromString<Problem>(RfcFixtures.OUT_OF_CREDIT_JSON) shouldBe
                RfcFixtures.outOfCreditProblem()
        }

        "the expected Problem serializes back to the RFC out-of-credit document" {
            val encoded = json.encodeToString(RfcFixtures.outOfCreditProblem())
            json.parseToJsonElement(encoded) shouldBe
                json.parseToJsonElement(RfcFixtures.OUT_OF_CREDIT_JSON)
        }

        "the RFC validation-errors example round-trips" {
            json.decodeFromString<Problem>(RfcFixtures.VALIDATION_ERRORS_JSON) shouldBe
                RfcFixtures.validationErrorsProblem()
            json.parseToJsonElement(json.encodeToString(RfcFixtures.validationErrorsProblem())) shouldBe
                json.parseToJsonElement(RfcFixtures.VALIDATION_ERRORS_JSON)
        }

        "extensions are flattened as siblings, never nested under an extensions key" {
            val encoded = json.encodeToString(RfcFixtures.outOfCreditProblem())
            encoded shouldContain "\"balance\":30"
            encoded shouldNotContain "extensions"
        }

        "an integer extension is never emitted as a decimal" {
            json.encodeToString(Problem(extensions = mapOf("balance" to ProblemPrimitive(30)))) shouldContain
                "\"balance\":30"
        }

        "a foreign Json instance produces flattened extensions too" {
            val pretty =
                Json {
                    prettyPrint = true
                    explicitNulls = true
                }
            val encoded = pretty.encodeToString(RfcFixtures.outOfCreditProblem())
            pretty.parseToJsonElement(encoded).toString() shouldNotContain "\"extensions\""
            encoded shouldContain "balance"
        }

        "a wrong-typed status is ignored rather than fatal, per RFC 3.1" {
            val problem = json.decodeFromString<Problem>("""{"status":"403","title":"nope"}""")
            problem.status shouldBe null
            problem.title shouldBe "nope"
        }

        "a wrong-typed title is ignored rather than fatal" {
            val problem = json.decodeFromString<Problem>("""{"title":{"nested":true},"status":403}""")
            problem.title shouldBe null
            problem.status shouldBe 403
        }

        "an explicit null standard member is treated as absent" {
            val problem = json.decodeFromString<Problem>("""{"detail":null,"status":400}""")
            problem.detail shouldBe null
            problem.status shouldBe 400
        }

        "an absent type defaults to about:blank" {
            json.decodeFromString<Problem>("""{"status":404}""").type shouldBe "about:blank"
        }

        "unrecognized members are preserved as extensions" {
            val problem = json.decodeFromString<Problem>("""{"status":400,"whatever":{"a":[1,"2"]}}""")
            problem.extensions.keys shouldBe setOf("whatever")
            problem.extensions["whatever"]
                ?.problemObject
                ?.get("a")
                ?.problemArray
                ?.size shouldBe 2
        }

        "a non-JSON encoder is refused with a message pointing at the XML codec" {
            val error =
                shouldThrow<SerializationException> {
                    ProblemSerializer.serialize(NonJsonEncoderStub, Problem(status = 400))
                }
            error.message!! shouldContain "problem+xml"
        }

        "a well-formed JSON document that is not an object is refused, per RFC 3" {
            // §3's leniency covers wrong-typed *members*; a document of another shape is not a
            // problem document at all, and it fails as a SerializationException like any other
            // kotlinx one. It does not fail as the IllegalArgumentException that
            // JsonElement.jsonObject would leak.
            listOf("""[1,2,3]""", """null""", """"text"""", """42""").forEach { document ->
                shouldThrow<SerializationException> { json.decodeFromString<Problem>(document) }
            }
        }
    })
