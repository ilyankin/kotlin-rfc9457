# Module problem-details-ktor-client-xml

Ktor Client integration for the Appendix B XML form: turn a recognized `application/problem+xml`
error response into the same [io.github.ilyankin.rfc9457.ProblemException] that application code
throws on the server side and `problem-details-ktor` answers.

Registering the XML codec is what pulls in an XML parser, exactly as on the server: an application
that only ever decodes JSON depends on `problem-details-ktor-client` alone and never resolves
`xmlutil`.

Published for `jvm`, `js`, `wasmJs`, `linuxX64`, `linuxArm64`, `mingwX64`, `macosArm64`, `iosArm64`
and `iosSimulatorArm64`. Depend on the plain coordinates from `commonMain` and Gradle picks the
variant.

## Both formats

```kotlin
val client = HttpClient(CIO) {
    expectSuccess = true
    HttpResponseValidator {
        problemJson()
        problemXml()
    }
}
```

**Order does not matter here**, unlike the server's `ContentNegotiation` pair, where whichever
converter is registered first wins an absent or wildcard `Accept`. These two gate on `Content-Type`
instead, and the two types they match are disjoint, so neither can shadow the other. Pinned in both
directions by `RegistrationOrderTest`.

`expectSuccess = true` is required for the same reason as on the JSON side: without it Ktor throws
nothing for a non-2xx response, so there is no exception to intercept and replace.

# Package io.github.ilyankin.rfc9457.ktor.client.xml

| Entry point | Role |
|---|---|
| [problemXml] | Inside `HttpResponseValidator { }`: convert an `application/problem+xml` response into `ProblemException`. |

Two behaviors here are decisions, not defaults, and each is pinned by a test:

- **There is no lenient mode.** `problemXml()` takes no parameters, and plain `application/xml` never
  matches. The JSON side offers `acceptPlainJson` because tooling overwhelmingly labels everything
  `application/json`; XML has no equivalent convention, and an application's own XML dialect is not
  this library's media type to claim. With only one way in, there is also only one failure rule: a
  body labeled `application/problem+xml` that fails to decode propagates as `SerializationException`
  rather than falling back to Ktor's own exception. That covers both of the reader's refusals — a
  malformed document, and a well-formed one rooted at something other than
  `<problem xmlns="urn:ietf:rfc:7807">`.
- **The transport's charset wins.** The body is read with the charset the response declares, falling
  back to UTF-8; a document's own `encoding` pseudo-attribute is never consulted, since the bytes are
  already text by the time the codec sees them. RFC 7303 makes the transport authoritative for
  `+xml` media types, so this is the order the specification asks for — and it matches how the JSON
  half reads its bodies.
