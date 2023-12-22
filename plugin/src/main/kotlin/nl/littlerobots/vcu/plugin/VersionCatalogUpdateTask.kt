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
import nl.littlerobots.vcu.model.VersionCatalog
import nl.littlerobots.vcu.model.VersionDefinition
import nl.littlerobots.vcu.model.mapPlugins
import nl.littlerobots.vcu.model.resolveSimpleVersionReference
import nl.littlerobots.vcu.model.resolvedVersion
import nl.littlerobots.vcu.plugin.model.BuildScriptArtifact
import nl.littlerobots.vcu.versions.VersionReportParser
import nl.littlerobots.vcu.versions.VersionReportResult
import nl.littlerobots.vcu.versions.model.Dependency
import nl.littlerobots.vcu.versions.model.module
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File
import java.util.jar.JarFile

private const val PROPERTIES_SUFFIX = ".properties"
private const val GRADLE_PLUGIN_MODULE_POST_FIX = ".gradle.plugin"

abstract class VersionCatalogUpdateTask : BaseVersionCatalogUpdateTask() {
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFile
    abstract val reportJson: RegularFileProperty

    @set:Option(option = "create", description = "Create a libs.versions.toml file based on current dependencies.")
    @get:Internal
    abstract var createCatalog: Boolean

    @get:Input
    internal abstract val buildScriptArtifacts: SetProperty<BuildScriptArtifact>

    private var versionsReportResult: VersionReportResult? = null
    override val addNewDependencies: Boolean
        get() = createCatalog

    override val keepUnusedEntriesByDefault: Boolean
        get() = false

    init {
        description = "Updates the libs.versions.toml file."
        group = "Version catalog update"
    }

    override fun loadCurrentVersionCatalog(): VersionCatalog {
        return if (catalogFile.get().exists()) {
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
    }

    override fun createCatalogWithLatestVersions(currentCatalog: VersionCatalog): VersionCatalog {
        val reportParser = VersionReportParser()
        val versionsReportResult =
            reportParser.generateCatalog(reportJson.get().asFile.inputStream(), currentCatalog, useLatestVersions = !createCatalog)
        val catalogFromDependencies = versionsReportResult.catalog
        val catalogWithResolvedPlugins = resolvePluginIds(
            buildScriptArtifacts.get(),
            catalogFromDependencies
        )
        this.versionsReportResult = versionsReportResult
        return catalogWithResolvedPlugins
    }

    override fun onVersionCatalogUpdated(updatedCatalog: VersionCatalog, currentCatalog: VersionCatalog) {
        val versionsReportResult = requireNotNull(versionsReportResult)
        if (versionsReportResult.exceeded.isNotEmpty() && !createCatalog) {
            emitExceededWarning(versionsReportResult.exceeded, currentCatalog)
        }
        checkForUpdatesForLibrariesWithVersionCondition(updatedCatalog, versionsReportResult.outdated)
        checkForUpdateForPluginsWithVersionCondition(updatedCatalog, buildScriptArtifacts.get(), versionsReportResult.outdated)
    }

    @TaskAction
    override fun updateCatalog() {
        if (interactive && createCatalog) {
            throw GradleException("--interactive cannot be used with --create")
        }

        super.updateCatalog()
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
            logger.warn("There are libraries using a version condition that could be updated:")
            for (library in librariesWithVersionCondition) {
                val key = catalog.libraries.entries.first {
                    it.value == library.key
                }.key

                val versionRef = when (val version = library.key.version) {
                    is VersionDefinition.Reference -> " ref:${version.ref}"
                    else -> ""
                }
                logger.warn(
                    " - ${library.key.module} ($key$versionRef) -> ${library.value.latestVersion}"
                )
            }
        }
    }

    private fun checkForUpdateForPluginsWithVersionCondition(
        catalog: VersionCatalog,
        buildScriptArtifacts: Set<BuildScriptArtifact>,
        outdated: Set<Dependency>
    ) {
        val pluginsWithVersionCondition = catalog.plugins.filter {
            it.value.resolvedVersion(catalog) is VersionDefinition.Condition
        }

        if (pluginsWithVersionCondition.isEmpty()) {
            return
        }

        val outdatedPluginIds = outdated.filter {
            it.module.endsWith(GRADLE_PLUGIN_MODULE_POST_FIX)
        }.associate {
            it.name.dropLast(GRADLE_PLUGIN_MODULE_POST_FIX.length) to it.latestVersion
        } + outdated.filter {
            !it.name.endsWith(GRADLE_PLUGIN_MODULE_POST_FIX)
        }.associate {
            it.module to it.latestVersion
        }.flatMap { entry ->
            val buildScriptArtifact = buildScriptArtifacts.firstOrNull { it.module == entry.key }
            buildScriptArtifact?.let { artifact ->
                val descriptors = checkGradlePluginDescriptor(artifact.file)
                descriptors.map {
                    it to entry.value
                }
            } ?: emptyList()
        }.toMap()

        if (outdatedPluginIds.isNotEmpty()) {
            logger.warn("There are plugins using a version condition that could be updated:")
            for (plugin in pluginsWithVersionCondition) {
                val update = outdatedPluginIds.entries.firstOrNull {
                    it.key == plugin.value.id
                }

                if (update != null) {
                    val versionRef = when (val version = plugin.value.version) {
                        is VersionDefinition.Reference -> " ref:${version.ref}"
                        else -> ""
                    }
                    logger.warn(
                        " - ${plugin.value.id} (${plugin.key}$versionRef) -> ${update.value}"
                    )
                }
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
                        logger.warn(
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
                    logger.warn(
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

    private fun resolvePluginIds(
        buildScriptArtifacts: Set<BuildScriptArtifact>,
        versionCatalog: VersionCatalog
    ): VersionCatalog {
        val moduleIds = versionCatalog.libraries.values.map { it.module }
        val knownPluginModules = versionCatalog.plugins.values.map { "${it.id}$GRADLE_PLUGIN_MODULE_POST_FIX" }

        val plugins = buildScriptArtifacts.mapNotNull { resolvedArtifact ->
            if (moduleIds.contains(resolvedArtifact.module) && !knownPluginModules.contains(resolvedArtifact.module)) {
                checkGradlePluginDescriptor(resolvedArtifact.file).map {
                    it to resolvedArtifact.module
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
            logger.debug("Could not check ${file.absolutePath} for Gradle plugin descriptors")
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
}
