package io.github.ilyankin.rfc9457

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/**
 * Decodes the whole extension map into [T]. This is the typed consuming edge.
 *
 * Extension members [T] does not declare are ignored, so a partial view is fine. So is reading a
 * document produced by a newer version of the problem type than [T] was written against. §3.2
 * requires this, not just a convenience: "consumers MUST ignore extension members they don't
 * recognize". It holds regardless of [json]. `ignoreUnknownKeys` gets re-applied on top of a caller's
 * instance, so a strictly configured application instance cannot turn a conforming document into an
 * exception. Everything else about [json], its `serializersModule` above all, stays the caller's.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9457#section-3.2">RFC 9457 §3.2, Extension Members</a>
 */
public fun <T> Problem.extensionsAs(
    deserializer: DeserializationStrategy<T>,
    json: Json = ProblemJson,
): T =
    json.lenientAboutUnknownMembers().decodeFromJsonElement(
        deserializer,
        JsonObject(extensions.mapValues { (_, value) -> value.toJsonElement() }),
    )

/**
 * Decodes the whole extension map into [T], with the deserializer resolved from [T]. Members [T]
 * does not declare are ignored, as RFC 9457 §3.2 requires.
 *
 * @sample io.github.ilyankin.rfc9457.samples.readTypedExtensions
 */
public inline fun <reified T> Problem.extensionsAs(json: Json = ProblemJson): T = extensionsAs(serializer(), json)

/**
 * Decodes a single extension member, or returns `null` when it is absent.
 *
 * Absence and a present-but-undecodable member are different cases. A member of the wrong shape
 * throws; it does not return `null`. Read it through [Problem.extensions] and the lenient accessors,
 * [stringOrNull] and friends, to follow §3's "ignore it like an absent member" rule.
 */
public fun <T> Problem.extension(
    name: String,
    deserializer: DeserializationStrategy<T>,
    json: Json = ProblemJson,
): T? =
    extensions[name]?.let {
        json.lenientAboutUnknownMembers().decodeFromJsonElement(deserializer, it.toJsonElement())
    }

/**
 * Decodes a single extension member with the deserializer resolved from [T], or returns `null` when
 * the member is absent.
 */
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
