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
import org.junit.Assert.assertTrue
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
    fun `updateCatalog adds version for modules with the most common version`() {
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
                    module = "androidx.camera:camera-camera2",
                    version = VersionDefinition.Simple("1.3.1")
                ),
                "generated-library-reference2" to Library(
                    module = "androidx.camera:camera-core",
                    version = VersionDefinition.Simple("1.3.1")
                ),
                "generated-library-reference3" to Library(
                    module = "androidx.camera:camera-mlkit-vision",
                    version = VersionDefinition.Simple("1.4.0-alpha03")
                )
            ),
            emptyMap(),
            emptyMap()
        )

        val result = catalog.updateFrom(updatedCatalog, addNew = true)

        assertEquals(3, result.libraries.size)
        assertNotNull(result.libraries["generated-library-reference"])
        assertNotNull(result.libraries["generated-library-reference2"])
        assertNotNull(result.libraries["generated-library-reference3"])
        assertNotNull(result.versions["androidx-camera"])
        assertEquals(
            Library(
                module = "androidx.camera:camera-camera2",
                version = VersionDefinition.Reference("androidx-camera")
            ),
            result.libraries["generated-library-reference"]
        )
        assertEquals(
            Library(
                module = "androidx.camera:camera-core",
                version = VersionDefinition.Reference("androidx-camera")
            ),
            result.libraries["generated-library-reference2"]
        )
        assertEquals(
            Library(
                module = "androidx.camera:camera-mlkit-vision",
                version = VersionDefinition.Simple("1.4.0-alpha03")
            ),
            result.libraries["generated-library-reference3"]
        )
        assertEquals(VersionDefinition.Simple("1.3.1"), result.versions["androidx-camera"])
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

    @Test
    fun `Keeps version references valid for same group artifacts with diverging versions`() {
        val catalog = VersionCatalogParser().parse(
            """
            [versions]
            groupedVersion = "1.0.0"

            [libraries]
            mylib = {module = "nl.littlerobots.test:example", version.ref = "groupedVersion" }
            mylib2 = {module = "nl.littlerobots.test:example2", version.ref = "groupedVersion" }
            mylib3 = {module = "nl.littlerobots.test:example3", version.ref = "groupedVersion" }
            """.trimIndent().byteInputStream()
        )

        val update = VersionCatalogParser().parse(
            """
            [libraries]
            generated-key-1 = "nl.littlerobots.test:example:2.0.0"
            generated-key-2 = "nl.littlerobots.test:example2:3.2.0"
            generated-key-3 = "nl.littlerobots.test:example3:2.0.0"
            """.trimIndent().byteInputStream()
        )

        val result = catalog.updateFrom(update)

        assertEquals(setOf("mylib", "mylib2", "mylib3"), result.libraries.keys)
        val mylib = result.libraries["mylib"]!!
        val mylib2 = result.libraries["mylib2"]!!
        val mylib3 = result.libraries["mylib3"]!!

        assertTrue(mylib.version is VersionDefinition.Reference)
        assertTrue(mylib2.version is VersionDefinition.Simple)
        assertTrue(mylib3.version is VersionDefinition.Reference)

        assertEquals(1, result.versions.size)
        assertEquals(VersionDefinition.Simple("2.0.0"), result.versions["groupedVersion"])
        assertEquals("groupedVersion", (mylib.version as VersionDefinition.Reference).ref)
        assertEquals("3.2.0", (mylib2.version as VersionDefinition.Simple).version)
    }

    @Test
    fun `Keeps existing version references same group artifacts`() {
        val catalog = VersionCatalogParser().parse(
            """
            [versions]
            groupedVersion = "1.0.0"
            groupedVersion2 = "2.0.0"

            [libraries]
            mylib = {module = "nl.littlerobots.test:example", version.ref = "groupedVersion" }
            mylib2 = {module = "nl.littlerobots.test:example2", version.ref = "groupedVersion2" }
            mylib3 = {module = "nl.littlerobots.test:example3", version.ref = "groupedVersion" }
            """.trimIndent().byteInputStream()
        )

        val update = VersionCatalogParser().parse(
            """
            [libraries]
            generated-key-1 = "nl.littlerobots.test:example:2.0.0"
            generated-key-2 = "nl.littlerobots.test:example2:2.0.0"
            generated-key-3 = "nl.littlerobots.test:example3:2.0.0"
            """.trimIndent().byteInputStream()
        )

        val result = catalog.updateFrom(update)

        assertEquals(setOf("mylib", "mylib2", "mylib3"), result.libraries.keys)
        val mylib = result.libraries["mylib"]!!
        val mylib2 = result.libraries["mylib2"]!!
        val mylib3 = result.libraries["mylib3"]!!

        assertTrue(mylib.version is VersionDefinition.Reference)
        assertTrue(mylib2.version is VersionDefinition.Reference)
        assertTrue(mylib3.version is VersionDefinition.Reference)

        assertEquals(2, result.versions.size)
        assertEquals(VersionDefinition.Simple("2.0.0"), result.versions["groupedVersion"])
        assertEquals("groupedVersion", (mylib.version as VersionDefinition.Reference).ref)
        assertEquals("groupedVersion2", (mylib2.version as VersionDefinition.Reference).ref)
    }

    @Test
    // scenario from https://github.com/littlerobots/version-catalog-update-plugin/issues/36
    fun `Room dependency update keeps correct version reference`() {
        val catalog = VersionCatalogParser().parse(
            """
            [versions]
            room = "2.4.2"
            roomPaging = "2.5.0-alpha01"

            [libraries]
            roomKtx = {module = "androidx.room:room-ktx", version.ref = "room" }
            roomPaging = {module = "androidx.room:room-paging", version.ref = "roomPaging" }
            roomRuntime = {module = "androidx.room:room-runtime", version.ref = "room" }
            roomCompiler = {module = "androidx.room:room-compiler", version.ref = "room" }
            """.trimIndent().byteInputStream()
        )

        val update = VersionCatalogParser().parse(
            """
            [libraries]
            generated-key-1 = "androidx.room:room-ktx:2.5.0-alpha01"
            generated-key-2 = "androidx.room:room-paging:2.5.0-alpha01"
            generated-key-3 = "androidx.room:room-runtime:2.5.0-alpha01"
            generated-key-4 = "androidx.room:room-compiler:2.4.2"
            """.trimIndent().byteInputStream()
        )

        val result = catalog.updateFrom(update)

        assertEquals(2, result.versions.size)
        // because runtime and ktx bumped versions, the "room" version is bumped
        assertEquals(VersionDefinition.Simple("2.5.0-alpha01"), result.versions["room"])
        assertEquals(VersionDefinition.Simple("2.5.0-alpha01"), result.versions["roomPaging"])
        assertEquals(Library("androidx.room:room-ktx", version = VersionDefinition.Reference("room")), result.libraries["roomKtx"])
        assertEquals(Library("androidx.room:room-paging", version = VersionDefinition.Reference("roomPaging")), result.libraries["roomPaging"])
        assertEquals(Library("androidx.room:room-runtime", version = VersionDefinition.Reference("room")), result.libraries["roomRuntime"])
        // room-compiler is not updated, and effectively removed from the version reference
        assertEquals(Library("androidx.room:room-compiler", version = VersionDefinition.Simple("2.4.2")), result.libraries["roomCompiler"])
    }

    @Test
    // repro for https://github.com/littlerobots/version-catalog-update-plugin/issues/80
    fun `handles unspecified version when collecting version groups`() {
        val result = VersionCatalogParser().parse(
            """
            [libraries]
            com-google-firebase-firebase-config-ktx = { module = "com.google.firebase:firebase-config-ktx" }
            com-google-firebase-firebase-bom = "com.google.firebase:firebase-bom:30.3.2"
            """.trimIndent().byteInputStream()
        )

        assertEquals(2, result.libraries.size)
    }
}
