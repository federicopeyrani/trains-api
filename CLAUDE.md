# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build (also generates code from OpenAPI specs)
./gradlew build

# Run the application
./gradlew bootRun

# Run all tests (unit, web, E2E; no network)
./gradlew test

# Run a single test class
./gradlew test --tests "com.federicopeyrani.trainapis.TrainApisE2ETests"

# Check the contract of the real ViaggiaTreno API (network; skipped when rate limited)
./gradlew providerContractTest

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
- **Domain** (`trip/domain`): `Departure` (a board entry plus its optional `Run`) decides whether it `serves` a destination: `YES`/`NO` from the run's calls (it must call there *after* the origin), `UNKNOWN` without the run. It derives a `Trip` as a segment of the run (`toTrip`). Stop status is derived from actual times (`OriginStop.of`, ...), delays from `Timing.delayed`, and `RunId` parses and formats run ids.
- **Application** (`TripService`): resolves "now" from the injected `Clock`, and fetches each origin board once. Trip searches (`getTrips`, `getAllTrips`) emit `TripSearchResult`s: a `Match` per trip, and an `Unverified` per departure whose run is unknown (once, whatever the number of destinations), so callers can tell that the result is incomplete. `/departures` leaves out entries without a known destination.
- **Outbound adapter**: `ViaggiaTrenoTripsProvider` only fetches (board, trains, stations) and calls the pure `ViaggiaTrenoTripMapper`. Streams degrade per entry: an unfetchable train gives a departure without run, an unresolvable destination gives a departure without destination (entries are never dropped, so trip searches can count them), and an unresolvable stop falls back to the stop's own name. The station feature follows the same shape (`StationsController` → `SearchStationsUseCase`/`StationService` → `StationsProvider` → `ViaggiaTrenoStationsProvider`).

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

- **Ports & adapters**: `StationsProvider` and `TripsProvider` are outbound ports returning domain models, with ViaggiaTreno as the only adapter. The ports keep ViaggiaTreno's types out of the core, but not its identity: `StationId` values are ViaggiaTreno codes (`S01700`), `RunId` is ViaggiaTreno's (origin, train number, departure day) triple, and a trip search is a filtered departure board. A second provider would need an id mapping, or a change to the public contract.
- **Typed ids**: station ids and train numbers are the `StationId`/`TrainNumber` value classes in the domain, ports and use cases; adapters unwrap them at the HTTP and provider edges.
- **Reactive with coroutines**: All data flows use `kotlinx.coroutines.flow.Flow`. Concurrent API fan-out uses `flatMapMerge` with configurable concurrency (`providers.viaggiatreno.concurrency`, bound to `ViaggiaTrenoProperties`).
- **Custom caching**: `Cache` in `shared.infrastructure.cache` is a plain-Kotlin, coroutine-aware TTL cache: concurrent callers for a key share one computation (single flight), failures are not cached, and the size is bounded. The API wrappers use it to cache upstream responses. Departure boards are cached per minute.
- **Clock**: "now" comes from the injected `kotlin.time.Clock` bean (`ClockConfig`). Never call `Clock.System` directly, so tests can use a fixed or hand-advanced clock.
- **Errors**: domain code throws subclasses of `shared.domain.DomainException`; `ApiExceptionHandler` maps them to 400/404/503 `ProblemDetail` responses. The ViaggiaTreno wrappers translate non-2xx statuses (the generated client does not throw on them) and transport errors into `ProviderUnavailableException`. Status codes can only change before a stream starts, so adapters skip broken entries rather than failing mid-stream.
- **Serialization**: Uses `kotlinx.serialization` (not Jackson) for the ViaggiaTreno client models and public API DTOs. Public DTOs are annotated with `@kotlinx.serialization.Serializable` via OpenAPI generator config. WebFlux would encode server-sent events with Jackson, so `WebCodecsConfig` sets the kotlinx encoder (`ApiJson`) for them too.
- **Trip search responses**: `/trips` and `/trips/all` answer JSON with a `TripSearch` (`trips` plus `unverifiedDepartures`). Their `text/event-stream` form is a different shape (one unnamed event per trip, then a `summary` event), which the generated interface cannot express: `TripsController` maps it by hand (`streamTrips`, `streamAllTrips`) and narrows the generated mappings to JSON so the two never match the same request.
- **Time handling**: Domain times are `kotlin.time.Instant`. The mapper only reads ViaggiaTreno's epoch-millis fields, never its pre-formatted `HH:mm` ones (`comp*`), which carry no date and can disagree with the raw times; public DTOs use `Instant` too (generator `typeMappings`), so times are written as RFC 3339 in UTC (`2026-01-23T21:15:00Z`). The `date` query parameter accepts an offset, or none (read as UTC), with or without seconds (`InstantPropertyEditor`). `Timing` is the same at every stop: `scheduled` from the stop's own field, `actual` once the event happened, and until then `expected` = scheduled + the train's current delay (`ritardo`, null before departure, negative when early).

### Test Setup

Tests use **fakes, not mocks** (enforced by `ArchitectureTest`). The suites, from narrowest to widest:
- **Domain/application rules**: plain tests on domain objects (`DepartureTests`, builders in `TripFixtures`) and on services with in-memory outbound ports (`TripServiceRulesTests`).
- **Web contract** (`TripsControllerTests`, `StationsControllerTests`): `@WebFluxTest` with the use cases (`fun interface`s) replaced by lambdas in a `@TestConfiguration`. They pin the JSON payloads strictly (`src/test/resources/expected/`), including absent optional fields and server-sent events, request binding (UTC dates, repeated params) and problem responses, so codec or binder configuration changes cannot slip through.
- **Adapters** (`ViaggiaTrenoTripsProviderTests`, `ViaggiaTrenoStationsProviderTests`, `ViaggiaTrenoTripMapperTests`): plain constructors over the fake HTTP engine, covering degradation rules, error translation and upstream traffic.
- **E2E** (`TrainApisE2ETests`): `@SpringBootTest(RANDOM_PORT)` over real HTTP with only the `HttpClientEngine` (a `@TestConfiguration` bean) and the `Clock` (`@TestBean`) replaced.
- **Provider contract** (`ViaggiaTrenoContractTests`, annotated `@ProviderContractTest`): call the real ViaggiaTreno API to check what the adapters rely on (decoding, `204`/placeholder semantics, time fields). Tagged `provider-contract`, excluded from `test`/`build` and run by `providerContractTest`; a 403 aborts (skips) them. Keep them to a handful of calls.

The fake ViaggiaTreno is `mockEngine(...)` (`src/test/kotlin/.../MockEngine.kt`, a Ktor `MockEngine`): it serves recorded JSON from `src/test/resources/providers/viaggiatreno/`, answers the paths in `statuses` (e.g. `"andamentoTreno/S01700/1/1769122800000" to NoContent`) with a bare status to simulate 204s, rate limits and outages, answers the paths in `bodies` with inline JSON for plausible but unrecorded responses, and keeps every request in `requestHistory`. A missing fixture fails the request immediately; record it with `curl` and commit it. Upstream behaviour faked here should be confirmed by a provider contract test.

- **Time**: implement `kotlin.time.Clock` with a fixed or mutable `now` (see `CacheTests`, `TrainApisE2ETests`).
