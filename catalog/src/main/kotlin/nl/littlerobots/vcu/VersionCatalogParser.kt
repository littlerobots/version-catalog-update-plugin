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

import com.fasterxml.jackson.dataformat.toml.TomlMapper
import nl.littlerobots.vcu.model.Comments
import nl.littlerobots.vcu.model.Library
import nl.littlerobots.vcu.model.Plugin
import nl.littlerobots.vcu.model.VersionCatalog
import nl.littlerobots.vcu.model.VersionDefinition
import java.io.BufferedReader
import java.io.InputStream
import java.io.StringReader

private val TABLE_REGEX = Regex("\\[\\s?(versions|libraries|bundles|plugins)\\s?].*")
private val KEY_REGEX = Regex("^(.*?)=.*")
private const val TABLE_VERSIONS = "versions"
private const val TABLE_LIBRARIES = "libraries"
private const val TABLE_BUNDLES = "bundles"
private const val TABLE_PLUGINS = "plugins"

class VersionCatalogParser {

    @Suppress("UNCHECKED_CAST")
    fun parse(inputStream: InputStream): VersionCatalog {
        val content = inputStream.bufferedReader().use {
            it.readText()
        }
        val mapper = TomlMapper()
        val catalog = mapper.readValue(content, Map::class.java) as Map<String, Any>

        val versions = catalog.getTable(TABLE_VERSIONS)?.toVersionDefinitionMap() ?: emptyMap()
        val libraries = catalog.getTable(TABLE_LIBRARIES)?.toDependencyMap() ?: emptyMap()
        val bundles = catalog.getTable(TABLE_BUNDLES)?.toTypedMap<List<String>>() ?: emptyMap()
        val plugins = catalog.getTable(TABLE_PLUGINS)?.toPluginDependencyMap() ?: emptyMap()

        return processComments(content, VersionCatalog(versions, libraries, bundles, plugins))
    }

    private fun processComments(content: String, versionCatalog: VersionCatalog): VersionCatalog {
        val comments = mutableMapOf<String, Comments>()
        val currentComment = mutableListOf<String>()
        var currentTable: String? = null
        val reader = BufferedReader(StringReader(content))
        do {
            val line = reader.readLine() ?: break
            when {
                line.startsWith("#") -> currentComment.add(line)
                line.trim().matches(TABLE_REGEX) -> {
                    val table = TABLE_REGEX.find(line)!!.groupValues[1]
                    currentTable = table
                    comments[table] = Comments(currentComment.toList(), emptyMap())
                    currentComment.clear()
                }
                line.matches(KEY_REGEX) && currentTable != null && currentComment.isNotEmpty() -> {
                    val key = KEY_REGEX.find(line)!!.groupValues[1].trim()
                    val currentComments = comments[currentTable] ?: error("Should have an entry")
                    comments[currentTable] =
                        currentComments.copy(entryComments = currentComments.entryComments + mapOf(key to currentComment.toList()))
                    currentComment.clear()
                }
            }
        } while (true)
        return versionCatalog.copy(
            versionComments = comments[TABLE_VERSIONS] ?: Comments(),
            libraryComments = comments[TABLE_LIBRARIES] ?: Comments(),
            bundleComments = comments[TABLE_BUNDLES] ?: Comments(),
            pluginComments = comments[TABLE_PLUGINS] ?: Comments()
        )
    }
}

private fun Map<String, Any>.toPluginDependencyMap(): Map<String, Plugin> {
    return toTypedMap<Any>().mapValues { entry ->
        when (val value = entry.value) {
            is String -> {
                val components = value.split(":")
                if (components.size != 2) {
                    throw IllegalStateException("Invalid plugin definition for ${entry.key}: ${entry.value}")
                }
                val (id, version) = components
                Plugin(id, VersionDefinition.Simple(version))
            }
            is Map<*, *> -> {
                val id = value["id"] as? String
                val version = value["version"]?.toDependencyVersion() ?: VersionDefinition.Unspecified

                if (id == null) {
                    throw IllegalStateException("No plugin id defined for ${entry.key}")
                }
                Plugin(id, version)
            }
            else -> throw IllegalStateException("Unsupported type ${value::class.java}")
        }
    }
}

private fun Map<String, Any>.toDependencyMap(): Map<String, Library> = toTypedMap<Any>().mapValues { entry ->
    when (val value = entry.value) {
        is String -> {
            val components = value.split(":")
            if (components.size != 3) {
                throw IllegalStateException("Invalid dependency definition for ${entry.key}: ${entry.value}")
            }
            val (group, name, version) = components
            Library(
                group = group,
                name = name,
                version = VersionDefinition.Simple(version)
            )
        }
        is Map<*, *> -> {
            val module = value["module"] as? String
            val group = value["group"] as? String
            val name = value["name"] as? String
            val version = value["version"]?.let {
                it.toDependencyVersion()
                    ?: throw IllegalStateException("Could not parse version or version.ref for ${entry.key}")
            } ?: VersionDefinition.Unspecified
            if (module == null && (group == null || name == null)) {
                throw IllegalStateException("${entry.key} should define module or group/name")
            }
            if (module != null && (group != null || name != null)) {
                throw IllegalStateException("${entry.key} should only define module or group/name combination")
            }
            if (module != null) {
                Library(module, version)
            } else {
                Library(requireNotNull(group), requireNotNull(name), version)
            }
        }
        else -> throw IllegalStateException("Unsupported type ${value::class.java}")
    }
}

private fun Map<String, Any>.toVersionDefinitionMap(): Map<String, VersionDefinition> {
    return mapValues {
        when (val version = it.value.toDependencyVersion()) {
            null -> throw IllegalStateException("Unable to parse version ${it.value}")
            is VersionDefinition.Reference -> throw IllegalStateException("Version specified cannot be a reference")
            else -> version
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun Any.toDependencyVersion(): VersionDefinition? = when (this) {
    is String -> VersionDefinition.Simple(this)
    is Map<*, *> -> {
        val stringMap = this as? Map<String, String> ?: throw IllegalStateException("Expected string map")
        if (size == 1 && stringMap.containsKey("ref")) {
            VersionDefinition.Reference(stringMap["ref"] as String)
        } else {
            VersionDefinition.Condition(stringMap)
        }
    }
    else -> null
}

@Suppress("UNCHECKED_CAST")
private fun Map<String, Any>.getTable(key: String) = get(key) as? Map<String, Any>

private inline fun <reified T> Map<String, Any>.toTypedMap(): Map<String, T> = map {
    if (it.value !is T) {
        throw IllegalStateException("Expected ${T::class.java} for key ${it.key}, but was ${it.value::class.java}")
    }
    it.key to it.value as T
}.toMap()
