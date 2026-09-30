#!/usr/bin/env bash
# Resolves the library the way outside users do, from a local Maven repository, in three separate
# builds: Gradle JVM, Maven and Kotlin Multiplatform. Run `./gradlew publishToMavenLocal` first.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
version=$(sed -n 's/^version=//p' "$root/gradle.properties")
kotlin=$(sed -n 's/^kotlin = "\(.*\)"$/\1/p' "$root/gradle/libs.versions.toml")

echo "Consumer smoke for ${version}, Kotlin ${kotlin}"

"$root/gradlew" -p "$root/consumer-smoke/gradle-jvm" -q run -Prfc9457.version="$version"
"$root/gradlew" -p "$root/consumer-smoke/kmp" -q assemble -Prfc9457.version="$version"
(cd "$root/consumer-smoke/maven" && ./mvnw -B -q verify -Drfc9457.version="$version" -Dkotlin.version="$kotlin")
