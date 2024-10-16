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

import nl.littlerobots.vcu.model.Library
import nl.littlerobots.vcu.model.Plugin
import nl.littlerobots.vcu.model.VersionCatalog
import nl.littlerobots.vcu.model.VersionDefinition
import nl.littlerobots.vcu.plugin.resolver.DependencyResolver
import nl.littlerobots.vcu.plugin.resolver.DependencyResolverResult
import nl.littlerobots.vcu.plugin.resolver.ModuleVersionSelector
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class ExperimentalVersionCatalogUpdateTask @Inject constructor(private val objectFactory: ObjectFactory) :
    BaseVersionCatalogUpdateTask() {
    private lateinit var result: DependencyResolverResult
    @get:Internal
    internal abstract val versionSelector: Property<ModuleVersionSelector>

    fun versionSelector(filter: ModuleVersionSelector) {
        versionSelector.set(filter)
    }

    override fun onVersionCatalogUpdated(updatedCatalog: VersionCatalog, currentCatalog: VersionCatalog) {
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

    private fun printExceededPlugins(currentCatalog: VersionCatalog, plugins: Collection<Plugin>) {
        printPluginWarning(
            currentCatalog,
            plugins,
            "There are plugins with invalid versions that could be updated:"
        ) { catalogPlugin, versionRef, plugin ->
            logger.warn(
                " - ${catalogPlugin.value.id} (${catalogPlugin.key}$versionRef) -> ${(plugin.version as VersionDefinition.Simple).version}"
            )
        }
    }

    private fun printExceededLibraries(currentCatalog: VersionCatalog, libraries: Collection<Library>) {
        printLibraryWarning(
            currentCatalog,
            libraries,
            "There are libraries with invalid versions that could be updated:"
        ) { current, versionRef, library ->
            logger.warn(
                " - ${current.value.module} (${current.key}$versionRef) -> ${(library.version as VersionDefinition.Simple).version}"
            )
        }
    }

    private fun printUnresolvedPlugins(currentCatalog: VersionCatalog, plugins: Collection<Plugin>) {
        printPluginWarning(
            currentCatalog,
            plugins,
            "There are plugins that could not be resolved:"
        ) { catalogPlugin, versionRef, _ ->
            logger.warn(
                " - ${catalogPlugin.value.id} (${catalogPlugin.key}$versionRef)"
            )
        }
    }

    private fun printUnresolvedLibraries(catalog: VersionCatalog, libraries: Collection<Library>) {
        printLibraryWarning(
            catalog,
            libraries,
            "There are libraries that could not be resolved:"
        ) { catalogLibrary, versionRef, library ->
            logger.warn(
                " - ${library.module} (${catalogLibrary.key}$versionRef)"
            )
        }
    }

    private fun printLibraryWarning(
        catalog: VersionCatalog,
        libraries: Collection<Library>,
        warning: String,
        block: (current: Map.Entry<String, Library>, versionRef: String, library: Library) -> Unit
    ) {
        logger.warn(warning)
        for (library in libraries) {
            val catalogLibrary = catalog.libraries.entries.first {
                it.value.module == library.module
            }

            val versionRef = when (val version = catalogLibrary.value.version) {
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
        block: (currentPluginEntry: Map.Entry<String, Plugin>, versionRef: String, plugin: Plugin) -> Unit
    ) {
        logger.warn(warning)
        for (plugin in plugins) {
            val catalogPlugin = catalog.plugins.entries.first {
                it.value.id == plugin.id
            }

            val versionRef = when (val version = catalogPlugin.value.version) {
                is VersionDefinition.Reference -> " ref:${version.ref}"
                else -> ""
            }

            block(catalogPlugin, versionRef, plugin)
        }
    }

    private fun printRichVersionPluginUpdates(catalog: VersionCatalog, plugins: Collection<Plugin>) {
        printPluginWarning(
            catalog,
            plugins,
            "There are plugins using a rich version that could be updated:"
        ) { catalogPlugin, versionRef, plugin ->
            logger.warn(
                " - ${catalogPlugin.value.id} (${catalogPlugin.key}$versionRef) -> ${(plugin.version as VersionDefinition.Simple).version}"
            )
        }
    }

    private fun printRichVersionLibraryUpdates(
        catalog: VersionCatalog,
        outdated: Collection<Library>
    ) {
        printLibraryWarning(
            catalog,
            outdated,
            "There are libraries using a rich version that could be updated:"
        ) { current, versionRef, library ->
            logger.warn(
                " - ${current.value.module} (${current.key}$versionRef) -> ${(library.version as VersionDefinition.Simple).version}"
            )
        }
    }

    override fun createCatalogWithLatestVersions(currentCatalog: VersionCatalog): VersionCatalog {
        val dependencyResolver = DependencyResolver()

        val result = dependencyResolver.resolveFromCatalog(
            project.configurations.detachedConfiguration(),
            project.configurations.detachedConfiguration(),
            project.buildscript.configurations.detachedConfiguration(),
            project.buildscript.configurations.detachedConfiguration(),
            project.dependencies,
            currentCatalog,
            versionSelector.get()
        )
        this.result = result
        return result.versionCatalog
    }

    @TaskAction
    override fun updateCatalog() {
        val keepConfiguration = objectFactory.newInstance(KeepConfiguration::class.java)
        keepConfiguration.keepUnusedLibraries.set(true)
        keepConfiguration.keepUnusedPlugins.set(true)
        val keepConfigurationInput = objectFactory.newInstance(KeepConfigurationInput::class.java, keepConfiguration)
        keep.set(keepConfigurationInput)
        super.updateCatalog()
    }
}
