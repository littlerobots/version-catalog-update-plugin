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
import nl.littlerobots.vcu.toml.TABLE_BUNDLES
import nl.littlerobots.vcu.toml.TABLE_LIBRARIES
import nl.littlerobots.vcu.toml.TABLE_PLUGINS
import nl.littlerobots.vcu.toml.TABLE_VERSIONS
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringWriter

class VersionCatalogWriterTest {
    @Test
    fun `writes simple version definitions`() {
        val catalogWriter = VersionCatalogWriter()
        val writer = StringWriter()
        val catalog = VersionCatalog(
            versions = mapOf("test" to VersionDefinition.Simple("1.0")),
            emptyMap(),
            emptyMap(),
            emptyMap()
        )
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
    fun `writes version condition definitions`() {
        val catalogWriter = VersionCatalogWriter()
        val writer = StringWriter()
        val catalog = VersionCatalog(
            versions = mapOf("test" to VersionDefinition.Condition(mapOf("strictly" to "1.5.2"))),
            emptyMap(),
            emptyMap(),
            emptyMap()
        )
        catalogWriter.write(catalog, writer)

        assertEquals(
            """
            [versions]
            test = { strictly = "1.5.2" }

            """.trimIndent(),
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
    fun `writes and formats bundle definition`() {
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
            """
               [bundles]
               test = [
                   "lib1",
                   "lib2",
               ]

            """.trimIndent(),
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

    @Test
    fun `omits unspecified version for library entry`() {
        val catalogWriter = VersionCatalogWriter()
        val writer = StringWriter()
        val catalog = VersionCatalog(
            emptyMap(),
            mapOf("lib" to Library(module = "nl.littlerobots.test:example", version = VersionDefinition.Unspecified)),
            emptyMap(),
            emptyMap()
        )
        catalogWriter.write(catalog, writer)

        assertEquals(
            """[libraries]
               |lib = { module = "nl.littlerobots.test:example" }
               |
        """.trimMargin(),
            writer.toString()
        )
    }

    @Test
    fun `writes out comments`() {
        val catalogWriter = VersionCatalogWriter()
        val writer = StringWriter()

        val toml = """
            [versions]
            # version comment
            myversion = "1.0.0"
            #something else
            test = "2.0"
            # Nice comment
            [libraries] # valid, but not retained
            # Comment for lib
            # Even more comments
            lib = { module = "nl.littlerobots.test:test", version = "1.0" }

            # Table comment for bundles
            [bundles]
            # test bundle comment
            test = ["lib"]

            # Table comment for plugins
            [plugins]
        """.trimIndent()

        val catalog = VersionCatalogParser().parse(toml.byteInputStream())
        catalogWriter.write(catalog, writer)

        assertEquals(
            """
                [versions]
                # version comment
                myversion = "1.0.0"
                #something else
                test = "2.0"

                # Nice comment
                [libraries]
                # Comment for lib
                # Even more comments
                lib = "nl.littlerobots.test:test:1.0"

                # Table comment for bundles
                [bundles]
                # test bundle comment
                test = [
                    "lib",
                ]

            """.trimIndent(),
            writer.toString()
        )
    }

    @Test
    fun `writes the tables in specified order`() {
        val catalogWriter = VersionCatalogWriter()
        val writer = StringWriter()

        val toml = """
            [versions]
            myversion = "1.0.0"
            [libraries]
            lib = { module = "nl.littlerobots.test:test", version = "1.0" }
            [bundles]
            test = ["lib"]

            [plugins]
        """.trimIndent()

        val catalog = VersionCatalogParser().parse(toml.byteInputStream())
        catalogWriter.write(catalog.copy(tableOrder = listOf(TABLE_PLUGINS, TABLE_BUNDLES, TABLE_LIBRARIES, TABLE_VERSIONS)), writer)

        assertEquals(
            """
                [bundles]
                test = [
                    "lib",
                ]

                [libraries]
                lib = "nl.littlerobots.test:test:1.0"

                [versions]
                myversion = "1.0.0"

            """.trimIndent(),
            writer.toString()
        )
    }

    // https://github.com/littlerobots/version-catalog-update-plugin/issues/177
    @Test
    fun `writes reject version condition`() {
        val catalogWriter = VersionCatalogWriter()
        val writer = StringWriter()

        val toml = """
            [versions]
            myversion = { reject = ["1.0","2.0"] }

            [libraries]
            lib = { module = "nl.littlerobots.test:test", version = { reject = ["1.0"] } }
        """.trimIndent()

        val catalog = VersionCatalogParser().parse(toml.byteInputStream())
        catalogWriter.write(catalog, writer)

        assertEquals(
            """
                [versions]
                myversion = { reject = ["1.0", "2.0"] }

                [libraries]
                lib = { module = "nl.littlerobots.test:test", version = { reject = ["1.0"] } }

            """.trimIndent(),
            writer.toString()
        )
    }
}
