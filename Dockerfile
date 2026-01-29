# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build

WORKDIR /workspace/app

# Copy gradle wrapper and build files first (these change less frequently)
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies (this layer will be cached unless build files change)
RUN ./gradlew dependencies --no-daemon

# Now copy the source code
COPY src src

# Build the application
RUN ./gradlew bootJar --no-daemon
RUN mkdir -p build/dependency && (cd build/dependency; jar -xf ../libs/*.jar)

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

VOLUME /tmp

# Copy the unpacked application from the build stage
ARG DEPENDENCY=/workspace/app/build/dependency

COPY --from=build ${DEPENDENCY}/BOOT-INF/lib /app/lib
COPY --from=build ${DEPENDENCY}/META-INF /app/META-INF
COPY --from=build ${DEPENDENCY}/BOOT-INF/classes /app

ENTRYPOINT ["java","-cp","app:app/lib/*","com.federicopeyrani.trainapis.TrainApisApplicationKt"]
