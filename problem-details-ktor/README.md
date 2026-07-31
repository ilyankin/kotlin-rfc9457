# Module problem-details-ktor

Ktor server integration: respond with a problem document, and turn thrown exceptions into problem
documents through `StatusPages`.

**This module never depends on `problem-details-xml`.** That edge is the reason the library is split
at all — an application that only emits JSON does not resolve an XML parser. XML support is a
separate artifact declaring its own registration function, so a missing dependency is a compile error
at the call site rather than a `NoClassDefFoundError` at runtime.

## What it does not do

It does not reimplement dispatch or content negotiation. Everything here generates the calls you
would otherwise hand-write into `StatusPages`' and `ContentNegotiation`'s own configuration DSLs.
Nearest-parent-class exception resolution and `Accept` quality-value matching stay Ktor's, with
Ktor's semantics and Ktor's bug fixes.

## Where to start

```kotlin
install(ContentNegotiation) { problemJson() }

install(StatusPages) {
    problemDetails {
        standardStatusCodes()
        map<IllegalArgumentException> { _, e -> problem { status = 400; detail = e.message } }
    }
}
```

Then respond from a route with [io.github.ilyankin.rfc9457.ktor.respondProblem], or just throw and
let the catalog answer.

# Package io.github.ilyankin.rfc9457.ktor

| Entry point | Role |
|---|---|
| [respondProblem] | Respond with a problem document; fills `status` and `instance` from the call. |
| [problemDetails] | Inside `install(StatusPages)`: build the exception-to-problem catalog. |
| [ProblemDetailsCatalog] | That catalog — `map<T>`, `forStatusCode`, `standardStatusCodes`, `onUnmapped`. |
| [problemJson] | Inside `install(ContentNegotiation)`: register the JSON codec. |
| [ProblemJsonConverter] | The converter behind it, if you need to register it yourself. |
| [ProblemContentTypes] | `application/problem+json` and `application/problem+xml` as `ContentType`. |

Three behaviours here are decisions rather than defaults, and each is pinned by a test:

- A problem document is **always** labelled `application/problem+json`, even when it was matched
  under `application/json`. RFC 9457 §3 permits the override, and echoing back `application/json`
  would strip the only wire-level marker saying the body is a problem document.
- `instance` is filled from `request.path()`, never `request.uri` — the query string is left out
  because problem documents get logged on both ends and query strings carry tokens often enough that
  echoing them back by default is the wrong trade. Set `instance` explicitly to include it.
- The catch-all handler **rethrows `CancellationException`** unless the status map claims it. A
  cancellation means the client is gone; answering it writes a document to a dead socket and logs a
  stack trace per dropped connection. `TimeoutCancellationException` still becomes a 504.
