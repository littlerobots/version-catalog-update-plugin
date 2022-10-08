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
        val reportJson = tempDir.newFile()

        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            tasks.named("versionCatalogUpdate").configure {
                it.reportJson = file("${reportJson.name}")
            }

            versionCatalogUpdate {
                versionCatalogs {
                    libs2 {
                        catalogFile = file("libs2.versions.toml")
                    }
                }
            }
            // Workaround for classpath issues with applying the dependency versions plugin in tests
            // With report path set, the dependency is not required
            tasks.named("versionCatalogUpdateLibs2").configure {
                it.reportJson = file("${reportJson.name}")
            }
            """.trimIndent()

        )

        val result = GradleRunner.create()
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
}
