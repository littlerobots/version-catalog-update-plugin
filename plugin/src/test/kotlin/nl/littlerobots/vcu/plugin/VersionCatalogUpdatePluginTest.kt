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
import org.junit.Assert.assertFalse
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
    fun `plugin does not require versions plugin if upgrade task is disabled`() {
        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            tasks.named("versionCatalogUpdate").configure {
                it.enabled = false
            }
            """.trimIndent()
        )

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withPluginClasspath()
            .build()
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
    fun `create uses current versions`() {
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

        reportJson.writeText(
            """
            {
                "current": {
                    "dependencies": [
                        {
                            "group": "androidx.activity",
                            "userReason": null,
                            "version": "1.4.0",
                            "projectUrl": "https://developer.android.com/jetpack/androidx/releases/activity#1.4.0",
                            "name": "activity-compose"
                        }
                   ]
                },
                "exceeded": {
                    "dependencies": [
                        {
                            "group": "androidx.compose.ui",
                            "latest": "1.1.0-rc01",
                            "userReason": null,
                            "version": "1.1.0-rc02",
                            "projectUrl": "https://developer.android.com/jetpack/androidx/releases/compose-ui#1.1.0-rc01",
                            "name": "ui-test-junit4"
                        }
                    ]
                },
                "outdated": {
                    "dependencies": [
                        {
                            "group": "io.coil-kt",
                            "available": {
                                "release": null,
                                "milestone": "2.0.0-alpha06",
                                "integration": null
                            },
                            "userReason": null,
                            "version": "2.0.0-alpha05",
                            "projectUrl": "https://github.com/coil-kt/coil",
                            "name": "coil-compose"
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
        // "create" will add both a library and a plugin because all plugin dependencies are considered in one go
        // and this could therefore be a normal "apply" in a subproject or a plugin declaration
        assertEquals(
            """
                [libraries]
                androidx-activity-activity-compose = "androidx.activity:activity-compose:1.4.0"
                androidx-compose-ui-ui-test-junit4 = "androidx.compose.ui:ui-test-junit4:1.1.0-rc02"
                io-coil-kt-coil-compose = "io.coil-kt:coil-compose:2.0.0-alpha05"

            """.trimIndent(),
            libs
        )
    }

    @Test
    fun `normal invocation updates versions`() {
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
        reportJson.writeText(
            """
            {
                "current": {
                    "dependencies": [
                        {
                            "group": "androidx.activity",
                            "userReason": null,
                            "version": "1.4.0",
                            "projectUrl": "https://developer.android.com/jetpack/androidx/releases/activity#1.4.0",
                            "name": "activity-compose"
                        }
                   ]
                },
                "exceeded": {
                    "dependencies": [
                        {
                            "group": "androidx.compose.ui",
                            "latest": "1.1.0-rc01",
                            "userReason": null,
                            "version": "1.1.0-rc02",
                            "projectUrl": "https://developer.android.com/jetpack/androidx/releases/compose-ui#1.1.0-rc01",
                            "name": "ui-test-junit4"
                        }
                    ]
                },
                "outdated": {
                    "dependencies": [
                        {
                            "group": "io.coil-kt",
                            "available": {
                                "release": null,
                                "milestone": "2.0.0-alpha06",
                                "integration": null
                            },
                            "userReason": null,
                            "version": "2.0.0-alpha05",
                            "projectUrl": "https://github.com/coil-kt/coil",
                            "name": "coil-compose"
                        }
                    ]
                }
            }
            """.trimIndent()
        )

        val toml = """
                [libraries]
                androidx-activity-activity-compose = "androidx.activity:activity-compose:1.4.0"
                androidx-compose-ui-ui-test-junit4 = "androidx.compose.ui:ui-test-junit4:1.1.0-rc02"
                io-coil-kt-coil-compose = "io.coil-kt:coil-compose:2.0.0-alpha05"

        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            """
                [libraries]
                androidx-activity-activity-compose = "androidx.activity:activity-compose:1.4.0"
                androidx-compose-ui-ui-test-junit4 = "androidx.compose.ui:ui-test-junit4:1.1.0-rc01"
                io-coil-kt-coil-compose = "io.coil-kt:coil-compose:2.0.0-alpha06"

            """.trimIndent(),
            libs
        )
    }

    @Test
    fun `exceeded dependencies emit warning`() {
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

        reportJson.writeText(
            """
            {
                "exceeded": {
                    "dependencies": [
                        {
                            "group": "androidx.compose.ui",
                            "latest": "1.1.0-rc01",
                            "userReason": null,
                            "version": "1.1.0-rc02",
                            "projectUrl": "https://developer.android.com/jetpack/androidx/releases/compose-ui#1.1.0-rc01",
                            "name": "ui-test-junit4"
                        }
                    ]
                }
            }
            """.trimIndent()
        )

        val toml = """
                [libraries]
                androidx-test-junit4 = "androidx.compose.ui:ui-test-junit4:1.1.0-rc02"

        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        val buildResult = GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            """
                [libraries]
                androidx-test-junit4 = "androidx.compose.ui:ui-test-junit4:1.1.0-rc01"

            """.trimIndent(),
            libs
        )
        assertTrue(
            buildResult.output.contains(
                """
            Some libraries declared in the version catalog did not match the resolved version used this project.
            This mismatch can occur when a version is declared that does not exist, or when a dependency is referenced by a transitive dependency that requires a different version.
            The version in the version catalog has been updated to the actual version. If this is not what you want, consider using a strict version definition.

            The affected libraries are:
             - androidx.compose.ui:ui-test-junit4 (libs.androidx.test.junit4)
                 requested: 1.1.0-rc02, resolved: 1.1.0-rc01

                """.trimIndent()
            )
        )
    }

    @Test
    fun `available update for version condition emits warning`() {
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

        reportJson.writeText(
            """
            {
                "outdated": {
                    "dependencies": [
                        {
                            "group": "io.coil-kt",
                            "available": {
                                "release": null,
                                "milestone": "2.0.0-alpha06",
                                "integration": null
                            },
                            "userReason": null,
                            "version": "2.0.0-alpha05",
                            "projectUrl": "https://github.com/coil-kt/coil",
                            "name": "coil-compose"
                        }
                    ]
                }            }
            """.trimIndent()
        )

        val toml = """
                [libraries]
                coil = { module = "io.coil-kt:coil-compose", version = {strictly = "1.0.0"}}

        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        val buildResult = GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            """
                [libraries]
                coil = { module = "io.coil-kt:coil-compose", version = { strictly = "1.0.0" } }

            """.trimIndent(),
            libs
        )
        assertTrue(
            buildResult.output.contains(
                """
            There are libraries using a version condition that could be updated:
             - io.coil-kt:coil-compose (coil) -> 2.0.0-alpha06
                """.trimIndent()
            )
        )
    }

    @Test
    fun `available update for pinned library emits message`() {
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

        reportJson.writeText(
            """
            {
                "outdated": {
                    "dependencies": [
                        {
                            "group": "io.coil-kt",
                            "available": {
                                "release": null,
                                "milestone": "2.0.0-alpha06",
                                "integration": null
                            },
                            "userReason": null,
                            "version": "2.0.0-alpha05",
                            "projectUrl": "https://github.com/coil-kt/coil",
                            "name": "coil-compose"
                        }
                    ]
                }            }
            """.trimIndent()
        )

        val toml = """
                [libraries]
                # @pin
                coil = { module = "io.coil-kt:coil-compose", version = "2.0.0-alpha05" }

        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        val buildResult = GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withPluginClasspath()
            .withDebug(true)
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            """
                [libraries]
                # @pin
                coil = "io.coil-kt:coil-compose:2.0.0-alpha05"

            """.trimIndent(),
            libs
        )
        println(buildResult.output)
        assertTrue(
            buildResult.output.contains(
                """
                    There are updates available for pinned libraries in the version catalog:
                     - io.coil-kt:coil-compose (coil) 2.0.0-alpha05 -> 2.0.0-alpha06

                """.trimIndent()
            )
        )
    }

    @Test
    fun `available update for pinned plugin emits message`() {
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

        reportJson.writeText(
            """
            {
                "outdated": {
                    "dependencies": [
                        {
                            "group": "nl.littlerobots.version-catalog-update",
                            "available": {
                                "release": null,
                                "milestone": "0.2.0",
                                "integration": null
                            },
                            "userReason": null,
                            "version": "0.1.0",
                            "name": "nl.littlerobots.version-catalog-update.gradle.plugin"
                        }
                    ]
                }            }
            """.trimIndent()
        )

        val toml = """
                [plugins]
                # @pin
                vcu = { id = "nl.littlerobots.version-catalog-update", version = "0.1.0" }

        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        val buildResult = GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withPluginClasspath()
            .withDebug(true)
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            """
               [plugins]
               # @pin
               vcu = "nl.littlerobots.version-catalog-update:0.1.0"

            """.trimIndent(),
            libs
        )
        println(buildResult.output)
        assertTrue(
            buildResult.output.contains(
                """
                    There are updates available for pinned plugins in the version catalog:
                     - nl.littlerobots.version-catalog-update (vcu) 0.1.0 -> 0.2.0

                """.trimIndent()
            )
        )
    }

    @Test
    fun `adds VersionCatalogUpdateTask and sets report path`() {
        val project: Project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.github.ben-manes.versions")
        project.pluginManager.apply("nl.littlerobots.version-catalog-update")
        // force creation and configuration of dependent task
        project.tasks.getByName("dependencyUpdates")

        val task = project.tasks.getByName(UPDATE_TASK_NAME) as VersionCatalogUpdateTask
        assertNotNull(task.reportJson.orNull)
    }

    @Test
    fun `proceed plugin in catalog without version`() {
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
                keep.keepUnusedPlugins.set(true)
            }
            """.trimIndent()
        )

        reportJson.writeText(
            """
            {
                "current": {
                    "dependencies": [
                            {
                                "group": "io.gitlab.arturbosch.detekt",
                                "userReason": null,
                                "version": "1.19.0",
                                "projectUrl": "https://detekt.github.io/detekt",
                                "name": "detekt-formatting"
                            }
                    ]
                }
            }
            """.trimIndent()
        )

        val toml = """
                [plugins]
                detekt = { id = "io.gitlab.arturbosch.detekt" }

        """.trimIndent()
        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withDebug(true)
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            """
                [plugins]
                detekt = { id = "io.gitlab.arturbosch.detekt" }

            """.trimIndent(),
            libs
        )
    }

    @Test
    fun `sortByKey set to false does not sort the toml file`() {
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
                keep {
                    keepUnusedLibraries = true
                    keepUnusedVersions = true
                    keepUnusedPlugins = true
                }
                sortByKey = false
            }
            """.trimIndent()
        )

        val toml = """
            [versions]
            bbb = "1.2.3"
            aaa = "4.5.6"

            [libraries]
            bbb = "example:library:1.0"
            aaa = "some:library:2.0"

            [bundles]
            bbb = ["bbb"]
            aaa = ["aaa"]

            [plugins]
            bbb = "some.id:1.2.3"
            aaa = "another.id:1.0.0"

        """.trimIndent()

        reportJson.writeText(
            """
            {
            }
            """.trimIndent()
        )
        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withDebug(true)
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            """
                [versions]
                bbb = "1.2.3"
                aaa = "4.5.6"

                [libraries]
                bbb = "example:library:1.0"
                aaa = "some:library:2.0"

                [bundles]
                bbb = [
                    "bbb",
                ]
                aaa = [
                    "aaa",
                ]

                [plugins]
                bbb = "some.id:1.2.3"
                aaa = "another.id:1.0.0"

            """.trimIndent(),
            libs
        )
    }

    @Test
    fun `sortByKey defaults to true and sorts the toml file`() {
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
                keep {
                    keepUnusedLibraries = true
                    keepUnusedVersions = true
                    keepUnusedPlugins = true
                }
            }
            """.trimIndent()
        )

        val toml = """
            [versions]
            bbb = "1.2.3"
            aaa = "4.5.6"

            [libraries]
            bbb = "example:library:1.0"
            aaa = "some:library:2.0"

            [bundles]
            bbb = ["bbb"]
            aaa = ["aaa"]

            [plugins]
            bbb = "some.id:1.2.3"
            aaa = "another.id:1.0.0"

        """.trimIndent()

        reportJson.writeText(
            """
            {
            }
            """.trimIndent()
        )
        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withDebug(true)
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            """
                [versions]
                aaa = "4.5.6"
                bbb = "1.2.3"

                [libraries]
                aaa = "some:library:2.0"
                bbb = "example:library:1.0"

                [bundles]
                aaa = [
                    "aaa",
                ]
                bbb = [
                    "bbb",
                ]

                [plugins]
                aaa = "another.id:1.0.0"
                bbb = "some.id:1.2.3"

            """.trimIndent(),
            libs
        )
    }

    @Test
    fun `table and key comments are retained`() {
        val reportJson = tempDir.newFile()

        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            tasks.named("versionCatalogUpdate").configure {
                it.reportJson = file("${reportJson.name}")
            }

            // keep everything so that we can run an empty report
            versionCatalogUpdate {
                keep {
                    keepUnusedLibraries = true
                    keepUnusedVersions = true
                    keepUnusedPlugins = true
                }
            }
            """.trimIndent()
        )

        val toml = """
            # Versions comment
            [versions]
            # Version key comment
            bbb = "1.2.3"
            aaa = "4.5.6"

            # Libraries
            # multiline
            # comment
            [libraries]
            bbb = "example:library:1.0"
            #comment for key aaa
            aaa = "some:library:2.0"

            #Bundles comment
            [bundles]
            bbb = ["bbb"]
            #For key aaa
            aaa = ["aaa"]

            # plugins table comment
            [plugins]
            # plugin bbb
            bbb = "some.id:1.2.3"
            # plugin aaa
            #
            aaa = "another.id:1.0.0"

        """.trimIndent()

        reportJson.writeText(
            """
            {
            }
            """.trimIndent()
        )
        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withDebug(true)
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            """
               # Versions comment
               [versions]
               aaa = "4.5.6"
               # Version key comment
               bbb = "1.2.3"

               # Libraries
               # multiline
               # comment
               [libraries]
               #comment for key aaa
               aaa = "some:library:2.0"
               bbb = "example:library:1.0"

               #Bundles comment
               [bundles]
               #For key aaa
               aaa = [
                   "aaa",
               ]
               bbb = [
                   "bbb",
               ]

               # plugins table comment
               [plugins]
               # plugin aaa
               #
               aaa = "another.id:1.0.0"
               # plugin bbb
               bbb = "some.id:1.2.3"

            """.trimIndent(),
            libs
        )
    }

    @Test
    fun `keeps annotated entries in toml file`() {
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

        val toml = """
            [versions]
            # @keep this because it's cool
            bbb = "1.2.3"
            aaa = "4.5.6"

            [libraries]
            bbb = {module = "example:library", version.ref = "bbb"}
            #@keep
            aaa = "some:library:2.0"

            # plugins table comment
            [plugins]
            bbb = { id = "some.id", version.ref = "bbb" }
            # @keep
            aaa = "another.id:1.0.0"

        """.trimIndent()

        reportJson.writeText(
            """
            {
            }
            """.trimIndent()
        )
        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withDebug(true)
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            """
               [versions]
               # @keep this because it's cool
               bbb = "1.2.3"

               [libraries]
               #@keep
               aaa = "some:library:2.0"

               # plugins table comment
               [plugins]
               # @keep
               aaa = "another.id:1.0.0"

            """.trimIndent(),
            libs
        )
    }

    @Test
    fun `pins annotated entries in toml file`() {
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

        val toml = """
            [versions]
            # @pin
            bbb = "1.2.3"

            [libraries]
            bbb = {module = "example:library", version.ref = "bbb"}
            #@pinned
            aaa = "some:library:2.0"

            [plugins]
            bbb = { id = "some.id", version.ref = "bbb" }
            # @pin this plugin version
            aaa = "another.id:1.0.0"

        """.trimIndent()

        reportJson.writeText(
            """
               {
                 "outdated": {
                   "dependencies": [
                     {
                       "group": "example",
                       "available": {
                         "release": null,
                         "milestone": "2.0.0-alpha06",
                         "integration": null
                       },
                       "version": "1.0.0-alpha05",
                       "name": "library"
                     },
                     {
                       "group": "some",
                       "available": {
                         "release": null,
                         "milestone": "3.0.0-alpha06",
                         "integration": null
                       },
                       "version": "1.0.0-alpha05",
                       "name": "library"
                     },
                     {
                       "group": "com.some.plugin",
                       "available": {
                         "release": null,
                         "milestone": "3.0.0-alpha06",
                         "integration": null
                       },
                       "version": "1.0.0-alpha05",
                       "name": "some.id.gradle.plugin"
                     },
                     {
                       "group": "com.another.plugin",
                       "available": {
                         "release": null,
                         "milestone": "3.0.0-alpha06",
                         "integration": null
                       },
                       "version": "1.0.0-alpha05",
                       "name": "another.id.gradle.plugin"
                     }
                   ]
                 }
               }
            """.trimIndent()
        )
        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withDebug(true)
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            """
                [versions]
                # @pin
                bbb = "1.2.3"

                [libraries]
                #@pinned
                aaa = "some:library:2.0"
                bbb = { module = "example:library", version.ref = "bbb" }

                [plugins]
                # @pin this plugin version
                aaa = "another.id:1.0.0"
                bbb = { id = "some.id", version.ref = "bbb" }

            """.trimIndent(),
            libs
        )
    }

    @Test
    // repro for https://github.com/littlerobots/version-catalog-update-plugin/issues/61
    fun `unused pins should be ignored when generating pin warnings`() {
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

        reportJson.writeText("{}")

        val toml = """
            [libraries]
            # @pin
            # @keep
            aaa = "some:library:2.0"

            [plugins]
            # @pin
            # @keep
            aaa = "another.id:1.0.0"

        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withDebug(true)
            .withPluginClasspath()
            .build()
    }

    @Test
    fun `interactive mode stages changes`() {
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

        reportJson.writeText(
            """
            {
                "outdated": {
                    "dependencies": [
                        {
                            "group": "io.coil-kt",
                            "available": {
                                "release": null,
                                "milestone": "2.0.0-alpha06",
                                "integration": null
                            },
                            "userReason": null,
                            "version": "2.0.0-alpha05",
                            "projectUrl": "https://github.com/coil-kt/coil",
                            "name": "coil-compose"
                        },
                        {
                            "group": "nl.littlerobots.version-catalog-update",
                            "available": {
                                "release": null,
                                "milestone": "0.2.0",
                                "integration": null
                            },
                            "userReason": null,
                            "version": "0.1.0",
                            "name": "nl.littlerobots.version-catalog-update.gradle.plugin"
                        }

                    ]
                }
            }
            """.trimIndent()
        )

        val toml = """
            [versions]
            coil = "1.0.0"

            [libraries]
            test = { module = "io.coil-kt:coil-compose", version.ref = "coil" }

            [plugins]
            vcu = "nl.littlerobots.version-catalog-update:1.0"
        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "--interactive")
            .withPluginClasspath()
            .build()

        val stagingToml = File(tempDir.root, "gradle/libs.versions.updates.toml")
        assertTrue(stagingToml.exists())

        val lines = stagingToml.readLines()
        val fileWithoutDateHeader = lines.drop(1).joinToString(separator = "\n")

        assertTrue(lines.first().startsWith("# Version catalog updates generated at "))

        assertEquals(
            """
            #
            # Contents of this file will be applied to libs.versions.toml when running versionCatalogApplyUpdates.
            #
            # Comments will not be applied to the version catalog when updating.
            # To prevent a version upgrade, comment out the entry or remove it.
            #
            [libraries]
            # From version 1.0.0 --> 2.0.0-alpha06
            test = "io.coil-kt:coil-compose:2.0.0-alpha06"

            [plugins]
            # From version 1.0 --> 0.2.0
            vcu = "nl.littlerobots.version-catalog-update:0.2.0"
            """.trimIndent(),
            fileWithoutDateHeader
        )
    }

    @Test
    fun `interactive mode without updates does not create staging file`() {
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

        reportJson.writeText("{}")

        val toml = """
            [versions]
            coil = "1.0.0"

            [libraries]
            test = { module = "io.coil-kt:coil-compose", version.ref = "coil" }

            [plugins]
            vcu = "nl.littlerobots.version-catalog-update:1.0"
        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "--interactive")
            .withPluginClasspath()
            .build()

        val stagingToml = File(tempDir.root, "gradle/libs.versions.updates.toml")
        assertFalse(stagingToml.exists())
    }

    @Test
    fun `interactive mode marks pinned changes`() {
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

        reportJson.writeText(
            """
            {
                "outdated": {
                    "dependencies": [
                        {
                            "group": "io.coil-kt",
                            "available": {
                                "release": null,
                                "milestone": "2.0.0-alpha06",
                                "integration": null
                            },
                            "userReason": null,
                            "version": "2.0.0-alpha05",
                            "projectUrl": "https://github.com/coil-kt/coil",
                            "name": "coil-compose"
                        },
                        {
                            "group": "nl.littlerobots.version-catalog-update",
                            "available": {
                                "release": null,
                                "milestone": "0.2.0",
                                "integration": null
                            },
                            "userReason": null,
                            "version": "0.1.0",
                            "name": "nl.littlerobots.version-catalog-update.gradle.plugin"
                        }

                    ]
                }
            }
            """.trimIndent()
        )

        val toml = """
            [versions]
            coil = "1.0.0"

            [libraries]
            # @pin
            test = { module = "io.coil-kt:coil-compose", version.ref = "coil" }

            [plugins]
            # @pin
            vcu = "nl.littlerobots.version-catalog-update:1.0"
        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "--interactive")
            .withPluginClasspath()
            .build()

        val stagingToml = File(tempDir.root, "gradle/libs.versions.updates.toml")
        assertTrue(stagingToml.exists())

        val lines = stagingToml.readLines()
        val fileWithoutDateHeader = lines.drop(1).joinToString(separator = "\n")

        assertTrue(lines.first().startsWith("# Version catalog updates generated at "))

        assertEquals(
            """
            #
            # Contents of this file will be applied to libs.versions.toml when running versionCatalogApplyUpdates.
            #
            # Comments will not be applied to the version catalog when updating.
            # To prevent a version upgrade, comment out the entry or remove it.
            #
            [libraries]
            # @pinned version 1.0.0 --> 2.0.0-alpha06
            #test = "io.coil-kt:coil-compose:2.0.0-alpha06"

            [plugins]
            # @pinned version 1.0 --> 0.2.0
            #vcu = "nl.littlerobots.version-catalog-update:0.2.0"
            """.trimIndent(),
            fileWithoutDateHeader
        )
    }

    @Test
    fun `normal update fails when staging file is present`() {
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

        reportJson.writeText("{}")

        val toml = """
            [versions]
            coil = "1.0.0"

            [libraries]
            test = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)
        val stagingToml = File(tempDir.root, "gradle/libs.versions.updates.toml")
        stagingToml.writeText("# dummy file")

        val result = GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(result.output.contains("libs.versions.updates.toml exists, did you mean to run the versionCatalogApplyUpdates task to apply the updates?"))
    }

    @Test // repro for https://github.com/littlerobots/version-catalog-update-plugin/issues/71
    fun `Transitive dependencies reported as current with different versions should be treated as unused`() {
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

        reportJson.writeText(
            """
                        {
                "current": {
                    "dependencies": [
                        {
                            "group": "androidx.activity",
                            "userReason": null,
                            "version": "1.4.0",
                            "projectUrl": "https://developer.android.com/jetpack/androidx/releases/activity#1.4.0",
                            "name": "activity-compose"
                        }
                   ]
                }
                }
            """.trimIndent()
        )

        val toml = """
            [libraries]
            # an entry that is never used as a direct dependency
            unused = "androidx.activity:activity-compose:1.6.0"
        """.trimIndent()

        val tomlFile = File(tempDir.root, "gradle/libs.versions.toml")
        File(tempDir.root, "gradle").mkdir()
        tomlFile.writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withPluginClasspath()
            .build()

        assertEquals("", tomlFile.readText())
    }

    @Test
    fun `interactive retains kept references`() {
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

        reportJson.writeText("{}")

        val toml = """
            [versions]
            # @keep
            coil = "1.0.0"

            [libraries]
            # removed event though the version is kept!
            test = { module = "io.coil-kt:coil-compose", version.ref = "coil" }
        """.trimIndent()

        val tomlFile = File(tempDir.root, "gradle/libs.versions.toml")
        File(tempDir.root, "gradle").mkdir()
        tomlFile.writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "--interactive")
            .withPluginClasspath()
            .build()

        assertEquals(
            """
            [versions]
            # @keep
            coil = "1.0.0"

            """.trimIndent(),
            tomlFile.readText()
        )
    }

    @Test
    fun `keep all versions retains version order in catalog`() {
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
                sortByKey = false
                keep.keepUnusedVersions.set(true)
            }
            """.trimIndent()
        )

        reportJson.writeText(
            """
                        {
                "current": {
                    "dependencies": [
                        {
                            "group": "androidx.activity",
                            "userReason": null,
                            "version": "1.4.0",
                            "projectUrl": "https://developer.android.com/jetpack/androidx/releases/activity#1.4.0",
                            "name": "activity-compose"
                        }
                   ]
                }
                }
            """.trimIndent()
        )

        val toml = """
            [versions]
            someVersion = "1.0.0"
            activity = "1.4.0"

            [libraries]
            test = { module = "androidx.activity:activity-compose", version.ref = "activity" }

        """.trimIndent()

        val tomlFile = File(tempDir.root, "gradle/libs.versions.toml")
        File(tempDir.root, "gradle").mkdir()
        tomlFile.writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withPluginClasspath()
            .build()

        assertEquals(
            toml,
            tomlFile.readText()
        )
    }
    @Test
    fun `kept version retains order in catalog`() {
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
                sortByKey = false
            }
            """.trimIndent()
        )

        reportJson.writeText(
            """
                        {
                "current": {
                    "dependencies": [
                        {
                            "group": "androidx.activity",
                            "userReason": null,
                            "version": "1.4.0",
                            "projectUrl": "https://developer.android.com/jetpack/androidx/releases/activity#1.4.0",
                            "name": "activity-compose"
                        }
                   ]
                }
                }
            """.trimIndent()
        )

        val toml = """
            [versions]
            # @keep
            someVersion = "1.0.0"
            activity = "1.4.0"

            [libraries]
            test = { module = "androidx.activity:activity-compose", version.ref = "activity" }

        """.trimIndent()

        val tomlFile = File(tempDir.root, "gradle/libs.versions.toml")
        File(tempDir.root, "gradle").mkdir()
        tomlFile.writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate")
            .withPluginClasspath()
            .build()

        assertEquals(
            toml,
            tomlFile.readText()
        )
    }
}
