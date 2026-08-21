# Module problem-details-core

The RFC 9457 model and the JSON codec. This is the only module a library or an application needs in
order to *build* and *read* problem documents. The others add a wire format or a framework
integration.

Depends on `kotlinx-serialization-json` and nothing else.

Published for `jvm`, `js`, `wasmJs`, `linuxX64`, `linuxArm64`, `mingwX64`, `macosArm64`, `iosArm64`
and `iosSimulatorArm64`. Depend on the plain coordinates from `commonMain` and Gradle picks the
variant.

## Where to start

Build a document with the [io.github.ilyankin.rfc9457.problem] builder, or with
[io.github.ilyankin.rfc9457.Problem] directly when every member is already known. Encode and decode
with `kotlinx.serialization.json.Json` — [io.github.ilyankin.rfc9457.Problem] carries its own
serializer, so no converter has to be registered and no foreign converter can win the type.

## The one thing worth knowing before reading further

Extension members (RFC 9457 §3.2) are written as **siblings** of the five standard members, never
nested under an `extensions` key. The `extensions` map is how they are held in memory; it is not a
member of the wire format. Several published implementations get this wrong, and a consumer reading
`{"extensions": {...}}` is reading a non-conforming document.

## Throwing a problem

[io.github.ilyankin.rfc9457.ProblemException] carries a [io.github.ilyankin.rfc9457.Problem] and adds
nothing to the wire format, so domain code can raise one without depending on a web framework:

```kotlin
throw OutOfCredit.exception(detail = "Your current balance is 30, but that costs 50.")
```

It *carries* a problem rather than *being* one. Making
[io.github.ilyankin.rfc9457.Problem] an interface would allow the other shape, at the cost of the
model's value semantics — `copy`, `equals`, destructuring — and of the single concrete type both
codecs serialize; nothing here substitutes an exception where a document is expected, so that trade
buys nothing. It is final for a separate reason: [io.github.ilyankin.rfc9457.ProblemType] already
declares a problem type once, and a subclass would restate that type's URI, title and status beside
it.

Reach for it when the document is assembled at the throw site. To turn an *existing* exception type
into a problem, map it in the `problem-details-ktor` catalog instead — that keeps the exception free
of any dependency on this library.

`problem-details-ktor` answers a thrown one with the document it carries.

# Package io.github.ilyankin.rfc9457

The problem-detail model, its builder, and the JSON codec.

| Type | Role |
|---|---|
| [Problem] | The document. Five standard members as properties, everything else in `extensions`. |
| [ProblemType] | A problem type — the `type` URI paired with the `title` that belongs to it. |
| [ProblemValue] | The value of an extension member: primitive, null, array or object. |
| [problem] | The builder. `problem { status = 404; title = "..." }`. |
| [ProblemSerializer] | The flattening codec, already attached to [Problem]. |
| [ProblemException] | A [Problem] that can be thrown. `throw someType.exception(detail = "…")`. |

Reading extension values is deliberately offered twice: `problem.extensions["x"]?.string` throws on a
wrong-typed member, `?.stringOrNull` returns `null` for it. RFC 9457 §3 requires *consumers* to
ignore a member of an unexpected type, so `*OrNull` is what a conforming reader wants; the strict
half exists for a caller that minted the problem type and wants a mismatch to surface.
