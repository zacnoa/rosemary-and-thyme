plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jooq.jooq-codegen-gradle") version "3.21.2"
}

group = "rosemary_and_thyme"
version = "0.0.1-SNAPSHOT"
description = "rosemary-and-thyme-backend"

java {
    toolchain {
        // Railway's runtime image only ships Java 21 - compiling for a newer
        // toolchain here (was 24) produces class files the deploy container's
        // JVM can't load (UnsupportedClassVersionError). 21 is an LTS release,
        // fully supported by Kotlin 2.2.21 / Spring Boot 4.0.5.
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Spring Boot's plugin also runs the plain `jar` task by default, producing a
// second, non-executable jar (backend-x.y.z-plain.jar) alongside the real bootJar.
// Disabling it keeps build/libs/*.jar unambiguous - relevant for Railway, whose
// start command globs for a single jar there and would otherwise sometimes pick
// the plain one (no Main-Class manifest -> "no main manifest attribute" crash).
tasks.named<Jar>("jar") {
    enabled = false
}

repositories {
    mavenCentral()
}
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // Google sign-in (see SecurityConfig.oauth2Login / GoogleOidcSuccessHandler) -
    // authorization code exchange, PKCE/state, and ID token (JWKS) validation are
    // all handled by this starter, not hand-rolled.
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.jooq:jooq:3.21.2")
    implementation("org.springframework.boot:spring-boot-starter-jooq")

    implementation("com.cloudinary:cloudinary-http5:2.3.2")

    runtimeOnly("org.postgresql:postgresql")
    jooqCodegen("org.jooq:jooq-meta-extensions:3.21.2")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation("org.springframework.security:spring-security-crypto")

    implementation("com.bucket4j:bucket4j-core:8.10.1")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
jooq {
    configuration {
        generator {
            name = "org.jooq.codegen.KotlinGenerator"
            database {
                // Parses db/schema.sql directly instead of introspecting a live
                // database - codegen no longer needs DB_URL/credentials or network
                // access, so it's safe to run as part of every build (see
                // compileKotlin.dependsOn below). db/indexes.sql is deliberately
                // NOT included here - see the comment in that file.
                name = "org.jooq.meta.extensions.ddl.DDLDatabase"
                includes = ".*"
                properties {
                    property {
                        key = "scripts"
                        value = "db/schema.sql"
                    }
                    property {
                        key = "sort"
                        value = "semantic"
                    }
                    // Without this, H2 (used internally by DDLDatabase to simulate the
                    // DDL) upper-cases every unquoted identifier from schema.sql - not
                    // just schema names (handled below), but TABLE and COLUMN names too.
                    // That silently baked "SESSIONS"/"USERS"/etc. into the generated
                    // code's rendered SQL, which then failed against Postgres's actual
                    // lowercase tables (relation "app_auth.SESSIONS" does not exist).
                    // "lower" makes it fold the other way, matching what Postgres does
                    // with unquoted identifiers by default.
                    property {
                        key = "defaultNameCase"
                        value = "lower"
                    }
                }
                schemata {
                    schema {
                        // schema.sql spells out "public.<table>" explicitly (e.g.
                        // "public.hero_images"), and with defaultNameCase = "lower"
                        // that unquoted "public" is folded/quoted to lowercase too -
                        // so the schema DDLDatabase reports is actually "public", not
                        // H2's implicit uppercase "PUBLIC". No rename needed; this
                        // entry only exists so "public" isn't excluded (schemata acts
                        // as an allow-list once any schema is listed).
                        inputSchema = "public"
                    }
                    schema {
                        // Same reasoning as "public" above - app_auth is already
                        // lowercase once parsed under defaultNameCase = "lower".
                        inputSchema = "app_auth"
                    }
                }
            }
            generate {
                isGeneratedAnnotation = false
                isKotlinNotNullPojoAttributes = true
                isKotlinNotNullRecordAttributes = true
                isKotlinNotNullInterfaceAttributes = true

            }
            target {
                directory = "src/main/kotlin"
                packageName = "rosemary_and_thyme.backend.jooqgenerated"
            }
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn("jooqCodegen")
}
