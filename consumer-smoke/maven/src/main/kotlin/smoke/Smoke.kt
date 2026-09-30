package smoke

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ktor.client.problemJson
import io.github.ilyankin.rfc9457.ktor.problemCatalog
import io.github.ilyankin.rfc9457.problem
import io.github.ilyankin.rfc9457.xml.ProblemXml
import io.ktor.client.plugins.HttpCallValidatorConfig
import kotlinx.serialization.json.Json

// Exercises one class from each artifact at run time, so a jar missing from the resolved graph
// fails here as `NoClassDefFoundError` rather than passing because it compiled.
fun main() {
    val original =
        problem {
            type = "https://example.com/probs/out-of-credit"
            status = 403
            title = "You do not have enough credit."
            extension("balance", 30)
        }

    val json = Json.encodeToString(Problem.serializer(), original)
    check("\"balance\":30" in json) { "Extension member is not a sibling: $json" }
    check(Json.decodeFromString(Problem.serializer(), json) == original) { "JSON round trip changed $json" }

    val xml = ProblemXml.encodeToString(original)
    check(ProblemXml.decodeFromString(xml).title == original.title) { "XML round trip changed $xml" }

    check(problemCatalog { }.problemTypes.isEmpty())
    val registerClient: HttpCallValidatorConfig.() -> Unit = { problemJson() }
    registerClient(HttpCallValidatorConfig())

    println("Consumer smoke passed")
}
