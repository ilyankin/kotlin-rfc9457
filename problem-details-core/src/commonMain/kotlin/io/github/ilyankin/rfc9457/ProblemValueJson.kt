package io.github.ilyankin.rfc9457

import kotlinx.serialization.ExperimentalSerializationApi
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
@OptIn(ExperimentalSerializationApi::class)
@PublishedApi
internal fun ProblemValue.toJsonElement(): JsonElement =
    when (this) {
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
            JsonArray(map { it.toJsonElement() })
        }

        is ProblemObject -> {
            JsonObject(mapValues { (_, value) -> value.toJsonElement() })
        }
    }

/** Converts from the JSON tree. [JsonNull] is checked first because it is a [JsonPrimitive]. */
@PublishedApi
internal fun JsonElement.toProblemValue(): ProblemValue =
    when (this) {
        JsonNull -> ProblemNull
        is JsonPrimitive -> ProblemPrimitive(content, isString)
        is JsonArray -> ProblemArray(map { it.toProblemValue() })
        is JsonObject -> ProblemObject(mapValues { (_, value) -> value.toProblemValue() })
    }
