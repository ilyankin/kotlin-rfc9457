package rfc9457.build

import groovy.util.Node
import groovy.util.NodeList
import org.gradle.api.Action
import org.gradle.api.XmlProvider

/**
 * Rewrites the root POM of a Kotlin Multiplatform publication into `packaging: pom` plus a single
 * compile-scoped dependency on the `-jvm` artifact, the way kotlinx-serialization and
 * kotlinx-coroutines publish theirs. Without it, a consumer who writes the plain coordinates gets
 * the metadata jar — no classes — and has to find the `-jvm` suffix the hard way.
 *
 * A class rather than a lambda on purpose: `withXml` runs during task execution, so anything it
 * closes over is serialized into the configuration cache. A lambda here failed with "cannot
 * serialize Gradle script object references"; reading `project.group` directly failed with "cannot
 * serialize object of type ... Project". A class taking three plain strings captures three strings.
 */
class RewriteRootPomToJvmRedirect(
    private val groupId: String,
    private val jvmArtifactId: String,
    private val version: String,
) : Action<XmlProvider> {
    override fun execute(xml: XmlProvider) {
        val root = xml.asNode()
        root.appendNode("packaging", "pom")
        // KGP emits the real dependencies at runtime scope here; they are replaced rather than
        // added to, so nothing is resolved twice.
        (root.get("dependencies") as NodeList)
            .toList()
            .forEach { root.remove(it as Node) }
        root.appendNode("dependencies")
            .appendNode("dependency").apply {
                appendNode("groupId", groupId)
                appendNode("artifactId", jvmArtifactId)
                appendNode("version", version)
                appendNode("scope", "compile")
            }
    }
}
