plugins {
    // Lets Gradle auto-download a matching JDK for the `java.toolchain` requirement
    // (build.gradle.kts) on any machine that doesn't already have it installed -
    // without this, a fresh environment (CI, Railway's build container, ...) fails
    // with "Toolchain download repositories have not been configured."
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "backend"
