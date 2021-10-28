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
package nl.littlerobots.vcu

import nl.littlerobots.vcu.model.Library
import nl.littlerobots.vcu.model.Plugin
import nl.littlerobots.vcu.model.VersionCatalog
import nl.littlerobots.vcu.model.VersionDefinition
import java.io.PrintWriter
import java.io.Writer

class VersionCatalogWriter {
    fun write(versionCatalog: VersionCatalog, writer: Writer) {
        val printWriter = PrintWriter(writer)
        if (versionCatalog.versions.isNotEmpty()) {
            printWriter.println("[versions]")
            for (version in versionCatalog.versions) {
                printWriter.println("""${version.key} = "${version.value}"""")
            }
        }
        if (versionCatalog.libraries.isNotEmpty()) {
            if (versionCatalog.versions.isNotEmpty()) {
                printWriter.println()
            }
            printWriter.println("[libraries]")
            for (library in versionCatalog.libraries) {
                printWriter.println("""${library.key} = ${formatLibrary(library.value)}""")
            }
        }
        if (versionCatalog.bundles.isNotEmpty()) {
            if (versionCatalog.versions.isNotEmpty() || versionCatalog.libraries.isNotEmpty()) {
                printWriter.println()
            }
            printWriter.println("[bundles]")
            for (bundle in versionCatalog.bundles) {
                printWriter.println(
                    "${bundle.key} = [${
                    bundle.value.joinToString(
                        ", "
                    ) { "\"${it}\"" }
                    }]"
                )
            }
        }
        if (versionCatalog.plugins.isNotEmpty()) {
            if (versionCatalog.versions.isNotEmpty() || versionCatalog.bundles.isNotEmpty() || versionCatalog.libraries.isNotEmpty()) {
                printWriter.println()
            }
            printWriter.println("[plugins]")
            for (plugin in versionCatalog.plugins) {
                printWriter.println("${plugin.key} = ${formatPlugin(plugin.value)}")
            }
        }

        printWriter.flush()
        printWriter.close()
    }

    private fun formatPlugin(plugin: Plugin): String = when (plugin.version) {
        is VersionDefinition.Simple -> "\"${plugin.id}:${plugin.version.version}\""
        is VersionDefinition.Reference -> "{ id = \"${plugin.id}\", version.ref = \"${plugin.version.ref}\" }"
        is VersionDefinition.Condition -> {
            StringBuilder("{ id = \"${plugin.id}\", version = { ").apply {
                val conditions = plugin.version.definition
                for (condition in conditions) {
                    append("${condition.key} = \"${condition.value}\", ")
                }
                setLength(length - 2)
                append(" } }")
            }.toString()
        }
        is VersionDefinition.Unspecified -> "{ id = \"${plugin.id}\" }"
    }

    /**
     * Format library dependency notation to the shortest version possible
     * @param library the library definition
     * @return the version catalog library value
     */
    private fun formatLibrary(library: Library): String = when (library.version) {
        is VersionDefinition.Simple -> "\"${library.module}:${library.version.version}\""
        is VersionDefinition.Reference -> """{ module = "${library.module}", version.ref = "${library.version.ref}" }"""
        is VersionDefinition.Condition -> {
            StringBuilder("""{ module = "${library.module}", version = { """).apply {
                val conditions = library.version.definition
                for (condition in conditions) {
                    append("${condition.key} = \"${condition.value}\", ")
                }
                setLength(length - 2)
                append(" } }")
            }.toString()
        }
        is VersionDefinition.Unspecified -> "{ module = \"${library.module}\" }"
    }
}
