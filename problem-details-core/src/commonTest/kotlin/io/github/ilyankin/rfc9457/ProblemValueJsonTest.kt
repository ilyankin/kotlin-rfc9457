package io.github.ilyankin.rfc9457

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ProblemValueJsonTest :
    StringSpec({

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
