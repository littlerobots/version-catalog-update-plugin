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
package nl.littlerobots.vcu.plugin.resolver

import nl.littlerobots.vcu.model.Library
import nl.littlerobots.vcu.model.Plugin
import nl.littlerobots.vcu.model.VersionCatalog
import nl.littlerobots.vcu.model.VersionDefinition
import nl.littlerobots.vcu.model.resolveVersions
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.dsl.DependencyHandler
import java.util.concurrent.TimeUnit

internal class DependencyResolver {
    internal fun resolveFromCatalog(
        currentLibraryConfiguration: Configuration,
        libraryConfiguration: Configuration,
        currentPluginConfiguration: Configuration,
        pluginConfiguration: Configuration,
        dependencyHandler: DependencyHandler,
        versionCatalog: VersionCatalog,
        moduleVersionSelector: ModuleVersionSelector
    ): DependencyResolverResult {
        val resolvedCatalog = versionCatalog.resolveVersions()

        configureConfiguration(currentLibraryConfiguration) {}
        configureConfiguration(currentPluginConfiguration) {}

        val currentVersions by lazy {
            currentLibraryConfiguration.resolvedConfiguration.lenientConfiguration.firstLevelModuleDependencies.associate {
                "${it.module.id.module.group}:${it.module.id.module.name}" to it.module.id.version
            } + currentLibraryConfiguration.resolvedConfiguration.lenientConfiguration.unresolvedModuleDependencies.associate {
                "${it.selector.module.group}:${it.selector.module.name}" to it.selector.version
            } + currentPluginConfiguration.resolvedConfiguration.lenientConfiguration.firstLevelModuleDependencies.associate {
                "${it.module.id.module.group}:${it.module.id.module.name}" to it.module.id.version
            } + currentPluginConfiguration.resolvedConfiguration.lenientConfiguration.unresolvedModuleDependencies.associate {
                "${it.selector.module.group}:${it.selector.module.name}" to it.selector.version
            }
        }

        var selectorError: Throwable? = null
        val selector: (ComponentSelection) -> Unit = {
            // Any error here is swallowed by Gradle, so work around
            try {
                val currentVersion = requireNotNull(currentVersions["${it.candidate.group}:${it.candidate.module}"])
                if (!moduleVersionSelector.select(ModuleVersionCandidate(it.candidate, currentVersion))) {
                    it.reject("${it.candidate.version} rejected by version selector as an upgrade for version $currentVersion")
                }
            } catch (t: Throwable) {
                if (selectorError == null) {
                    selectorError = t
                }
                throw t
            }
        }

        configureConfiguration(libraryConfiguration, selector)
        configureConfiguration(pluginConfiguration, selector)

        resolvedCatalog.libraries.values.forEach { library ->
            when (val version = library.version) {
                is VersionDefinition.Simple -> {
                    libraryConfiguration.dependencies.add(dependencyHandler.create("${library.module}:+"))
                    currentLibraryConfiguration.dependencies.add(dependencyHandler.create("${library.module}:${version.version}"))
                }

                is VersionDefinition.Condition -> {
                    libraryConfiguration.dependencies.add(dependencyHandler.create("${library.module}:+"))
                    currentLibraryConfiguration.dependencies.add(dependencyHandler.create(library.module))
                    currentLibraryConfiguration.dependencyConstraints.add(
                        dependencyHandler.constraints.create(
                            library.module
                        ) { constraint ->
                            configureConstraint(constraint, version)
                        }
                    )
                }

                else -> {
                    // no version specified
                }
            }
        }
        resolvedCatalog.plugins.values.forEach {
            when (val version = it.version) {
                is VersionDefinition.Simple -> {
                    pluginConfiguration.dependencies.add(dependencyHandler.create("${it.id}:${it.id}.gradle.plugin:+"))
                    currentPluginConfiguration.dependencies.add(dependencyHandler.create("${it.id}:${it.id}.gradle.plugin:${version.version}"))
                }

                is VersionDefinition.Condition -> {
                    pluginConfiguration.dependencies.add(dependencyHandler.create("${it.id}:${it.id}.gradle.plugin:+"))
                    currentPluginConfiguration.dependencies.add(dependencyHandler.create("${it.id}:${it.id}.gradle.plugin"))
                    currentPluginConfiguration.dependencyConstraints.add(
                        dependencyHandler.constraints.create(
                            pluginConfiguration.name
                        ) { constraint ->
                            configureConstraint(constraint, version)
                        }
                    )
                }

                else -> {
                    // either no version specified or rich version
                }
            }
        }

        val resolvedLibraries =
            libraryConfiguration.resolvedConfiguration.lenientConfiguration.firstLevelModuleDependencies
        val resolvedPlugins =
            pluginConfiguration.resolvedConfiguration.lenientConfiguration.firstLevelModuleDependencies

        if (selectorError != null) {
            throw GradleException("An error occurred when selecting versions", selectorError)
        }

        val catalog = VersionCatalog(
            versions = emptyMap(),
            libraries = resolvedLibraries.associate {
                val module = it.module.id
                "${module.group}.${module.name}" to Library(
                    "${module.group}:${module.name}",
                    VersionDefinition.Simple(module.version)
                )
            },
            plugins = resolvedPlugins.associate {
                val module = it.module.id
                module.group to Plugin(module.group, VersionDefinition.Simple(module.version))
            },
            bundles = emptyMap()
        )

        val unresolvedLibraryUpdates =
            libraryConfiguration.resolvedConfiguration.lenientConfiguration.unresolvedModuleDependencies.mapNotNull { dependency ->
                resolvedCatalog.libraries.values.firstOrNull { it.module == "${dependency.selector.module.group}:${dependency.selector.module.name}" }
            }

        val unresolvedPluginUpdates =
            pluginConfiguration.resolvedConfiguration.lenientConfiguration.unresolvedModuleDependencies.mapNotNull { dependency ->
                resolvedCatalog.plugins.values.firstOrNull { it.id == dependency.selector.group }
            }

        val exceededLibraryUpdates =
            currentLibraryConfiguration.resolvedConfiguration.lenientConfiguration.unresolvedModuleDependencies.mapNotNull { dependency ->
                catalog.libraries.values.firstOrNull { library -> library.module == "${dependency.selector.module.group}:${dependency.selector.module.name}" }
            }

        val exceededPluginUpdates =
            currentPluginConfiguration.resolvedConfiguration.lenientConfiguration.unresolvedModuleDependencies.mapNotNull { dependency ->
                catalog.plugins.values.firstOrNull { plugin -> plugin.id == dependency.selector.module.group }
            }

        val richVersionLibraryUpdates = resolvedLibraries.mapNotNull { dependency ->
            resolvedCatalog.libraries.values.firstOrNull {
                it.module == "${dependency.module.id.group}:${dependency.module.id.name}"
            }?.let {
                dependency to it
            }
        }.filter {
            val (dependency, library) = it
            val version = when (val version = library.version) {
                is VersionDefinition.Simple -> version.version
                else -> null
            }
            library.version.richVersion && dependency.moduleVersion != version
        }.associate {
            val (dependency, library) = it
            library to Library(library.module, VersionDefinition.Simple(dependency.moduleVersion))
        }

        val richVersionPluginUpdates = resolvedPlugins.mapNotNull { dependency ->
            resolvedCatalog.plugins.values.firstOrNull {
                it.id == dependency.module.id.group
            }?.let {
                dependency to it
            }
        }.filter {
            val (dependency, plugin) = it
            val version = when (val version = plugin.version) {
                is VersionDefinition.Simple -> version.version
                else -> null
            }
            plugin.version.richVersion && dependency.moduleVersion != version
        }.associate {
            val (dependency, plugin) = it
            plugin to Plugin(plugin.id, VersionDefinition.Simple(dependency.moduleVersion))
        }

        return DependencyResolverResult(
            versionCatalog = catalog.copy(
                libraries = catalog.libraries.toMutableMap().apply {
                    values.removeAll(
                        values.filter { library -> richVersionLibraryUpdates.values.any { it.module == library.module } }
                            .toSet()
                    )
                    values.removeAll(
                        values.filter { library -> exceededLibraryUpdates.any { it.module == library.module } }
                            .toSet()
                    )
                },
                plugins = catalog.plugins.toMutableMap().apply {
                    values.removeAll(
                        values.filter { plugin -> richVersionPluginUpdates.values.any { it.id == plugin.id } }
                            .toSet()
                    )
                    values.removeAll(
                        values.filter { plugin -> exceededPluginUpdates.any { it.id == plugin.id } }
                            .toSet()
                    )
                }
            ),
            exceeded = DependencyResolverResult.DependencyCollection(exceededLibraryUpdates, exceededPluginUpdates),
            unresolved = DependencyResolverResult.DependencyCollection(
                unresolvedLibraryUpdates,
                unresolvedPluginUpdates
            ),
            richVersions = DependencyResolverResult.DependencyCollection(
                richVersionLibraryUpdates.values,
                richVersionPluginUpdates.values
            )
        )
    }

    private fun configureConstraint(constraint: DependencyConstraint, richVersion: VersionDefinition.Condition) {
        val require = richVersion.definition["require"]
        val reject = richVersion.definition["reject"]
        val strictly = richVersion.definition["strictly"]
        val prefer = richVersion.definition["prefer"]
        val rejectAll = richVersion.definition["rejectAll"]?.toBoolean() ?: false
        constraint.version { version ->
            require?.let {
                version.require(it)
            }
            reject?.let {
                version.reject(it)
            }
            strictly?.let {
                version.strictly(it)
            }
            prefer?.let {
                version.prefer(it)
            }
            if (rejectAll) {
                version.rejectAll()
            }
        }
    }

    private fun configureConfiguration(
        configuration: Configuration,
        componentSelection: (ComponentSelection) -> Unit
    ) {
        configuration.isTransitive = false
        configuration.isCanBeResolved = true
        configuration.isVisible = false
        configuration.isCanBeConsumed = false
        configuration.resolutionStrategy.componentSelection { rules ->
            rules.all {
                componentSelection(it)
            }
        }
        configuration.resolutionStrategy.cacheDynamicVersionsFor(60, TimeUnit.SECONDS)
    }
}

private val VersionDefinition.richVersion: Boolean
    get() = when (this) {
        is VersionDefinition.Simple -> version.contains("[")
        is VersionDefinition.Condition -> true
        else -> false
    }
