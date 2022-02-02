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

import nl.littlerobots.vcu.VersionCatalogParser
import nl.littlerobots.vcu.VersionCatalogWriter
import nl.littlerobots.vcu.model.Library
import nl.littlerobots.vcu.model.Plugin
import nl.littlerobots.vcu.model.VersionCatalog
import nl.littlerobots.vcu.model.VersionDefinition
import nl.littlerobots.vcu.model.mapPlugins
import nl.littlerobots.vcu.model.resolveSimpleVersionReference
import nl.littlerobots.vcu.model.resolveVersions
import nl.littlerobots.vcu.model.resolvedVersion
import nl.littlerobots.vcu.model.sortKeys
import nl.littlerobots.vcu.model.updateFrom
import nl.littlerobots.vcu.versions.VersionReportParser
import nl.littlerobots.vcu.versions.model.Dependency
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import java.io.File
import java.util.jar.JarFile
import javax.inject.Inject

private const val PROPERTIES_SUFFIX = ".properties"

abstract class VersionCatalogUpdateTask @Inject constructor(
    private val extension: VersionCatalogUpdateExtension
) :
    DefaultTask() {
    @get:InputFile
    abstract val reportJson: RegularFileProperty

    @get:OutputFiles
    @get:Optional
    abstract val catalogFile: Property<ConfigurableFileCollection>

    @set:Option(option = "create", description = "Create libs.versions.toml based on current dependencies")
    @get:Internal
    abstract var createCatalog: Boolean

    @get:Input
    abstract val pins: Property<PinsConfigurationInput>

    // @get:Input
    @get:Internal
    abstract val keep: Property<KeepConfigurationInput>

    private var addDependencies: Boolean? = null

    @Option(option = "add", description = "Add new dependencies in the toml file")
    fun setAddDependenciesOption(add: Boolean) {
        this.addDependencies = add
    }

    private val pinRefs by lazy {
        pins.orNull?.getVersionCatalogRefs() ?: emptySet()
    }

    private val keepRefs by lazy {
        keep.orNull?.getVersionCatalogRefs() ?: emptySet()
    }

    @TaskAction
    fun updateCatalog() {
        val reportParser = VersionReportParser()

        val versionsReportResult =
            reportParser.generateCatalog(reportJson.get().asFile.inputStream(), useLatestVersions = !createCatalog)
        val catalogFromDependencies = versionsReportResult.catalog
        val catalogFiles = catalogFile.get().files

        for (file in catalogFiles) {
            val currentCatalog = if (catalogFiles.first().exists()) {
                if (createCatalog) {
                    throw GradleException("${catalogFiles.first()} already exists and cannot be created from scratch.")
                }
                val catalogParser = VersionCatalogParser()
                catalogParser.parse(file.inputStream())
            } else {
                if (createCatalog) {
                    VersionCatalog(emptyMap(), emptyMap(), emptyMap(), emptyMap())
                } else {
                    throw GradleException("${catalogFiles.first()} does not exist. Did you mean to specify the --create option?")
                }
            }

            val pins = getPins(currentCatalog, pinRefs)

            val catalogWithResolvedPlugins = resolvePluginIds(
                getResolvedBuildScriptArtifacts(project),
                catalogFromDependencies
            )

            val updatedCatalog = currentCatalog.updateFrom(
                catalog = catalogWithResolvedPlugins
                    .withPins(pins)
                    .withKeptReferences(
                        currentCatalog = currentCatalog,
                        refs = keepRefs,
                        keepUnusedLibraries = keep.orNull?.keepUnusedLibraries?.getOrElse(false) ?: false,
                        keepUnusedPlugins = keep.orNull?.keepUnusedPlugins?.getOrElse(false) ?: false,
                    ),
                addNew = (addDependencies ?: false) || createCatalog,
                purge = true
            ).withKeepUnusedVersions(currentCatalog, keep.orNull?.keepUnusedVersions?.getOrElse(false) ?: false)
                .withKeptVersions(currentCatalog, keepRefs)
                .let {
                    if (extension.sortByKey) {
                        it.sortKeys()
                    } else {
                        it
                    }
                }

            val writer = VersionCatalogWriter()
            writer.write(updatedCatalog, file.writer())

            if (versionsReportResult.exceeded.isNotEmpty() && !createCatalog) {
                emitExceededWarning(versionsReportResult.exceeded, currentCatalog)
            }

            checkForUpdatesForLibrariesWithVersionCondition(updatedCatalog, versionsReportResult.outdated)
            checkForUpdatesToPins(updatedCatalog, catalogWithResolvedPlugins, pins)
        }
    }

    /**
     * Emit a warning when there are updates for pinned libraries and plugins
     * @param updatedCatalog the updated catalog
     * @param catalogWithResolvedPlugins the catalog with the latest updates
     * @param pins the pins
     */
    private fun checkForUpdatesToPins(
        updatedCatalog: VersionCatalog,
        catalogWithResolvedPlugins: VersionCatalog,
        pins: Pins
    ) {
        val resolvedVersions = updatedCatalog.resolveVersions()

        val appliedPins = pins.libraries.filter { pin ->
            resolvedVersions.libraries.values.any {
                it.group == pin.group
            }
        }.map { lib ->
            lib to catalogWithResolvedPlugins.libraries.entries.first {
                it.value.group == lib.group
            }
        }.filter {
            it.first.version != it.second.value.version && it.first.version is VersionDefinition.Simple
        }

        if (appliedPins.isNotEmpty()) {
            project.logger.warn(
                "There are updates available for pinned entries in the version catalog:"
            )
            for (pin in appliedPins) {
                val message = " - ${pin.first.module} (${pin.second.key}) " +
                    "${(pin.first.version as VersionDefinition.Simple).version} -> " +
                    (pin.second.value.version as VersionDefinition.Simple).version
                project.logger.warn(message)
            }
        }
    }

    private fun checkForUpdatesForLibrariesWithVersionCondition(catalog: VersionCatalog, outdated: Set<Dependency>) {
        val librariesWithVersionCondition = (catalog.libraries.values).filter {
            it.resolvedVersion(catalog) is VersionDefinition.Condition
        }.mapNotNull { library ->
            outdated.firstOrNull {
                it.group == library.group && it.name == library.name
            }?.let {
                library to it
            }
        }.toMap()

        if (librariesWithVersionCondition.isNotEmpty()) {
            project.logger.warn("There are libraries using a version condition that could be updated:")
            for (library in librariesWithVersionCondition) {
                val key = catalog.libraries.entries.first {
                    it.value == library.key
                }.key

                val versionRef = when (val version = library.key.version) {
                    is VersionDefinition.Reference -> " ref:${version.ref}"
                    else -> ""
                }
                project.logger.warn(
                    " - ${library.key.module} ($key$versionRef) -> ${library.value.latestVersion}"
                )
            }
        }
    }

    private fun emitExceededWarning(dependencies: Set<Dependency>, catalog: VersionCatalog) {
        var didOutputPreamble = false
        for (dependency in dependencies) {
            val declaredCatalogEntry = catalog.libraries.entries.firstOrNull {
                it.value.group == dependency.group && it.value.name == dependency.name
            }
            declaredCatalogEntry?.let {
                val resolvedVersion = it.value.resolveSimpleVersionReference(catalog)
                // only warn for versions that we can resolve / handle
                if (resolvedVersion != null) {
                    if (!didOutputPreamble) {
                        project.logger.warn(
                            "Some libraries declared in the version catalog did not match the resolved version used this project.\n" +
                                "This mismatch can occur when a version is declared that does not exist, or when a dependency is referenced by a transitive dependency that requires a different version.\n" +
                                "The version in the version catalog has been updated to the actual version. If this is not what you want, consider using a strict version definition.\n\n" +
                                "The affected libraries are:"
                        )
                        didOutputPreamble = true
                    }
                    val versionRef = when (val version = it.value.version) {
                        is VersionDefinition.Reference -> " (${version.ref})"
                        else -> ""
                    }
                    project.logger.warn(
                        " - ${dependency.group}:${dependency.name} (libs.${
                        declaredCatalogEntry.key.replace(
                            '-',
                            '.'
                        )
                        })\n     requested: ${dependency.currentVersion}$versionRef, resolved: ${dependency.latestVersion}"
                    )
                }
            }
        }
    }

    /**
     * Get the resolved build script dependencies for the given project and any subprojects
     *
     * @param project project to get the dependencies for
     * @return a set of [ResolvedArtifact], may be empty
     */
    private fun getResolvedBuildScriptArtifacts(project: Project): Set<ResolvedArtifact> {
        val projectResolvedArtifacts =
            project.buildscript.configurations.firstOrNull()?.resolvedConfiguration?.resolvedArtifacts?.filterNotNull()
                ?.toSet()
                ?: (emptySet())
        return if (project.subprojects.isNotEmpty()) {
            project.subprojects.map { getResolvedBuildScriptArtifacts(it) }.flatten().toSet() + projectResolvedArtifacts
        } else {
            projectResolvedArtifacts
        }
    }

    private fun resolvePluginIds(
        buildScriptArtifacts: Set<ResolvedArtifact>,
        versionCatalog: VersionCatalog
    ): VersionCatalog {
        val moduleIds = versionCatalog.libraries.values.map { it.module }
        val knownPluginModules = versionCatalog.plugins.values.map { "${it.id}.gradle.plugins" }

        val plugins = buildScriptArtifacts.mapNotNull { resolvedArtifact ->
            val module = (resolvedArtifact.id as? ModuleComponentArtifactIdentifier)?.let {
                "${it.componentIdentifier.moduleIdentifier.group}:${it.componentIdentifier.moduleIdentifier.name}"
            }

            if (module != null && moduleIds.contains(module) && !knownPluginModules.contains(module)) {
                checkGradlePluginDescriptor(resolvedArtifact.file).map {
                    it to module
                }
            } else {
                null
            }
        }.flatten().toMap()

        return versionCatalog.mapPlugins(plugins)
    }

    private fun checkGradlePluginDescriptor(file: File): Set<String> {
        val jarFile = try {
            JarFile(file)
        } catch (ex: Exception) {
            project.logger.debug("Could not check ${file.absolutePath} for Gradle plugin descriptors")
            null
        } ?: return emptySet()

        val ids = mutableSetOf<String>()
        jarFile.use {
            for (entry in it.entries()) {
                if (entry.name.startsWith("META-INF/gradle-plugins") &&
                    !entry.isDirectory &&
                    entry.name.endsWith(PROPERTIES_SUFFIX)
                ) {
                    ids.add(File(entry.name).name.dropLast(PROPERTIES_SUFFIX.length))
                }
            }
        }
        return ids
    }

    @Suppress("UnstableApiUsage")
    private fun getPins(currentCatalog: VersionCatalog, pins: Set<VersionCatalogRef>): Pins {
        val pinsByType = pins.groupBy { it::class }

        val versionPinned = pinsByType.getOrDefault(VersionRef::class, emptyList())
            .filterIsInstance<VersionRef>()
            .map {
                VersionDefinition.Reference(it.versionName)
            }

        val versionPinnedLibs = versionPinned.flatMap { version ->
            currentCatalog.libraries.values.filter { library ->
                library.version == version
            }
        }

        val versionPinnedPlugins = versionPinned.flatMap { version ->
            currentCatalog.plugins.values.filter { plugin ->
                plugin.version == version
            }
        }

        val libraryPinned = pinsByType.getOrDefault(LibraryRef::class, emptyList())
            .filterIsInstance<LibraryRef>()
            .map {
                it.dependency
            }
            .flatMap { pin ->
                currentCatalog.libraries.values.filter { it.module == "${pin.group}:${pin.name}" }
            }

        val pluginsPinned = pinsByType.getOrDefault(PluginRef::class, emptyList())
            .filterIsInstance<PluginRef>()
            .map {
                it.pluginId
            }
            .flatMap { pin ->
                currentCatalog.plugins.values.filter { it.id == pin }
            }

        val groupsPinned = pinsByType.getOrDefault(GroupRef::class, emptyList())
            .filterIsInstance<GroupRef>()
            .flatMap { pin ->
                currentCatalog.libraries.values.filter { it.group == pin.group }
            }

        return Pins(
            libraries = (versionPinnedLibs.toSet() + libraryPinned.toSet() + groupsPinned.toSet()).map {
                it.copy(version = it.resolvedVersion(currentCatalog))
            }.toSet(),
            plugins = (versionPinnedPlugins.toSet() + pluginsPinned.toSet()).map {
                it.copy(version = it.resolvedVersion(currentCatalog))
            }.toSet()
        )
    }
}

private data class Pins(val libraries: Set<Library>, val plugins: Set<Plugin>)

private fun VersionCatalog.withKeptReferences(
    currentCatalog: VersionCatalog,
    refs: Set<VersionCatalogRef>,
    keepUnusedLibraries: Boolean,
    keepUnusedPlugins: Boolean
): VersionCatalog {
    val refsByType =
        refs.let {
            if (keepUnusedLibraries) it.addAllLibraries(currentCatalog) else it
        }.let {
            if (keepUnusedPlugins) it.addAllPlugins(currentCatalog) else it
        }.groupBy { it::class }

    val keptLibraries = currentCatalog.libraries.entries.filter { entry ->
        refsByType.getOrDefault(GroupRef::class, emptyList())
            .filterIsInstance<GroupRef>().any {
                it.group == entry.value.group
            } || refsByType.getOrDefault(LibraryRef::class, emptyList())
            .filterIsInstance<LibraryRef>()
            .map {
                it.dependency
            }
            .any {
                "${it.group}:${it.name}" == entry.value.module
            }
    }.filter { entry ->
        !libraries.values.any {
            it.module == entry.value.module
        }
    }.associate {
        it.key to it.value
    }

    // plugins to keep that are not in this (update) catalog
    val keptPlugins = currentCatalog.plugins.entries.filter { entry ->
        refsByType.getOrDefault(PluginRef::class, emptyList())
            .filterIsInstance<PluginRef>()
            .map {
                it.pluginId
            }
            .any {
                it == entry.value.id
            }
    }.filter { entry ->
        !plugins.values.any {
            it.id == entry.value.id
        }
    }.associate {
        it.key to it.value
    }

    return copy(libraries = this.libraries + keptLibraries, plugins = this.plugins + keptPlugins)
}

private fun VersionCatalog.withKeepUnusedVersions(currentCatalog: VersionCatalog, keep: Boolean): VersionCatalog {
    if (!keep) {
        return this
    }
    val removedVersions = currentCatalog.versions.entries.filter {
        !versions.containsKey(it.key)
    }.associate { it.key to it.value }
    return copy(versions = versions + removedVersions)
}

private fun VersionCatalog.withKeepUnusedLibraries(currentCatalog: VersionCatalog, keep: Boolean): VersionCatalog {
    if (!keep) {
        return this
    }
    val removedLibraries = currentCatalog.libraries.entries.filter { entry ->
        !libraries.values.any { it.module == entry.value.module }
    }.associate { it.key to it.value }

    return copy(libraries = libraries + removedLibraries)
}

private fun VersionCatalog.withKeepUnusedPlugins(currentCatalog: VersionCatalog, keep: Boolean): VersionCatalog {
    if (!keep) {
        return this
    }
    val removedPlugins = currentCatalog.plugins.entries.filter { entry ->
        !plugins.values.any { it.id == entry.value.id }
    }.associate { it.key to it.value }

    return copy(plugins = plugins + removedPlugins)
}

private fun VersionCatalog.withKeptVersions(
    currentCatalog: VersionCatalog,
    refs: Set<VersionCatalogRef>
): VersionCatalog {
    val keptVersions = refs.filterIsInstance<VersionRef>().filter {
        currentCatalog.versions.containsKey(it.versionName) && !versions.containsKey(it.versionName)
    }.mapNotNull { ref ->
        currentCatalog.versions[ref.versionName]?.let {
            ref.versionName to it
        }
    }.toMap()
    return copy(versions = versions + keptVersions)
}

private fun VersionCatalog.withPins(pins: Pins): VersionCatalog {
    // pins that actually exist in ths catalog
    val validLibraryPins = pins.libraries.filter { library ->
        libraries.values.any {
            it.module == library.module
        }
    }

    val validPluginPins = pins.plugins.filter { plugin ->
        plugins.values.any {
            it.id == plugin.id
        }
    }

    return copy(
        libraries = libraries.toMutableMap().apply {
            removeLibraries(validLibraryPins)
            putAll(
                validLibraryPins.map {
                    // not a valid toml key, but only used for merging catalogs + existing entries
                    // so this should be ok
                    it.module to it
                }
            )
        },
        plugins = plugins.toMutableMap().apply {
            removePlugins(validPluginPins)
            putAll(
                validPluginPins.map {
                    it.id to it
                }
            )
        }
    )
}

private fun Set<VersionCatalogRef>.addAllLibraries(versionCatalog: VersionCatalog): Set<VersionCatalogRef> {
    return this + versionCatalog.libraries.map {
        LibraryRef(object : ModuleIdentifier {
            override fun getGroup(): String = it.value.group

            override fun getName(): String = it.value.name
        })
    }
}

private fun Set<VersionCatalogRef>.addAllPlugins(versionCatalog: VersionCatalog): Set<VersionCatalogRef> {
    return this + versionCatalog.plugins.map {
        PluginRef(it.value.id)
    }
}

private fun MutableMap<String, Plugin>.removePlugins(plugins: Collection<Plugin>) {
    values.removeIf { plugin ->
        plugins.any {
            it.id == plugin.id
        }
    }
}

private fun MutableMap<String, Library>.removeLibraries(libs: Collection<Library>) {
    values.removeIf { lib ->
        libs.any {
            it.module == lib.module
        }
    }
}
