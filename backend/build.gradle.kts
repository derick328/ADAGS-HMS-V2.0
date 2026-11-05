plugins {
    java
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "com.adags"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val archunitVersion = "1.3.0"
val testcontainersVersion = "1.21.4"

// Override the BOM's own version property — the mechanism it actually respects
// (plain resolutionStrategy.force loses to the BOM here). See backend/CLAUDE.md
// for why this needs to stay current and why systemProperty("api.version", ...)
// below is required regardless of which Testcontainers version is pinned.
extra["testcontainers.version"] = testcontainersVersion

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Argon2id (Spring Security Crypto delegates to Bouncy Castle for Argon2)
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // 16-lab-verification-model.md v0.2 §4 / 15-lab-technician.md §5.4:
    // machine output goes to object storage, never a database blob. MinIO
    // is already provisioned in docker-compose.yml; this is the first
    // module to actually talk to it.
    implementation("io.minio:minio:8.5.12")

    // Phase 5/9 catalogue import: parse the admin's .xlsx price file
    // server-side. poi-ooxml pulls in the OOXML (.xlsx) subset; the older
    // .xls format is out of scope.
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.apache.commons:commons-csv:1.11.0")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:$archunitVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Real OS env var, matching what V3__BootstrapAdmin reads in production —
    // this is a fixed test-only value, never used outside a Testcontainers Postgres.
    environment("ADAGS_BOOTSTRAP_ADMIN_PASSWORD", "Integration-Test-Bootstrap-Pw1")
    // Testcontainers' DockerClientProviderStrategy falls back to hardcoded API
    // 1.32 for its connectivity probe whenever it can't resolve an explicit
    // client API version — true on every Testcontainers release through 2.0.x,
    // not just old ones. This host's Docker Engine (API 1.54, min supported
    // 1.40) rejects that probe outright. docker-java's config loader only
    // reads the API version from the "api.version" JVM system property — there
    // is no environment-variable form, so DOCKER_API_VERSION is a silent no-op.
    systemProperty("api.version", "1.41")
}

// Emits api/openapi.yaml at the repo root by running OpenApiSpecGenerationTest,
// which boots the full context against a Testcontainers Postgres and writes the
// resolved /v3/api-docs.yaml response to disk. Driven by a JUnit test (not the
// springdoc bootRun+curl plugin) so it needs nothing running beyond Docker —
// no manual `docker-compose up` step, and it works the same in CI.
// The frontend's openapi-typescript step reads api/openapi.yaml — never hand-write
// a request/response type from it.
val generateOpenApiDocs by tasks.registering(Test::class) {
    group = "documentation"
    description = "Generates api/openapi.yaml from the running API surface."
    useJUnitPlatform()
    filter {
        includeTestsMatching("com.adags.OpenApiSpecGenerationTest")
    }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    outputs.upToDateWhen { false }
}

tasks.named("build") {
    dependsOn(generateOpenApiDocs)
}
