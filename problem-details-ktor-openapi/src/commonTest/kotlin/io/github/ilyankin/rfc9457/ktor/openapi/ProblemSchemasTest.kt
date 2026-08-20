package io.github.ilyankin.rfc9457.ktor.openapi

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.openapi.AdditionalProperties
import io.ktor.openapi.JsonType

class ProblemSchemasTest :
    StringSpec({

        "the schema carries a title, which is what makes Ktor hoist it into components" {
            ProblemSchemas.problem.title shouldBe "ProblemDetails"
            ProblemSchemas.problemWithErrors.title shouldBe "ProblemDetailsWithErrors"
        }

        "every standard member is optional, as RFC 9457 3.1 requires" {
            ProblemSchemas.problem.required shouldBe null
        }

        "extension members are admitted as siblings, per RFC 9457 3.2" {
            ProblemSchemas.problem.additionalProperties shouldBe AdditionalProperties.Allowed(true)
        }

        "the standard members carry the shapes RFC 9457 3.1 gives them" {
            ProblemSchemas.problem.type shouldBe JsonType.OBJECT

            val properties = ProblemSchemas.problem.properties.shouldNotBeNull()
            properties.keys shouldBe setOf("type", "title", "status", "detail", "instance")

            properties
                .getValue("type")
                .valueOrNull()
                .shouldNotBeNull()
                .format shouldBe "uri-reference"
            properties
                .getValue("instance")
                .valueOrNull()
                .shouldNotBeNull()
                .format shouldBe "uri-reference"

            val status = properties.getValue("status").valueOrNull().shouldNotBeNull()
            status.minimum shouldBe 100.0
            status.maximum shouldBe 599.0
        }

        "the errors variant adds an errors array of detail and pointer entries" {
            val properties = ProblemSchemas.problemWithErrors.properties.shouldNotBeNull()
            properties.keys shouldBe setOf("type", "title", "status", "detail", "instance", "errors")

            val errors = properties.getValue("errors").valueOrNull().shouldNotBeNull()
            errors.type shouldBe JsonType.ARRAY
            errors.items
                .shouldNotBeNull()
                .valueOrNull()
                .shouldNotBeNull()
                .properties
                .shouldNotBeNull()
                .keys shouldBe setOf("detail", "pointer")
        }

        "the errors variant is standalone, so it never emits a dangling reference" {
            ProblemSchemas.problemWithErrors.allOf shouldBe null
        }
    })
