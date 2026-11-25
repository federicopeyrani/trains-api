package com.federicopeyrani.trainapis

import com.tngtech.archunit.base.DescribedPredicate.alwaysTrue
import com.tngtech.archunit.base.DescribedPredicate.not
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.onionArchitecture
import kotlin.test.Test

/**
 * Enforces the hexagonal (ports & adapters) layout:
 *
 * ```
 * <feature>/domain                       pure model, no framework
 * <feature>/application                  use cases (port/inbound), driven ports (port/outbound), services
 * <feature>/adapter/inbound/<tech>       driving adapters (e.g. REST controllers)
 * <feature>/adapter/outbound/<tech>      driven adapters (e.g. ViaggiaTreno client)
 * shared/                                shared kernel + cross-cutting infrastructure
 * ```
 *
 * Features are independent of each other and may only share code through `shared`.
 */
class ArchitectureTest {

    companion object {

        private const val ROOT = "com.federicopeyrani.trainapis"

        private val features = listOf("station", "trip")

        /** Packages produced by the OpenAPI generators and other technical concerns. */
        private val technicalPackages = arrayOf(
            "$ROOT.api..",
            "$ROOT.model..",
            "$ROOT.shared.infrastructure..",
            "io.ktor..",
        )

        private val classes: JavaClasses = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages(ROOT)

        private val testClasses: JavaClasses = ClassFileImporter()
            .withImportOption(ImportOption.OnlyIncludeTests())
            .importPackages(ROOT)
    }

    @Test
    fun `features follow the onion architecture`() {
        features.forEach { feature ->
            onionArchitecture()
                .domainModels("$ROOT.$feature.domain..", "$ROOT.shared.domain..")
                .applicationServices("$ROOT.$feature.application..")
                .adapter("inbound", "$ROOT.$feature.adapter.inbound..")
                .adapter("outbound", "$ROOT.$feature.adapter.outbound..")
                .withOptionalLayers(true)
                // `shared.domain` is a layer of every feature: only check dependencies originating in this feature
                .ignoreDependency(not(resideInAPackage("$ROOT.$feature..")), alwaysTrue())
                .because("feature '$feature' must follow ports & adapters")
                .check(classes)
        }
    }

    @Test
    fun `domain is framework and transport agnostic`() {
        noClasses()
            .that().resideInAnyPackage("$ROOT..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", *technicalPackages)
            .check(classes)
    }

    @Test
    fun `application layer only uses Spring stereotypes and no adapter technology`() {
        noClasses()
            .that().resideInAnyPackage("$ROOT..application..")
            .should().dependOnClassesThat(
                resideInAPackage("org.springframework..")
                    .and(not(resideInAPackage("org.springframework.stereotype..")))
                    .or(resideInAnyPackage(*technicalPackages))
            )
            .check(classes)
    }

    @Test
    fun `features do not depend on each other`() {
        features.forEach { feature ->
            val others = (features - feature).map { "$ROOT.$it.." }.toTypedArray()
            noClasses()
                .that().resideInAPackage("$ROOT.$feature..")
                .should().dependOnClassesThat().resideInAnyPackage(*others)
                .because("features may only share code through the 'shared' package")
                .check(classes)
        }
    }

    @Test
    fun `shared code does not depend on features`() {
        noClasses()
            .that().resideInAPackage("$ROOT.shared..")
            .should().dependOnClassesThat().resideInAnyPackage(*features.map { "$ROOT.$it.." }.toTypedArray())
            .check(classes)
    }

    @Test
    fun `tests use fakes instead of mocking frameworks`() {
        noClasses()
            .should().dependOnClassesThat().resideInAnyPackage("io.mockk..", "com.ninjasquad.springmockk..", "org.mockito..")
            .because("ports are small interfaces: use in-memory fakes or lambdas instead of mocks")
            .check(testClasses)
    }
}
