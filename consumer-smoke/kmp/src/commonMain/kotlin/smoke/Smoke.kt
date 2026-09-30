package smoke

import io.github.ilyankin.rfc9457.Problem
import io.github.ilyankin.rfc9457.ktor.client.problemJson
import io.github.ilyankin.rfc9457.ktor.problemCatalog
import io.github.ilyankin.rfc9457.problem
import io.github.ilyankin.rfc9457.xml.ProblemXml
import io.ktor.client.plugins.HttpCallValidatorConfig
import kotlinx.serialization.json.Json

// Compiled for every target from `commonMain`: what a multiplatform consumer writes. Resolution and
// compilation are the check; `gradle-jvm` and `maven` run the same code.
fun smoke(): String {
    val original =
        problem {
            type = "https://example.com/probs/out-of-credit"
            status = 403
            title = "You do not have enough credit."
            extension("balance", 30)
        }
    val json = Json.encodeToString(Problem.serializer(), original)
    val xml = ProblemXml.encodeToString(original)
    problemCatalog { }
    val registerClient: HttpCallValidatorConfig.() -> Unit = { problemJson() }
    return json + xml + registerClient
}
