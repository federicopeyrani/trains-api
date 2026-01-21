plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.19.0"
    kotlin("plugin.serialization") version "2.2.21"
}

group = "com.federicopeyrani"
version = "0.0.1-SNAPSHOT"
description = "train-apis"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("io.swagger.core.v3:swagger-annotations:2.2.26")
    implementation("jakarta.validation:jakarta.validation-api")
    implementation("jakarta.annotation:jakarta.annotation-api")

    // OkHttp client dependencies for ViaggiaTreno API client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Spring Cache
    implementation("org.springframework.boot:spring-boot-starter-cache")

    implementation("io.ktor:ktor-client-core:3.3.3")
    implementation("io.ktor:ktor-client-content-negotiation:3.3.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.3.3")
    implementation("io.ktor:ktor-client-java:3.3.3")
    implementation("io.ktor:ktor-client-logging:3.3.3")
    implementation("ch.qos.logback:logback-classic:1.5.25")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named("openApiGenerate", org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
    generatorName.set("kotlin-spring")
    inputSpec.set("$rootDir/src/main/resources/openapi.yaml")
    outputDir.set(layout.buildDirectory.dir("generated").get().asFile.path)
    apiPackage.set("com.federicopeyrani.trainapis.api")
    modelPackage.set("com.federicopeyrani.trainapis.model")
    configOptions.set(mapOf(
        "interfaceOnly" to "true",
        "useSpringBoot3" to "true",
        "reactive" to "true",
        "useTags" to "true",
        "skipDefaultInterface" to "true",
        "documentationProvider" to "none",
        "enumPropertyNaming" to "UPPERCASE",
        "serializationLibrary" to "jackson",
        "useCoroutines" to "true",
    ))
}

tasks.register("openApiGenerateViaggiatreno", org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
    generatorName.set("kotlin")
    inputSpec.set("$rootDir/src/main/resources/viaggiatreno-api.yaml")
    outputDir.set(layout.buildDirectory.dir("generated-viaggiatreno").get().asFile.path)
    apiPackage.set("com.federicopeyrani.trainapis.providers.viaggiatreno.client.api")
    modelPackage.set("com.federicopeyrani.trainapis.providers.viaggiatreno.client.model")
    configOptions.set(mapOf(
        "dateLibrary" to "kotlinx-datetime",
        "serializationLibrary" to "kotlinx_serialization",
        "enumPropertyNaming" to "UPPERCASE",
        "library" to "jvm-ktor"
    ))
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

