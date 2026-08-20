# Contributing

Thanks for looking. This is a small library with a single maintainer, so the most useful thing you
can do is tell it what you actually need — the roadmap in the [README](README.md#roadmap) is ordered
by demand, and right now there is very little demand to go on.

## Where things go

| You have | Go to |
|---|---|
| A defect | [Issues](https://github.com/ilyankin/kotlin-rfc9457/issues) — the bug form asks for the version, the artifact and a reproducer |
| A proposal | Issues — the feature form. If it is already on the roadmap, comment there instead |
| A question | [Discussions](https://github.com/ilyankin/kotlin-rfc9457/discussions) |
| A security vulnerability | Not a public issue — [report it privately](https://github.com/ilyankin/kotlin-rfc9457/security/advisories/new) |

For a spec question, quote the section of
[RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) you are reading it against. Most disagreements
about this library's behaviour turn out to be disagreements about the RFC, and settle immediately
once both sides are looking at the same paragraph.

## Building

```bash
./gradlew build          # compile, tests, ABI check — everything CI gates on except the docs
```

You do not need a JDK installed: the wrapper provisions the daemon JVM (21) and the compile
toolchain (17) itself.

This is a Kotlin Multiplatform build with a single `jvm()` target, which changes two commands from
what you may expect:

```bash
./gradlew :problem-details-core:jvmTest    # `jvmTest`, not `test` — `test` is not a registered task
./gradlew :problem-details-core:jvmTest --tests "io.github.ilyankin.rfc9457.ProblemBuilderTest"
```

Tests use [Kotest](https://kotest.io) on the JUnit Platform. Nearly all of them live in
`commonTest`; the only exception is `problem-details-ktor/src/jvmTest`, which holds the logging
tests, because Ktor's `Logger` is an `expect interface` whose JVM `actual` is a typealias for
`org.slf4j.Logger` and a recording logger cannot be written in common code.

## Things that will fail CI if you miss them

- **`api/*.api` is the API review.** Every module dumps its public surface, and `check` compares
  against the dump. Change a public declaration and the build fails until you run
  `./gradlew updateKotlinAbi` and commit the result. Read that diff before you push — if it exposes
  something you did not mean to expose, the fix belongs in the source, not the dump.

- **Undocumented public declarations fail the documentation build.** `reportUndocumented` and
  `failOnWarning` are both on. This is not wired into `check` (Dokka needs network access and the
  local build must work offline), so it surfaces on the pull request rather than on your machine —
  CI runs `dokkaGenerate` and greps the output for unresolved KDoc links.

- **`@sample` blocks point at real code.** Public entry points reference functions under
  `src/commonTest/kotlin/io/github/ilyankin/rfc9457/samples`, which are compiled and run as ordinary
  tests. Change a referenced signature and the sample has to change with it.

- **Isolated Projects.** A separate CI job builds with `-Dorg.gradle.isolated-projects=true`. Build
  logic lives in convention plugins under `build-logic/`; `subprojects { }` and `allprojects { }`
  break that job and were removed from this build on purpose.

## Conventions worth knowing before you write code

- **`explicitApi()` is on.** Every public declaration needs an explicit visibility modifier and an
  explicit return type.

- **Extension members are siblings, not nested.** RFC 9457 §3.2 extension members are written at the
  top level of the document, never under an `"extensions"` key. That is the library's whole point of
  difference from other implementations, and both codecs enforce it.

- **Strict and `OrNull` accessors come in pairs.** `.string` throws on the wrong shape,
  `.stringOrNull` returns `null`, because §3 requires *consumers* to treat a wrong-typed member as
  absent. A new accessor should ship both halves rather than pick one.

- **Multiplatform-first.** All library code lives in `commonMain`, there are no `expect`/`actual`
  declarations, and there is exactly one target today. A JVM-only public declaration closes a door
  this build is deliberately holding open — if you think you need one, open an issue first.

- **`api` versus `implementation` is load-bearing.** Gradle `api` becomes POM `compile` and
  `implementation` becomes `runtime`, so anything whose type appears in a public signature — a
  thrown type included — must be `api`. A leak that Gradle consumers never notice breaks Maven
  consumers at compile time.

- **Module boundaries.** `problem-details-ktor` must never depend on the XML modules: an application
  that emits only JSON should never resolve an XML parser. The same holds for
  `problem-details-ktor-openapi`, whose XML half is `problem-details-ktor-openapi-xml`. Optionality is
  expressed by *which artifact declares the registration function*, so a missing dependency is a
  compile error at the call site rather than a runtime `NoClassDefFoundError`.

## Pull requests

Small and focused beats complete. One logical change per commit, with a one-line imperative subject
(`fix(core): Reject a negative status`) — the history here has no commit bodies.

A bug fix should come with the test that reproduced it. A consumer-visible change should come with a
`CHANGELOG.md` entry under `## [Unreleased]`.

The version is `0.x` and there is no compatibility promise yet, so a breaking change is allowed —
but say in the pull request that it is one, so it lands under *Breaking changes* in the changelog.

## Licence

By contributing you agree that your work is licensed under
[Apache License 2.0](LICENSE), the same as the rest of the project. There is no CLA.
