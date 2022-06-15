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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VersionCatalogFormatPluginTest {
    @get:Rule
    val tempDir = TemporaryFolder()
    lateinit var buildFile: File

    @Before
    fun setup() {
        buildFile = tempDir.newFile("build.gradle")
    }

    @Test
    fun `formats the version catalog`() {
        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            // disable to forego the versions plugin requirement
            tasks.named("versionCatalogUpdate").configure {
                it.enabled = false
            }
            """.trimIndent()
        )

        val toml = """
                [libraries]
                test = { module = "some.dependency:test", version = "1.0.0" }
                androidx-test-junit4 = "androidx.compose.ui:ui-test-junit4:1.1.0-rc02"

        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogFormat")
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            """
            [libraries]
            androidx-test-junit4 = "androidx.compose.ui:ui-test-junit4:1.1.0-rc02"
            test = "some.dependency:test:1.0.0"

            """.trimIndent(),
            libs
        )
    }
}