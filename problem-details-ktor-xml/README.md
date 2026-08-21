# Module problem-details-ktor-xml

Registers the `application/problem+xml` codec with Ktor's `ContentNegotiation`. One function and one
converter; everything else lives in `problem-details-ktor` and `problem-details-xml`, both of which
this module depends on.

Add it only if a client of yours asks for XML. Depending on it is the *only* way XML support enters
an application — that is the point of the split, and why this module exists instead of a flag on
`problemJson()`.

Published for `jvm`, `js`, `wasmJs`, `linuxX64`, `linuxArm64`, `mingwX64`, `macosArm64`, `iosArm64`
and `iosSimulatorArm64`. Depend on the plain coordinates from `commonMain` and Gradle picks the
variant.

## Registration order is part of the contract

```kotlin
install(ContentNegotiation) {
    problemJson()   // must come first
    problemXml()
}
```

`Accept: */*` matches both codecs equally, and Ktor breaks that tie by registration order. JSON
should win it. Before the library was split this guarantee was internal; now it is the caller's, so
both orderings are tested and only this one gives JSON the tie.

## Everything is UTF-8, in both directions

Responses are labelled `charset=UTF-8` and encoded as UTF-8 whatever the request negotiated, and
request bodies are decoded as UTF-8 whatever they declare.

This is stricter than Ktor's default and deliberately so. An XML document states its own encoding
*in-band*, and `ProblemXml` writes that declaration as a literal `encoding="UTF-8"` — so honouring a
negotiated `ISO-8859-1` produced latin-1 bytes inside a document claiming to be UTF-8. The HTTP header
and the declaration contradicted each other, and anything reading the bytes without the header — a
saved file, a queued message — got it wrong. Pinning the charset is also what keeps the declaration
byte-identical to the example RFC 9457 Appendix B publishes.

On the read side this matches `problemJson()`, so the two formats never disagree about the same
request.

# Package io.github.ilyankin.rfc9457.ktor.xml

[problemXml] registers [ProblemXmlConverter] for `application/problem+xml`.

Unlike `problemJson()`, there is no "also accept the plain type" flag: `application/xml` is not
registered, and no equivalent of `acceptPlainJson` exists. A client that wants an XML problem
document asks for `application/problem+xml`.
