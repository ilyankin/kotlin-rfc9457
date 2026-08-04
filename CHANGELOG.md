# Changelog

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

**While the version is 0.x, any release may contain breaking changes without a deprecation cycle.**
That is what 0.y.z means, and it is deliberate. Any that occur are listed under *Breaking changes*.

## [0.4.0] — 2026-08-04

### Added

- **`problem-details-ktor-client-xml`** — `problemXml()` registers the same kind of
  `HttpResponseValidator` hook as `problem-details-ktor-client`'s `problemJson()`, for responses
  labeled `application/problem+xml`. Its own artifact, so a client that only decodes JSON never
  resolves an XML parser. Registration order does not matter, unlike the server's
  `ContentNegotiation` pair: each hook gates on a `Content-Type` the other never matches, so neither
  can shadow the other. There is no lenient mode — plain `application/xml` never matches — and a body
  labeled `application/problem+xml` that fails to decode propagates as `SerializationException`
  rather than falling back to Ktor's own exception.

## [0.3.0] — 2026-08-04

### Added

- **`problem-details-ktor-client`** — `problemJson()` registers a Ktor `HttpResponseValidator` hook
  that turns a recognized `application/problem+json` error response into `problem-details-core`'s
  existing `ProblemException`, the same type application code throws on the server side and
  `problem-details-ktor` answers. JSON only, by artifact rather than by flag, so a client that never
  speaks XML never resolves an XML parser; the `application/problem+xml` half is planned as its own
  module. Requires `expectSuccess = true` on the client — Ktor's own default is `false`.

### Fixed

- **`problem-details-xml`** — a body that is not markup at all (plain text, or a lone `<`) escaped
  `ProblemXml.decodeFromString`'s wrapper as `IllegalStateException` instead of the documented
  `SerializationException`. xmlutil reports that one case as a bare `IllegalStateException` rather
  than an `XmlException`, which the wrapper did not expect.

## [0.2.0] — 2026-08-02

`problem-details-xml` and `problem-details-ktor-xml` publish for the first time, alongside
`problem-details-core` and `problem-details-ktor`, all on the same version.

### Added

- **`problem-details-xml`** — the RFC 9457 Appendix B `application/problem+xml` codec (`ProblemXml`),
  byte-exact against the RFC's own example in both directions. The generic (non-JDK) xmlutil parser
  is used explicitly, so external entities are never resolved. xmlutil is an `implementation`
  dependency; no xmlutil type appears in this module's API, thrown types included.
- **`problem-details-ktor-xml`** — `ProblemXmlConverter` and `problemXml()`, registering the codec
  with Ktor's `ContentNegotiation`. `problem-details-ktor` still never depends on either XML module.
  Register `problemXml()` after `problemJson()` — order decides which format an absent or wildcard
  `Accept` resolves to.
- Every JVM jar now declares an `Automatic-Module-Name` matching its root package.

### Notes

- The XML writer refuses what XML cannot express rather than writing it verbatim: an extension
  member name that is not an XML `NCName`, or text carrying `U+0000` or an unpaired surrogate. A
  `Problem` valid for JSON may therefore be refused here — see the module README. Every refusal
  surfaces as `SerializationException`, never an xmlutil type.
- A whitespace-only member (`detail = " "`) survives an XML round trip.
- `application/problem+xml` requests and responses are always UTF-8, regardless of what charset was
  negotiated — an XML document states its own encoding in-band.

## [0.1.0] — 2026-08-02

First release. JSON only: `problem-details-core` and `problem-details-ktor`. The XML modules
(`problem-details-xml`, `problem-details-ktor-xml`) are written and tested but deliberately left out
of this release.

### Added

- **`problem-details-core`** — `Problem` with the five RFC 9457 §3.1 members and arbitrary §3.2
  extensions; `ProblemValue` (`ProblemPrimitive`/`ProblemArray`/`ProblemObject`/`ProblemNull`);
  the `problem { }` builder; `ProblemType` for reusable problem kinds; typed extension access via
  `extensions(obj)`, `extensionsAs<T>()` and `extension<T>(name)`.
- **`problem-details-core`** — `ProblemSerializer`, attached to `Problem` as its own serializer, so
  extension members are written as siblings of the standard ones rather than nested under an
  `extensions` key, no matter which converter wins the media-type match.
- **`problem-details-core`** — `ProblemException`, a `Problem` that can be thrown, and
  `ProblemType.exception(detail, instance, cause)` to raise one from a declared problem type. It
  lives in core rather than in a Ktor module, so domain code can throw a problem document without
  depending on a web framework.
- **`problem-details-ktor`** — `respondProblem`, which keeps `status` in sync with the real response
  status and fills `instance` from the request path; `ProblemDetailsCatalog` and `problemDetails { }`
  over `StatusPages`; `ProblemJsonConverter` and `problemJson()` over `ContentNegotiation`;
  `ProblemContentTypes`.
- **`problem-details-ktor`** — `problemDetails { }` answers a thrown `ProblemException` with the
  document it carries instead of a generic 500, and logs a non-null `cause` that `StatusPages` would
  otherwise drop silently. The entry is seeded into the catalog, so `map<ProblemException>` replaces
  it like any other.
- Both codecs bound extension nesting at `Problem.MAX_NESTING_DEPTH` (64) and fail with
  `SerializationException` rather than a `StackOverflowError`.
- **API reference** at <https://ilyankin.github.io/kotlin-rfc9457/>, and in each `-javadoc.jar`,
  which is what [javadoc.io](https://javadoc.io) serves. Every module's `README.md` doubles as its
  module description, and the main entry points carry `@sample` examples that live in `commonTest`,
  so they are compiled and run rather than merely written.

### Notes

- `ProblemException` carries a `Problem` rather than being one, and is final. Use it when the
  document is assembled at the throw site; to turn an *existing* exception type into a problem,
  keep mapping it in the catalog, which leaves that exception free of any dependency on this
  library.
- No `@RequiresOptIn` markers: at 0.x everything is unstable by declaration, so opt-in annotations
  would carve islands out of nothing. They arrive with 1.0 for whatever is not ready to freeze.
- The public surface is recorded in `api/*.api` and checked on every build, and every public
  declaration in it carries KDoc — `reportUndocumented` plus `failOnWarning` make an undocumented one
  fail the documentation build.
- The root POM is published `packaging: pom` with a compile-scoped dependency on the `-jvm`
  artifact, so Maven consumers can use the plain coordinates.
