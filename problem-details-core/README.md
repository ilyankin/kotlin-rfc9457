# Module problem-details-core

The RFC 9457 model and the JSON codec. This is the only module a library or an application needs in
order to *build* and *read* problem documents; the other three add a wire format or a framework.

Depends on `kotlinx-serialization-json` and nothing else.

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

# Package io.github.ilyankin.rfc9457

The problem-detail model, its builder, and the JSON codec.

| Type | Role |
|---|---|
| [Problem] | The document. Five standard members as properties, everything else in `extensions`. |
| [ProblemType] | A problem type — the `type` URI paired with the `title` that belongs to it. |
| [ProblemValue] | The value of an extension member: primitive, null, array or object. |
| [problem] | The builder. `problem { status = 404; title = "..." }`. |
| [ProblemSerializer] | The flattening codec, already attached to [Problem]. |

Reading extension values is deliberately offered twice: `problem.extensions["x"]?.string` throws on a
wrong-typed member, `?.stringOrNull` returns `null` for it. RFC 9457 §3 requires *consumers* to
ignore a member of an unexpected type, so `*OrNull` is what a conforming reader wants; the strict
half exists for a caller that minted the problem type and wants a mismatch to surface.
