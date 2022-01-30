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
package nl.littlerobots.vcu.model

import nl.littlerobots.vcu.VersionCatalogParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VersionCatalogTest {
    @Test
    fun `updateCatalog adds library`() {
        val catalog = VersionCatalog(
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap()
        )
        val updatedCatalog = VersionCatalog(
            emptyMap(),
            mapOf(
                "generated-library-reference" to Library(
                    module = "nl.littlerobots.test:example",
                    version = VersionDefinition.Simple("1.0")
                )
            ),
            emptyMap(),
            emptyMap()
        )

        val result = catalog.updateFrom(updatedCatalog, addNew = true)

        assertEquals(1, result.libraries.size)
        assertNotNull(result.libraries["generated-library-reference"])
        assertEquals(
            Library(
                module = "nl.littlerobots.test:example",
                version = VersionDefinition.Simple("1.0")
            ),
            result.libraries["generated-library-reference"]
        )
    }

    @Test
    fun `updateCatalog adds version if more than one module in the same group have the same version`() {
        val catalog = VersionCatalog(
            emptyMap(),
            emptyMap(),
            emptyMap(),
            emptyMap()
        )
        val updatedCatalog = VersionCatalog(
            emptyMap(),
            mapOf(
                "generated-library-reference" to Library(
                    module = "nl.littlerobots.test:example",
                    version = VersionDefinition.Simple("1.0")
                ),
                "generated-library-reference-2" to Library(
                    module = "nl.littlerobots.test:example2",
                    version = VersionDefinition.Simple("1.0")
                )
            ),
            emptyMap(),
            emptyMap()
        )

        val result = catalog.updateFrom(updatedCatalog, addNew = true)

        assertEquals(2, result.libraries.size)
        assertNotNull(result.libraries["generated-library-reference"])
        assertNotNull(result.libraries["generated-library-reference-2"])
        assertNotNull(result.versions["nl-littlerobots-test"])
        assertEquals(
            Library(
                module = "nl.littlerobots.test:example",
                version = VersionDefinition.Reference("nl-littlerobots-test")
            ),
            result.libraries["generated-library-reference"]
        )
        assertEquals(
            Library(
                module = "nl.littlerobots.test:example2",
                version = VersionDefinition.Reference("nl-littlerobots-test")
            ),
            result.libraries["generated-library-reference-2"]
        )
        assertEquals(VersionDefinition.Simple("1.0"), result.versions["nl-littlerobots-test"])
    }

    @Test
    fun `updateCatalog updates version reference for group id if all on the same version`() {
        val catalog = VersionCatalog(
            mapOf("my-lib" to VersionDefinition.Simple("1.0")),
            mapOf(
                "generated-library-reference" to Library(
                    module = "nl.littlerobots.test:example",
                    version = VersionDefinition.Reference("my-lib")
                ),
                "generated-library-reference-2" to Library(
                    module = "nl.littlerobots.test:example2",
                    version = VersionDefinition.Reference("my-lib")
                )
            ),
            emptyMap(),
            emptyMap()
        )
        val updatedCatalog = VersionCatalog(
            emptyMap(),
            mapOf(
                "generated-library-reference" to Library(
                    module = "nl.littlerobots.test:example",
                    version = VersionDefinition.Simple("1.1")
                ),
                "generated-library-reference-2" to Library(
                    module = "nl.littlerobots.test:example2",
                    version = VersionDefinition.Simple("1.1")
                )
            ),
            emptyMap(),
            emptyMap()
        )

        val result = catalog.updateFrom(updatedCatalog, true)

        assertEquals(2, result.libraries.size)
        assertNotNull(result.libraries["generated-library-reference"])
        assertNotNull(result.libraries["generated-library-reference-2"])
        assertNotNull(result.versions["my-lib"])
        assertEquals(
            Library(
                module = "nl.littlerobots.test:example",
                version = VersionDefinition.Reference("my-lib")
            ),
            result.libraries["generated-library-reference"]
        )
        assertEquals(
            Library(
                module = "nl.littlerobots.test:example2",
                version = VersionDefinition.Reference("my-lib")
            ),
            result.libraries["generated-library-reference-2"]
        )
        assertEquals(VersionDefinition.Simple("1.1"), result.versions["my-lib"])
    }

    @Test
    fun `updateCatalog updates version reference if defined`() {
        val catalog = VersionCatalog(
            mapOf("my-lib" to VersionDefinition.Simple("1.0")),
            mapOf(
                "generated-library-reference" to Library(
                    module = "nl.littlerobots.test:example",
                    version = VersionDefinition.Reference("my-lib")
                )
            ),
            emptyMap(),
            emptyMap()
        )
        val updatedCatalog = VersionCatalog(
            emptyMap(),
            mapOf(
                "generated-library-reference" to Library(
                    module = "nl.littlerobots.test:example",
                    version = VersionDefinition.Simple("1.1")
                )
            ),
            emptyMap(),
            emptyMap()
        )

        val result = catalog.updateFrom(updatedCatalog, true)

        assertEquals(1, result.libraries.size)
        assertNotNull(result.libraries["generated-library-reference"])
        assertNotNull(result.versions["my-lib"])
        assertEquals(
            Library(
                module = "nl.littlerobots.test:example",
                version = VersionDefinition.Reference("my-lib")
            ),
            result.libraries["generated-library-reference"]
        )
        assertEquals(VersionDefinition.Simple("1.1"), result.versions["my-lib"])
    }

    @Test
    fun `updateCatalog maintains version condition reference if defined`() {
        val catalog = VersionCatalog(
            // the actual condition does not matter
            mapOf(
                "my-lib" to VersionDefinition.Condition(mapOf()),
                "my-plugin" to VersionDefinition.Condition(mapOf())
            ),
            mapOf(
                "generated-library-reference" to Library(
                    module = "nl.littlerobots.test:example",
                    version = VersionDefinition.Reference("my-lib")
                )
            ),
            emptyMap(),
            mapOf("my-plugin" to Plugin("test.plugin", version = VersionDefinition.Reference("my-plugin")))
        )
        val updatedCatalog = VersionCatalog(
            emptyMap(),
            mapOf(
                "generated-library-reference" to Library(
                    module = "nl.littlerobots.test:example",
                    version = VersionDefinition.Simple("1.1")
                )
            ),
            emptyMap(),
            mapOf("my-plugin" to Plugin("test.plugin", version = VersionDefinition.Simple("2.0")))
        )

        val result = catalog.updateFrom(updatedCatalog, true)

        assertEquals(catalog, result)
    }

    @Test
    fun `updateCatalog updates version reference for multiple libraries`() {
        val catalog = VersionCatalog(
            mapOf("my-lib" to VersionDefinition.Simple("1.0")),
            mapOf(
                "generated-library-reference" to Library(
                    module = "nl.littlerobots.test:example",
                    version = VersionDefinition.Reference("my-lib")
                ),
                // different group id
                "generated-library-reference-2" to Library(
                    module = "nl.littlerobots.test2:example",
                    version = VersionDefinition.Reference("my-lib")
                )
            ),
            emptyMap(),
            mapOf("plugin" to Plugin("my.plugin.id", version = VersionDefinition.Reference("my-lib")))
        )
        val updatedCatalog = VersionCatalog(
            emptyMap(),
            mapOf(
                "generated-library-reference" to Library(
                    module = "nl.littlerobots.test:example",
                    version = VersionDefinition.Simple("1.1")
                ),
                "generated-library-reference-2" to Library(
                    module = "nl.littlerobots.test2:example",
                    version = VersionDefinition.Simple("1.1")
                )
            ),
            emptyMap(),
            mapOf("plugin" to Plugin("my.plugin.id", version = VersionDefinition.Simple("1.1")))
        )

        val result = catalog.updateFrom(updatedCatalog, true)

        assertEquals(2, result.libraries.size)
        assertNotNull(result.libraries["generated-library-reference"])
        assertNotNull(result.versions["my-lib"])
        assertEquals(
            Library(
                module = "nl.littlerobots.test:example",
                version = VersionDefinition.Reference("my-lib")
            ),
            result.libraries["generated-library-reference"]
        )
        assertEquals(
            Library(
                module = "nl.littlerobots.test2:example",
                version = VersionDefinition.Reference("my-lib")
            ),
            result.libraries["generated-library-reference-2"]
        )

        assertEquals(1, result.plugins.size)
        assertEquals(Plugin("my.plugin.id", version = VersionDefinition.Reference("my-lib")), result.plugins["plugin"])
        assertEquals(VersionDefinition.Simple("1.1"), result.versions["my-lib"])
    }

    @Test
    fun `updateCatalog updates version`() {
        val catalog = VersionCatalog(
            emptyMap(),
            mapOf(
                "my-library" to Library(
                    module = "nl.littlerobots.test:example",
                    version = VersionDefinition.Simple("1.0")
                )
            ),
            emptyMap(),
            emptyMap()
        )
        val updatedCatalog = VersionCatalog(
            emptyMap(),
            mapOf(
                "generated-library-reference" to Library(
                    module = "nl.littlerobots.test:example",
                    version = VersionDefinition.Simple("1.1")
                )
            ),
            emptyMap(),
            emptyMap()
        )

        val result = catalog.updateFrom(updatedCatalog)

        assertEquals(1, result.libraries.size)
        assertNotNull(result.libraries["my-library"])
        assertEquals(
            Library(
                module = "nl.littlerobots.test:example",
                version = VersionDefinition.Simple("1.1")
            ),
            result.libraries["my-library"]
        )
    }

    @Test
    fun `updateCatalog maintains library order`() {
        val catalog = VersionCatalogParser().parse(
            """
            [libraries]
            junit = "junit:junit:4.13.2"
            firebase-auth = "com.google.firebase:firebase-auth:21.0.1"
            firebase-bom = "com.google.firebase:firebase-bom:28.4.2"
            """.trimIndent().byteInputStream()
        )

        val updatedCatalog = VersionCatalogParser().parse(
            """
            [libraries]
            com-google-firebase-firebase-auth = "com.google.firebase:firebase-auth:21.0.1"
            com-google-firebase-firebase-bom = "com.google.firebase:firebase-bom:28.4.2"
            junit = "junit:junit:4.13.2"
            org-jetbrains-kotlin-kotlin-scripting-compiler-embeddable = "org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:1.5.31"
            """.trimIndent().byteInputStream()
        )

        val result = catalog.updateFrom(updatedCatalog, addNew = true)

        assertEquals(4, result.libraries.size)
        assertEquals(catalog.libraries.keys.toList(), result.libraries.keys.toList().dropLast(1))
    }

    @Test
    fun `removes unused libraries`() {
        val catalog = VersionCatalog(
            emptyMap(),
            mapOf(
                "my-library" to Library(
                    module = "nl.littlerobots.test:example",
                    version = VersionDefinition.Simple("1.0")
                ),
                "my-unused-lib" to Library(
                    module = "nl.littlerobots.test:example2",
                    version = VersionDefinition.Simple("1.0")
                )
            ),
            emptyMap(),
            emptyMap()
        )
        val updatedCatalog = VersionCatalog(
            emptyMap(),
            mapOf(
                "generated-library-reference" to Library(
                    module = "nl.littlerobots.test:example",
                    version = VersionDefinition.Simple("1.1")
                )
            ),
            emptyMap(),
            emptyMap()
        )

        val result = catalog.updateFrom(updatedCatalog, purge = true)

        assertEquals(1, result.libraries.size)
        assertNotNull(result.libraries["my-library"])
    }

    @Test
    fun `removes unused plugin ids`() {
        val catalog = VersionCatalogParser().parse(
            """
            [plugins]
            myplugin = "some.plugin.id:1.4"
            myplugin2 = "another.plugin.id:1.4"
            """.trimIndent().byteInputStream()
        )

        val update = VersionCatalog(
            emptyMap(),
            emptyMap(),
            emptyMap(),
            mapOf("plugin" to Plugin(id = "some.plugin.id", version = VersionDefinition.Simple("1.5")))
        )

        val result = catalog.updateFrom(update)

        assertEquals(1, result.plugins.size)
        assertNull(result.plugins["myplugin2"])
        assertEquals(
            Plugin(id = "some.plugin.id", version = VersionDefinition.Simple("1.5")),
            result.plugins["myplugin"]
        )
    }

    @Test
    fun `removes unused version references`() {
        val catalog = VersionCatalogParser().parse(
            """
            [versions]
            unused = "1.0.0"
            junit = "4.13.2"
            plugin = "1.0.0"
            [libraries]
            junit = { module = "junit:junit", version.ref = "junit" }
            [plugins]
            myplugin = { id = "test", version.ref = "plugin" }
            """.trimIndent().byteInputStream()
        )

        val result = catalog.pruneVersions()

        assertEquals(
            mapOf(
                "junit" to VersionDefinition.Simple("4.13.2"),
                "plugin" to VersionDefinition.Simple("1.0.0")
            ),
            result.versions
        )
    }

    @Test
    fun `updates bundle references`() {
        val catalog = VersionCatalogParser().parse(
            """
            [libraries]
            junit = "junit:junit:4.13.2"
            alib = "alib:alib:1.0.0"
            [bundles]
            example = ["junit", "missing"]
            sorted = ["junit", "alib"]
            stale = ["old", "older" ]
            """.trimIndent().byteInputStream()
        )

        val result = catalog.updateBundles()

        assertEquals(listOf("junit"), result.bundles["example"])
        assertEquals(listOf("alib", "junit"), result.bundles["sorted"])
        assertNull(result.bundles["stale"])
    }

    @Test
    fun `updates existing plugin reference`() {
        val catalog = VersionCatalogParser().parse(
            """
            [plugins]
            myplugin = "some.plugin.id:1.4"
            """.trimIndent().byteInputStream()
        )

        val update = VersionCatalogParser().parse(
            """
            [plugins]
            incoming-plugin = "some.plugin.id:1.5"
            """.trimIndent().byteInputStream()
        )

        val result = catalog.updateFrom(update)

        assertEquals(1, result.plugins.size)
        assertEquals(
            Plugin(id = "some.plugin.id", version = VersionDefinition.Simple("1.5")),
            result.plugins["myplugin"]
        )
    }

    @Test
    fun `keeps unspecified version on update`() {
        val catalog = VersionCatalogParser().parse(
            """
            [libraries]
            mylib = { module = "nl.littlerobots.test:example" }
            """.trimIndent().byteInputStream()
        )

        val update = VersionCatalogParser().parse(
            """
            [libraries]
            update = "nl.littlerobots.test:example:1.1"
            """.trimIndent().byteInputStream()
        )

        val result = catalog.updateFrom(update)

        assertEquals(1, result.libraries.size)
        assertEquals(
            Library(module = "nl.littlerobots.test:example", version = VersionDefinition.Unspecified),
            result.libraries["mylib"]
        )
    }

    @Test
    fun `keeps complex version definition`() {
        val catalog = VersionCatalogParser().parse(
            """
            [libraries]
            test = { group = "com.mycompany", name = "alternate", version = { require = "1.4" } }
            """.trimIndent().byteInputStream()
        )

        val update = VersionCatalogParser().parse(
            """
            [libraries]
            test = "com.mycompany:alternate:1.8"
            """.trimIndent().byteInputStream()
        )

        val result = catalog.updateFrom(update)

        assertEquals(1, result.libraries.size)
        assertEquals(
            Library(
                module = "com.mycompany:alternate",
                version = VersionDefinition.Condition(mapOf("require" to "1.4"))
            ),
            result.libraries["test"]
        )
    }

    @Test
    fun `adds plugins from module plugin mapping`() {
        val catalog = VersionCatalogParser().parse(
            """
            [libraries]
            mylib = "nl.littlerobots.test:example:1.0.0"
            """.trimIndent().byteInputStream()
        )

        val result = catalog.mapPlugins(
            mapOf(
                "some.plugin.id" to "nl.littlerobots.test:example",
                "another.plugin.id" to "nl.littlerobots.test:example"
            )
        )

        assertEquals(2, result.plugins.size)
        assertEquals(1, result.libraries.size)
        assertEquals(
            Plugin(id = "some.plugin.id", version = VersionDefinition.Simple("1.0.0")),
            result.plugins["some-plugin-id"]
        )
        assertEquals(
            Plugin(id = "another.plugin.id", version = VersionDefinition.Simple("1.0.0")),
            result.plugins["another-plugin-id"]
        )
    }

    @Test
    fun `updatePlugins ignores existing plugins for updates`() {
        val catalog = VersionCatalogParser().parse(
            """
            [libraries]
            mylib = "nl.littlerobots.test:example:1.0.0"

            [plugins]
            test = "some.plugin.id:2.0.0"
            """.trimIndent().byteInputStream()
        )

        val result = catalog.mapPlugins(
            mapOf(
                "some.plugin.id" to "nl.littlerobots.test:example",
                "another.plugin.id" to "nl.littlerobots.test:example"
            )
        )

        assertEquals(2, result.plugins.size)
        assertEquals(1, result.libraries.size)
        assertEquals(Plugin(id = "some.plugin.id", version = VersionDefinition.Simple("2.0.0")), result.plugins["test"])
        assertEquals(
            Plugin(id = "another.plugin.id", version = VersionDefinition.Simple("1.0.0")),
            result.plugins["another-plugin-id"]
        )
    }

    @Test
    fun `Retains unused version references`() {
        val catalog = VersionCatalog(
            versions = mapOf("unused" to VersionDefinition.Simple("1.0.0")),
            libraries = emptyMap(),
            bundles = emptyMap(),
            plugins = emptyMap()
        )

        val result = catalog.updateFrom(catalog, purge = false)

        assertEquals(catalog.versions, result.versions)
    }

    @Test
    fun `Removes unused version references`() {
        val catalog = VersionCatalog(
            versions = mapOf("unused" to VersionDefinition.Simple("1.0.0")),
            libraries = emptyMap(),
            bundles = emptyMap(),
            plugins = emptyMap()
        )

        val result = catalog.updateFrom(catalog, purge = true)

        assertEquals(emptyMap<String, String>(), result.versions)
    }
}
