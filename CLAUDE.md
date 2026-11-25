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
./gradlew test --tests "com.federicopeyrani.trainapis.trip.application.TripServiceTests"

# Regenerate API code from OpenAPI specs only
./gradlew openApiGenerate openApiGenerateViaggiatreno

# Clean build
./gradlew clean build
```

## Architecture

This is a Kotlin/Spring Boot 4 WebFlux application that exposes a REST API for querying Italian train schedules. It wraps the ViaggiaTreno API (Trenitalia's unofficial API) behind a clean OpenAPI-defined interface.

### Code Generation (two separate OpenAPI specs)

1. **Public API** (`src/main/resources/openapi.yaml`) → generates controller interfaces and DTOs in `com.federicopeyrani.trainapis.api` and `com.federicopeyrani.trainapis.model`. Uses `kotlin-spring` generator with coroutines.
2. **ViaggiaTreno client** (`src/main/resources/viaggiatreno-api.yaml`) → generates a Ktor-based HTTP client in `com.federicopeyrani.trainapis.shared.infrastructure.viaggiatreno.client`. Uses `kotlin` generator with `jvm-ktor` library.

Generated code lives in `build/generated/` and `build/generated-viaggiatreno/` — both are added as source sets. `compileKotlin` depends on both generation tasks. Operations in `openapi.yaml` are tagged (`stations`, `trips`), so with `useTags` the generator emits one interface per feature (`StationsApi`, `TripsApi`); every new operation needs a tag.

### Hexagonal Layout (feature-sliced)

Code is split by feature, and each feature is split into ports & adapters layers:

```
com.federicopeyrani.trainapis
├── station/                         # GET /stations
├── trip/                            # GET /departures, /trips, /trips/all, /runs
│   ├── domain/                      # Pure Kotlin models (no Spring, no generated types)
│   ├── application/                 # Services implementing the use cases
│   │   └── port/inbound|outbound    # Use-case interfaces / driven ports (e.g. TripsProvider)
│   └── adapter/
│       ├── inbound/web/             # Controllers implementing generated *Api + domain→DTO mappers
│       └── outbound/viaggiatreno/   # ViaggiaTreno implementation of the outbound port
└── shared/
    ├── domain/                      # Shared kernel (Station)
    ├── infrastructure/              # cache, web binders, ViaggiaTreno client config + cached API wrappers
    └── util/
```

Packages are named `inbound`/`outbound` rather than `in`/`out` because `in` is a Kotlin keyword.

Request flow: `TripsController` → `Get*UseCase` (implemented by `TripService`) → `TripsProvider` port → `ViaggiaTrenoTripsProvider` → `ViaggiaTreno*ApiWrapper` (cached Ktor calls) → ViaggiaTreno API. The station feature follows the same shape (`StationsController` → `SearchStationsUseCase`/`StationService` → `StationsProvider` → `ViaggiaTrenoStationsProvider`).

Generated public DTOs (`trainapis.model`) may only be used in `adapter/inbound/web`; generated ViaggiaTreno models only in `shared/infrastructure/viaggiatreno` and `adapter/outbound/viaggiatreno`.

### Architecture Enforcement

`src/test/kotlin/.../ArchitectureTest.kt` (ArchUnit) runs with `./gradlew test` and fails the build when:
- a feature breaks the onion dependency direction (adapter → application → domain; adapters not depending on each other),
- `domain` depends on Spring, generated code, Ktor or `shared.infrastructure`,
- `application` depends on anything Spring other than `org.springframework.stereotype`, or on adapter technology,
- one feature depends on another (sharing goes through `shared`), or `shared` depends on a feature.

When adding a feature, add its name to `features` in `ArchitectureTest`.

### Key Design Decisions

- **Ports & adapters**: `StationsProvider` and `TripsProvider` are outbound ports returning domain models, with ViaggiaTreno as the only adapter. This allows swapping data sources.
- **Reactive with coroutines**: All data flows use `kotlinx.coroutines.flow.Flow`. Concurrent API fan-out uses `flatMapMerge` with configurable concurrency (`providers.viaggiatreno.concurrency` property).
- **Custom caching**: `Cache`/`CacheElement` in `com.federicopeyrani.trainapis.shared.infrastructure.cache` wraps Spring's `CacheManager` with TTL support. The API wrappers use this to cache upstream responses.
- **Serialization**: Uses `kotlinx.serialization` (not Jackson) for the ViaggiaTreno client models and public API DTOs. Public DTOs are annotated with `@kotlinx.serialization.Serializable` via OpenAPI generator config.
- **Time handling**: All times from ViaggiaTreno (Europe/Rome timezone, epoch millis) are converted to UTC `kotlinx.datetime.LocalDateTime` before returning to clients.

### Test Setup

Tests use `io.ktor:ktor-client-mock` to mock ViaggiaTreno HTTP responses and `io.mockk:mockk` / `springmockk` for unit testing. Mock engine configuration is in `src/test/kotlin/.../shared/infrastructure/viaggiatreno/ViaggiaTrenoMockEngineConfiguration.kt` (mock JSON fixtures stay in `src/test/resources/providers/viaggiatreno/`).
