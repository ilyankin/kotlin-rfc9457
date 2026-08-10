package io.github.ilyankin.rfc9457

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

/**
 * The [Json] used to convert typed extension payloads when the caller supplies none. That covers the
 * values passed to `extensions(…)` and `extension(name, …)`, and the types read back by
 * [extensionsAs].
 *
 * This does not govern the problem document itself. [ProblemSerializer] encodes through whatever
 * `Json` the caller is using, so an application's own formatting settings already apply on the wire.
 *
 * Override it per call, with `problem(json = myJson) { … }` or `extensionsAs<T>(json = myJson)`,
 * whenever the payload depends on how the application configures kotlinx.serialization. That includes
 * a `serializersModule` holding contextual or polymorphic serializers, where the default instance
 * fails outright, or a `namingStrategy`, or a `classDiscriminator`. Passing an instance from a DI
 * container is the intended use. Nothing here is global or mutable.
 *
 * `ignoreUnknownKeys` gets re-applied on top of a caller's instance. §3.2 requires that "consumers
 * MUST ignore extension members they don't recognize", the rule that lets a problem type gain members
 * without breaking clients written against the older shape. `encodeDefaults` departs from kotlinx's
 * own default so a payload's defaulted properties still reach the wire.
 */
public val ProblemJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

/**
 * Converts to the JSON tree. Non-string scalars go through [JsonUnquotedLiteral], not
 * `JsonPrimitive(Number)`. That is what preserves an exact literal. kotlinx's own documentation
 * prescribes it for values that must not be quoted or truncated.
 */
@PublishedApi
internal fun ProblemValue.toJsonElement(): JsonElement = toJsonElement(depth = 0)

/**
 * Converts from the JSON tree. [JsonNull] is checked first because it is a [JsonPrimitive].
 *
 * The guard below covers only the recursion this library owns. kotlinx has already built the whole
 * `JsonElement` tree by the time this runs, so a document deep enough to overflow its own parser
 * never arrives here. Measured against kotlinx-serialization 1.11.0: nested objects survive past
 * 50 000 levels ([#1594](https://github.com/Kotlin/kotlinx.serialization/issues/1594) moved them to
 * `DeepRecursiveFunction`), and nested arrays still overflow around 5 000, since the same fix was
 * [declined for them](https://github.com/Kotlin/kotlinx.serialization/issues/1703). That residue sits
 * upstream, outside this library's reach.
 */
@PublishedApi
internal fun JsonElement.toProblemValue(): ProblemValue = toProblemValue(depth = 0)

/**
 * The recursion the library owns, bounded by [Problem.MAX_NESTING_DEPTH].
 *
 * The public entry points above keep their original signatures and don't gain a `depth` parameter.
 * They are `@PublishedApi internal` and inlined into callers, so changing their shape would be an ABI
 * break for no gain.
 */
private fun ProblemValue.toJsonElement(depth: Int): JsonElement {
    checkDepth(depth)
    return when (this) {
        is ProblemPrimitive -> {
            when {
                isString -> JsonPrimitive(content)

                // JsonUnquotedLiteral rejects the literal "null"; that case is ProblemNull's job anyway.
                content == "null" -> JsonNull

                else -> JsonUnquotedLiteral(content)
            }
        }

        ProblemNull -> {
            JsonNull
        }

        is ProblemArray -> {
            JsonArray(map { it.toJsonElement(depth + 1) })
        }

        is ProblemObject -> {
            JsonObject(mapValues { (_, value) -> value.toJsonElement(depth + 1) })
        }
    }
}

private fun JsonElement.toProblemValue(depth: Int): ProblemValue {
    checkDepth(depth)
    return when (this) {
        JsonNull -> ProblemNull
        is JsonPrimitive -> ProblemPrimitive(content, isString)
        is JsonArray -> ProblemArray(map { it.toProblemValue(depth + 1) })
        is JsonObject -> ProblemObject(mapValues { (_, value) -> value.toProblemValue(depth + 1) })
    }
}

private fun checkDepth(depth: Int) {
    if (depth > Problem.MAX_NESTING_DEPTH) {
        throw SerializationException(
            "Extension value nests deeper than ${Problem.MAX_NESTING_DEPTH} levels",
        )
    }
}
