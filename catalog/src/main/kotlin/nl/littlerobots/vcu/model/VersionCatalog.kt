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

data class VersionCatalog(
    val versions: Map<String, String>,
    val libraries: Map<String, Library>,
    val bundles: Map<String, List<String>>,
    val plugins: Map<String, Plugin>
)

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
            if (currentLib.version == VersionDefinition.Unspecified) {
                it to entry.value.copy(version = VersionDefinition.Unspecified)
            } else {
                it to entry.value
            }
        } ?: if (addNew) (entry.key to entry.value) else null
    }.toMap()

    val libraries = this.libraries.toMutableMap().apply {
        putAll(updatedLibraries)
        if (purge) {
            val modules = catalog.libraries.map { it.value.module }
            val purgeKeys = libraryKeys.toMutableMap().apply {
                keys.removeAll(modules)
            }
            keys.removeAll(purgeKeys.values)
        }
    }

    val pluginKeys = this.plugins.map {
        it.value.id to it.key
    }.toMap()

    // for plugins find the id in the current map
    val pluginUpdates = catalog.plugins.mapNotNull { entry ->
        pluginKeys[entry.value.id]?.let {
            it to entry.value
        } ?: if (addNew) entry.key to entry.value else null
    }.toMap()

    val plugins = this.plugins.toMutableMap().apply {
        putAll(pluginUpdates)
        // no pruning here until we can get the plugins in a more reliable way
    }

    val versions = this.versions.toMutableMap()

    retainCurrentVersionReferences(versions, libraries, plugins)

    // collect this.libraries references that point to a single group
    // check libraries for possible groupings (= same group + same version)
    // reuse if reference exist or create if group size > 1
    // collect all version refs that point to a single group with all the libs using the same version
    collectVersionReferenceForGroups(libraries, versions)

    return this.copy(
        versions = versions,
        libraries = libraries,
        plugins = plugins
    ).updateBundles()
        .pruneVersions()
}

private fun VersionCatalog.retainCurrentVersionReferences(
    newVersions: MutableMap<String, String>,
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
            (it.entry.version as VersionDefinition.Simple).version
        }.maxBy {
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

private fun VersionCatalog.collectVersionReferenceForGroups(
    libraries: MutableMap<String, Library>,
    versions: MutableMap<String, String>
) {
    val groupIdVersionRef = this.libraries.values.filter {
        it.version is VersionDefinition.Reference
    }.groupBy {
        (it.version as VersionDefinition.Reference).ref
    }.filterValues { libs ->
        val firstLib = libs.first()
        libs.all { it.version == firstLib.version && it.group == firstLib.group }
    }.map {
        it.value.first().group to it.key
    }.toMap()

    libraries.values.filter {
        it.version is VersionDefinition.Simple
    }.groupBy {
        it.group
    }.filterValues { libs ->
        val firstLib = libs.first()
        // all libraries in the group have the same (simple) version
        libs.all { it.version == firstLib.version }
    }.forEach { entry ->
        val existing = groupIdVersionRef[entry.key]
        val reference = when {
            existing == null && entry.value.size > 1 -> entry.key.replace('.', '-')
            else -> existing
        }
        reference?.let { ref ->
            versions[ref] = (entry.value.first().version as VersionDefinition.Simple).version
            for (lib in entry.value) {
                val libEntry = libraries.entries.first { it.value == lib }
                libraries[libEntry.key] = lib.copy(version = VersionDefinition.Reference(ref))
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
        keys.retainAll(versionReferences)
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
