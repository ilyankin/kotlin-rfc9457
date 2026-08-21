# Module problem-details-xml

The `application/problem+xml` codec — RFC 9457 Appendix B, byte-exact against the example the RFC
publishes, in both directions.

Split out of `problem-details-core` on purpose: roughly nine applications in ten only ever emit JSON,
and they should not resolve an XML parser to do it. Add this module only if a client of yours asks
for XML.

Depends on `problem-details-core` (`api`) and xmlutil (`implementation`). **No xmlutil type is
visible in this module's API — including as a thrown type.** `decodeFromString` wraps xmlutil's
`XmlException` in `kotlinx.serialization.SerializationException` with the cause chained, so catching
a parse failure never requires xmlutil in your build.

Published for `jvm`, `js`, `wasmJs`, `linuxX64`, `linuxArm64`, `mingwX64`, `macosArm64`, `iosArm64`
and `iosSimulatorArm64`. Depend on the plain coordinates from `commonMain` and Gradle picks the
variant.

## Not every problem can be written as XML

A `Problem` that encodes to JSON without complaint may be refused here, and finding out on write is
the intended behaviour rather than an oversight.

Appendix B puts each extension member's *name* into an element name, and XML constrains names and
character content in ways JSON does not. `encodeToString` therefore throws `SerializationException`
when:

- an extension member's name is not an XML `NCName` (XML 1.0 §2.3 without a colon) — so `"balance"`
  and `"тип"` are fine, while `"account balance"`, `"1abc"`, `""` and `"x:y"` are not;
- any member's text carries `U+0000` or an unpaired surrogate, neither of which XML can represent in
  any form — not escaped, not in a CDATA section.

`Problem` itself accepts any extension name on purpose: RFC 9457 §3.2's naming guidance (start with a
letter, only `[A-Za-z0-9_]`, three characters or longer) is a `SHOULD`, and enforcing it in the model
would break JSON-only callers over a format they never use. §3.2 gives that guidance *because* of
this appendix — following it means a problem type works in both formats. The hard XML constraint is
enforced; the `SHOULD` is not, so a two-character name is accepted.

## Information this format loses

Appendix B's mapping is not lossless, and the losses are specified and tested rather than accidental:

- `ProblemNull`, an empty array and an empty object all encode to an empty element, and all read back
  as the empty string.
- Scalars carry no type on the wire, so a string extension whose text reads `30` comes back as a
  number.
- An object whose keys are *all* `i` comes back as an array, because Appendix B defines an element
  whose children are all named `i` to be one. `{"i": "a"}` round-trips to `["a"]`.

Round-tripping through XML is therefore not the identity. If both formats have to agree exactly, keep
the JSON document as the source of truth.

## What the reader tolerates

RFC 9457 §3 asks consumers to ignore what they do not recognise rather than reject the document, so
the reader is lenient in several ways. Each is a decision, and each is pinned by a test:

- a member in a foreign namespace is skipped; a *root* element in one is rejected, since that is the
  document's identity rather than one of its members;
- a `<problem>` root with no namespace declaration at all is accepted;
- where a member appears more than once, the last occurrence wins — for standard members and
  extensions alike;
- content after the closing `</problem>` is ignored, and an XML 1.1 declaration parses;
- attributes are ignored wherever they appear. Appendix B's schema admits them but defines no mapping
  from one into the data model, so honouring one would mean inventing semantics the RFC withheld. The
  writer emits none.

# Package io.github.ilyankin.rfc9457.xml

The Appendix B codec.

[ProblemXml] is the entry point: `ProblemXml.encodeToString(problem)` and
`ProblemXml.decodeFromString(text)`. Both are bounded by
[io.github.ilyankin.rfc9457.Problem.MAX_NESTING_DEPTH], the same constant the JSON codec uses, so
neither format can accept a document the other refuses.

The namespace is `urn:ietf:rfc:7807`. That is not a typo and not a leftover: RFC 9457 kept RFC 7807's
namespace deliberately. Emitting `urn:ietf:rfc:9457` round-trips happily against itself and fails
against everyone else.
