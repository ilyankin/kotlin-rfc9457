package io.github.ilyankin.rfc9457.samples

import io.github.ilyankin.rfc9457.ktor.client.problemJson
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.plugins.HttpResponseValidator

/** @see io.github.ilyankin.rfc9457.ktor.client.problemJson */
internal fun HttpClientConfig<MockEngineConfig>.problemJsonSample() {
    expectSuccess = true
    HttpResponseValidator {
        problemJson()
    }
}
