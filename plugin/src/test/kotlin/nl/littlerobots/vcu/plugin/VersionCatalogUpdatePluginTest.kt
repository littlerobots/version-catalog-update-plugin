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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VersionCatalogUpdatePluginTest {
    @get:Rule
    val tempDir = TemporaryFolder()
    lateinit var buildFile: File

    @Before
    fun setup() {
        buildFile = tempDir.newFile("build.gradle")
    }

    @Test
    fun `plugin requires versions plugin`() {
        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }
            """.trimIndent()
        )

        val runner = GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(runner.output.contains("com.github.ben-manes.versions needs to be applied as a plugin"))
    }

    @Test
    fun `plugins with plugin block syntax in subprojects are detected`() {
        val reportJson = tempDir.newFile()

        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            tasks.named("versionCatalogUpdate").configure {
                it.reportJson = file("${reportJson.name}")
            }
            """.trimIndent()
        )
        val moduleDir = File(tempDir.root, "module1")
        moduleDir.mkdir()
        val moduleBuildFile = File(moduleDir, "build.gradle")
        moduleBuildFile.writeText(
            """
            plugins {
                id "com.github.ben-manes.versions" version "0.39.0"
            }
            """.trimIndent()
        )
        val settingsFile = File(tempDir.root, "settings.gradle")
        settingsFile.writeText(
            """
            include(":module1")
            """.trimIndent()
        )

        // this is the plugin marker artifact that allows Gradle to resolve a plugin id
        reportJson.writeText(
            """
            {
              "current": {
                "dependencies": [
                  {
                    "group": "com.github.ben-manes.versions",
                    "version": "0.39.0",
                    "name": "com.github.ben-manes.versions.gradle.plugin"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "--create")
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            """
            [plugins]
            com-github-ben-manes-versions = "com.github.ben-manes.versions:0.39.0"

            """.trimIndent(),
            libs
        )
    }

    @Test
    fun `plugins with plugin block syntax mapped through a resolutionStrategy subprojects are detected`() {
        val reportJson = tempDir.newFile()

        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            tasks.named("versionCatalogUpdate").configure {
                it.reportJson = file("${reportJson.name}")
            }
            """.trimIndent()
        )
        val moduleDir = File(tempDir.root, "module1")
        moduleDir.mkdir()
        val moduleBuildFile = File(moduleDir, "build.gradle")
        moduleBuildFile.writeText(
            """
            plugins {
                id "com.github.ben-manes.versions" version "0.39.0"
            }
            """.trimIndent()
        )
        val settingsFile = File(tempDir.root, "settings.gradle")
        settingsFile.writeText(
            """
            include(":module1")
            """.trimIndent()
        )

        // this artifact would normally not be reported, unless it's mapped in settings.gradle,
        // so this fakes the plugin being resolved through an resolutionStrategy
        reportJson.writeText(
            """
            {
              "current": {
                "dependencies": [
                  {
                    "group": "com.github.ben-manes",
                    "version": "0.39.0",
                    "name": "gradle-versions-plugin"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "--create")
            .withPluginClasspath()
            .withDebug(true)
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()
        // "create" will add both a library and a plugin because all plugin dependencies are considered in one go
        // and this could therefore be a normal "apply" in a subproject or a plugin declaration
        assertEquals(
            """
            [libraries]
            com-github-ben-manes-gradle-versions-plugin = "com.github.ben-manes:gradle-versions-plugin:0.39.0"

            [plugins]
            com-github-ben-manes-versions = "com.github.ben-manes.versions:0.39.0"

            """.trimIndent(),
            libs
        )
    }

    @Test
    fun `plugin with report path does not require versions plugin`() {
        val reportJson = tempDir.newFile()

        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            tasks.named("versionCatalogUpdate").configure {
                it.reportJson = file("${reportJson.name}")
            }
            """.trimIndent()
        )

        // empty report
        reportJson.writeText("{}")

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "--create")
            .withPluginClasspath()
            .build()
    }

    @Test
    fun `adds VersionCatalogUpdateTask and sets report path`() {
        val project: Project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.github.ben-manes.versions")
        project.pluginManager.apply("nl.littlerobots.version-catalog-update")
        // force creation and configuration of dependent task
        project.tasks.getByName("dependencyUpdates")

        val task = project.tasks.getByName(TASK_NAME) as VersionCatalogUpdateTask
        assertNotNull(task.reportJson.orNull)
    }
}
