package io.github.ilyankin.rfc9457

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun nestedValue(depth: Int): ProblemValue {
    var value: ProblemValue = ProblemPrimitive("leaf")
    repeat(depth) { value = ProblemObject(mapOf("a" to value)) }
    return value
}

class ProblemValueJsonTest :
    StringSpec({

        "encoding an extension nested past the limit throws instead of overflowing the stack" {
            // Both conversions recurse per level. Unguarded, this was a StackOverflowError at
            // ~10 000 levels, an Error, so it slips past ordinary handling. kotlinx has no setting
            // that could bound it: JsonConfiguration exposes no depth or size parameter at all.
            val problem = Problem(extensions = mapOf("deep" to nestedValue(Problem.MAX_NESTING_DEPTH + 5)))
            shouldThrow<SerializationException> { Json.encodeToString(problem) }
        }

        "decoding an extension nested past the limit throws instead of overflowing the stack" {
            val document =
                buildString {
                    append("""{"type":"about:blank","deep":""")
                    repeat(Problem.MAX_NESTING_DEPTH + 5) { append("""{"a":""") }
                    append("1")
                    repeat(Problem.MAX_NESTING_DEPTH + 5) { append("}") }
                    append("}")
                }
            shouldThrow<SerializationException> { Json.decodeFromString<Problem>(document) }
        }

        "nesting up to the limit still round-trips" {
            // MAX_NESTING_DEPTH - 1 wrappers puts the deepest value exactly at the limit.
            val problem = Problem(extensions = mapOf("deep" to nestedValue(Problem.MAX_NESTING_DEPTH - 1)))
            Json.decodeFromString<Problem>(Json.encodeToString(problem)) shouldBe problem
        }

        "the limit is shared with the XML codec rather than duplicated" {
            // Both codecs read Problem.MAX_NESTING_DEPTH. If one ever grew its own constant, a
            // document one codec emits could be one the other refuses.
            Problem.MAX_NESTING_DEPTH shouldBe 64
        }

        "an integer literal survives as an unquoted number" {
            ProblemPrimitive(30).toJsonElement().toString() shouldBe "30"
        }

        "a high-precision decimal is neither quoted nor truncated" {
            val literal = "3.141592653589793238462643383279"
            ProblemPrimitive(literal, isString = false).toJsonElement().toString() shouldBe literal
        }

        "a string stays quoted" {
            ProblemPrimitive("30").toJsonElement().toString() shouldBe "\"30\""
        }

        "null maps both ways" {
            ProblemNull.toJsonElement() shouldBe JsonNull
            (JsonNull as kotlinx.serialization.json.JsonElement).toProblemValue() shouldBe ProblemNull
        }

        "arrays and objects map both ways" {
            val value =
                ProblemObject(
                    mapOf(
                        "balance" to ProblemPrimitive(30),
                        "accounts" to ProblemArray(listOf(ProblemPrimitive("/a"), ProblemPrimitive("/b"))),
                    ),
                )
            val json =
                buildJsonObject {
                    put("balance", JsonPrimitive(30))
                    put(
                        "accounts",
                        buildJsonArray {
                            add(JsonPrimitive("/a"))
                            add(JsonPrimitive("/b"))
                        },
                    )
                }
            value.toJsonElement() shouldBe json
            json.toProblemValue() shouldBe value
        }

        "round-tripping preserves the number-versus-string distinction" {
            val number = ProblemPrimitive(30)
            val text = ProblemPrimitive("30")
            number.toJsonElement().toProblemValue() shouldBe number
            text.toJsonElement().toProblemValue() shouldBe text
        }
    })
