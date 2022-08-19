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
import nl.littlerobots.vcu.model.Comments
import nl.littlerobots.vcu.model.HasVersion
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
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.jar.JarFile

private const val PROPERTIES_SUFFIX = ".properties"

abstract class VersionCatalogUpdateTask : DefaultTask() {
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFile
    abstract val reportJson: RegularFileProperty

    @get:OutputFile
    @get:Optional
    abstract val catalogFile: Property<File>

    @set:Option(option = "create", description = "Create libs.versions.toml based on current dependencies")
    @get:Internal
    abstract var createCatalog: Boolean

    @set:Option(option = "interactive", description = "Stage changes before applying to the toml file")
    @get:Internal
    abstract var interactive: Boolean

    @get:Input
    abstract val pins: Property<PinsConfigurationInput>

    // @get:Input
    @get:Internal
    abstract val keep: Property<KeepConfigurationInput>

    @get:Input
    @get:Optional
    abstract val sortByKey: Property<Boolean>

    private val pinRefs by lazy {
        pins.orNull?.getVersionCatalogRefs() ?: emptySet()
    }

    private val keepRefs by lazy {
        keep.orNull?.getVersionCatalogRefs() ?: emptySet()
    }

    @TaskAction
    fun updateCatalog() {
        if (interactive && createCatalog) {
            throw GradleException("--interactive cannot be used with --create")
        }

        checkInteractiveState()

        val currentCatalog = if (catalogFile.get().exists()) {
            if (createCatalog) {
                throw GradleException("${catalogFile.get()} already exists and cannot be created from scratch.")
            }
            val catalogParser = VersionCatalogParser()
            catalogParser.parse(catalogFile.get().inputStream())
        } else {
            if (createCatalog) {
                VersionCatalog(emptyMap(), emptyMap(), emptyMap(), emptyMap())
            } else {
                throw GradleException("${catalogFile.get()} does not exist. Did you mean to specify the --create option?")
            }
        }

        val reportParser = VersionReportParser()
        val versionsReportResult =
            reportParser.generateCatalog(reportJson.get().asFile.inputStream(), currentCatalog, useLatestVersions = !createCatalog)
        val catalogFromDependencies = versionsReportResult.catalog

        val pins = getPins(currentCatalog, pinRefs + getPinnedRefsFromComments(currentCatalog))
        val keepRefs = this.keepRefs + getKeepRefsFromComments(currentCatalog)

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
            addNew = createCatalog,
            purge = true
        ).withKeepUnusedVersions(currentCatalog, keep.orNull?.keepUnusedVersions?.getOrElse(false) ?: false)
            .withKeptVersions(currentCatalog, keepRefs)
            .let {
                if (sortByKey.getOrElse(true)) {
                    it.sortKeys()
                } else {
                    it
                }
            }

        if (versionsReportResult.exceeded.isNotEmpty() && !createCatalog) {
            emitExceededWarning(versionsReportResult.exceeded, currentCatalog)
        }

        checkForUpdatesForLibrariesWithVersionCondition(updatedCatalog, versionsReportResult.outdated)
        checkForUpdatedPinnedLibraries(updatedCatalog, catalogWithResolvedPlugins, pins)
        checkForUpdatedPinnedPlugins(updatedCatalog, catalogWithResolvedPlugins, pins)

        if (interactive) {
            writeUpdatesFile(
                currentCatalog,
                updatedCatalog,
                getPinsWithUpdatedVersions(catalogWithResolvedPlugins, pins)
            )
            if (keep.orNull?.keepingAnything == false) {
                // remove entries that are no longer in the update
                val c = currentCatalog.copy(
                    libraries = currentCatalog.libraries.filterValues { library ->
                        updatedCatalog.libraries.values.any {
                            it.group == library.group
                        }
                    },
                    plugins = currentCatalog.plugins.filterValues { plugin ->
                        updatedCatalog.plugins.values.any {
                            it.id == plugin.id
                        }
                    }
                )
                // update again to fix bundles and versions
                val currentPruned = currentCatalog.updateFrom(c, purge = true)
                    .withKeepUnusedVersions(currentCatalog, keep.orNull?.keepUnusedVersions?.getOrElse(false) ?: false)
                    .withKeptVersions(currentCatalog, keepRefs)

                val writer = VersionCatalogWriter()
                // write out the current catalog without sorting it
                writer.write(currentPruned, catalogFile.get().writer())
            }
        } else {
            val writer = VersionCatalogWriter()
            writer.write(updatedCatalog, catalogFile.get().writer())
        }
    }

    /**
     * Write a version catalog diff file
     * @param currentCatalog the version catalog before updates
     * @param updatedCatalog the version catalog updated, considering pins & keeps etc,
     * @param pins the pins holding the _updated_ version from [getPinsWithUpdatedVersions]
     */
    private fun writeUpdatesFile(currentCatalog: VersionCatalog, updatedCatalog: VersionCatalog, pins: Pins) {
        val currentResolved = currentCatalog.resolveVersions()
        // the update as if the pins are reverted, e.g. all possible updates
        val updatedResolved = updatedCatalog
            .updateFrom(updatedCatalog.withPins(pins), purge = false)
            .resolveVersions()
        val catalogFile = this.catalogFile.get()

        val diff = updatedResolved.copy(
            libraries = updatedResolved.libraries.filterNot { entry ->
                currentResolved.libraries.values.contains(entry.value)
            },
            plugins = updatedResolved.plugins.filterNot { entry ->
                currentResolved.plugins.values.contains(entry.value)
            },
            bundles = emptyMap(), versions = emptyMap()
        )

        if (diff.plugins.isEmpty() && diff.libraries.isEmpty() && pins.libraries.isEmpty() && pins.plugins.isEmpty()) {
            project.logger.warn("There are no updates available")
            return
        }

        val tableComments = """
                    # Version catalog updates generated at ${
        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }
                    #
                    # Contents of this file will be applied to ${catalogFile.name} when running ${VersionCatalogApplyUpdatesTask.TASK_NAME}.
                    #
                    # Comments will not be applied to the version catalog when updating.
                    # To prevent a version upgrade, comment out the entry or remove it.
                    #
        """.trimIndent().lines()

        fun getUpdateComment(dependencyEntry: Map.Entry<String, HasVersion>, dependenciesMap: Map<String, HasVersion>): List<String> {
            val previousVersion = (dependenciesMap[dependencyEntry.key]?.version as? VersionDefinition.Simple)?.version
            return previousVersion?.let { version ->
                dependenciesMap[dependencyEntry.key]?.version?.let { versionDefinition ->
                    val currentVersionGroup = if (versionDefinition is VersionDefinition.Reference) {
                        " (${versionDefinition.ref})"
                    } else {
                        ""
                    }
                    val matchingPin = when (val dependency = dependencyEntry.value) {
                        is Library -> pins.libraries.firstOrNull { it.group == dependency.group }
                        is Plugin -> pins.plugins.firstOrNull { it.id == dependency.id }
                        else -> error("Unexpected dependency type ${dependency.javaClass.name}")
                    }
                    val updatedVersion = (dependencyEntry.value.version as VersionDefinition.Simple).version
                    if (matchingPin != null) {
                        listOf("# @pinned version $version$currentVersionGroup --> $updatedVersion")
                    } else {
                        listOf("# From version $version$currentVersionGroup --> $updatedVersion")
                    }
                }
            } ?: emptyList()
        }

        val diffWithComments = diff.copy(
            libraryComments = Comments(
                tableComments = tableComments,
                entryComments = diff.libraries.map { entry ->
                    entry.key to getUpdateComment(entry, currentResolved.libraries)
                }.toMap()
            ),
            pluginComments = Comments(
                tableComments = if (diff.libraries.isEmpty()) tableComments else emptyList(),
                entryComments = diff.plugins.map { entry ->
                    entry.key to getUpdateComment(entry, currentResolved.plugins)
                }.toMap()
            )
        )

        val updateFile = catalogFile.updatesFile

        val writer = VersionCatalogWriter()
        updateFile.writer().use { outputStreamWriter ->
            writer.write(diffWithComments, outputStreamWriter) { versioned ->
                when (versioned) {
                    is Library -> pins.libraries.any { it.group == versioned.group }
                    is Plugin -> pins.plugins.any { it.id == versioned.id }
                    else -> false
                }
            }
        }
        project.logger.warn("Updates are written to ${updateFile.name}. Run the ${VersionCatalogApplyUpdatesTask.TASK_NAME} task to apply updates to ${catalogFile.name}")
    }

    private fun checkInteractiveState() {
        val updateFile = catalogFile.orNull?.updatesFile
        if (updateFile?.exists() == true) {
            throw GradleException("${updateFile.absolutePath} exists, did you mean to run the ${VersionCatalogApplyUpdatesTask.TASK_NAME} task to apply the updates?")
        }
    }

    /**
     * Return pins that can be updated
     * @param catalog the version catalog to use as a source for library and plugin updates
     * @param pins the pins based on the current version in the version catalog
     */
    private fun getPinsWithUpdatedVersions(catalog: VersionCatalog, pins: Pins): Pins {
        return Pins(
            libraries = pins.libraries
                .filter { pinned ->
                    catalog.libraries.values.filter {
                        it.version is VersionDefinition.Simple
                    }.any {
                        it.group == pinned.group && it.version != pinned.version
                    }
                }.map { pinned ->
                    catalog.libraries.values.first { it.group == pinned.group }
                }.toSet(),
            plugins = pins.plugins.filter { pinned ->
                catalog.plugins.values.filter {
                    it.version is VersionDefinition.Simple
                }.any {
                    it.id == pinned.id && it.version != pinned.version
                }
            }.map { pinned ->
                catalog.plugins.values.first {
                    it.id == pinned.id
                }
            }.toSet()
        )
    }

    /**
     * Emit a warning when there are updates for pinned libraries and plugins
     * @param updatedCatalog the updated catalog
     * @param catalogWithResolvedPlugins the catalog with the latest available updates
     * @param pins the pins
     */
    @Suppress("DuplicatedCode")
    private fun checkForUpdatedPinnedLibraries(
        updatedCatalog: VersionCatalog,
        catalogWithResolvedPlugins: VersionCatalog,
        pins: Pins
    ) {
        val resolvedVersions = updatedCatalog.resolveVersions()

        val appliedPins = pins.libraries.filter { pin ->
            resolvedVersions.libraries.values.any {
                it.group == pin.group
            }
        }.mapNotNull { lib ->
            // can be null for kept, but unused libraries
            catalogWithResolvedPlugins.libraries.entries.firstOrNull {
                it.value.group == lib.group
            }?.let {
                lib to it
            }
        }.filter {
            it.first.version != it.second.value.version && it.first.version is VersionDefinition.Simple
        }

        val libraryKeys = appliedPins.associate {
            it.first.group to updatedCatalog.libraries.entries.first { entry ->
                entry.value.group == it.first.group
            }.key
        }

        if (appliedPins.isNotEmpty()) {
            project.logger.warn(
                "There are updates available for pinned libraries in the version catalog:"
            )
            for (pin in appliedPins) {
                val message = " - ${pin.first.module} (${libraryKeys[pin.first.group]}) " +
                    "${(pin.first.version as VersionDefinition.Simple).version} -> " +
                    (pin.second.value.version as VersionDefinition.Simple).version
                project.logger.warn(message)
            }
        }
    }

    /**
     * Emit a warning when there are updates for pinned plugins
     * @param updatedCatalog the updated catalog
     * @param catalogWithResolvedPlugins the catalog with the latest available updates
     * @param pins the pins
     */
    @Suppress("DuplicatedCode")
    private fun checkForUpdatedPinnedPlugins(
        updatedCatalog: VersionCatalog,
        catalogWithResolvedPlugins: VersionCatalog,
        pins: Pins
    ) {
        val resolvedVersions = updatedCatalog.resolveVersions()

        val appliedPins = pins.plugins.filter { pin ->
            resolvedVersions.plugins.values.any {
                it.id == pin.id
            }
        }.mapNotNull { plugin ->
            // can be null for kept, but unused plugins
            catalogWithResolvedPlugins.plugins.entries.firstOrNull() {
                it.value.id == plugin.id
            }?.let {
                plugin to it
            }
        }.filter {
            it.first.version != it.second.value.version && it.first.version is VersionDefinition.Simple
        }

        val pluginKeys = appliedPins.associate {
            it.first.id to updatedCatalog.plugins.entries.first { entry ->
                entry.value.id == it.first.id
            }.key
        }

        if (appliedPins.isNotEmpty()) {
            project.logger.warn(
                "There are updates available for pinned plugins in the version catalog:"
            )
            for (pin in appliedPins) {
                val message = " - ${pin.first.id} (${pluginKeys[pin.first.id]}) " +
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

val File.updatesFile: File
    get() = File(parentFile, "$nameWithoutExtension.updates.$extension")
