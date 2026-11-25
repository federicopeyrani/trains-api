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
│   ├── domain/                      # Pure Kotlin models and rules (no Spring, no generated types)
│   ├── application/                 # Services implementing the use cases
│   │   └── port/inbound|outbound    # Use-case interfaces / driven ports (e.g. TripsProvider)
│   └── adapter/
│       ├── inbound/web/             # Controllers implementing generated *Api + domain→DTO mappers
│       └── outbound/viaggiatreno/   # ViaggiaTreno implementation of the outbound port + pure mapper
└── shared/
    ├── domain/                      # Shared kernel (Station, DomainException hierarchy)
    ├── infrastructure/              # cache, clock, web (binders, ApiExceptionHandler),
    │                                # ViaggiaTreno client factories/config + cached API wrappers
    └── util/
```

Packages are named `inbound`/`outbound` rather than `in`/`out` because `in` is a Kotlin keyword.

Request flow: `TripsController` → `Get*UseCase` (implemented by `TripService`) → `TripsProvider` port → `ViaggiaTrenoTripsProvider` → `ViaggiaTreno*ApiWrapper` (cached Ktor calls) → ViaggiaTreno API.

Where the trip logic lives:
- **Domain** (`trip/domain`): `Departure` (a board entry plus its optional `Run`) decides whether it `serves` a destination (the run must call there *after* the origin) and derives a `Trip` as a segment of the run (`toTrip`). Stop status is derived from actual times (`OriginStop.of`, ...), delays from `Timing.delayed`, and `RunId` parses and formats run ids.
- **Application** (`TripService`): resolves "now" from the injected `Clock`, filters departures into trips, and fetches each origin board once in `getAllTrips`.
- **Outbound adapter**: `ViaggiaTrenoTripsProvider` only fetches (board, trains, stations) and calls the pure `ViaggiaTrenoTripMapper`. Streams degrade per entry: an unfetchable train gives a departure without run, an unresolvable destination skips the entry, and an unresolvable stop falls back to the stop's own name. The station feature follows the same shape (`StationsController` → `SearchStationsUseCase`/`StationService` → `StationsProvider` → `ViaggiaTrenoStationsProvider`).

Generated public DTOs (`trainapis.model`) may only be used in `adapter/inbound/web`; generated ViaggiaTreno models only in `shared/infrastructure/viaggiatreno` and `adapter/outbound/viaggiatreno`.

### Architecture Enforcement

`src/test/kotlin/.../ArchitectureTest.kt` (ArchUnit) runs with `./gradlew test` and fails the build when:
- a feature breaks the onion dependency direction (adapter → application → domain; adapters not depending on each other),
- `domain` depends on Spring, generated code, Ktor or `shared.infrastructure`,
- `application` depends on anything Spring other than `org.springframework.stereotype`, or on adapter technology,
- one feature depends on another (sharing goes through `shared`), or `shared` depends on a feature,
- test code uses a mocking framework (mockk, springmockk, Mockito): use fakes or lambdas instead.

When adding a feature, add its name to `features` in `ArchitectureTest`.

### Key Design Decisions

- **Ports & adapters**: `StationsProvider` and `TripsProvider` are outbound ports returning domain models, with ViaggiaTreno as the only adapter. This allows swapping data sources.
- **Reactive with coroutines**: All data flows use `kotlinx.coroutines.flow.Flow`. Concurrent API fan-out uses `flatMapMerge` with configurable concurrency (`providers.viaggiatreno.concurrency`, bound to `ViaggiaTrenoProperties`).
- **Custom caching**: `Cache` in `shared.infrastructure.cache` is a plain-Kotlin, coroutine-aware TTL cache: concurrent callers for a key share one computation (single flight), failures are not cached, and the size is bounded. The API wrappers use it to cache upstream responses. Departure boards are cached per minute.
- **Clock**: "now" comes from the injected `kotlin.time.Clock` bean (`ClockConfig`). Never call `Clock.System` directly, so tests can use a fixed or hand-advanced clock.
- **Errors**: domain code throws subclasses of `shared.domain.DomainException`; `ApiExceptionHandler` maps them to 400/404/503 `ProblemDetail` responses. The ViaggiaTreno wrappers translate non-2xx statuses (the generated client does not throw on them) and transport errors into `ProviderUnavailableException`. Status codes can only change before a stream starts, so adapters skip broken entries rather than failing mid-stream.
- **Serialization**: Uses `kotlinx.serialization` (not Jackson) for the ViaggiaTreno client models and public API DTOs. Public DTOs are annotated with `@kotlinx.serialization.Serializable` via OpenAPI generator config.
- **Time handling**: All times from ViaggiaTreno (Europe/Rome timezone, epoch millis) are converted to UTC `kotlinx.datetime.LocalDateTime` before returning to clients. The `date` query parameter is also interpreted as UTC.

### Test Setup

Tests use **fakes, not mocks** (enforced by `ArchitectureTest`):
- **Use cases** are `fun interface`s, so web tests (`@WebFluxTest`) register lambdas in a `@TestConfiguration`.
- **Outbound ports** are small interfaces, so application tests use in-memory implementations (see `TripServiceRulesTests`, with domain builders in `TripFixtures`).
- **ViaggiaTreno**: `mockEngine(...)` (`src/test/kotlin/.../MockEngine.kt`, a Ktor `MockEngine` fake) serves recorded JSON from `src/test/resources/providers/viaggiatreno/`. Adapters and wrappers are built with plain constructors (`ViaggiaTrenoClients.trainsApi(engine)`, ...), so no Spring context is needed. A missing fixture fails the request immediately; record it with `curl` and commit it.
- **Mapping** is pure: `ViaggiaTrenoTripMapperTests` decodes a fixture and maps it directly.
- **Time**: implement `kotlin.time.Clock` with a mutable `now` (see `CacheTests`).
