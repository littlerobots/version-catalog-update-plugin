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
import nl.littlerobots.vcu.model.VersionDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VersionCatalogParserTest {
    @Test
    fun `parses version mapping`() {
        val toml = """
            [versions]
            test = "1.0.0"
            test2 = "2.0.0"
            test3 = { strictly = "2.0.0" }
        """.trimIndent()
        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream())

        assertEquals(
            mapOf(
                "test" to VersionDefinition.Simple("1.0.0"),
                "test2" to VersionDefinition.Simple("2.0.0"),
                "test3" to VersionDefinition.Condition(mapOf("strictly" to "2.0.0"))
            ),
            result.versions
        )
    }

    @Test
    fun `parses bundle mapping`() {
        val toml = """
            [bundles]
            test = ["lib1", "lib2" ]
        """.trimIndent()
        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream())

        assertEquals(mapOf("test" to listOf("lib1", "lib2")), result.bundles)
    }

    @Test
    fun `parses string dependency notation`() {
        val toml = """
            [libraries]
            test = "com.company:name:version"
        """.trimIndent()
        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream())

        assertEquals(1, result.libraries.size)
        assertNotNull(result.libraries["test"])
        assertEquals(
            Library(
                group = "com.company",
                name = "name",
                version = VersionDefinition.Simple("version")
            ),
            result.libraries["test"]
        )
    }

    @Test
    fun `parses module dependency notation`() {
        val toml = """
            [libraries]
            test = { module = "org.codehaus.groovy:groovy", version = "1.0.0" }
        """.trimIndent()
        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream())

        assertEquals(1, result.libraries.size)
        assertNotNull(result.libraries["test"])
        assertEquals(
            Library(
                module = "org.codehaus.groovy:groovy",
                version = VersionDefinition.Simple("1.0.0")
            ),
            result.libraries["test"]
        )
    }

    @Test
    fun `parses group and name dependency notation`() {
        val toml = """
            [libraries]
            test = { group = "org.codehaus.groovy", name = "groovy", version = "1.0.0" }
        """.trimIndent()
        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream())

        assertEquals(1, result.libraries.size)
        assertNotNull(result.libraries["test"])
        assertEquals(
            Library(
                group = "org.codehaus.groovy",
                name = "groovy",
                version = VersionDefinition.Simple("1.0.0")
            ),
            result.libraries["test"]
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `requires module in dependency`() {
        val toml = """
            [libraries]
            test = { version = "1.0.0" }
        """.trimIndent()
        val parser = VersionCatalogParser()

        parser.parse(toml.byteInputStream())
    }

    @Test(expected = IllegalStateException::class)
    fun `requires module without group or name in dependency`() {
        val toml = """
            [libraries]
            test = { module = "test", name = "test", version = "1.0.0" }
        """.trimIndent()
        val parser = VersionCatalogParser()

        parser.parse(toml.byteInputStream())
    }

    @Test
    fun `parses version ref in dependency`() {
        val toml = """
            [libraries]
            test = { group = "org.codehaus.groovy", name="groovy", version.ref="ref" }
        """.trimIndent()
        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream())

        assertEquals(1, result.libraries.size)
        assertNotNull(result.libraries["test"])
        assertEquals(
            Library(
                group = "org.codehaus.groovy",
                name = "groovy",
                version = VersionDefinition.Reference("ref")
            ),
            result.libraries["test"]
        )
    }

    @Test
    fun `parses version condition in dependency`() {
        val toml = """
            [libraries]
            test = { group = "com.mycompany", name = "alternate", version = { require = "1.4" } }
        """.trimIndent()
        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream())

        assertEquals(1, result.libraries.size)
        assertNotNull(result.libraries["test"])
        assertEquals(
            Library(
                group = "com.mycompany",
                name = "alternate",
                version = VersionDefinition.Condition(mapOf("require" to "1.4"))
            ),
            result.libraries["test"]
        )
    }

    @Test
    fun `parses simple plugin definition`() {
        val toml = """
            [plugins]
            short-notation = "some.plugin.id:1.4"
        """.trimIndent()

        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream())

        assertEquals(1, result.plugins.size)
        assertNotNull(result.plugins["short-notation"])
        assertEquals(
            Plugin(id = "some.plugin.id", version = VersionDefinition.Simple("1.4")),
            result.plugins["short-notation"]!!
        )
    }

    @Test
    fun `parses long notation plugin definition`() {
        val toml = """
            [plugins]
            long-notation = { id = "some.plugin.id", version = "1.4" }
        """.trimIndent()

        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream())

        assertEquals(1, result.plugins.size)
        assertNotNull(result.plugins["long-notation"])
        assertEquals(
            Plugin(id = "some.plugin.id", version = VersionDefinition.Simple("1.4")),
            result.plugins["long-notation"]!!
        )
    }

    @Test
    fun `parses reference notation plugin definition`() {
        val toml = """
            [plugins]
            reference-notation = { id = "some.plugin.id", version.ref = "common" }
        """.trimIndent()

        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream())

        assertEquals(1, result.plugins.size)
        assertNotNull(result.plugins["reference-notation"])
        assertEquals(
            Plugin(id = "some.plugin.id", version = VersionDefinition.Reference("common")),
            result.plugins["reference-notation"]!!
        )
    }

    @Test
    // not explicitly supported in the Gradle docs, but seems to be allowed
    fun `parses libraries without version specification`() {
        val toml = """
            [libraries]
            lib = { module = "nl.littlerobots.test:test" }
        """.trimIndent()

        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream())

        assertEquals(1, result.libraries.size)
        assertNotNull(result.libraries["lib"])
        assertEquals(
            Library(module = "nl.littlerobots.test:test", version = VersionDefinition.Unspecified),
            result.libraries["lib"]!!
        )
    }

    @Test
    fun `records comments on tables and entries`() {
        val toml = """
            # Nice comment
            [libraries] # valid, but not retained
            # Comment for lib
            # Even more comments
            lib = { module = "nl.littlerobots.test:test" }

            [bundles]
            # test bundle comment
            test = ["lib"]

            # this is a trailing comment
        """.trimIndent()

        val parser = VersionCatalogParser()
        val result = parser.parse(toml.byteInputStream())

        assertEquals(listOf("# Nice comment"), result.libraryComments.tableComments)
        assertEquals(listOf("# Comment for lib", "# Even more comments"), result.libraryComments.entryComments["lib"])
        assertEquals(listOf("# test bundle comment"), result.bundleComments.entryComments["test"])
    }
}
