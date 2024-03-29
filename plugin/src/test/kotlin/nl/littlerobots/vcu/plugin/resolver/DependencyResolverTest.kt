/*
* Copyright 2021 Hugo Visser
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package nl.littlerobots.vcu.plugin.resolver

import nl.littlerobots.vcu.VersionCatalogParser
import nl.littlerobots.vcu.model.VersionDefinition
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class DependencyResolverTest {

    @Test
    fun `Resolves basic updates`() {
        val project = ProjectBuilder.builder().withName("test").build()
        val resolver = DependencyResolver()
        val catalog = VersionCatalogParser().parse(
            """
           [versions]
           androidx-camera = "1.3.0"

           [libraries]
           camerax = { module = "androidx.camera:camera-core", version.ref = "androidx-camera" }

            """.trimIndent().asStream()
        )

        project.repositories.add(project.repositories.google())

        val result = resolver.resolveFromCatalog(
            project.configurations.detachedConfiguration(),
            project.configurations.detachedConfiguration(),
            project.buildscript.configurations.detachedConfiguration(),
            project.buildscript.configurations.detachedConfiguration(),
            project.dependencies,
            catalog,
            object : ModuleVersionSelector {
                override fun select(candidate: ModuleVersionCandidate): Boolean {
                    return candidate.candidate.version == "1.3.1"
                }
            }
        )

        assertTrue(result.versionCatalog.libraries.isNotEmpty())
        assertEquals(
            "1.3.1",
            (result.versionCatalog.libraries.values.first().version as? VersionDefinition.Simple)?.version
        )
    }

    @Test
    fun `Reports unresolved libraries`() {
        val project = ProjectBuilder.builder().withName("test").build()
        val resolver = DependencyResolver()
        val catalog = VersionCatalogParser().parse(
            """
           [versions]
           androidx-camera = "1.3.0"

           [libraries]
           camerax = { module = "androidx.camera:camera-core", version.ref = "androidx-camera" }

            """.trimIndent().asStream()
        )

        val result = resolver.resolveFromCatalog(
            project.configurations.detachedConfiguration(),
            project.configurations.detachedConfiguration(),
            project.buildscript.configurations.detachedConfiguration(),
            project.buildscript.configurations.detachedConfiguration(),
            project.dependencies,
            catalog,
            object : ModuleVersionSelector {
                override fun select(candidate: ModuleVersionCandidate): Boolean {
                    return candidate.candidate.version == "1.3.1"
                }
            }
        )

        assertTrue(result.unresolved.libraries.isNotEmpty())
    }

    @Test
    fun `Reports exceeded libraries`() {
        val project = ProjectBuilder.builder().withName("test").build()
        val resolver = DependencyResolver()
        val catalog = VersionCatalogParser().parse(
            """
           [versions]
           androidx-camera = "1.3.0.0"

           [libraries]
           camerax = { module = "androidx.camera:camera-core", version.ref = "androidx-camera" }

            """.trimIndent().asStream()
        )

        project.repositories.add(project.repositories.google())

        val result = resolver.resolveFromCatalog(
            project.configurations.detachedConfiguration(),
            project.configurations.detachedConfiguration(),
            project.buildscript.configurations.detachedConfiguration(),
            project.buildscript.configurations.detachedConfiguration(),
            project.dependencies,
            catalog,
            VersionSelectors.PREFER_STABLE
        )

        assertTrue(result.versionCatalog.libraries.isEmpty())
        assertTrue(result.exceeded.libraries.isNotEmpty())
    }

    @Test
    fun `Reports rich version libraries`() {
        val project = ProjectBuilder.builder().withName("test").build()
        val resolver = DependencyResolver()
        val catalog = VersionCatalogParser().parse(
            """
           [versions]
           androidx-camera = { required = "1.3.0" }

           [libraries]
           camerax = { module = "androidx.camera:camera-core", version.ref = "androidx-camera" }

            """.trimIndent().asStream()
        )

        project.repositories.add(project.repositories.google())

        val result = resolver.resolveFromCatalog(
            project.configurations.detachedConfiguration(),
            project.configurations.detachedConfiguration(),
            project.buildscript.configurations.detachedConfiguration(),
            project.buildscript.configurations.detachedConfiguration(),
            project.dependencies,
            catalog,
            VersionSelectors.PREFER_STABLE
        )

        assertTrue(result.versionCatalog.libraries.isEmpty())
        assertTrue(result.richVersions.libraries.isNotEmpty())
    }

    @Test
    // https://github.com/littlerobots/version-catalog-update-plugin/issues/129
    fun `Resolves the original library for variants though Gradle module data`() {
        val project = ProjectBuilder.builder().withName("test").build()
        val resolver = DependencyResolver()
        val catalog = VersionCatalogParser().parse(
            """
           [libraries]
           # on the jvm this will actually resolve to apollo-api-jvm
           apollo-api = "com.apollographql.apollo3:apollo-api:3.8.1"

            """.trimIndent().asStream()
        )

        project.repositories.add(project.repositories.mavenCentral())

        val checkedCandidates = mutableSetOf<ModuleVersionCandidate>()

        val result = resolver.resolveFromCatalog(
            project.configurations.detachedConfiguration(),
            project.configurations.detachedConfiguration(),
            project.buildscript.configurations.detachedConfiguration(),
            project.buildscript.configurations.detachedConfiguration(),
            project.dependencies,
            catalog,
            object : ModuleVersionSelector {
                override fun select(candidate: ModuleVersionCandidate): Boolean {
                    checkedCandidates.add(candidate)
                    return candidate.candidate.version == "3.8.2"
                }
            }
        )

        assertTrue(result.versionCatalog.libraries.isNotEmpty())
        assertEquals("3.8.2", (result.versionCatalog.libraries.values.first().version as? VersionDefinition.Simple)?.version)
        assertEquals(
            setOf("com.apollographql.apollo3:apollo-api"),
            checkedCandidates.map { "${it.candidate.group}:${it.candidate.module}" }.toSet()
        )
    }

    @Test
    // https://github.com/littlerobots/version-catalog-update-plugin/issues/131
    fun `Handles capability resolving errors for updates`() {
        val project = ProjectBuilder.builder().withName("test").build()
        val resolver = DependencyResolver()
        val catalog = VersionCatalogParser().parse(
            """
           [libraries]
           # Updates to 1.6.x publish for multiple platforms/configurations (jvm/android)
           compose-foundation = "androidx.compose.foundation:foundation:1.5.4"

            """.trimIndent().asStream()
        )

        project.repositories.add(project.repositories.google())

        val result = resolver.resolveFromCatalog(
            project.configurations.detachedConfiguration(),
            project.configurations.detachedConfiguration(),
            project.buildscript.configurations.detachedConfiguration(),
            project.buildscript.configurations.detachedConfiguration(),
            project.dependencies,
            catalog,
            object : ModuleVersionSelector {
                override fun select(candidate: ModuleVersionCandidate): Boolean {
                    return candidate.candidate.version == "1.6.0-beta03"
                }
            }
        )

        assertTrue(result.versionCatalog.libraries.isNotEmpty())
        assertTrue(result.unresolved.libraries.isEmpty())
        assertTrue(result.exceeded.libraries.isEmpty())
        assertEquals("1.6.0-beta03", (result.versionCatalog.libraries.values.first().version as? VersionDefinition.Simple)?.version)
    }

    private fun String.asStream(): ByteArrayInputStream {
        return ByteArrayInputStream(toByteArray())
    }
}
