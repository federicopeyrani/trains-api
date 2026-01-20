# Train APIs

This project uses OpenAPI Generator to automatically generate Spring WebFlux controllers and DTOs from the OpenAPI specification.

## How It Works

The project is configured to automatically generate API interfaces and data models from the `src/main/resources/openapi.yaml` file during the build process.

### Generated Files

When you build the project, the following files are generated in `build/generated/src/main/kotlin/`:

- **Controllers (API Interfaces)**: Located in `com.federicopeyrani.trainapis.api` package
  - These are Spring WebFlux controller interfaces with `@RestController` annotations
  - Your implementation classes should implement these interfaces

- **DTOs (Data Models)**: Located in `com.federicopeyrani.trainapis.model` package
  - These are Kotlin data classes with Jackson annotations for JSON serialization
  - Includes validation annotations based on the OpenAPI spec

### Building the Project

```bash
# Generate code and build the project
./gradlew build

# Only generate code from OpenAPI spec
./gradlew openApiGenerate

# Clean and rebuild
./gradlew clean build
```

### Implementing API Endpoints

1. Define your API endpoints in `src/main/resources/openapi.yaml`
2. Run `./gradlew openApiGenerate` to generate the API interfaces and models
3. Create controller classes that implement the generated API interfaces in `src/main/kotlin/com/federicopeyrani/trainapis/controller/`

Example:
```kotlin
@RestController
class TrainsController : TrainsApi {
    override fun getAllTrains(): ResponseEntity<Flow<Train>> {
        // Your implementation here
    }
}
```

### Configuration

The OpenAPI Generator is configured in `build.gradle.kts` with the following settings:

- **Generator**: `kotlin-spring` - Generates Kotlin code for Spring Boot
- **Reactive**: Enabled - Uses Spring WebFlux with Kotlin Coroutines
- **Interface Only**: Controllers are generated as interfaces for you to implement
- **Spring Boot 3**: Compatible with Spring Boot 3.x and Jakarta EE
- **Validation**: Includes Jakarta Bean Validation annotations

### Modifying the OpenAPI Spec

Edit `src/main/resources/openapi.yaml` to add or modify API endpoints. The code will be regenerated automatically on the next build.

### IDE Support

Note: The IDE may show errors in `build.gradle.kts` for the OpenAPI Generator task configuration. These are false positives - the build works correctly. The errors appear because the IDE's build script compilation doesn't have access to the plugin classes, but Gradle itself resolves them correctly at runtime.

