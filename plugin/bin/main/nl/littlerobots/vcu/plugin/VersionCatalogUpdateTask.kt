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
import nl.littlerobots.vcu.model.resolveVersions
import nl.littlerobots.vcu.model.resolvedVersion
import nl.littlerobots.vcu.model.sortKeys
import nl.littlerobots.vcu.model.updateFrom
import nl.littlerobots.vcu.plugin.resolver.DependencyResolver
import nl.littlerobots.vcu.plugin.resolver.DependencyResolverResult
import nl.littlerobots.vcu.plugin.resolver.ModuleVersionSelector
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.util.GradleVersion
import org.xml.sax.SAXException
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.SAXParserFactory

abstract class VersionCatalogUpdateTask : DefaultTask() {
    @get:OutputFile
    @get:Optional
    abstract val catalogFile: Property<File>

    @set:Option(
        option = "interactive",
        description = "Stage changes before applying them to the libs.versions.toml file.",
    )
    @get:Internal
    abstract var interactive: Boolean

    @get:Input
    abstract val pins: Property<PinsConfigurationInput>

    @get:Internal
    abstract val keep: Property<KeepConfigurationInput>

    @get:Input
    @get:Optional
    abstract val sortByKey: Property<Boolean>

    @get:Internal
    internal abstract val versionSelector: Property<ModuleVersionSelector>

    fun versionSelector(filter: ModuleVersionSelector) {
        versionSelector.set(filter)
    }

    private val pinRefs by lazy {
        pins.orNull?.getVersionCatalogRefs() ?: emptySet()
    }

    private val keepRefs by lazy {
        keep.orNull?.getVersionCatalogRefs() ?: emptySet()
    }

    init {
        description = "Updates the libs.versions.toml file."
        group = "Version catalog update"
    }

    open fun loadCurrentVersionCatalog(): VersionCatalog {
        if (!catalogFile.isPresent) {
            throw GradleException("catalogFile property is not set")
        }
        return if (catalogFile.get().exists()) {
            val catalogParser = VersionCatalogParser()
            catalogParser.parse(catalogFile.get().inputStream())
        } else {
            throw GradleException("catalogFile ${catalogFile.get().path} does not exist")
        }
    }

    private fun resolveCatalog(currentCatalog: VersionCatalog): DependencyResolverResult {
        val dependencyResolver = DependencyResolver()

        val result =
            dependencyResolver.resolveFromCatalog(
                project.configurations.detachedConfiguration(),
                project.configurations.detachedConfiguration(),
                project.buildscript.configurations.detachedConfiguration(),
                project.buildscript.configurations.detachedConfiguration(),
                project.dependencies,
                currentCatalog,
                versionSelector.get(),
            )

        return result
    }

    @TaskAction
    open fun updateCatalog() {
        ensureSupportedSaxParser()
        checkInteractiveState()

        val currentCatalog = loadCurrentVersionCatalog()
        val resolverResult = resolveCatalog(currentCatalog)
        val catalogFromDependencies = resolverResult.versionCatalog

        val pins = getPins(currentCatalog, pinRefs + getPinnedRefsFromComments(currentCatalog))
        val keepRefs = this.keepRefs + getKeepRefsFromComments(currentCatalog)

        val updatedCatalog =
            currentCatalog
                .updateFrom(
                    catalog =
                        catalogFromDependencies
                            .withPins(pins),
                    pruneVersions = true,
                ).withKeepUnusedVersions(currentCatalog, keep.orNull?.keepUnusedVersions?.getOrElse(false) ?: false)
                .withKeptVersions(currentCatalog, keepRefs)
                .let {
                    if (sortByKey.getOrElse(true)) {
                        it.sortKeys()
                    } else {
                        it
                    }
                }

        printWarnings(resolverResult, currentCatalog)

        checkForUpdatedPinnedLibraries(updatedCatalog, catalogFromDependencies, pins)
        checkForUpdatedPinnedPlugins(updatedCatalog, catalogFromDependencies, pins)

        if (interactive) {
            writeUpdatesFile(
                currentCatalog,
                updatedCatalog,
                getPinsWithUpdatedVersions(catalogFromDependencies, pins),
            )
        } else {
            val writer = VersionCatalogWriter()
            writer.write(updatedCatalog, catalogFile.get().writer())
        }
    }

    private fun printWarnings(
        result: DependencyResolverResult,
        currentCatalog: VersionCatalog,
    ) {
        if (result.richVersions.libraries.isNotEmpty()) {
            printRichVersionLibraryUpdates(currentCatalog, result.richVersions.libraries)
        }
        if (result.richVersions.plugins.isNotEmpty()) {
            printRichVersionPluginUpdates(currentCatalog, result.richVersions.plugins)
        }
        if (result.unresolved.libraries.isNotEmpty()) {
            printUnresolvedLibraries(currentCatalog, result.unresolved.libraries)
        }
        if (result.unresolved.plugins.isNotEmpty()) {
            printUnresolvedPlugins(currentCatalog, result.unresolved.plugins)
        }
        if (result.exceeded.libraries.isNotEmpty()) {
            printExceededLibraries(currentCatalog, result.exceeded.libraries)
        }
        if (result.exceeded.plugins.isNotEmpty()) {
            printExceededPlugins(currentCatalog, result.exceeded.plugins)
        }
    }

    private fun printExceededPlugins(
        currentCatalog: VersionCatalog,
        plugins: Collection<Plugin>,
    ) {
        printPluginWarning(
            currentCatalog,
            plugins,
            "There are plugins with invalid versions that could be updated:",
        ) { catalogPlugin, versionRef, plugin ->
            logger.warn(
                " - ${catalogPlugin.value.id} (${catalogPlugin.key}$versionRef) -> ${(plugin.version as VersionDefinition.Simple).version}",
            )
        }
    }

    private fun printExceededLibraries(
        currentCatalog: VersionCatalog,
        libraries: Collection<Library>,
    ) {
        printLibraryWarning(
            currentCatalog,
            libraries,
            "There are libraries with invalid versions that could be updated:",
        ) { current, versionRef, library ->
            logger.warn(
                " - ${current.value.module} (${current.key}$versionRef) -> ${(library.version as VersionDefinition.Simple).version}",
            )
        }
    }

    private fun printUnresolvedPlugins(
        currentCatalog: VersionCatalog,
        plugins: Collection<Plugin>,
    ) {
        printPluginWarning(
            currentCatalog,
            plugins,
            "There are plugins that could not be resolved:",
        ) { catalogPlugin, versionRef, _ ->
            logger.warn(
                " - ${catalogPlugin.value.id} (${catalogPlugin.key}$versionRef)",
            )
        }
    }

    private fun printUnresolvedLibraries(
        catalog: VersionCatalog,
        libraries: Collection<Library>,
    ) {
        printLibraryWarning(
            catalog,
            libraries,
            "There are libraries that could not be resolved:",
        ) { catalogLibrary, versionRef, library ->
            logger.warn(
                " - ${library.module} (${catalogLibrary.key}$versionRef)",
            )
        }
    }

    private fun printLibraryWarning(
        catalog: VersionCatalog,
        libraries: Collection<Library>,
        warning: String,
        block: (current: Map.Entry<String, Library>, versionRef: String, library: Library) -> Unit,
    ) {
        logger.warn(warning)
        for (library in libraries) {
            val catalogLibrary =
                catalog.libraries.entries.first {
                    it.value.module == library.module
                }

            val versionRef =
                when (val version = catalogLibrary.value.version) {
                    is VersionDefinition.Reference -> " ref:${version.ref}"
                    else -> ""
                }

            block(catalogLibrary, versionRef, library)
        }
    }

    private fun printPluginWarning(
        catalog: VersionCatalog,
        plugins: Collection<Plugin>,
        warning: String,
        block: (currentPluginEntry: Map.Entry<String, Plugin>, versionRef: String, plugin: Plugin) -> Unit,
    ) {
        logger.warn(warning)
        for (plugin in plugins) {
            val catalogPlugin =
                catalog.plugins.entries.first {
                    it.value.id == plugin.id
                }

            val versionRef =
                when (val version = catalogPlugin.value.version) {
                    is VersionDefinition.Reference -> " ref:${version.ref}"
                    else -> ""
                }

            block(catalogPlugin, versionRef, plugin)
        }
    }

    private fun printRichVersionPluginUpdates(
        catalog: VersionCatalog,
        plugins: Collection<Plugin>,
    ) {
        printPluginWarning(
            catalog,
            plugins,
            "There are plugins using a rich version that could be updated:",
        ) { catalogPlugin, versionRef, plugin ->
            logger.warn(
                " - ${catalogPlugin.value.id} (${catalogPlugin.key}$versionRef) -> ${(plugin.version as VersionDefinition.Simple).version}",
            )
        }
    }

    private fun printRichVersionLibraryUpdates(
        catalog: VersionCatalog,
        outdated: Collection<Library>,
    ) {
        printLibraryWarning(
            catalog,
            outdated,
            "There are libraries using a rich version that could be updated:",
        ) { current, versionRef, library ->
            logger.warn(
                " - ${current.value.module} (${current.key}$versionRef) -> ${(library.version as VersionDefinition.Simple).version}",
            )
        }
    }

    /**
     * Write a version catalog diff file
     * @param currentCatalog the version catalog before updates
     * @param updatedCatalog the version catalog updated, considering pins & keeps etc,
     * @param pins the pins holding the _updated_ version from [getPinsWithUpdatedVersions]
     */
    private fun writeUpdatesFile(
        currentCatalog: VersionCatalog,
        updatedCatalog: VersionCatalog,
        pins: Pins,
    ) {
        val currentResolved = currentCatalog.resolveVersions()
        // the update as if the pins are reverted, e.g. all possible updates
        val updatedResolved =
            updatedCatalog
                .updateFrom(updatedCatalog.withPins(pins), pruneVersions = false)
                .resolveVersions()
        val catalogFile = this.catalogFile.get()

        val diff =
            updatedResolved.copy(
                libraries =
                    updatedResolved.libraries.filterNot { entry ->
                        currentResolved.libraries.values.contains(entry.value)
                    },
                plugins =
                    updatedResolved.plugins.filterNot { entry ->
                        currentResolved.plugins.values.contains(entry.value)
                    },
                bundles = emptyMap(),
                versions = emptyMap(),
            )

        if (diff.plugins.isEmpty() && diff.libraries.isEmpty() && pins.libraries.isEmpty() && pins.plugins.isEmpty()) {
            logger.warn("There are no updates available")
            return
        }

        val tableComments =
            """
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

        fun getUpdateComment(
            dependencyEntry: Map.Entry<String, HasVersion>,
            dependenciesMap: Map<String, HasVersion>,
        ): List<String> {
            val previousVersion = (dependenciesMap[dependencyEntry.key]?.version as? VersionDefinition.Simple)?.version
            return previousVersion?.let { version ->
                dependenciesMap[dependencyEntry.key]?.version?.let { versionDefinition ->
                    val currentVersionGroup =
                        if (versionDefinition is VersionDefinition.Reference) {
                            " (${versionDefinition.ref})"
                        } else {
                            ""
                        }
                    val matchingPin =
                        when (val dependency = dependencyEntry.value) {
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

        val diffWithComments =
            diff.copy(
                libraryComments =
                    Comments(
                        tableComments = tableComments,
                        entryComments =
                            diff.libraries
                                .map { entry ->
                                    entry.key to getUpdateComment(entry, currentResolved.libraries)
                                }.toMap(),
                    ),
                pluginComments =
                    Comments(
                        tableComments = if (diff.libraries.isEmpty()) tableComments else emptyList(),
                        entryComments =
                            diff.plugins
                                .map { entry ->
                                    entry.key to getUpdateComment(entry, currentResolved.plugins)
                                }.toMap(),
                    ),
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
        logger.warn(
            "Updates are written to ${updateFile.name}. Run the ${VersionCatalogApplyUpdatesTask.TASK_NAME} task to apply updates to ${catalogFile.name}",
        )
    }

    private fun checkInteractiveState() {
        val updateFile = catalogFile.orNull?.updatesFile
        if (updateFile?.exists() == true) {
            throw GradleException(
                "${updateFile.absolutePath} exists, did you mean to run the ${VersionCatalogApplyUpdatesTask.TASK_NAME} task to apply the updates?",
            )
        }
    }

    /**
     * Return pins that can be updated
     * @param catalog the version catalog to use as a source for library and plugin updates
     * @param pins the pins based on the current version in the version catalog
     */
    private fun getPinsWithUpdatedVersions(
        catalog: VersionCatalog,
        pins: Pins,
    ): Pins =
        Pins(
            libraries =
                pins.libraries
                    .filter { pinned ->
                        catalog.libraries.values
                            .filter {
                                it.version is VersionDefinition.Simple
                            }.any {
                                it.group == pinned.group && it.version != pinned.version
                            }
                    }.map { pinned ->
                        catalog.libraries.values.first { it.group == pinned.group }
                    }.toSet(),
            plugins =
                pins.plugins
                    .filter { pinned ->
                        catalog.plugins.values
                            .filter {
                                it.version is VersionDefinition.Simple
                            }.any {
                                it.id == pinned.id && it.version != pinned.version
                            }
                    }.map { pinned ->
                        catalog.plugins.values.first {
                            it.id == pinned.id
                        }
                    }.toSet(),
        )

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
        pins: Pins,
    ) {
        val resolvedVersions = updatedCatalog.resolveVersions()

        val appliedPins =
            pins.libraries
                .filter { pin ->
                    resolvedVersions.libraries.values.any {
                        it.group == pin.group
                    }
                }.mapNotNull { lib ->
                    // can be null for kept, but unused libraries
                    catalogWithResolvedPlugins.libraries.entries
                        .firstOrNull {
                            it.value.group == lib.group
                        }?.let {
                            lib to it
                        }
                }.filter {
                    it.first.version != it.second.value.version && it.first.version is VersionDefinition.Simple
                }

        val libraryKeys =
            appliedPins.associate {
                it.first.group to
                    updatedCatalog.libraries.entries
                        .first { entry ->
                            entry.value.group == it.first.group
                        }.key
            }

        if (appliedPins.isNotEmpty()) {
            logger.warn(
                "There are updates available for pinned libraries in the version catalog:",
            )
            for (pin in appliedPins) {
                val message =
                    " - ${pin.first.module} (${libraryKeys[pin.first.group]}) " +
                        "${(pin.first.version as VersionDefinition.Simple).version} -> " +
                        (pin.second.value.version as VersionDefinition.Simple).version
                logger.warn(message)
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
        pins: Pins,
    ) {
        val resolvedVersions = updatedCatalog.resolveVersions()

        val appliedPins =
            pins.plugins
                .filter { pin ->
                    resolvedVersions.plugins.values.any {
                        it.id == pin.id
                    }
                }.mapNotNull { plugin ->
                    // can be null for kept, but unused plugins
                    catalogWithResolvedPlugins.plugins.entries
                        .firstOrNull {
                            it.value.id == plugin.id
                        }?.let {
                            plugin to it
                        }
                }.filter {
                    it.first.version != it.second.value.version && it.first.version is VersionDefinition.Simple
                }

        val pluginKeys =
            appliedPins.associate {
                it.first.id to
                    updatedCatalog.plugins.entries
                        .first { entry ->
                            entry.value.id == it.first.id
                        }.key
            }

        if (appliedPins.isNotEmpty()) {
            logger.warn(
                "There are updates available for pinned plugins in the version catalog:",
            )
            for (pin in appliedPins) {
                val message =
                    " - ${pin.first.id} (${pluginKeys[pin.first.id]}) " +
                        "${(pin.first.version as VersionDefinition.Simple).version} -> " +
                        (pin.second.value.version as VersionDefinition.Simple).version
                logger.warn(message)
            }
        }
    }

    private fun getPins(
        currentCatalog: VersionCatalog,
        pins: Set<VersionCatalogRef>,
    ): Pins {
        val pinsByType = pins.groupBy { it::class }

        val versionPinned =
            pinsByType
                .getOrDefault(VersionRef::class, emptyList())
                .filterIsInstance<VersionRef>()
                .map {
                    VersionDefinition.Reference(it.versionName)
                }

        val versionPinnedLibs =
            versionPinned.flatMap { version ->
                currentCatalog.libraries.values.filter { library ->
                    library.version == version
                }
            }

        val versionPinnedPlugins =
            versionPinned.flatMap { version ->
                currentCatalog.plugins.values.filter { plugin ->
                    plugin.version == version
                }
            }

        val libraryPinned =
            pinsByType
                .getOrDefault(LibraryRef::class, emptyList())
                .filterIsInstance<LibraryRef>()
                .map {
                    it.dependency
                }.flatMap { pin ->
                    currentCatalog.libraries.values.filter { it.module == "${pin.group}:${pin.name}" }
                }

        val pluginsPinned =
            pinsByType
                .getOrDefault(PluginRef::class, emptyList())
                .filterIsInstance<PluginRef>()
                .map {
                    it.pluginId
                }.flatMap { pin ->
                    currentCatalog.plugins.values.filter { it.id == pin }
                }

        val groupsPinned =
            pinsByType
                .getOrDefault(GroupRef::class, emptyList())
                .filterIsInstance<GroupRef>()
                .flatMap { pin ->
                    currentCatalog.libraries.values.filter { it.group == pin.group }
                }

        return Pins(
            libraries =
                (versionPinnedLibs.toSet() + libraryPinned.toSet() + groupsPinned.toSet())
                    .map {
                        it.copy(version = it.resolvedVersion(currentCatalog))
                    }.toSet(),
            plugins =
                (versionPinnedPlugins.toSet() + pluginsPinned.toSet())
                    .map {
                        it.copy(version = it.resolvedVersion(currentCatalog))
                    }.toSet(),
        )
    }

    private fun ensureSupportedSaxParser() {
        if ((
                GradleVersion.current() >= GradleVersion.version("7.6.3") &&
                    GradleVersion.current() <=
                    GradleVersion.version(
                        "8.0",
                    )
            ) ||
            GradleVersion.current() >= GradleVersion.version("8.4")
        ) {
            val factory = SAXParserFactory.newInstance()
            try {
                factory.newSAXParser().setProperty("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
            } catch (ex: SAXException) {
                throw GradleException(
                    """A plugin or custom build logic has included an old XML parser, which is not suitable for dependency resolution with this version of Gradle.
                    |You can work around this issue by specifying the SAXParserFactory to use or by updating any plugin that depends on an old XML parser version.
                    |
                    |Use ./gradlew buildEnvironment to get a list of buildscript dependencies to check your build script dependencies.
                    |
                    |See https://docs.gradle.org/8.4/userguide/upgrading_version_8.html#changes_8.4 for more details and a workaround.
                    """.trimMargin(),
                )
            }
        }
    }
}

private data class Pins(
    val libraries: Set<Library>,
    val plugins: Set<Plugin>,
)

private fun VersionCatalog.withPins(pins: Pins): VersionCatalog {
    // pins that actually exist in ths catalog
    val validLibraryPins =
        pins.libraries.filter { library ->
            libraries.values.any {
                it.module == library.module
            }
        }

    val validPluginPins =
        pins.plugins.filter { plugin ->
            plugins.values.any {
                it.id == plugin.id
            }
        }

    return copy(
        libraries =
            libraries.toMutableMap().apply {
                removeLibraries(validLibraryPins)
                putAll(
                    validLibraryPins.map {
                        // not a valid toml key, but only used for merging catalogs + existing entries
                        // so this should be ok
                        it.module to it
                    },
                )
            },
        plugins =
            plugins.toMutableMap().apply {
                removePlugins(validPluginPins)
                putAll(
                    validPluginPins.map {
                        it.id to it
                    },
                )
            },
    )
}

private fun Set<VersionCatalogRef>.addAllLibraries(versionCatalog: VersionCatalog): Set<VersionCatalogRef> =
    this +
        versionCatalog.libraries.map {
            LibraryRef(
                object : ModuleIdentifier {
                    override fun getGroup(): String = it.value.group

                    override fun getName(): String = it.value.name
                },
            )
        }

private fun Set<VersionCatalogRef>.addAllPlugins(versionCatalog: VersionCatalog): Set<VersionCatalogRef> =
    this +
        versionCatalog.plugins.map {
            PluginRef(it.value.id)
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
