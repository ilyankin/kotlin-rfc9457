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

        "the five standard members are described" {
            ProblemSchemas.problem.properties
                .shouldNotBeNull()
                .keys shouldBe
                setOf("type", "title", "status", "detail", "instance")
        }

        "status is bounded to the HTTP range" {
            val status =
                ProblemSchemas.problem.properties
                    .shouldNotBeNull()
                    .getValue("status")
            status.valueOrNull().shouldNotBeNull().minimum shouldBe 100.0
            status.valueOrNull().shouldNotBeNull().maximum shouldBe 599.0
        }

        "type and instance are URI references" {
            val properties = ProblemSchemas.problem.properties.shouldNotBeNull()
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
        }

        "the object type is declared" {
            ProblemSchemas.problem.type shouldBe JsonType.OBJECT
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
