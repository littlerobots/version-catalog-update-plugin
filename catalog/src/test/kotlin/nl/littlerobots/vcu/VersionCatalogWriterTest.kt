package nl.littlerobots.vcu

import nl.littlerobots.vcu.model.Library
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
        """.trimMargin(), writer.toString()
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
        """.trimMargin(), writer.toString()
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
        """.trimMargin(), writer.toString()
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
        """.trimMargin(), writer.toString()
        )
    }

    @Test
    fun `writes bundle definiton`() {
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
        """.trimMargin(), writer.toString()
        )
    }
}