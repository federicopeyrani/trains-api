import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.27.0"
}

group = "com.federicopeyrani"
version = "0.0.1-SNAPSHOT"
description = "train-apis"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

ext["kotlin-coroutines.version"] = "1.11.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("io.swagger.core.v3:swagger-annotations:2.2.56")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("jakarta.annotation:jakarta.annotation-api")

    // OkHttp client dependencies for ViaggiaTreno API client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Spring Cache
    implementation("org.springframework.boot:spring-boot-starter-cache")

    implementation("io.ktor:ktor-client-core:3.6.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.6.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.6.1")
    implementation("io.ktor:ktor-client-java:3.6.1")
    implementation("io.ktor:ktor-client-logging:3.6.1")
    testImplementation("io.ktor:ktor-client-mock:3.6.1")
    implementation("ch.qos.logback:logback-classic:1.5.25")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("io.mockk:mockk:1.14.12")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named("openApiGenerate", GenerateTask::class) {
    generatorName = "kotlin-spring"
    inputSpec = layout.projectDirectory.file("src/main/resources/openapi.yaml")
    outputDir = layout.buildDirectory.dir("generated")
    apiPackage = "com.federicopeyrani.trainapis.api"
    modelPackage = "com.federicopeyrani.trainapis.model"
    configOptions = mapOf(
        "interfaceOnly" to "true",
        "useSpringBoot3" to "true",
        "reactive" to "true",
        "useTags" to "true",
        "skipDefaultInterface" to "true",
        "documentationProvider" to "none",
        "enumPropertyNaming" to "UPPERCASE",
        "serializationLibrary" to "jackson",
        "declarativeInterfaceWrapResponses" to "true",
        "declarativeInterfaceReactiveMode" to "coroutines",
        "additionalModelTypeAnnotations" to "@kotlinx.serialization.Serializable",
    )
    typeMappings = mapOf(
        "DateTime" to "LocalDateTime"
    )
    importMappings = mapOf(
        "LocalDateTime" to "kotlinx.datetime.LocalDateTime"
    )
}

tasks.register("openApiGenerateViaggiatreno", GenerateTask::class) {
    generatorName = "kotlin"
    inputSpec = layout.projectDirectory.file("src/main/resources/viaggiatreno-api.yaml")
    outputDir = layout.buildDirectory.dir("generated-viaggiatreno")
    apiPackage = "com.federicopeyrani.trainapis.providers.viaggiatreno.client.api"
    modelPackage = "com.federicopeyrani.trainapis.providers.viaggiatreno.client.model"
    configOptions = mapOf(
        "dateLibrary" to "kotlinx-datetime",
        "serializationLibrary" to "kotlinx_serialization",
        "enumPropertyNaming" to "UPPERCASE",
        "library" to "jvm-ktor",
    )
    typeMappings = mapOf(
        "java.math.BigDecimal" to "kotlin.Double",
    )
}

sourceSets {
    main {
        kotlin {
            srcDir(layout.buildDirectory.dir("generated/src/main/kotlin"))
            srcDir(layout.buildDirectory.dir("generated-viaggiatreno/src/main/kotlin"))
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn("openApiGenerate", "openApiGenerateViaggiatreno")
}

