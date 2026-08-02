# Changelog

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project follows
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

**While the version is 0.x, any release may contain breaking changes without a deprecation cycle.**
That is what 0.y.z means, and it is deliberate. Any that occur are listed under *Breaking changes*.

## [Unreleased]

### Added

- Every JVM jar declares an `Automatic-Module-Name` in its manifest, so a JPMS consumer gets a stable
  module name instead of one the runtime derives from the file name: `io.github.ilyankin.rfc9457`
  for `problem-details-core`, `io.github.ilyankin.rfc9457.ktor` for `problem-details-ktor`. Each name
  equals that module's root package, and from here on it is part of the API — changing one would
  break every `requires` that names it.

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
