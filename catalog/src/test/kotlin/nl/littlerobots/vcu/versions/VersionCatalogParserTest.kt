package nl.littlerobots.vcu.versions

import nl.littlerobots.vcu.VersionCatalogParser
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
        """.trimIndent()
        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream().reader())

        assertEquals(mapOf("test" to "1.0.0", "test2" to "2.0.0"), result.versions)
    }

    @Test
    fun `parses bundle mapping`() {
        val toml = """
            [bundles]
            test = ["lib1", "lib2" ]
        """.trimIndent()
        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream().reader())

        assertEquals(mapOf("test" to listOf("lib1", "lib2")), result.bundles)
    }

    @Test
    fun `parses string dependency notation`() {
        val toml = """
            [libraries]
            test = "com.company:name:version"
        """.trimIndent()
        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream().reader())

        assertEquals(1, result.libraries.size)
        assertNotNull(result.libraries["test"])
        assertEquals(
            Library(
                group = "com.company",
                name = "name",
                version = VersionDefinition.Simple("version")
            ), result.libraries["test"]
        )
    }

    @Test
    fun `parses module dependency notation`() {
        val toml = """
            [libraries]
            test = { module = "org.codehaus.groovy:groovy", version = "1.0.0" }
        """.trimIndent()
        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream().reader())

        assertEquals(1, result.libraries.size)
        assertNotNull(result.libraries["test"])
        assertEquals(
            Library(
                module = "org.codehaus.groovy:groovy",
                version = VersionDefinition.Simple("1.0.0")
            ), result.libraries["test"]
        )
    }

    @Test
    fun `parses group and name dependency notation`() {
        val toml = """
            [libraries]
            test = { group = "org.codehaus.groovy", name = "groovy", version = "1.0.0" }
        """.trimIndent()
        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream().reader())

        assertEquals(1, result.libraries.size)
        assertNotNull(result.libraries["test"])
        assertEquals(
            Library(
                group = "org.codehaus.groovy",
                name = "groovy",
                version = VersionDefinition.Simple("1.0.0")
            ), result.libraries["test"]
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `requires module in dependency`() {
        val toml = """
            [libraries]
            test = { version = "1.0.0" }
        """.trimIndent()
        val parser = VersionCatalogParser()

        parser.parse(toml.byteInputStream().reader())
    }

    @Test(expected = IllegalStateException::class)
    fun `requires module without group or name in dependency`() {
        val toml = """
            [libraries]
            test = { module = "test", name = "test", version = "1.0.0" }
        """.trimIndent()
        val parser = VersionCatalogParser()

        parser.parse(toml.byteInputStream().reader())
    }

    @Test
    fun `parses version ref in dependency`() {
        val toml = """
            [libraries]
            test = { group = "org.codehaus.groovy", name="groovy", version.ref="ref" }
        """.trimIndent()
        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream().reader())

        assertEquals(1, result.libraries.size)
        assertNotNull(result.libraries["test"])
        assertEquals(
            Library(
                group = "org.codehaus.groovy",
                name = "groovy",
                version = VersionDefinition.Reference("ref")
            ), result.libraries["test"]
        )
    }

    @Test
    fun `parses version condition in dependency`() {
        val toml = """
            [libraries]
            test = { group = "com.mycompany", name = "alternate", version = { require = "1.4" } }
        """.trimIndent()
        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream().reader())

        assertEquals(1, result.libraries.size)
        assertNotNull(result.libraries["test"])
        assertEquals(
            Library(
                group = "com.mycompany",
                name = "alternate",
                version = VersionDefinition.Condition(mapOf("require" to "1.4"))
            ), result.libraries["test"]
        )
    }

    @Test
    fun `parses simple plugin definition`() {
        val toml = """
            [plugins]
            short-notation = "some.plugin.id:1.4"
        """.trimIndent()

        val parser = VersionCatalogParser()

        val result = parser.parse(toml.byteInputStream().reader())

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

        val result = parser.parse(toml.byteInputStream().reader())

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

        val result = parser.parse(toml.byteInputStream().reader())

        assertEquals(1, result.plugins.size)
        assertNotNull(result.plugins["reference-notation"])
        assertEquals(
            Plugin(id = "some.plugin.id", version = VersionDefinition.Reference("common")),
            result.plugins["reference-notation"]!!
        )
    }
}