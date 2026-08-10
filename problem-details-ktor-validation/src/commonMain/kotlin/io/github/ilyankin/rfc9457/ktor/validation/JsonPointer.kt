package io.github.ilyankin.rfc9457.ktor.validation

import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.serialDescriptor
import kotlin.reflect.KProperty1

/**
 * Builds the JSON Pointer (RFC 6901) that identifies [property] in [T]'s serialized form, in the URI
 * fragment notation RFC 9457's own example uses: `#/age`.
 *
 * Preferred over writing the pointer as a string, because renaming the property is then a compile
 * error rather than a pointer that silently names a member no longer there.
 *
 * The property's own name is the wire name only when nothing renames it. If the class carries
 * `@SerialName` on that property, or the application's `Json` applies a `JsonNamingStrategy`, this
 * function cannot see it — the mapping from a Kotlin property to a serial name is not reachable
 * without `kotlin-reflect`, which this library does not use. `@SerialName` is at least detected: the
 * name is checked against [T]'s serial descriptor and an unknown one throws. A naming strategy is
 * not detectable at all, and a project using one should build its pointers from strings instead.
 *
 * @throws IllegalArgumentException if [T]'s serialized form has no member under the property's name.
 * @sample io.github.ilyankin.rfc9457.samples.typedPointerSample
 */
public inline fun <reified T> jsonPointer(property: KProperty1<T, *>): String =
    buildJsonPointer(serialDescriptor<T>(), listOf(property.name))

/**
 * Builds the JSON Pointer (RFC 6901) for [path] in [T]'s serialized form, in the URI fragment
 * notation RFC 9457's own example uses: `jsonPointer<Customer>("profile", "color")` gives
 * `#/profile/color`.
 *
 * Every segment is checked against the serial descriptor it belongs to, so the whole path has to
 * exist. Use this form for a nested member, for a member renamed by `@SerialName` — the segments are
 * serial names, which is what the wire carries — and wherever the path is assembled rather than
 * written out. Segments are escaped for you: `~` and `/` per RFC 6901 §3, then whatever a URI
 * fragment does not admit is percent-encoded.
 *
 * A list segment is its index, or `-` for the position past the end, as RFC 6901 §4 defines. Under a
 * map, any segment is accepted, since map keys are not known statically. Under a polymorphic or
 * contextual member the shape is unknown, so checking stops there and the rest of the path is taken
 * on trust.
 *
 * @throws IllegalArgumentException if [path] is empty, or if a segment names nothing in the
 *   descriptor it is resolved against.
 * @sample io.github.ilyankin.rfc9457.samples.typedPointerSample
 */
public inline fun <reified T> jsonPointer(vararg path: String): String =
    buildJsonPointer(serialDescriptor<T>(), path.asList())

/**
 * The non-inline half of [jsonPointer]. Exists so the descriptor walk itself is not inlined into
 * every call site, the same reason `ProblemDetailsCatalog.addMapping` is split out.
 */
@PublishedApi
internal fun buildJsonPointer(
    root: SerialDescriptor,
    path: List<String>,
): String {
    require(path.isNotEmpty()) { "path must name at least one member" }
    var current: SerialDescriptor? = root
    return path.joinToString(separator = "/", prefix = "#/") { segment ->
        current = current?.let { descriptor -> descriptor.resolve(segment) }
        segment.escapeForFragment()
    }
}

/** The descriptor a [segment] leads into, or `null` where the shape stops being knowable. */
private fun SerialDescriptor.resolve(segment: String): SerialDescriptor? =
    when (kind) {
        StructureKind.CLASS, StructureKind.OBJECT -> {
            val index = getElementIndex(segment)
            require(index >= 0) {
                val members = (0 until elementsCount).joinToString { getElementName(it) }
                "$serialName has no member \"$segment\"; it has [$members]. A property renamed with " +
                    "@SerialName must be named by its serial name."
            }
            getElementDescriptor(index)
        }

        StructureKind.LIST -> {
            require(segment == "-" || segment.toUIntOrNull() != null) {
                "$serialName is a list, so \"$segment\" must be an index or \"-\" (RFC 6901 §4)"
            }
            getElementDescriptor(0)
        }

        // Map keys are not known statically, so the segment cannot be wrong; the value descriptor is
        // element 1, the key being element 0.
        StructureKind.MAP -> getElementDescriptor(1)

        SerialKind.CONTEXTUAL, is PolymorphicKind -> null

        else -> throw IllegalArgumentException("$serialName holds no members, so \"$segment\" resolves to nothing")
    }

/** Unreserved and sub-delimiter characters a URI fragment admits as they are (RFC 3986 §3.5). */
private const val FRAGMENT_SAFE: String = "-._~!$&'()*+,;=:@"

private const val HEX_DIGITS: String = "0123456789ABCDEF"

/**
 * RFC 6901 §3 escaping, then RFC 3986 percent-encoding of whatever a fragment does not admit. Order
 * matters: `~` is doubled first, or the `~` a `/` turns into would be escaped a second time.
 */
private fun String.escapeForFragment(): String {
    val escaped = replace("~", "~0").replace("/", "~1")
    return buildString(escaped.length) {
        for (byte in escaped.encodeToByteArray()) {
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            if ((code < 0x80 && char.isLetterOrDigit()) || char in FRAGMENT_SAFE) {
                append(char)
            } else {
                append('%').append(HEX_DIGITS[code shr 4]).append(HEX_DIGITS[code and 0x0F])
            }
        }
    }
}
