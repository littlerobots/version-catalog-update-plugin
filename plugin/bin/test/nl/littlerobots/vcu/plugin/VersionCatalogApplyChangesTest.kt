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
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VersionCatalogApplyChangesTest {
    @get:Rule
    val tempDir = TemporaryFolder()
    lateinit var buildFile: File

    @Before
    fun setup() {
        buildFile = tempDir.newFile("build.gradle")
    }

    @Test
    fun `changes are applied to the version catalog`() {
        val reportJson = tempDir.newFile()

        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            tasks.named("versionCatalogUpdate").configure {
                it.reportJson = file("${reportJson.name}")
            }
            """.trimIndent(),
        )

        val toml =
            """
            [versions]
            coil = "1.0.0"

            [libraries]
            test = { module = "io.coil-kt:coil-compose", version.ref = "coil" }

            [plugins]
            vcu = "nl.littlerobots.version-catalog-update:1.0"
            """.trimIndent()

        val staged =
            """
            [libraries]
            test = "io.coil-kt:coil-compose:2.0.0"

            [plugins]
            #vcu = "nl.littlerobots.version-catalog-update:1.0"
            """.trimIndent()

        val tomlFile = File(tempDir.root, "gradle/libs.versions.toml")
        val stagingFile = File(tempDir.root, "gradle/libs.versions.updates.toml")

        File(tempDir.root, "gradle").mkdir()
        tomlFile.writeText(toml)
        stagingFile.writeText(staged)

        GradleRunner
            .create()
            .withProjectDir(tempDir.root)
            .withArguments(VersionCatalogApplyUpdatesTask.TASK_NAME)
            .withPluginClasspath()
            .build()

        assertFalse(stagingFile.exists())
        // keeps version group and updates it, skips vcu due to comment
        assertEquals(
            """
            [versions]
            coil = "2.0.0"

            [libraries]
            test = { module = "io.coil-kt:coil-compose", version.ref = "coil" }

            [plugins]
            vcu = "nl.littlerobots.version-catalog-update:1.0"

            """.trimIndent(),
            tomlFile.readText(),
        )
    }
}
