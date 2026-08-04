# Module problem-details-ktor-client

Ktor Client integration: turn a recognized `application/problem+json` error response into the same
[io.github.ilyankin.rfc9457.ProblemException] that application code throws on the server side and
`problem-details-ktor` answers.

**This module never depends on `problem-details-ktor`.** It only needs to know the wire format, not
how the other side of the connection is built — the same reasoning that keeps `problem-details-ktor`
itself independent of either XML module.

JSON only, likewise by artifact: decoding the Appendix B XML form is planned as its own module,
exactly as `problemXml()` is on the server, so a client that never speaks XML never resolves an XML
parser.

## `expectSuccess` must be `true`

Ktor's own `HttpClientConfig.expectSuccess` defaults to `false`. Without it, nothing throws for a
non-2xx response in the first place, so there is nothing for this module to intercept and replace:

```kotlin
val client = HttpClient(CIO) {
    expectSuccess = true
    HttpResponseValidator {
        problemJson()
    }
}
```

A client that has to keep the default can opt in per request instead —
`client.get(url) { expectSuccess = true }`. Either way, the failure mode when it is missing is
silence: nothing throws, and `get()` returns the error response as if it were a normal one.

## What it does not do

It does not touch outgoing requests — no automatic `Accept: application/problem+json` header. Ktor's
own default response validation runs regardless of what `Accept` was sent, and a server may answer
with a problem document even when the client did not list the media type in `Accept`: RFC 9457 §3
does exactly that in its own example, as RFC 9110 §12.5.1 allows.

# Package io.github.ilyankin.rfc9457.ktor.client

| Entry point | Role |
|---|---|
| [problemJson] | Inside `HttpResponseValidator { }`: convert a recognized problem response into `ProblemException`. |

Two behaviors here are decisions, not defaults, and each is pinned by a test:

- **`application/problem+json` and a plain `application/json` are not trusted equally.** A response
  strictly labeled `application/problem+json` that fails to decode is a broken promise from the
  server and propagates loudly, as `SerializationException`. A response merely labeled
  `application/json` (matched only when `acceptPlainJson = true`) was never a promise about shape — a
  decode failure there falls back silently to Ktor's own `ClientRequestException`/
  `ServerResponseException`. `acceptPlainJson` defaults to `false`, the opposite of
  `problem-details-ktor`'s own `acceptPlainJson = true` default, because it now governs bodies from
  servers this project does not control, not this project's own well-formed output. Note that on this
  side the flag reads the response's `Content-Type`; it has nothing to do with the `Accept` header,
  which this module never sets.
- **A redirect answered with a problem document is replaced too.** Every status Ktor raises a
  `ResponseException` for qualifies — 3xx included, not just 4xx/5xx. A server that explains a
  redirect it cannot perform gets the same treatment as one that explains a failure.
