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

private const val BUNDLE_INDENT = 4

class VersionCatalogWriter {
    fun write(versionCatalog: VersionCatalog, writer: Writer) {
        val printWriter = PrintWriter(writer)
        if (versionCatalog.versions.isNotEmpty()) {
            for (line in versionCatalog.versionComments.tableComments) {
                printWriter.println(line)
            }
            printWriter.println("[versions]")
            for (version in versionCatalog.versions) {
                for (comment in versionCatalog.versionComments.getCommentsForKey(version.key)) {
                    printWriter.println(comment)
                }
                printWriter.println("""${version.key} = ${formatVersion(version.value)}""")
            }
        }
        if (versionCatalog.libraries.isNotEmpty()) {
            if (versionCatalog.versions.isNotEmpty()) {
                printWriter.println()
            }
            for (line in versionCatalog.libraryComments.tableComments) {
                printWriter.println(line)
            }
            printWriter.println("[libraries]")
            for (library in versionCatalog.libraries) {
                for (comment in versionCatalog.libraryComments.getCommentsForKey(library.key)) {
                    printWriter.println(comment)
                }
                printWriter.println("""${library.key} = ${formatLibrary(library.value)}""")
            }
        }
        if (versionCatalog.bundles.isNotEmpty()) {
            if (versionCatalog.versions.isNotEmpty() || versionCatalog.libraries.isNotEmpty()) {
                printWriter.println()
            }
            for (comment in versionCatalog.bundleComments.tableComments) {
                printWriter.println(comment)
            }
            printWriter.println("[bundles]")
            for (bundle in versionCatalog.bundles) {
                for (comment in versionCatalog.bundleComments.getCommentsForKey(bundle.key)) {
                    printWriter.println(comment)
                }
                val bundleStart = "${bundle.key} = ["
                printWriter.println(
                    bundle.value.joinToString(
                        prefix = bundleStart + System.lineSeparator() + " ".repeat(BUNDLE_INDENT),
                        separator = System.lineSeparator() + " ".repeat(
                            BUNDLE_INDENT
                        ),
                        postfix = System.lineSeparator() + "]"
                    ) { "\"$it\"," }
                )
            }
        }
        if (versionCatalog.plugins.isNotEmpty()) {
            if (versionCatalog.versions.isNotEmpty() || versionCatalog.bundles.isNotEmpty() || versionCatalog.libraries.isNotEmpty()) {
                printWriter.println()
            }
            for (comment in versionCatalog.pluginComments.tableComments) {
                printWriter.println(comment)
            }
            printWriter.println("[plugins]")
            for (plugin in versionCatalog.plugins) {
                for (comment in versionCatalog.pluginComments.getCommentsForKey(plugin.key)) {
                    printWriter.println(comment)
                }
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

    private fun formatVersion(versionDefinition: VersionDefinition): String = when (versionDefinition) {
        is VersionDefinition.Simple -> "\"${versionDefinition.version}\""
        is VersionDefinition.Condition -> {
            versionDefinition.definition.entries.joinToString(prefix = "{ ", postfix = " }", separator = ", ") {
                "${it.key} = \"${it.value}\""
            }
        }
        else -> throw IllegalStateException("Invalid version definition $versionDefinition")
    }
}

private fun List<String>.toQuotedList() = joinToString(separator = ", ") { "\"${it}\"" }
