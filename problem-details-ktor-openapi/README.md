# Module problem-details-ktor-openapi

Teaches Ktor's OpenAPI generator to describe the problem responses a `problem-details-ktor` application
actually produces: `application/problem+json` bodies, an RFC 9457 schema that admits extension members
as siblings, and the status codes a `problemDetails { }` catalog answers.

Ktor infers an operation's responses from what its route handler calls. Problem documents come from
`StatusPages`, which sits outside every route lambda, so inference can never see them and an otherwise
complete document says nothing about failures. This module supplies them explicitly, through Ktor's own
`io.ktor.openapi` model, so the result merges with whatever inference already found.

# Package io.github.ilyankin.rfc9457.ktor.openapi

| Entry point | Role |
|---|---|
| [problemResponses] | On a `Route`: attach a catalog's universal responses to that route and everything below it. One call inside `routing { }` covers the whole application. |
| [problemsFrom] | Inside `responses { }`: document the half of a `ProblemDetailsCatalog` that holds for every operation — the catch-all and its registered status codes. |
| [problemResponse] | Inside `responses { }`: document one problem type at its own status, or a bare status code. |
| [problemDefault] | Inside `responses { }`: document the catch-all `default` response as a problem document. |
| [ProblemSchemas] | The RFC 9457 JSON Schemas — `problem` and the `errors[]`-carrying `problemWithErrors`. Hand-written, because the serializer's descriptor describes neither optionality nor extension members. |

Published for `jvm`, `js`, `wasmJs`, `linuxX64`, `linuxArm64`, `mingwX64`, `macosArm64`, `iosArm64`
and `iosSimulatorArm64`. Depend on the plain coordinates from `commonMain` and Gradle picks the
variant.

## Usage

```kotlin
val catalog = problemCatalog { standardStatusCodes() }

install(StatusPages) { problemDetails(catalog) }

routing {
    // Every endpoint below now documents the catch-all and the catalog's status codes.
    problemResponses(catalog)

    get("/orders/{id}") { call.respondText("an order") }
        .describe { responses { problemResponse(OutOfCredit) } }
}
```

Ktor folds route metadata from the routing root downwards, so the one call at the root reaches every
endpoint, and a `describe` on a leaf merges with it rather than replacing it. Call
[problemResponses] on a `route("/api") { }` instead when only a subtree should carry them.

The schema lands in `components/schemas/ProblemDetails` and every response references it — Ktor hoists
any titled schema on its own, so no component registration is needed here.

## What a catalog can and cannot tell you

A `ProblemDetailsCatalog` is installed once for the whole application and records no route. Two halves
of it therefore document very differently.

The catch-all and every status registered through `forStatusCode`/`standardStatusCodes()` hold
*wherever* the catalog is installed — any route can 404, any unhandled failure answers with a problem
document. [problemsFrom] attaches exactly those.

A type declared with `map<InsufficientFunds>(OutOfCredit)` does not. Attaching it everywhere would
document `GET /health` as returning 403 and make a client generator emit handling for it. Name such a
type where it can actually occur, with [problemResponse].

## Two problem types on one status

OpenAPI allows a single response object per status code, so declaring two types that share one — a 403
that is either "out of credit" or "account locked" — cannot produce two entries. The two calls collapse
into one response whose description names both, carrying one example per type keyed by its type URI.
Nothing is silently dropped, and a client generator still sees a single 403 shape, which is what the
wire actually offers.

## Why the schemas are not inferred

`jsonSchema<Problem>()` would read `ProblemSerializer`'s descriptor, which declares `type` non-optional
and has no way to describe extension members at all — they are decided at runtime. The result would
require a member RFC 9457 §3.1 makes optional and omit the sibling extensions of §3.2 entirely.

Each schema carries a non-null `title`, and that is load-bearing: Ktor hoists a titled schema into
`components/schemas` and rewrites every use to a `$ref`. Clearing the title inlines the whole schema at
every response instead.
