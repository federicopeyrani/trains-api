package com.federicopeyrani.trainapis

import org.junit.jupiter.api.Tag

/**
 * Marks tests that call a real external provider to check that its API still behaves as the adapters expect.
 *
 * They depend on the network and on the provider's availability, so they are excluded from `./gradlew test` and run
 * with `./gradlew providerContractTest`. Keep them few and cheap: providers rate limit aggressively.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Tag("provider-contract")
annotation class ProviderContractTest
