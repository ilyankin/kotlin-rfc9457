# kotlin-rfc9457

[![CI](https://github.com/ilyankin/kotlin-rfc9457/actions/workflows/ci.yml/badge.svg)](https://github.com/ilyankin/kotlin-rfc9457/actions/workflows/ci.yml)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
![Status](https://img.shields.io/badge/status-0.x%20pre--release-orange.svg)

[RFC 9457 Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457) for Kotlin, with a
Ktor integration that generates the wiring you would otherwise hand-write into `StatusPages` and
`ContentNegotiation`, rather than reimplementing either.

Written as Kotlin Multiplatform with a single `jvm()` target: all code lives in `commonMain`, so
adding a target is a line in `kotlin { }` rather than a redesign.

## Features

- **Spec-correct extension members.** RFC 9457 §3.2 extension members are written as *siblings* of
  the five standard members, never nested under an `extensions` key — the mistake several existing
  implementations make.
- **Typed extension read & write.** Spread an `@Serializable` object into extension members with
  `extensions(obj)`; read it back typed with `extensionsAs<T>()`, or a single member with
  `extensions["x"]` / `extension<T>("x")`.
- **Declarative Ktor wiring.** `problemDetails { }` generates the `StatusPages` registrations you'd
  otherwise hand-write; `problemJson()` registers the codec with `ContentNegotiation`. Dispatch —
  nearest-parent-class exception resolution, `Accept` quality-value matching — stays Ktor's.
- **Content-negotiation-safe.** A problem document always claims `application/problem+json` on the
  wire, even when it matched under `application/json`, so the media type itself still says "this is a
  problem".
- **Bounded, guarded codec.** Extension nesting is capped at `Problem.MAX_NESTING_DEPTH` and fails
  with `SerializationException`, not a `StackOverflowError`.
- **Multiplatform-ready today.** Every module is `kotlin("multiplatform")` with all code in
  `commonMain`; a `jvm()` target is declared for v1, and adding another target is a one-line change.
- **Throw a problem from domain code.** `throw OutOfCredit.exception(detail = "…")`. The throwable
  type lives in `problem-details-core`, so raising a problem never drags in a web framework, and the
  Ktor integration answers it with the carried document.
- **Public API tracked in diff form.** `explicitApi()` everywhere, `api/*.api` ABI dumps checked on
  every build — any accidental widening of the public surface shows up in review before 1.0 freezes it.

## Modules

| Artifact | Contains |
|---|---|
| [`problem-details-core`](problem-details-core/README.md) | `Problem`, `ProblemType`, `ProblemValue`, the `problem { }` builder, typed extension reading, and the flattening JSON codec |
| [`problem-details-ktor`](problem-details-ktor/README.md) | `respondProblem`, `ProblemDetailsCatalog`, `problemDetails { }`, `problemJson()` |

API reference: **<https://ilyankin.github.io/kotlin-rfc9457/>**, regenerated from `main` on every
push. Per-artifact documentation is also served by javadoc.io once a version is published.

## Requirements

- JDK 17+
- Kotlin 2.4+ (built with 2.4.10)
- Ktor 3.5+ for `problem-details-ktor`

## Installation

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("io.github.ilyankin:problem-details-core:0.2.1")
    implementation("io.github.ilyankin:problem-details-ktor:0.2.1")
}
```

Maven:

```xml
<dependency>
  <groupId>io.github.ilyankin</groupId>
  <artifactId>problem-details-core</artifactId>
  <version>0.2.1</version>
</dependency>
```

The plain coordinates work from Maven as well as Gradle: the root POM is published with
`packaging: pom` and a compile-scoped dependency on the `-jvm` artifact, the way kotlinx-serialization
and kotlinx-coroutines do it. You do **not** need to write `-jvm` yourself.

## Quick start

### Building a problem document

```kotlin
val problem = problem {
    type = "https://example.net/validation-error"
    status = 422
    title = "Your request is not valid."
    detail = "The 'age' field must be a positive integer."
    instance = "/account/12345/msgs/abc"
}
```

### Extension members

Written as *siblings* of the standard members, never nested under an `extensions` key:

```kotlin
@Serializable
data class OutOfCreditDetails(val balance: Int, val accounts: List<String>)

val problem = problem {
    type = "https://example.com/probs/out-of-credit"
    status = 403
    extensions(OutOfCreditDetails(balance = 30, accounts = listOf("/account/12345")))
}
// {"type":"…","status":403,"balance":30,"accounts":["/account/12345"]}
```

Reading them back is typed:

```kotlin
val details = problem.extensionsAs<OutOfCreditDetails>()
val balance = problem.extensions["balance"]?.int
```

### Ktor

```kotlin
install(ContentNegotiation) { problemJson() }
install(StatusPages) { problemDetails { } }
```

That alone turns every unhandled exception and every error status into a conformant problem document,
with `instance` filled from the request path and `status` kept in sync with the real response status.

Declare a problem type once and throw it from anywhere — including code that knows nothing about
Ktor, since both `ProblemType` and the throwable live in `problem-details-core`:

```kotlin
object OutOfCredit : ProblemType {
    override val typeUri: String = "https://example.com/probs/out-of-credit"
    override val title: String = "You do not have enough credit."
    override val status: Int = 403
}

throw OutOfCredit.exception(detail = "Your current balance is 30, but that costs 50.")
```

The reply is the document the exception carried, with `instance` filled from the request path. Pass
`cause` to keep the underlying failure: it is logged server-side and never written into the document,
which RFC 9457 §5 asks you to keep free of debugging detail.

Exceptions you don't own are mapped declaratively instead, which leaves them free of any dependency
on this library:

```kotlin
install(StatusPages) {
    problemDetails {
        map<InsufficientFundsException> { _, cause ->
            problem {
                type = "https://example.com/probs/out-of-credit"
                status = 403
                detail = "Your balance is ${cause.balance}."
            }
        }
        forStatusCode(HttpStatusCode.NotFound) { Problem.blank(HttpStatusCode.NotFound) }
        standardStatusCodes()
    }
}
```

Nearest-parent-class exception resolution and `Accept` quality-value matching stay Ktor's — this
library only generates the registrations.

Or respond directly:

```kotlin
call.respondProblem(HttpStatusCode.Forbidden, problem)
```

## Stability

**This is a 0.x release. Anything may change in any release, and nothing is frozen yet.** No
deprecation cycle is owed and no binary compatibility is promised until 1.0. The public surface is
recorded in `api/*.api` dumps and checked on every build, so changes are at least visible in a diff.

There are consequently no `@RequiresOptIn` markers: opt-in annotations exist to carve unstable
islands out of a *stable* release, and at 0.x everything is unstable by declaration.

## Roadmap

**XML.** `problem-details-xml` and `problem-details-ktor-xml` cover the RFC Appendix B XML
representation, byte-exact against the RFC's own example in both directions. They are not part of
this release — less public surface to commit to before anyone has said they need XML at all — and
they will ship as separate artifacts, so an application that emits only JSON never resolves an XML
parser. Optionality is expressed by *which artifact declares the registration function*, so a missing
dependency is a compile error at the call site, not a runtime `NoClassDefFoundError`.

**Planned, next phase** — independent optional modules, the same way the XML half is independent of
JSON:

| Module | Would add |
|---|---|
| `problem-details-ktor-client` | Ktor Client support: parse an `application/problem+json`/`+xml` response body into a typed exception — the same `ProblemException` the server side throws. |

**Backlog** — scoped, timing intentionally undecided:

| Module | Would add |
|---|---|
| `problem-details-ktor-validation` | Maps `RequestValidationException.reasons` to the `errors[]` + JSON Pointer pattern RFC 9457 itself recommends for multi-field validation errors. |
| `problem-details-ktor-openapi` | Auto-documents problem responses in a generated OpenAPI spec. |
| `problem-details-ktor-i18n` | `Accept-Language`-based localization of `title`/`detail` (Spring `MessageSource`-style). |
| `problem-details-ktor-hooks` | A global enrichment hook (ASP.NET `CustomizeProblemDetails`-style) for adding fields like `traceId` to every response. |

**1.0.** `@RequiresOptIn` markers arrive for whatever isn't ready to freeze — `ProblemDetailsCatalog`
and the shape of `ProblemType` are the named candidates — once someone outside this repo has actually
used the library.

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).
