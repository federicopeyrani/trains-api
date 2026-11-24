# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build (also generates code from OpenAPI specs)
./gradlew build

# Run the application
./gradlew bootRun

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.federicopeyrani.trainapis.trains.TrainsServiceTests"

# Regenerate API code from OpenAPI specs only
./gradlew openApiGenerate openApiGenerateViaggiatreno

# Clean build
./gradlew clean build
```

## Architecture

This is a Kotlin/Spring Boot 4 WebFlux application that exposes a REST API for querying Italian train schedules. It wraps the ViaggiaTreno API (Trenitalia's unofficial API) behind a clean OpenAPI-defined interface.

### Code Generation (two separate OpenAPI specs)

1. **Public API** (`src/main/resources/openapi.yaml`) → generates controller interfaces and DTOs in `com.federicopeyrani.trainapis.api` and `com.federicopeyrani.trainapis.model`. Uses `kotlin-spring` generator with coroutines.
2. **ViaggiaTreno client** (`src/main/resources/viaggiatreno-api.yaml`) → generates a Ktor-based HTTP client in `com.federicopeyrani.trainapis.providers.viaggiatreno.client`. Uses `kotlin` generator with `jvm-ktor` library.

Generated code lives in `build/generated/` and `build/generated-viaggiatreno/` — both are added as source sets. `compileKotlin` depends on both generation tasks.

### Request Flow

`TrainsController` (implements generated `DefaultApi`) → `TripsService` → `TripsProvider`/`StationsProvider` interfaces → `ViaggiaTreno*Provider` implementations → `ViaggiaTreno*ApiWrapper` (cached HTTP calls via Ktor) → ViaggiaTreno API.

### Key Design Decisions

- **Provider pattern**: `StationsProvider` and `TripsProvider` are interfaces, with ViaggiaTreno as the only implementation. This allows swapping data sources.
- **Reactive with coroutines**: All data flows use `kotlinx.coroutines.flow.Flow`. Concurrent API fan-out uses `flatMapMerge` with configurable concurrency (`providers.viaggiatreno.concurrency` property).
- **Custom caching**: `Cache`/`CacheElement` in `com.federicopeyrani.trainapis.data` wraps Spring's `CacheManager` with TTL support. The API wrappers use this to cache upstream responses.
- **Serialization**: Uses `kotlinx.serialization` (not Jackson) for the ViaggiaTreno client models and public API DTOs. Public DTOs are annotated with `@kotlinx.serialization.Serializable` via OpenAPI generator config.
- **Time handling**: All times from ViaggiaTreno (Europe/Rome timezone, epoch millis) are converted to UTC `kotlinx.datetime.LocalDateTime` before returning to clients.

### Test Setup

Tests use `io.ktor:ktor-client-mock` to mock ViaggiaTreno HTTP responses and `io.mockk:mockk` / `springmockk` for unit testing. Mock engine configuration is in `src/test/kotlin/.../providers/viaggiatreno/ViaggiaTrenoMockEngineConfiguration.kt`.
