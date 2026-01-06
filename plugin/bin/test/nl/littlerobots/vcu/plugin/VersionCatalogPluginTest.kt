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
package nl.littlerobots.vcu.plugin

import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VersionCatalogPluginTest {
    @get:Rule
    val tempDir = TemporaryFolder()
    lateinit var buildFile: File

    @Before
    fun setup() {
        buildFile = tempDir.newFile("build.gradle")
    }

    @Test
    fun `versionCatalogs block creates task for each catalog`() {
        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            versionCatalogUpdate {
                versionCatalogs {
                    libs2 {
                        catalogFile = file("libs2.versions.toml")
                    }
                }
            }
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(tempDir.root)
                .withArguments("tasks")
                .withPluginClasspath()
                .build()

        assertTrue(result.output.contains("versionCatalogUpdate"))
        assertTrue(result.output.contains("versionCatalogFormat"))
        assertTrue(result.output.contains("versionCatalogApply"))
        assertTrue(result.output.contains("versionCatalogUpdateLibs2"))
        assertTrue(result.output.contains("versionCatalogFormatLibs2"))
        assertTrue(result.output.contains("versionCatalogApplyUpdatesLibs2"))
    }

    @Test
    fun `default version catalog file can be changed`() {
        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            versionCatalogUpdate {
                catalogFile = file("libs3.versions.toml")
            }
            """.trimIndent(),
        )

        val toml =
            """
            [libraries]
            b = "com.test:example:1.0.0"
            a = "com.test2:example:1.0.0"
            """.trimIndent()

        val tomlFile = tempDir.newFile("libs3.versions.toml")
        tomlFile.writeText(toml)

        GradleRunner
            .create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogFormat")
            .withPluginClasspath()
            .build()

        // check if the file was formatted
        val formattedToml = tomlFile.readText()

        assertEquals(
            """
            [libraries]
            a = "com.test2:example:1.0.0"
            b = "com.test:example:1.0.0"

            """.trimIndent(),
            formattedToml,
        )
    }
}
