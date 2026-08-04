package io.github.ilyankin.rfc9457.samples

import io.github.ilyankin.rfc9457.ktor.client.problemJson
import io.github.ilyankin.rfc9457.ktor.client.xml.problemXml
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.plugins.HttpResponseValidator

/** @see io.github.ilyankin.rfc9457.ktor.client.xml.problemXml */
internal fun HttpClientConfig<MockEngineConfig>.problemXmlSample() {
    expectSuccess = true
    HttpResponseValidator {
        problemJson()
        problemXml()
    }
}
