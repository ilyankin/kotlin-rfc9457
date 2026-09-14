# kotlin-rfc9457

[![CI](https://github.com/ilyankin/kotlin-rfc9457/actions/workflows/ci.yml/badge.svg)](https://github.com/ilyankin/kotlin-rfc9457/actions/workflows/ci.yml)
[![codecov](https://codecov.io/github/ilyankin/kotlin-rfc9457/graph/badge.svg?token=8F1IBCDE94)](https://codecov.io/github/ilyankin/kotlin-rfc9457)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF.svg?logo=kotlin)](https://kotlinlang.org)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.ilyankin/problem-details-core)](https://central.sonatype.com/artifact/io.github.ilyankin/problem-details-core)
[![GitHub Release](https://img.shields.io/github/v/release/ilyankin/kotlin-rfc9457)](https://github.com/ilyankin/kotlin-rfc9457/releases)
[![javadoc](https://javadoc.io/badge2/io.github.ilyankin/problem-details-core/javadoc.svg)](https://javadoc.io/doc/io.github.ilyankin/problem-details-core)

![jvm][badge-jvm] ![js][badge-js] ![wasm][badge-wasm] ![linux][badge-linux] ![windows][badge-windows] ![macos][badge-macos] ![ios][badge-ios]

[RFC 9457 Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457) is the standard
machine-readable body an HTTP API sends to say what went wrong. This library gives you that document
as a Kotlin type, with codecs for JSON and XML. On top of it sits the Ktor wiring you would otherwise
hand-write into `StatusPages` and `ContentNegotiation`.

## See it

Declare a problem type once, in domain code that imports nothing web-related:

```kotlin
object OutOfCredit : ProblemType {
    override val typeUri: String = "https://example.com/probs/out-of-credit"
    override val title: String = "You do not have enough credit."
    override val status: Int = 403
}
```

Wire Ktor up at startup:

```kotlin
install(ContentNegotiation) { problemJson() }
install(StatusPages) { problemDetails { } }
```

Throw it wherever the failure is detected:

```kotlin
throw OutOfCredit.exception(detail = "Your current balance is 30, but that costs 50.")
```

The caller gets this:

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

`instance` comes from the request path and `status` from the problem type. The two `install` calls
also handle unmapped exceptions, preventing stack traces from reaching the client.

## Install

```kotlin
dependencies {
    implementation("io.github.ilyankin:problem-details-core:0.8.0")
    implementation("io.github.ilyankin:problem-details-ktor:0.8.0")
}
```

| Requires | Version |
|---|---|
| JDK | 17+ |
| Kotlin | 2.4+ (built with 2.4.10) |
| Ktor | 3.5+, for the Ktor modules |

<details>
<summary>Maven</summary>

```xml
<dependency>
  <groupId>io.github.ilyankin</groupId>
  <artifactId>problem-details-core</artifactId>
  <version>0.8.0</version>
</dependency>
```

</details>

## Artifacts

| To | Add |
|---|---|
| Return RFC 9457 documents from a Ktor server | `problem-details-core` + `problem-details-ktor` |
| Build or parse documents without a web framework | `problem-details-core` |
| Map `RequestValidation` failures to `errors[]` | + `problem-details-ktor-validation` |
| Show problem responses in generated OpenAPI documents | + `problem-details-ktor-openapi` (and `problem-details-ktor-openapi-xml` for XML) |
| Answer `application/problem+xml` alongside JSON | + `problem-details-xml` and `problem-details-ktor-xml` |
| Decode problem responses from APIs you call | + `problem-details-ktor-client` (and `problem-details-ktor-client-xml` for XML) |

All modules share the same version.

<details>
<summary>All artifacts, with per-module documentation</summary>

| Artifact | Contains | Javadoc |
|---|---|---|
| [`problem-details-core`](problem-details-core/README.md) | `Problem`, `ProblemType`, `ProblemValue`, the `problem { }` builder, typed extension access, and flattening JSON codec | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-core) |
| [`problem-details-ktor`](problem-details-ktor/README.md) | `respondProblem`, `ProblemDetailsCatalog`, `problemDetails { }`, `problemJson()` | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-ktor) |
| [`problem-details-xml`](problem-details-xml/README.md) | The RFC Appendix B XML codec (`ProblemXml`) | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-xml) |
| [`problem-details-ktor-xml`](problem-details-ktor-xml/README.md) | Registers the XML codec with Ktor `ContentNegotiation` (`problemXml()`) | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-ktor-xml) |
| [`problem-details-ktor-client`](problem-details-ktor-client/README.md) | `problemJson()`, decoding recognized problem responses into `ProblemException` | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-ktor-client) |
| [`problem-details-ktor-client-xml`](problem-details-ktor-client-xml/README.md) | `problemXml()`, decoding XML problem responses into `ProblemException` | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-ktor-client-xml) |
| [`problem-details-ktor-validation`](problem-details-ktor-validation/README.md) | `invalidField`/`invalidFields`, `jsonPointer`, `requestValidation(type)` to map `RequestValidationException` to `errors[]` with JSON Pointer | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-ktor-validation) |
| [`problem-details-ktor-openapi`](problem-details-ktor-openapi/README.md) | `Route.problemResponses(catalog)`, `problemsFrom`, `problemResponse`, `problemDefault`, `ProblemSchemas` for generated OpenAPI documents | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-ktor-openapi) |
| [`problem-details-ktor-openapi-xml`](problem-details-ktor-openapi-xml/README.md) | `problemXmlContent()` for `application/problem+xml` in OpenAPI | [javadoc.io](https://javadoc.io/doc/io.github.ilyankin/problem-details-ktor-openapi-xml) |

</details>

API reference for every module: <https://ilyankin.github.io/kotlin-rfc9457/>, regenerated from
`main` on every push. javadoc.io also serves each artifact at its latest release.

## Recipes

### Build a document

```kotlin
val problem = problem {
    type = "https://example.net/validation-error"
    status = 422
    title = "Your request is not valid."
    detail = "The 'age' field must be a positive integer."
    instance = "/account/12345/msgs/abc"
}

Json.encodeToString(problem)
```

`Problem` carries its own serializer, so standard `Json` encodes and decodes it without registering custom serializers.

### Add your own fields

```kotlin
@Serializable
data class OutOfCreditDetails(val balance: Int, val accounts: List<String>)

val problem = problem {
    type = "https://example.com/probs/out-of-credit"
    status = 403
    extensions(OutOfCreditDetails(balance = 30, accounts = listOf("/account/12345")))
}
```

```json
{
  "type": "https://example.com/probs/out-of-credit",
  "status": 403,
  "balance": 30,
  "accounts": ["/account/12345"]
}
```

RFC 9457 §3.2 puts extension members at the top level of the document.
The `extensions` map holds them in memory and never serializes as a nested object. Reading them back is type-safe:

```kotlin
val details = problem.extensionsAs<OutOfCreditDetails>()
val balance = problem.extensions["balance"]?.int
```

### Map exceptions you don't own

Your own code throws `OutOfCredit.exception(…)`. For exception types from external libraries,
declare the mapping at startup without adding this library as a dependency to the external library:

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
        map<AccountLockedException>(AccountLocked)   // detail comes from the exception message
        standardStatusCodes()
    }
}
```

`standardStatusCodes()` covers the four status codes Ktor generates without a body: 404, 405,
406, and 415. Other codes require explicit mapping via `forStatusCode` to avoid intercepting
responses that include explicit bodies.

### Field-level validation errors

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

The pointer derives from the property reference, making renames compile-safe. Deeper paths use
`jsonPointer<Customer>("profile", "color")`, which validates against the target type's serial descriptor.

### Put a trace id on every document

```kotlin
install(StatusPages) {
    problemDetails {
        standardStatusCodes()
        customize { call, problem ->
            val traceId = call.request.headers["X-Trace-Id"] ?: return@customize problem
            problem.copy(extensions = problem.extensions + ("traceId" to ProblemPrimitive(traceId)))
        }
    }
}
```

```json
{
  "type": "about:blank",
  "status": 404,
  "title": "Not Found",
  "instance": "/orders/17",
  "traceId": "b7ad6b7169203331"
}
```

`customize` runs on every document produced by the catalog before sending, whether built by
`map`, `onUnmapped`, or `forStatusCode`. Each call registers an additional transformation step,
running in registration order.
Use the same hook to localize `title` and `detail` by `Accept-Language`: read the header from
the call, resolve localized strings, and return `problem.copy(...)`.

### Respond from inside a route

```kotlin
get("/account/{id}") {
    call.respondProblem(HttpStatusCode.Forbidden, problem)
}
```

### Read a problem from an API you call

```kotlin
val client = HttpClient(CIO) {
    expectSuccess = true
    HttpResponseValidator { problemJson() }
}

try {
    client.get("https://api.example.com/orders/1").body<Order>()
} catch (e: ProblemException) {
    val status = e.problem.status
    val reason = e.problem.detail
}
```

The client and server use the same `ProblemException`. `expectSuccess = true` is required;
without it, Ktor does not throw on non-2xx responses, leaving nothing for the validator to intercept.

### Answer XML

```kotlin
install(ContentNegotiation) {
    problemJson()   // register first: `Accept: */*` matches both, and Ktor breaks the tie by order
    problemXml()
}
```

```xml
<?xml version="1.0" encoding="UTF-8"?>
<problem xmlns="urn:ietf:rfc:7807">
  <type>https://example.com/probs/out-of-credit</type>
  <title>You do not have enough credit.</title>
  <detail>Your current balance is 30, but that costs 50.</detail>
  <status>403</status>
  <instance>/account/12345/msgs/abc</instance>
</problem>
```

The writer produces byte-exact output matching RFC 9457 Appendix B, and the reader parses it
back under namespace `urn:ietf:rfc:7807`. In client code, `problemXml()` inside
`HttpResponseValidator { }` decodes the same format into `ProblemException`, regardless of registration order.

### Document it in OpenAPI

```kotlin
val catalog = problemCatalog { standardStatusCodes() }

install(StatusPages) { problemDetails(catalog) }

routing {
    problemResponses(catalog)   // the catch-all and 404/405/406/415, for every endpoint below

    get("/orders/{id}") { call.respondText("an order") }
        .describe { responses { problemResponse(OutOfCredit) } }
}
```

Ktor infers endpoint responses from route handler bodies. Because problem documents are generated
by `StatusPages` outside route lambdas, OpenAPI generation cannot detect them automatically.
`problemResponses` and `problemResponse` register `application/problem+json` schemas and support
extension members as siblings.

## Under the hood

| Behavior | Rationale |
|---|---|
| Documents use `application/problem+json` even when matched by `application/json`. | The media type identifies the payload as a problem details document. RFC 9457 §3 permits this override. |
| `instance` is set from `request.path()`, excluding query strings. | Query strings frequently contain sensitive tokens. Set `instance` explicitly if query parameters are required. |
| The unmapped handler rethrows `CancellationException`. | If the client disconnects, writing to the closed socket fails. `TimeoutCancellationException` maps to 504. |
| Thrown `ProblemException.cause` is logged server-side and omitted from the body. | Problem documents are sent to clients (§5), so internal file paths and SQL queries are omitted. |
| Both codecs share `Problem.MAX_NESTING_DEPTH`. | Sets a recursion limit, failing with `SerializationException` instead of encountering a `StackOverflowError`. |

Every module publishes for nine targets with logic in `commonMain` and no `expect`/`actual` declarations.
Public API signatures are tracked in `api/*.api` and `api/*.klib.api` and verified on every build to prevent
unintended API changes.

## Stability

The library is in `0.x`. Releases may introduce breaking API and binary changes without a deprecation
cycle. ABI dumps record public API differences in pull request reviews.

`@RequiresOptIn` annotations are omitted in `0.x` because all APIs are subject to change before 1.0.
Opt-in annotations will be introduced in 1.0 for APIs that remain experimental.

## Contributing

See [`CONTRIBUTING.md`](CONTRIBUTING.md) for issue guidelines, build instructions, and pull request requirements.

## License

Apache License 2.0. See [`LICENSE`](LICENSE).

[badge-jvm]: https://img.shields.io/badge/-jvm-DB413D.svg?style=flat
[badge-js]: https://img.shields.io/badge/-js-F8DB5D.svg?style=flat
[badge-wasm]: https://img.shields.io/badge/-wasm-624FE8.svg?style=flat
[badge-linux]: https://img.shields.io/badge/-linux-2D3F6C.svg?style=flat
[badge-windows]: https://img.shields.io/badge/-windows-4D76CD.svg?style=flat
[badge-macos]: https://img.shields.io/badge/-macos-111111.svg?style=flat
[badge-ios]: https://img.shields.io/badge/-ios-CDCDCD.svg?style=flat
