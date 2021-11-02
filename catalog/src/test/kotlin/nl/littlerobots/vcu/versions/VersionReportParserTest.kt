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
package nl.littlerobots.vcu.versions

import nl.littlerobots.vcu.VersionCatalogWriter
import nl.littlerobots.vcu.model.Library
import nl.littlerobots.vcu.model.Plugin
import nl.littlerobots.vcu.model.VersionDefinition
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringWriter

class VersionReportParserTest {
    @Test
    fun `converts current dependency to library`() {
        val report = """
            {
                "current": {
                    "dependencies": [
                        {
                            "group": "androidx.concurrent",
                            "userReason": null,
                            "version": "1.1.0",
                            "projectUrl": "https://developer.android.com/topic/libraries/architecture/index.html",
                            "name": "concurrent-futures-ktx"
                        } ]
                 }
            }
        """.trimIndent()
        val updater = VersionReportParser()

        val catalog = updater.generateCatalog(report.byteInputStream())

        assertEquals(1, catalog.libraries.size)
        assertEquals(
            Library(
                group = "androidx.concurrent",
                name = "concurrent-futures-ktx",
                version = VersionDefinition.Simple("1.1.0")
            ),
            catalog.libraries["androidx-concurrent-concurrent-futures-ktx"]
        )
    }

    @Test
    fun `converts outdated dependency to library`() {
        val report = """
            {
              "outdated": {
                "dependencies": [
                  {
                    "group": "org.jetbrains.kotlin",
                    "available": {
                      "release": null,
                      "milestone": "1.6.0-RC",
                      "integration": null
                    },
                    "userReason": null,
                    "version": "1.5.30",
                    "projectUrl": "https://kotlinlang.org/",
                    "name": "kotlin-scripting-compiler-embeddable"
                  }
                ]
              }
            }
        """.trimIndent()
        val updater = VersionReportParser()

        val catalog = updater.generateCatalog(report.byteInputStream())

        assertEquals(1, catalog.libraries.size)
        assertEquals(
            Library(
                group = "org.jetbrains.kotlin",
                name = "kotlin-scripting-compiler-embeddable",
                version = VersionDefinition.Simple("1.6.0-RC")
            ),
            catalog.libraries["org-jetbrains-kotlin-kotlin-scripting-compiler-embeddable"]
        )
    }

    @Test
    fun `reports plugin updates`() {
        val report = """
            {
                "current": {
                    "dependencies": [
                        {
                "group": "com.github.ben-manes.versions",
                "userReason": null,
                "version": "0.39.0",
                "projectUrl": null,
                "name": "com.github.ben-manes.versions.gradle.plugin"
            } ]
                 }
            }
        """.trimIndent()
        val updater = VersionReportParser()

        val catalog = updater.generateCatalog(report.byteInputStream())

        val writer = VersionCatalogWriter()
        val output = StringWriter()
        writer.write(catalog, output)

        assertEquals(1, catalog.plugins.size)
        assertEquals(
            Plugin(
                id = "com.github.ben-manes.versions",
                version = VersionDefinition.Simple("0.39.0")
            ),
            catalog.plugins["com-github-ben-manes-versions"]
        )
    }
}
