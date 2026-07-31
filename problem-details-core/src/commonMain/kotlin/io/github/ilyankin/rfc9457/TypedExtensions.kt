package io.github.ilyankin.rfc9457

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/**
 * Decodes the whole extension map into [T] — the typed consuming edge.
 *
 * Extension members that [T] does not declare are ignored, so a partial view of a problem's
 * extensions is fine — and so is reading a document produced by a newer version of the problem type
 * than [T] was written against. That is not a convenience but RFC 9457 §3.2: *"consumers MUST ignore
 * extension members they don't recognize"*. It therefore holds regardless of [json] —
 * `ignoreUnknownKeys` is re-applied on top of a caller's instance rather than left to it, since a
 * strictly configured application instance would otherwise turn a conforming document into an
 * exception. Everything else about [json] — its `serializersModule` above all — is the caller's.
 */
public fun <T> Problem.extensionsAs(
    deserializer: DeserializationStrategy<T>,
    json: Json = ProblemJson,
): T =
    json.lenientAboutUnknownMembers().decodeFromJsonElement(
        deserializer,
        JsonObject(extensions.mapValues { (_, value) -> value.toJsonElement() }),
    )

public inline fun <reified T> Problem.extensionsAs(json: Json = ProblemJson): T = extensionsAs(serializer(), json)

/** Decodes a single extension member, or returns `null` when it is absent. */
public fun <T> Problem.extension(
    name: String,
    deserializer: DeserializationStrategy<T>,
    json: Json = ProblemJson,
): T? =
    extensions[name]?.let {
        json.lenientAboutUnknownMembers().decodeFromJsonElement(deserializer, it.toJsonElement())
    }

public inline fun <reified T> Problem.extension(
    name: String,
    json: Json = ProblemJson,
): T? = extension(name, serializer(), json)

/**
 * Returns [this] unchanged when it already ignores unknown keys, and a copy that does otherwise.
 * The copy keeps every other setting, so a caller's serializers module and naming strategy survive.
 */
private fun Json.lenientAboutUnknownMembers(): Json =
    if (configuration.ignoreUnknownKeys) this else Json(from = this) { ignoreUnknownKeys = true }
