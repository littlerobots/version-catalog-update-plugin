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
            it to entry.value
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

    val versions = this.versions.toMutableMap()

    // collect this.libraries references that point to a single group
    // check libraries for possible groupings (= same group + same version)
    // reuse if reference exist or create if group size > 1

    // collect all version refs that point to a single group with all the libs using the same version
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

    return this.copy(
        versions = versions,
        libraries = libraries,
        plugins = plugins
    ).updateBundles()
        .pruneVersions()
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
    return this.copy(bundles = bundles.mapValues {
        it.value.intersect(libraries.keys).toList().sorted()
    }.filter {
        it.value.isNotEmpty()
    })
}

fun VersionCatalog.sortKeys(): VersionCatalog {
    return copy(
        versions = versions.toSortedMap(),
        libraries = libraries.toSortedMap(),
        bundles = bundles.toSortedMap(),
        plugins = plugins.toSortedMap()
    )
}