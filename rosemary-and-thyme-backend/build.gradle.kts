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
        languageVersion = JavaLanguageVersion.of(24)
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
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.jooq:jooq:3.21.2")
    implementation("org.springframework.boot:spring-boot-starter-jooq")

    implementation("com.cloudinary:cloudinary-http5:2.3.2")

    runtimeOnly("org.postgresql:postgresql")
    jooqCodegen("org.postgresql:postgresql")

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
        jdbc {
            driver = "org.postgresql.Driver"
            url = System.getenv("DB_URL")
            user = System.getenv("DB_USER")
            password = System.getenv("DB_PASSWORD")
        }
        generator {
            name = "org.jooq.codegen.KotlinGenerator"
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                includes = ".*"
                schemata {

                    schema {
                        inputSchema = "public"
                    }
                    schema {
                        inputSchema = "auth"
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
