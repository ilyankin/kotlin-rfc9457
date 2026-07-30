plugins { alias(libs.plugins.kotlin.serialization) }

kotlin {
    sourceSets.getByName("commonMain").dependencies {
        api(libs.kotlinx.serialization.json)
    }
}
