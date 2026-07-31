package io.github.ilyankin.rfc9457

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.Contextual
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

/** A type the library cannot serialize on its own — it needs the application's serializers module. */
private class Money(
    val amount: String,
)

private object MoneySerializer : KSerializer<Money> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Money", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: Money,
    ): Unit = encoder.encodeString(value.amount)

    override fun deserialize(decoder: Decoder): Money = Money(decoder.decodeString())
}

@Serializable
private data class Priced(
    val label: String,
    @Contextual val price: Money,
)

@Serializable
private data class TracedRequest(
    val traceId: String,
)

@Serializable
private data class OnlyBalance(
    val balance: Int,
)

/** What an application would have in its DI container: a module, and no leniency of its own. */
private val applicationJson =
    Json {
        serializersModule = SerializersModule { contextual(Money::class, MoneySerializer) }
    }

class CallerSuppliedJsonTest :
    StringSpec({

        "the default Json cannot encode a payload that needs the application's serializers module" {
            shouldThrow<SerializationException> {
                problem {
                    extensions(Priced(label = "cup", price = Money("3.50")))
                }
            }
        }

        "a caller-supplied Json carries its serializers module into extension encoding" {
            val problem =
                problem(json = applicationJson) {
                    extensions(Priced(label = "cup", price = Money("3.50")))
                }
            problem.extensions["label"]?.string shouldBe "cup"
            problem.extensions["price"]?.string shouldBe "3.50"
        }

        "a caller-supplied Json carries its serializers module into extension decoding" {
            val problem =
                problem(json = applicationJson) {
                    extensions(Priced(label = "cup", price = Money("3.50")))
                }
            problem.extensionsAs<Priced>(json = applicationJson).price.amount shouldBe "3.50"
        }

        "a caller's naming strategy decides the extension member names" {
            @OptIn(ExperimentalSerializationApi::class)
            val snakeCase = Json { namingStrategy = JsonNamingStrategy.SnakeCase }
            val problem =
                problem(json = snakeCase) {
                    extensions(TracedRequest(traceId = "abc-123"))
                }
            problem.extensions.keys shouldBe setOf("trace_id")
        }

        "reading stays lenient about foreign members even when the caller's Json is strict" {
            applicationJson.configuration.ignoreUnknownKeys shouldBe false
            val problem =
                problem {
                    extension("balance", 30)
                    extension("traceId", "abc-123")
                }
            problem.extensionsAs<OnlyBalance>(json = applicationJson) shouldBe OnlyBalance(balance = 30)
        }

        "a single member decodes through the caller's Json too" {
            val problem =
                problem(json = applicationJson) {
                    extension("price", Money("3.50"), MoneySerializer)
                }
            problem.extension("price", MoneySerializer, json = applicationJson)?.amount shouldBe "3.50"
        }
    })
