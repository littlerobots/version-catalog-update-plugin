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
    fun `normal invocation updates versions`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            repositories {
                maven {
                    url "$m2"
                }
            }

            """.trimIndent()
        )

        val toml = """
                [libraries]
                androidx-activity-activity-compose = "androidx.activity:activity-compose:1.4.0"

        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
            .withPluginClasspath()
            .withDebug(true)
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            """
                [libraries]
                androidx-activity-activity-compose = "androidx.activity:activity-compose:1.9.2"

            """.trimIndent(),
            libs
        )
    }

    @Test
    fun `exceeded dependencies emit warning`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            repositories {
                maven {
                    url "$m2"
                }
            }

            """.trimIndent()
        )

        val toml = """
                [libraries]
                androidx-activity-activity-compose = "androidx.activity:activity-compose:1.9.3"

        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        val buildResult = GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            """
                [libraries]
                androidx-activity-activity-compose = "androidx.activity:activity-compose:1.9.3"

            """.trimIndent(),
            libs
        )

        assertTrue(
            buildResult.output.contains(
                """
                    There are libraries with invalid versions that could be updated:
                     - androidx.activity:activity-compose (androidx-activity-activity-compose) -> 1.9.2

                """.trimIndent()
            )
        )
    }

    @Test
    fun `available update for version condition emits warning`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            repositories {
                maven {
                    url "$m2"
                }
            }

            """.trimIndent()
        )

        val toml = """
                [libraries]
                androidx-activity-activity-compose = { module = "androidx.activity:activity-compose", version = { strictly = "1.4.0" } }

        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        val buildResult = GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            toml,
            libs
        )
        assertTrue(
            buildResult.output.contains(
                """
                    There are libraries using a rich version that could be updated:
                     - androidx.activity:activity-compose (androidx-activity-activity-compose) -> 1.9.2

                """.trimIndent()
            )
        )
    }

    // No rich versions for plugins?
    @Test
    fun `available update for plugin by descriptor with version condition emits warning`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            buildscript {
                repositories {
                    maven {
                        url "$m2"
                    }
                }
            }
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            repositories {
                maven {
                    url "$m2"
                }
            }

            """.trimIndent()
        )

        val toml = """
                [plugins]
                android-library = { id = "com.android.library", version = { strictly = "8.7.0" } }

        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        val buildResult = GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
            .withPluginClasspath()
            .withDebug(true)
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            toml,
            libs
        )

        assertTrue(
            buildResult.output.contains(
                """
                    There are plugins using a rich version that could be updated:
                     - com.android.library (android-library) -> 8.7.1

                """.trimIndent()
            )
        )
    }

    @Test
    fun `available update for plugin with version condition references version group in warning`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            buildscript {
                repositories {
                    maven {
                        url "$m2"
                    }
                }
            }
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            repositories {
                maven {
                    url "$m2"
                }
            }

            """.trimIndent()
        )

        val toml = """
                [versions]
                android = { strictly = "8.7.0" }

                [plugins]
                android-library = { id = "com.android.library", version.ref = "android" }

        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        val buildResult = GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
            .withPluginClasspath()
            .withDebug(true)
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            toml,
            libs
        )

        assertTrue(
            buildResult.output.contains(
                """
                    There are plugins using a rich version that could be updated:
                     - com.android.library (android-library ref:android) -> 8.7.1

                """.trimIndent()
            )
        )
    }

    @Test
    fun `available update for pinned library emits message`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            repositories {
                maven {
                    url "$m2"
                }
            }

            """.trimIndent()
        )

        val toml = """
                [libraries]
                # @pin
                androidx-activity-activity-compose = "androidx.activity:activity-compose:1.4.0"

        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        val buildResult = GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            toml,
            libs
        )

        assertTrue(
            buildResult.output.contains(
                """
                    There are updates available for pinned libraries in the version catalog:
                     - androidx.activity:activity-compose (androidx-activity-activity-compose) 1.4.0 -> 1.9.2

                """.trimIndent()
            )
        )
    }

    @Test
    fun `available update for pinned plugin emits message`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            buildscript {
                repositories {
                    maven {
                        url "$m2"
                    }
                }
            }
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            repositories {
                maven {
                    url "$m2"
                }
            }

            """.trimIndent()
        )

        val toml = """
                [plugins]
                # @pin
                android-library = "com.android.library:8.7.0"

        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        val buildResult = GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            toml,
            libs
        )

        assertTrue(
            buildResult.output.contains(
                """
                    There are updates available for pinned plugins in the version catalog:
                     - com.android.library (android-library) 8.7.0 -> 8.7.1

                """.trimIndent()
            )
        )
    }

    @Test
    fun `proceed plugin in catalog without version`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            buildscript {
                repositories {
                    maven {
                        url "$m2"
                    }
                }
            }
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            repositories {
                maven {
                    url "$m2"
                }
            }

            """.trimIndent()
        )

        val toml = """
                [plugins]
                android-library = { id = "com.android.library" }

        """.trimIndent()
        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            toml,
            libs
        )
    }

    @Test
    fun `sortByKey set to false does not sort the toml file`() {
        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

             versionCatalogUpdate {
                sortByKey = false
                 keep {
                    keepUnusedVersions = true
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

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
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
        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }


             versionCatalogUpdate {
                 keep {
                    keepUnusedVersions = true
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

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
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

            versionCatalogUpdate {
                keep {
                    keepUnusedVersions = true
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
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
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
    fun `keeps annotated versions in toml file`() {
        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            versionCatalogUpdate {
                keep {
                    keepUnusedVersions = false
                }
            }

            """.trimIndent()
        )

        val toml = """
            [versions]
            # @keep this because it's cool
            bbb = "1.2.3"
            aaa = "4.5.6"

            [libraries]
            aaa = "some:library:2.0"

            # plugins table comment
            [plugins]
            aaa = "another.id:1.0.0"

        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
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
               aaa = "some:library:2.0"

               # plugins table comment
               [plugins]
               aaa = "another.id:1.0.0"

            """.trimIndent(),
            libs
        )
    }

    @Test
    fun `pins annotated entries in toml file`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            buildscript {
                repositories {
                    maven {
                        url "$m2"
                    }
                }
            }
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            repositories {
                maven {
                    url "$m2"
                }
            }

            """.trimIndent()
        )

        val toml = """
            [versions]
            # @pin
            activity-compose = "1.4.0"

            [libraries]
            # @pinned
            activity-compose = {module = "androidx.activity:activity-compose", version.ref = "activity-compose"}

            [plugins]
            # @pin
            android-library = "com.android.library:8.7.0"

        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
            .withDebug(true)
            .withPluginClasspath()
            .build()

        val libs = File(tempDir.root, "gradle/libs.versions.toml").readText()

        assertEquals(
            """
                [versions]
                # @pin
                activity-compose = "1.4.0"

                [libraries]
                # @pinned
                activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }

                [plugins]
                # @pin
                android-library = "com.android.library:8.7.0"

            """.trimIndent(),
            libs
        )
    }

    @Test
    // repro for https://github.com/littlerobots/version-catalog-update-plugin/issues/61
    fun `unused pins should be ignored when generating pin warnings`() {

        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            """.trimIndent()
        )

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
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
            .withDebug(true)
            .withPluginClasspath()
            .build()
    }

    @Test
    fun `interactive mode stages changes`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            buildscript {
                repositories {
                    maven {
                        url "$m2"
                    }
                }
            }
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            repositories {
                maven {
                    url "$m2"
                }
            }

            """.trimIndent()
        )

        val toml = """
            [versions]
            activity-compose = "1.4.0"

            [libraries]
            test = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }

            [plugins]
            android-library = "com.android.library:8.7.0"
        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "--interactive", "-Pnl.littlerobots.vcu.resolver=true")
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
                # From version 1.4.0 --> 1.9.2
                test = "androidx.activity:activity-compose:1.9.2"

                [plugins]
                # From version 8.7.0 --> 8.7.1
                android-library = "com.android.library:8.7.1"
            """.trimIndent(),
            fileWithoutDateHeader
        )
    }

    @Test
    fun `interactive mode without updates does not create staging file`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            buildscript {
                repositories {
                    maven {
                        url "$m2"
                    }
                }
            }
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            repositories {
                maven {
                    url "$m2"
                }
            }

            """.trimIndent()
        )

        val toml = """
            [versions]
            activity-compose = "1.9.2"

            [libraries]
            test = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }

            [plugins]
            android-library = "com.android.library:8.7.1"
        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "--interactive", "-Pnl.littlerobots.vcu.resolver=true")
            .withPluginClasspath()
            .build()

        val stagingToml = File(tempDir.root, "gradle/libs.versions.updates.toml")
        assertFalse(stagingToml.exists())
    }

    @Test
    fun `interactive mode marks pinned changes`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            buildscript {
                repositories {
                    maven {
                        url "$m2"
                    }
                }
            }
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            repositories {
                maven {
                    url "$m2"
                }
            }

            """.trimIndent()
        )

        val toml = """
            [versions]
            activity-compose = "1.4.0"

            [libraries]
            # @pin
            test = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }

            [plugins]
            android-library = "com.android.library:8.7.0"
        """.trimIndent()

        File(tempDir.root, "gradle").mkdir()
        File(tempDir.root, "gradle/libs.versions.toml").writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "--interactive", "-Pnl.littlerobots.vcu.resolver=true")
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
                # @pinned version 1.4.0 --> 1.9.2
                #test = "androidx.activity:activity-compose:1.9.2"

                [plugins]
                # From version 8.7.0 --> 8.7.1
                android-library = "com.android.library:8.7.1"
            """.trimIndent(),
            fileWithoutDateHeader
        )
    }

    @Test
    fun `normal update fails when staging file is present`() {
        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            """.trimIndent()
        )

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
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
            .withPluginClasspath()
            .buildAndFail()

        assertTrue(result.output.contains("libs.versions.updates.toml exists, did you mean to run the versionCatalogApplyUpdates task to apply the updates?"))
    }

    @Test
    fun `keep all versions retains version order in catalog`() {
        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            versionCatalogUpdate {
                sortByKey = false
                keep.keepUnusedVersions.set(true)
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
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
            .withPluginClasspath()
            .build()

        assertEquals(
            toml,
            tomlFile.readText()
        )
    }

    @Test
    fun `kept version retains order in catalog`() {
        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            versionCatalogUpdate {
                sortByKey = false
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
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
            .withPluginClasspath()
            .build()

        assertEquals(
            toml,
            tomlFile.readText()
        )
    }

    @Test
    fun `retains order of tables in the version catalog`() {
        buildFile.writeText(
            """
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            versionCatalogUpdate {
                keep.keepUnusedVersions.set(true)
            }
            """.trimIndent()
        )

        val toml = """
            [bundles]
            bundle = ["test"]

            [plugins]
            myplugin = "myplugin:1.0"

            [libraries]
            test = { module = "androidx.activity:activity-compose", version.ref = "activity" }

            [versions]
            someVersion = "1.0.0"
            activity = "1.4.0"

        """.trimIndent()

        val tomlFile = File(tempDir.root, "gradle/libs.versions.toml")
        File(tempDir.root, "gradle").mkdir()
        tomlFile.writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
            .withPluginClasspath()
            .build()

        assertEquals(
            """
                [bundles]
                bundle = [
                    "test",
                ]

                [plugins]
                myplugin = "myplugin:1.0"

                [libraries]
                test = { module = "androidx.activity:activity-compose", version.ref = "activity" }

                [versions]
                activity = "1.4.0"
                someVersion = "1.0.0"

            """.trimIndent(),
            tomlFile.readText()
        )
    }

    @Test
    fun `version selector is invoked`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            buildscript {
                repositories {
                    maven {
                        url "$m2"
                    }
                }
            }
            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            versionCatalogUpdate {
                versionSelector {
                    it.candidate.version == "1.3.0-alpha01"
                }

            }

            repositories {
                maven {
                    url "$m2"
                }
            }

            """.trimIndent()
        )

        val toml = """
            [versions]
            activity = "1.4.0"

            [libraries]
            test = { module = "androidx.activity:activity-compose", version.ref = "activity" }

        """.trimIndent()

        val tomlFile = File(tempDir.root, "gradle/libs.versions.toml")
        File(tempDir.root, "gradle").mkdir()
        tomlFile.writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
            .withPluginClasspath()
            .build()

        // check that the fixed version is selected
        assertEquals(
            """
                [versions]
                activity = "1.3.0-alpha01"

                [libraries]
                test = { module = "androidx.activity:activity-compose", version.ref = "activity" }

            """.trimIndent(),
            tomlFile.readText()
        )
    }

    @Test
    fun `version selector is invoked for kts`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent
        buildFile.delete()
        val ktsBuildFile = tempDir.newFile("build.gradle.kts")
        ktsBuildFile.writeText(
            """
            import nl.littlerobots.vcu.plugin.versionSelector
            buildscript {
                repositories {
                    maven {
                        url = uri("$m2")
                    }
                }
            }
            plugins {
                id("nl.littlerobots.version-catalog-update")
            }

            versionCatalogUpdate {
              versionSelector {
                it.candidate.version == "1.3.0-alpha01"
              }
            }

            repositories {
                maven {
                   url = uri("$m2")
                }
            }

            """.trimIndent()
        )

        val toml = """
            [versions]
            activity = "1.4.0"

            [libraries]
            test = { module = "androidx.activity:activity-compose", version.ref = "activity" }

        """.trimIndent()

        val tomlFile = File(tempDir.root, "gradle/libs.versions.toml")
        File(tempDir.root, "gradle").mkdir()
        tomlFile.writeText(toml)

        GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "-Pnl.littlerobots.vcu.resolver=true")
            .withPluginClasspath()
            .build()

        // check that the fixed version is selected
        assertEquals(
            """
                [versions]
                activity = "1.3.0-alpha01"

                [libraries]
                test = { module = "androidx.activity:activity-compose", version.ref = "activity" }

            """.trimIndent(),
            tomlFile.readText()
        )
    }

    @Test
    fun `fails the build when there are updates`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            buildscript {
                repositories {
                    maven {
                       url = uri("$m2")
                    }
                }
            }

            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            repositories {
                maven {
                   url = uri("$m2")
                }
            }
            """.trimIndent()
        )

        val toml = """
            [versions]
            activity-compose = "1.4.0"

            [libraries]
            test = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }

            [plugins]
            android-library = "com.android.library:8.7.0"
        """.trimIndent()

        val tomlFile = File(tempDir.root, "gradle/libs.versions.toml")
        File(tempDir.root, "gradle").mkdir()
        tomlFile.writeText(toml)

        val result = GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "--check")
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()

        assertTrue(
            result.output.contains(
                """
            There are libraries that could be updated:
             - androidx.activity:activity-compose:1.4.0 (test) -> 1.9.2

            There are plugins that could be updated:
             - com.android.library:8.7.0 (android-library) -> 8.7.1

                """.trimIndent()
            )
        )
    }

    @Test
    fun `fails the build when there are updates unless pinned`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            buildscript {
                repositories {
                    maven {
                       url = uri("$m2")
                    }
                }
            }

            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            repositories {
                maven {
                   url = uri("$m2")
                }
            }
            """.trimIndent()
        )

        val toml = """
            [versions]
            activity-compose = "1.4.0"

            [libraries]
            # @pin
            test = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }

            [plugins]
            # @pin
            android-library = "com.android.library:8.7.0"
        """.trimIndent()

        val tomlFile = File(tempDir.root, "gradle/libs.versions.toml")
        File(tempDir.root, "gradle").mkdir()
        tomlFile.writeText(toml)

        val result = GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "--check")
            .withPluginClasspath()
            .withDebug(true)
            .build()

        assertTrue(
            result.output.contains(
                """
            There are updates available for pinned libraries in the version catalog:
             - androidx.activity:activity-compose (test) 1.4.0 -> 1.9.2
            There are updates available for pinned plugins in the version catalog:
             - com.android.library (android-library) 8.7.0 -> 8.7.1

                """.trimIndent()
            )
        )
    }

    @Test
    fun `fails the build when a specified library is out of date`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            buildscript {
                repositories {
                    maven {
                       url = uri("$m2")
                    }
                }
            }

            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            repositories {
                maven {
                   url = uri("$m2")
                }
            }
            """.trimIndent()
        )

        val toml = """
            [versions]
            activity-compose = "1.4.0"

            [libraries]
            test = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }

            [plugins]
            android-library = "com.android.library:8.7.0"
        """.trimIndent()

        val tomlFile = File(tempDir.root, "gradle/libs.versions.toml")
        File(tempDir.root, "gradle").mkdir()
        tomlFile.writeText(toml)

        val result = GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "--check", "--libraries", "test")
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()

        assertTrue(
            result.output.contains(
                """
            There are libraries that could be updated:
             - androidx.activity:activity-compose:1.4.0 (test) -> 1.9.2

                """.trimIndent()
            )
        )
    }

    @Test
    fun `fails the build when a specified plugin is out of date`() {
        val m2 = File(javaClass.getResource("/m2/m2.txt")!!.file).absoluteFile.parent

        buildFile.writeText(
            """
            buildscript {
                repositories {
                    maven {
                       url = uri("$m2")
                    }
                }
            }

            plugins {
                id "nl.littlerobots.version-catalog-update"
            }

            repositories {
                maven {
                   url = uri("$m2")
                }
            }
            """.trimIndent()
        )

        val toml = """
            [versions]
            activity-compose = "1.4.0"

            [libraries]
            test = { module = "androidx.activity:activity-compose", version.ref = "activity-compose" }

            [plugins]
            android-library = "com.android.library:8.7.0"
        """.trimIndent()

        val tomlFile = File(tempDir.root, "gradle/libs.versions.toml")
        File(tempDir.root, "gradle").mkdir()
        tomlFile.writeText(toml)

        val result = GradleRunner.create()
            .withProjectDir(tempDir.root)
            .withArguments("versionCatalogUpdate", "--check", "--plugins", "android-library")
            .withPluginClasspath()
            .withDebug(true)
            .buildAndFail()

        assertTrue(
            result.output.contains(
                """
            There are plugins that could be updated:
             - com.android.library:8.7.0 (android-library) -> 8.7.1

                """.trimIndent()
            )
        )
    }
}
