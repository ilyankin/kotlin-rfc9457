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
 * The [Json] used to convert *typed* extension payloads — the values passed to `extensions(…)` and
 * `extension(name, …)`, and the types read back by [extensionsAs] — when the caller supplies none.
 *
 * This does **not** govern the problem document itself: [ProblemSerializer] encodes through whatever
 * `Json` the caller is using, so an application's own formatting settings already apply on the wire.
 *
 * Override it per call — `problem(json = myJson) { … }`, `extensionsAs<T>(json = myJson)` — whenever
 * the application configures kotlinx.serialization in ways the payload depends on: a
 * `serializersModule` holding contextual or polymorphic serializers (where the default instance
 * fails outright), a `namingStrategy`, a `classDiscriminator`. Passing an instance from a DI
 * container is the intended use; nothing here is global or mutable.
 *
 * `ignoreUnknownKeys` matters for [extensionsAs]: decoding the whole extension map into a type that
 * only declares some of the members must not fail. That one setting is re-applied on top of a
 * caller's instance, because RFC 9457 §3.2 requires that *"consumers MUST ignore extension members
 * they don't recognize"* — the very rule that lets a problem type gain members without breaking
 * clients written against the older shape. `encodeDefaults` departs from kotlinx's own default so
 * that a payload's defaulted properties still reach the wire.
 */
public val ProblemJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

/**
 * Converts to the JSON tree. Non-string scalars go through [JsonUnquotedLiteral] rather than
 * `JsonPrimitive(Number)`, which is what preserves an exact literal — kotlinx's own documentation
 * prescribes it for values that must not be quoted or truncated.
 */
@PublishedApi
internal fun ProblemValue.toJsonElement(): JsonElement = toJsonElement(depth = 0)

/**
 * Converts from the JSON tree. [JsonNull] is checked first because it is a [JsonPrimitive].
 *
 * Note what this does *not* protect against. By the time it runs, kotlinx has already built the
 * whole `JsonElement` tree, so a document deep enough to overflow *its* parser never reaches this
 * function. Measured against kotlinx-serialization 1.11.0: nested **objects** survive past 50 000
 * levels, because `JsonTreeReader` switched to `DeepRecursiveFunction`
 * ([#1594](https://github.com/Kotlin/kotlinx.serialization/issues/1594)); nested **arrays** still
 * overflow at around 5 000, because the same fix was
 * [declined for arrays](https://github.com/Kotlin/kotlinx.serialization/issues/1703) as "narrow and
 * unique". That residue is upstream and outside this library's reach — the guard below covers the
 * recursion this library owns.
 */
@PublishedApi
internal fun JsonElement.toProblemValue(): ProblemValue = toProblemValue(depth = 0)

/**
 * The recursion the library owns, bounded by [Problem.MAX_NESTING_DEPTH].
 *
 * The public entry points above keep their original signatures rather than gaining a `depth`
 * parameter: they are `@PublishedApi internal` and therefore inlined into callers, so changing their
 * shape would be an ABI break for no gain.
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
