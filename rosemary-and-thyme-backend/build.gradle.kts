
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.jooq.codegen)
}

group = "rosemary_and_thyme"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.server.config.yaml)
    
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)

    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.sessions)
    implementation(ktorLibs.server.statusPages)

    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.serialization.kotlinx.json)

    implementation(libs.spring.security.crypto)
    implementation(libs.password4j)
    implementation(libs.jooq)
    implementation(libs.jooq.kotlin)
    implementation(libs.logback.classic)
    implementation(libs.aws.s3)
    implementation(libs.java.jwt)

    runtimeOnly(libs.postgresql)
    jooqCodegen(libs.postgresql)

    implementation(libs.hikari) 





    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
jooq {
    configuration {
        jdbc {
            driver = "org.postgresql.Driver"

            url = System.getenv("DB_URL")
                ?: "jdbc:postgresql://localhost:5432/rosemary_and_thyme"

            user = System.getenv("DB_USER")
                ?: "user"

            password = System.getenv("DB_PASSWORD")
                ?: "password"
        }

        generator {
            name = "org.jooq.codegen.KotlinGenerator"

            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"

                includes = ".*"
                excludes = "flyway_schema_history"

                schemata {
                    schema {
                        inputSchema = "public"
                    }

                    schema {
                        inputSchema = "app_auth"
                    }
                }
            }

            target {
                packageName = "rosemary_and_thyme.jooq"

                directory = layout.buildDirectory
                    .dir("generated-src/jooq/main")
                    .get()
                    .asFile
                    .path
            }
        }
    }
}
