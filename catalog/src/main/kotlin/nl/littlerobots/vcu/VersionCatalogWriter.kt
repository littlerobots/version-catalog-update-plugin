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

import nl.littlerobots.vcu.model.HasVersion
import nl.littlerobots.vcu.model.Library
import nl.littlerobots.vcu.model.Plugin
import nl.littlerobots.vcu.model.VersionCatalog
import nl.littlerobots.vcu.model.VersionDefinition
import nl.littlerobots.vcu.toml.TABLE_BUNDLES
import nl.littlerobots.vcu.toml.TABLE_LIBRARIES
import nl.littlerobots.vcu.toml.TABLE_PLUGINS
import nl.littlerobots.vcu.toml.TABLE_VERSIONS
import java.io.PrintWriter
import java.io.Writer

private const val BUNDLE_INDENT = 4

class VersionCatalogWriter {
    fun write(versionCatalog: VersionCatalog, writer: Writer, commentEntry: (HasVersion) -> Boolean = { false }) {
        val printWriter = PrintWriter(writer)
        var shouldAddNewLine = false
        for (i in versionCatalog.tableOrder.indices) {
            when (versionCatalog.tableOrder[i]) {
                TABLE_VERSIONS -> {
                    if (versionCatalog.versions.isNotEmpty()) {
                        writeNewLine(printWriter, shouldAddNewLine)
                        writeVersions(versionCatalog, printWriter)
                        shouldAddNewLine = true
                    }
                }

                TABLE_LIBRARIES -> {
                    if (versionCatalog.libraries.isNotEmpty()) {
                        writeNewLine(printWriter, shouldAddNewLine)
                        writeLibraries(versionCatalog, printWriter, commentEntry)
                        shouldAddNewLine = true
                    }
                }

                TABLE_BUNDLES -> {
                    if (versionCatalog.bundles.isNotEmpty()) {
                        writeNewLine(printWriter, shouldAddNewLine)
                        writeBundles(versionCatalog, printWriter)
                        shouldAddNewLine = true
                    }
                }

                TABLE_PLUGINS -> {
                    if (versionCatalog.plugins.isNotEmpty()) {
                        writeNewLine(printWriter, shouldAddNewLine)
                        writePlugins(versionCatalog, printWriter, commentEntry)
                        shouldAddNewLine = true
                    }
                }

                else -> error("Unknown table ${versionCatalog.tableOrder[i]}")
            }
        }
        printWriter.flush()
        printWriter.close()
    }

    private fun writePlugins(
        versionCatalog: VersionCatalog,
        printWriter: PrintWriter,
        commentEntry: (HasVersion) -> Boolean
    ) {
        for (comment in versionCatalog.pluginComments.tableComments) {
            printWriter.println(comment)
        }
        printWriter.println("[plugins]")
        for (plugin in versionCatalog.plugins) {
            for (comment in versionCatalog.pluginComments.getCommentsForKey(plugin.key)) {
                printWriter.println(comment)
            }
            if (commentEntry(plugin.value)) {
                printWriter.print("#")
            }
            printWriter.println("${plugin.key} = ${formatPlugin(plugin.value)}")
        }
    }

    private fun writeBundles(versionCatalog: VersionCatalog, printWriter: PrintWriter) {
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

    private fun writeLibraries(
        versionCatalog: VersionCatalog,
        printWriter: PrintWriter,
        commentEntry: (HasVersion) -> Boolean
    ) {
        for (line in versionCatalog.libraryComments.tableComments) {
            printWriter.println(line)
        }
        printWriter.println("[libraries]")
        for (library in versionCatalog.libraries) {
            for (comment in versionCatalog.libraryComments.getCommentsForKey(library.key)) {
                printWriter.println(comment)
            }
            if (commentEntry(library.value)) {
                printWriter.print("#")
            }
            printWriter.println("""${library.key} = ${formatLibrary(library.value)}""")
        }
    }

    private fun writeNewLine(writer: PrintWriter, shouldAddNewLine: Boolean) {
        if (shouldAddNewLine) {
            writer.println()
        }
    }

    private fun writeVersions(versionCatalog: VersionCatalog, printWriter: PrintWriter) {
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
                    append("${condition.key} = ${condition.value.formatValue()}, ")
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
                "${it.key} = ${it.value.formatValue()}"
            }
        }

        else -> throw IllegalStateException("Invalid version definition $versionDefinition")
    }

    private fun Any.formatValue(): String {
        return when (this) {
            is List<*> -> joinToString(", ", prefix = "[", postfix = "]") { "\"${it}\"" }
            else -> "\"${this}\""
        }
    }
}
