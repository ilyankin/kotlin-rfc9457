# Module problem-details-ktor-openapi-xml

Documents `application/problem+xml` responses in an OpenAPI document, alongside the JSON ones
`problem-details-ktor-openapi` writes. One function; the schemas, the response primitives and the
Appendix B constants all live in modules this one depends on.

Add it only if your application actually serves XML — that is, only if it already depends on
`problem-details-ktor-xml`. Depending on this module is the sole way XML reaches the generated
document, the same split `problem-details-ktor-xml` makes for the runtime codec.

Published for `jvm`, `js`, `wasmJs`, `linuxX64`, `linuxArm64`, `mingwX64`, `macosArm64`, `iosArm64`
and `iosSimulatorArm64`. Depend on the plain coordinates from `commonMain` and Gradle picks the
variant.

## Usage

```kotlin
get("/orders/{id}") { call.respondText("an order") }
    .describe {
        responses {
            problemResponse(OutOfCredit) { problemXmlContent() }
        }
    }
```

One response object, two media types.
[io.github.ilyankin.rfc9457.ktor.openapi.xml.problemXmlContent] is written *inside* the
`problemResponse` block rather than beside it, because OpenAPI allows a single response per status
code and both formats describe the same 403.

## Why the XML schema is a separate component

Ktor hoists any titled schema into `components/schemas`, keyed by title alone, and the last schema
registered under a title wins. A JSON schema and an XML one sharing the title `ProblemDetails` would
therefore collapse into one entry, and which of the two survived — with or without the `xml` object —
would depend on the order the routing tree happened to be walked.

[io.github.ilyankin.rfc9457.ktor.openapi.xml.problemXmlContent] retitles its copy with an `Xml`
suffix, so `ProblemDetails` and
`ProblemDetailsXml` are hoisted separately and each media type references its own. The JSON component
stays free of XML metadata, and the XML one always carries Appendix B's root element and namespace.

# Package io.github.ilyankin.rfc9457.ktor.openapi.xml

| Entry point | Role |
|---|---|
| [problemXmlContent] | Inside a `problemResponse`/`problemDefault` block: add `application/problem+xml` beside the JSON body already documented. |

The namespace written into the schema is `urn:ietf:rfc:7807` — RFC 7807's, unchanged, because the wire
format did not change when RFC 9457 obsoleted it.
