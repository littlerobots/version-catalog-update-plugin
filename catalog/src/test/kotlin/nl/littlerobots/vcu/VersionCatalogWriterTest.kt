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
package nl.littlerobots.vcu

import nl.littlerobots.vcu.model.Library
import nl.littlerobots.vcu.model.Plugin
import nl.littlerobots.vcu.model.VersionCatalog
import nl.littlerobots.vcu.model.VersionDefinition
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringWriter

class VersionCatalogWriterTest {
    @Test
    fun `writes version definitions`() {
        val catalogWriter = VersionCatalogWriter()
        val writer = StringWriter()
        val catalog = VersionCatalog(versions = mapOf("test" to "1.0"), emptyMap(), emptyMap(), emptyMap())
        catalogWriter.write(catalog, writer)

        assertEquals(
            """[versions]
            |test = "1.0"
            |
        """.trimMargin(),
            writer.toString()
        )
    }

    @Test
    fun `writes simple library definition`() {
        val catalogWriter = VersionCatalogWriter()
        val writer = StringWriter()
        val catalog = VersionCatalog(
            emptyMap(),
            mapOf(
                "test" to Library(
                    group = "nl.littlerobots.test",
                    name = "testlib",
                    version = VersionDefinition.Simple("1.0")
                )
            ),
            emptyMap(),
            emptyMap()
        )
        catalogWriter.write(catalog, writer)

        assertEquals(
            """[libraries]
               |test = "nl.littlerobots.test:testlib:1.0"
               |
        """.trimMargin(),
            writer.toString()
        )
    }

    @Test
    fun `writes library with version reference`() {
        val catalogWriter = VersionCatalogWriter()
        val writer = StringWriter()
        val catalog = VersionCatalog(
            emptyMap(),
            mapOf(
                "test" to Library(
                    group = "nl.littlerobots.test",
                    name = "testlib",
                    version = VersionDefinition.Reference("my-reference")
                )
            ),
            emptyMap(),
            emptyMap()
        )
        catalogWriter.write(catalog, writer)

        assertEquals(
            """[libraries]
               |test = { module = "nl.littlerobots.test:testlib", version.ref = "my-reference" }
               |
        """.trimMargin(),
            writer.toString()
        )
    }

    @Test
    fun `writes library with version condition`() {
        val catalogWriter = VersionCatalogWriter()
        val writer = StringWriter()
        val catalog = VersionCatalog(
            emptyMap(),
            mapOf(
                "test" to Library(
                    group = "nl.littlerobots.test",
                    name = "testlib",
                    version = VersionDefinition.Condition(mapOf("require" to "1.4"))
                )
            ),
            emptyMap(),
            emptyMap()
        )
        catalogWriter.write(catalog, writer)

        assertEquals(
            """[libraries]
               |test = { module = "nl.littlerobots.test:testlib", version = { require = "1.4" } }
               |
        """.trimMargin(),
            writer.toString()
        )
    }

    @Test
    fun `writes bundle definition`() {
        val catalogWriter = VersionCatalogWriter()
        val writer = StringWriter()
        val catalog = VersionCatalog(
            emptyMap(),
            emptyMap(),
            mapOf("test" to listOf("lib1", "lib2")),
            emptyMap()
        )
        catalogWriter.write(catalog, writer)

        assertEquals(
            """[bundles]
               |test = ["lib1", "lib2"]
               |
        """.trimMargin(),
            writer.toString()
        )
    }

    @Test
    fun `writes simple plugin definition`() {
        val catalogWriter = VersionCatalogWriter()
        val writer = StringWriter()
        val catalog = VersionCatalog(
            emptyMap(),
            emptyMap(),
            emptyMap(),
            mapOf("test" to Plugin("my.plugin.id", VersionDefinition.Simple("1.0")))
        )
        catalogWriter.write(catalog, writer)

        assertEquals(
            """[plugins]
               |test = "my.plugin.id:1.0"
               |
        """.trimMargin(),
            writer.toString()
        )
    }

    @Test
    fun `writes plugin definition with version reference`() {
        val catalogWriter = VersionCatalogWriter()
        val writer = StringWriter()
        val catalog = VersionCatalog(
            emptyMap(),
            emptyMap(),
            emptyMap(),
            mapOf("test" to Plugin("my.plugin.id", VersionDefinition.Reference("my-version")))
        )
        catalogWriter.write(catalog, writer)

        assertEquals(
            """[plugins]
               |test = { id = "my.plugin.id", version.ref = "my-version" }
               |
        """.trimMargin(),
            writer.toString()
        )
    }
}
