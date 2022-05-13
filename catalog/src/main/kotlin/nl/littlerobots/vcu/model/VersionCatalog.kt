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
package nl.littlerobots.vcu.model

import nl.littlerobots.vcu.toml.toTomlKey

data class VersionCatalog(
    val versions: Map<String, VersionDefinition>,
    val libraries: Map<String, Library>,
    val bundles: Map<String, List<String>>,
    val plugins: Map<String, Plugin>
) {
    /**
     * The effective version definition of a [HasVersion], resolving [VersionDefinition.Reference]
     */
    internal val HasVersion.resolvedVersion: VersionDefinition
        get() {
            if (versions.isEmpty()) {
                return version
            }
            val version = version
            return if (version is VersionDefinition.Reference) {
                versions[version.ref] ?: error("$this references undeclared version: ${version.ref}")
            } else {
                version
            }
        }
}

/**
 * Create an updated [VersionCatalog] by combining with [catalog]
 *
 * @param catalog the catalog to use for updating
 * @param addNew whether to add new entries to the catalog, defaults to false
 * @param purge whether to remove unused entries from the catalog, defaults to true
 */
fun VersionCatalog.updateFrom(
    catalog: VersionCatalog,
    addNew: Boolean = false,
    purge: Boolean = true
): VersionCatalog {
    // Note that in theory there could be multiple mappings for the same module, those are collapsed here
    val libraryKeys = this.libraries.map { it.value.module to it.key }.toMap()
    val updatedLibraries = catalog.libraries.mapNotNull { entry ->
        libraryKeys[entry.value.module]?.let {
            val currentLib = this.libraries[it]!!
            when (currentLib.resolvedVersion) {
                VersionDefinition.Unspecified -> {
                    it to entry.value.copy(version = VersionDefinition.Unspecified)
                }
                is VersionDefinition.Condition -> {
                    // keep the version condition
                    it to entry.value.copy(version = currentLib.version)
                }
                else -> {
                    it to entry.value
                }
            }
        } ?: if (addNew) (entry.key to entry.value) else null
    }.toMap()

    val libraries = this.libraries.toMutableMap().apply {
        putAll(updatedLibraries)
        if (purge) {
            val modules = catalog.libraries.map { it.value.module }.toSet()
            val purgeKeys = libraryKeys.toMutableMap().apply {
                keys.removeAll(modules)
            }
            keys.removeAll(purgeKeys.values.toSet())
        }
    }

    val pluginKeys = this.plugins.map {
        it.value.id to it.key
    }.toMap()

    // for plugins find the id in the current map
    val pluginUpdates = catalog.plugins.mapNotNull { entry ->
        pluginKeys[entry.value.id]?.let {
            val currentPlugin = requireNotNull(plugins[it])
            when (currentPlugin.resolvedVersion) {
                // keep condition reference
                is VersionDefinition.Condition -> it to currentPlugin
                else -> it to entry.value
            }
        } ?: if (addNew) entry.key to entry.value else null
    }.toMap()

    val plugins = this.plugins.toMutableMap().apply {
        putAll(pluginUpdates)
        if (purge) {
            keys.retainAll(pluginUpdates.keys)
        }
    }

    val versions = this.versions.toMutableMap()

    retainCurrentVersionReferences(versions, libraries, plugins)

    // collect this.libraries references that point to a single group
    // check libraries for possible groupings (= same group + same version)
    // reuse if reference exist or create if group size > 1
    // collect all version refs that point to a single group with all the libs using the same version
    collectVersionReferenceForGroups(libraries, versions)

    val result = this.copy(
        versions = versions,
        libraries = libraries,
        plugins = plugins
    ).updateBundles()

    return if (purge) result.pruneVersions() else result
}

private fun VersionCatalog.retainCurrentVersionReferences(
    newVersions: MutableMap<String, VersionDefinition>,
    newLibraries: MutableMap<String, Library>,
    newPlugins: MutableMap<String, Plugin>
) {
    // all library, plugin keys using a version (before)
    val versionsToCurrentKeys = this.versions.mapValues { version ->
        // find the library & plugin keys referencing this value
        this.libraries.filterValues {
            it.version is VersionDefinition.Reference && it.version.ref == version.key
        }.map {
            Key(it.key, it.value)
        } + this.plugins.filterValues {
            it.version is VersionDefinition.Reference && it.version.ref == version.key
        }.map {
            Key(it.key, it.value)
        }
    }

    versionsToCurrentKeys.entries.forEach { entry ->
        // select the entry with the most referenced keys if there's more than one
        val bestEntry = entry.value.mapNotNull { key ->
            when (key.entry) {
                is Library -> newLibraries[key.key]?.let { Key(key.key, it) }
                is Plugin -> newPlugins[key.key]?.let { Key(key.key, it) }
                else -> null
            }
        }.filter {
            it.entry.version is VersionDefinition.Simple
        }.groupBy {
            (it.entry.version as VersionDefinition.Simple)
        }.maxByOrNull {
            it.value.size
        }

        if (bestEntry != null) {
            newVersions[entry.key] = bestEntry.key
            bestEntry.value.forEach {
                when (it.entry) {
                    is Library -> newLibraries[it.key] = it.entry.copy(version = VersionDefinition.Reference(entry.key))
                    is Plugin -> newPlugins[it.key] = it.entry.copy(version = VersionDefinition.Reference(entry.key))
                }
            }
        }
    }
}

/**
 * Updates plugins for this catalog
 *
 * This will add any plugins matching the module id in the libraries section to plugins if that plugin
 * is not declared yet in the catalog.
 *
 * @param plugins a map of pluginId to its Maven module
 * @return an updated version catalog
 */
fun VersionCatalog.mapPlugins(
    plugins: Map<String, String>
): VersionCatalog {
    if (plugins.isEmpty()) {
        return this
    }

    // libraries that are actually plugins
    val libraryByModule = libraries.filter {
        plugins.containsValue(it.value.module)
    }.map {
        it.value.module to it.value
    }.toMap()

    // already known plugin ids which we won't touch, assuming those are discovered by other means
    val pluginIds = this.plugins.values.map { it.id }.toSet()

    val addedPlugins = plugins.toMutableMap().apply { keys.removeAll(pluginIds) }
    val newPlugins = this.plugins + addedPlugins.mapNotNull { entry ->
        libraryByModule[entry.value]?.version?.let {
            entry.key.toTomlKey() to Plugin(entry.key, it)
        }
    }

    return copy(plugins = newPlugins)
}

private fun VersionCatalog.collectVersionReferenceForGroups(
    libraries: MutableMap<String, Library>,
    versions: MutableMap<String, VersionDefinition>
) {
    val librariesByGroup = libraries.values.groupBy { it.group }.filter { it.value.size > 1 }
    val resolvedVersions = librariesByGroup.mapValues { entry ->
        entry.value.map {
            it.copy(version = it.resolvedVersion(copy(versions = versions)))
        }
    }
    val librariesWithoutVersionRef = libraries.filterValues {
        it.version is VersionDefinition.Simple && librariesByGroup.containsKey(it.group)
    }

    val newVersions = mutableMapOf<String, VersionDefinition.Reference>()

    for (library in librariesWithoutVersionRef) {
        val group = resolvedVersions[library.value.group] ?: throw IllegalStateException()
        val groupVersion = group.first().version as VersionDefinition.Simple
        if (group.all { it.version == groupVersion } && library.value.version == groupVersion) {
            val versionRef = librariesByGroup[library.value.group]?.firstOrNull {
                it.version is VersionDefinition.Reference
            }?.version as? VersionDefinition.Reference ?: newVersions[library.value.group]

            if (versionRef == null) {
                val versionRefKey = library.value.group.replace(".", "-")
                if (versions[versionRefKey] == null) {
                    versions[versionRefKey] = VersionDefinition.Simple(groupVersion.version)
                    libraries[library.key] = library.value.copy(version = VersionDefinition.Reference(versionRefKey))
                    newVersions[library.value.group] = VersionDefinition.Reference(versionRefKey)
                } // else bail out and leave as a normal version
            } else {
                libraries[library.key] = library.value.copy(version = versionRef)
            }
        }
    }
}

internal fun VersionCatalog.pruneVersions(): VersionCatalog {
    if (this.versions.isEmpty()) {
        return this
    }

    val versionReferences = this.libraries.values.filter {
        it.version is VersionDefinition.Reference
    }.map {
        (it.version as VersionDefinition.Reference).ref
    } + this.plugins.values.filter {
        it.version is VersionDefinition.Reference
    }.map {
        (it.version as VersionDefinition.Reference).ref
    }.distinct()

    val versions = this.versions.toMutableMap().apply {
        keys.retainAll(versionReferences.toSet())
    }
    return copy(versions = versions)
}

internal fun VersionCatalog.updateBundles(): VersionCatalog {
    if (this.bundles.isEmpty()) {
        return this
    }
    return this.copy(
        bundles = bundles.mapValues {
            it.value.intersect(libraries.keys).toList().sorted()
        }.filter {
            it.value.isNotEmpty()
        }
    )
}

fun VersionCatalog.resolveVersions(): VersionCatalog {
    if (this.versions.isEmpty()) {
        return this
    }
    return copy(
        libraries = libraries.mapValues {
            it.value.copy(version = it.value.resolvedVersion)
        },
        plugins = plugins.mapValues {
            it.value.copy(version = it.value.resolvedVersion)
        }
    )
}

private data class Key<T : HasVersion>(
    val key: String,
    val entry: T
)

fun VersionCatalog.sortKeys(): VersionCatalog {
    return copy(
        versions = versions.toSortedMap(),
        libraries = libraries.toSortedMap(),
        bundles = bundles.toSortedMap(),
        plugins = plugins.toSortedMap()
    )
}
