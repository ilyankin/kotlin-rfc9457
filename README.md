# kotlin-rfc9457

[![CI](https://github.com/ilyankin/kotlin-rfc9457/actions/workflows/ci.yml/badge.svg)](https://github.com/ilyankin/kotlin-rfc9457/actions/workflows/ci.yml)
[![codecov](https://codecov.io/github/ilyankin/kotlin-rfc9457/graph/badge.svg?token=8F1IBCDE94)](https://codecov.io/github/ilyankin/kotlin-rfc9457)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.ilyankin/problem-details-core)](https://central.sonatype.com/artifact/io.github.ilyankin/problem-details-core)
[![GitHub Release](https://img.shields.io/github/v/release/ilyankin/kotlin-rfc9457)](https://github.com/ilyankin/kotlin-rfc9457/releases)
[![javadoc](https://javadoc.io/badge2/io.github.ilyankin/problem-details-core/javadoc.svg)](https://javadoc.io/doc/io.github.ilyankin/problem-details-core)

[RFC 9457 Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457) for Kotlin — the
standard way for an HTTP API to say *what went wrong*, in one machine-readable body shape instead of
a different error format per service. This library models that document, serializes it correctly, and
generates the Ktor wiring you would otherwise hand-write into `StatusPages` and `ContentNegotiation`,
rather than reimplementing either.

## See it

Declare a problem type once, in ordinary domain code that imports nothing web-related:

```kotlin
object OutOfCredit : ProblemType {
    override val typeUri: String = "https://example.com/probs/out-of-credit"
    override val title: String = "You do not have enough credit."
    override val status: Int = 403
}
```

Wire Ktor up once, at startup:

```kotlin
install(ContentNegotiation) { problemJson() }
install(StatusPages) { problemDetails { } }
```

Then throw it from anywhere — a service, a repository, a validator:

```kotlin
throw OutOfCredit.exception(detail = "Your current balance is 30, but that costs 50.")
```

And the caller gets this, with nothing else configured:

```http
HTTP/1.1 403 Forbidden
Content-Type: application/problem+json

{
  "type": "https://example.com/probs/out-of-credit",
  "status": 403,
  "title": "You do not have enough credit.",
  "detail": "Your current balance is 30, but that costs 50.",
  "instance": "/account/12345/msgs/abc"
}
```

`instance` came from the request path, `status` from the problem type. Those same two `install` lines
also answer every *unhandled* exception with a document instead of a stack trace. Bare status codes —
the 404 from a route that matched nothing — stay untouched until you ask for them with
`standardStatusCodes()`, because `StatusPages` fires that hook for every response carrying the code,
including bodies your own handlers built on purpose.

## What it does

**On the server.** `problemDetails { }` writes the `StatusPages` registrations for you: a catch-all
that never leaks a file path or a SQL fragment into a response, mappings for exception types you do
not own, and bodies for individual status codes. `problemJson()` registers the codec with
`ContentNegotiation`. Dispatch itself stays Ktor's — nearest-parent-class exception resolution and
`Accept` quality values are not reimplemented here.

**In your domain code.** `ProblemType` and the throwable both live in `problem-details-core`, so
raising a problem from a service layer costs no dependency on a web framework. Pass `cause` and the
underlying failure is logged server-side and kept out of the document, which RFC 9457 §5 asks for.

**Your own fields, beside the standard ones.** Spread an `@Serializable` object into a document with
`extensions(obj)`, read it back typed with `extensionsAs<T>()`. They land as *siblings* of
`type`/`status`/`title`/`detail`/`instance` — what §3.2 requires, and what implementations that nest
them under an `extensions` key get wrong.

**Field-level validation errors.** `requestValidation(type)` turns Ktor `RequestValidation` failures
into the `errors[]` array with JSON Pointer references that the RFC itself recommends for multi-field
validation, and `jsonPointer(Customer::age)` derives each pointer from the property so it cannot
drift away from the DTO it points into.

**In the OpenAPI document, not just on the wire.** `problemResponses(catalog)` inside `routing { }`
documents the catch-all and every status the catalog answers, for the whole application, in one line.
Ktor's own inference cannot find these: it reads route handler bodies, and problem documents come from
`StatusPages`, which sits outside them. Bodies are keyed `application/problem+json`, which most
implementations still get wrong.

**Reading problems, not only writing them.** `problemJson()` on Ktor Client turns a problem response
from an API you call back into the same `ProblemException` your own server throws — one exception
type for both directions, not two.

**XML when a client asks for it.** The RFC Appendix B format, byte-exact against the RFC's own example
in both directions, in artifacts of its own so a JSON-only application never resolves an XML parser.

## Which artifact do I need

| If you want to… | Add | Since |
|---|---|---|
| Return RFC 9457 documents from a Ktor server | `problem-details-core` + `problem-details-ktor` | 0.1.0 |
| Build or read the documents with no web framework at all | `problem-details-core` | 0.1.0 |
| Map `RequestValidation` failures to `errors[]` | …plus `problem-details-ktor-validation` | 0.5.0 |
| Show those failures in a generated OpenAPI document | …plus `problem-details-ktor-openapi`, and `problem-details-ktor-openapi-xml` if you also answer XML | 0.6.0 |
| Answer `application/problem+xml` as well as JSON | …plus `problem-details-xml` and `problem-details-ktor-xml` | 0.2.0 |
| Decode problem responses from APIs you call | …plus `problem-details-ktor-client`, and `problem-details-ktor-client-xml` if those answers can be XML | 0.3.0 / 0.4.0 |

All modules always share one version, so you choose a version once and use it everywhere.

<details>
<summary>All nine artifacts, with per-module documentation</summary>

| Artifact | Contains | Javadoc |
|---|---|---|
| [`problem-details-core`](problem-details-core/README.md) | `Problem`, `ProblemType`, `ProblemValue`, the `problem { }` builder, typed extension reading, and the flattening JSON codec | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-core) |
| [`problem-details-ktor`](problem-details-ktor/README.md) | `respondProblem`, `ProblemDetailsCatalog`, `problemDetails { }`, `problemJson()` | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-ktor) |
| [`problem-details-xml`](problem-details-xml/README.md) | The RFC Appendix B XML codec (`ProblemXml`) | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-xml) |
| [`problem-details-ktor-xml`](problem-details-ktor-xml/README.md) | Registers the XML codec with Ktor's `ContentNegotiation` (`problemXml()`) | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-ktor-xml) |
| [`problem-details-ktor-client`](problem-details-ktor-client/README.md) | `problemJson()` — decode a recognized problem response into the same `ProblemException` the server throws | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-ktor-client) |
| [`problem-details-ktor-client-xml`](problem-details-ktor-client-xml/README.md) | `problemXml()` — the same for `application/problem+xml` | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-ktor-client-xml) |
| [`problem-details-ktor-validation`](problem-details-ktor-validation/README.md) | `invalidField`/`invalidFields`, `jsonPointer`, `requestValidation(type)` — `RequestValidationException` to `errors[]` with JSON Pointer | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-ktor-validation) |
| [`problem-details-ktor-openapi`](problem-details-ktor-openapi/README.md) | `Route.problemResponses(catalog)`, `problemsFrom`, `problemResponse`, `problemDefault`, `ProblemSchemas` — problem responses in a generated OpenAPI document | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-ktor-openapi) |
| [`problem-details-ktor-openapi-xml`](problem-details-ktor-openapi-xml/README.md) | `problemXmlContent()` — the same, for `application/problem+xml` | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-ktor-openapi-xml) |

</details>

API reference for every module: **<https://ilyankin.github.io/kotlin-rfc9457/>**, regenerated from
`main` on every push. Per-artifact documentation is also served unversioned by javadoc.io, resolving
to the latest release, once that module has a version published.

## Requirements

- JDK 17+
- Kotlin 2.4+ (built with 2.4.10)
- Ktor 3.5+ for the Ktor modules

## Installation

Gradle (Kotlin DSL):

```kotlin
dependencies {
    implementation("io.github.ilyankin:problem-details-core:0.6.0")
    implementation("io.github.ilyankin:problem-details-ktor:0.6.0")
}
```

Maven:

```xml
<dependency>
  <groupId>io.github.ilyankin</groupId>
  <artifactId>problem-details-core</artifactId>
  <version>0.6.0</version>
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

### Mapping exceptions you don't own

Throwing `OutOfCredit.exception(…)` covers your own code — see [See it](#see-it). Exception types
from a library get mapped declaratively instead, which leaves them free of any dependency on this one:

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

### Validation errors

```kotlin
install(RequestValidation) {
    validate<Customer> { customer ->
        if (customer.age > 0) ValidationResult.Valid
        else invalidField(Customer::age, "must be a positive integer")
    }
}

install(StatusPages) { problemDetails { requestValidation(ValidationError) } }
```

```json
{
  "type": "https://example.net/validation-error",
  "status": 422,
  "title": "Your request is not valid.",
  "instance": "/customers",
  "errors": [
    { "detail": "must be a positive integer", "pointer": "#/age" }
  ]
}
```

### Responding directly

```kotlin
call.respondProblem(HttpStatusCode.Forbidden, problem)
```

## Under the hood

Things that are easy to get wrong and are therefore settled here once:

- **The media type always says "problem".** A document goes out labelled
  `application/problem+json` even when the request matched it under plain `application/json`, so the
  media type never stops being the marker that this body is an error report.
- **Recursion is bounded.** The JSON and XML codecs share one nesting limit
  (`Problem.MAX_NESTING_DEPTH`) and fail with `SerializationException`, not a `StackOverflowError`.
- **`problem-details-ktor` never depends on the XML modules.** That is the point of the split: an
  application that only emits JSON does not resolve an XML parser, and optionality is expressed by
  *which artifact declares the registration function* — so a missing dependency is a compile error at
  the call site, not a runtime `NoClassDefFoundError`.
- **Multiplatform.** Every module publishes for `jvm`, `js`, `wasmJs`, `linuxX64`, `linuxArm64`,
  `mingwX64`, `macosArm64`, `iosArm64` and `iosSimulatorArm64`, with all code in `commonMain` and no
  `expect`/`actual`. Depend on the plain coordinates from `commonMain` and Gradle resolves the
  variant.
- **The public surface is reviewed as a diff.** `explicitApi()` everywhere, plus `api/*.api` and
  `api/*.klib.api` ABI dumps checked on every build, so any accidental widening shows up in review
  before 1.0 freezes it.

## Stability

**This is a 0.x release. Anything may change in any release, and nothing is frozen yet.** No
deprecation cycle is owed and no binary compatibility is promised until 1.0. The public surface is
recorded in `api/*.api` dumps and checked on every build, so changes are at least visible in a diff.

There are consequently no `@RequiresOptIn` markers: opt-in annotations exist to carve unstable
islands out of a *stable* release, and at 0.x everything is unstable by declaration.

## Roadmap

**Backlog** — scoped, timing intentionally undecided:

| Module | Would add |
|---|---|
| `problem-details-ktor-i18n` | `Accept-Language`-based localization of `title`/`detail` (Spring `MessageSource`-style). |
| `problem-details-ktor-hooks` | A global enrichment hook (ASP.NET `CustomizeProblemDetails`-style) for adding fields like `traceId` to every response. |

**1.0.** `@RequiresOptIn` markers arrive for whatever isn't ready to freeze — `ProblemDetailsCatalog`
and the shape of `ProblemType` are the named candidates — once someone outside this repo has actually
used the library.

## Contributing

Bug reports, proposals and questions are all welcome — see [`CONTRIBUTING.md`](CONTRIBUTING.md) for
where each of them goes and what the build checks before a pull request can land. The roadmap above
is ordered by demand, so saying you need something counts as a contribution.

## License

Apache License 2.0 — see [`LICENSE`](LICENSE).
