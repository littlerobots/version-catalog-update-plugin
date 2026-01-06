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
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.UnresolvedDependency
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
        moduleVersionSelector: ModuleVersionSelector,
    ): DependencyResolverResult {
        val resolvedCatalog = versionCatalog.resolveVersions()
        val acceptedVersions = mutableMapOf<Dependency, String>()
        val currentAcceptedVersions = mutableMapOf<Dependency, String>()

        configureConfiguration(currentLibraryConfiguration) {
            currentAcceptedVersions[Dependency(it.candidate.moduleIdentifier)] = it.candidate.version
        }
        configureConfiguration(currentPluginConfiguration) {
            currentAcceptedVersions[Dependency(it.candidate.moduleIdentifier)] = it.candidate.version
        }

        val currentVersions by lazy {
            currentLibraryConfiguration.resolvedConfiguration.lenientConfiguration.firstLevelModuleDependencies.associate {
                Dependency(it.module.id) to it.module.id.version
            } +
                currentLibraryConfiguration.resolvedConfiguration.lenientConfiguration.unresolvedModuleDependencies.associate {
                    Dependency(it.selector.module) to it.selector.version
                } +
                currentPluginConfiguration.resolvedConfiguration.lenientConfiguration.firstLevelModuleDependencies.associate {
                    Dependency(it.module.id) to it.module.id.version
                } +
                currentPluginConfiguration.resolvedConfiguration.lenientConfiguration.unresolvedModuleDependencies.associate {
                    Dependency(it.selector.module) to it.selector.version
                }
        }

        var selectorError: Throwable? = null
        val selector: (ComponentSelection) -> Unit = {
            // Any error here is swallowed by Gradle, so work around
            try {
                val currentVersion = currentVersions[Dependency(it.candidate.moduleIdentifier)]
                // if the version is null, then we don't have this specific artifact in the catalog
                // This can be caused if the resolved artifact is an alias to a different artifact
                // Just selecting whatever is presented here _should_ be fine in that case, as the original
                // artifact passes the version selection first.
                if (currentVersion != null && !moduleVersionSelector.select(ModuleVersionCandidate(it.candidate, currentVersion))) {
                    it.reject("${it.candidate.version} rejected by version selector as an upgrade for version $currentVersion")
                } else if (currentVersion != null) {
                    acceptedVersions[Dependency(it.candidate.moduleIdentifier)] = it.candidate.version
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
                            library.module,
                        ) { constraint ->
                            configureConstraint(constraint, version)
                        },
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
                    currentPluginConfiguration.dependencies.add(
                        dependencyHandler.create("${it.id}:${it.id}.gradle.plugin:${version.version}"),
                    )
                }

                is VersionDefinition.Condition -> {
                    pluginConfiguration.dependencies.add(dependencyHandler.create("${it.id}:${it.id}.gradle.plugin:+"))
                    currentPluginConfiguration.dependencies.add(dependencyHandler.create("${it.id}:${it.id}.gradle.plugin"))
                    currentPluginConfiguration.dependencyConstraints.add(
                        dependencyHandler.constraints.create(
                            "${it.id}:${it.id}.gradle.plugin",
                        ) { constraint ->
                            configureConstraint(constraint, version)
                        },
                    )
                }

                else -> {
                    // either no version specified or rich version
                }
            }
        }

        // this triggers resolving
        val resolvedLibraryConfiguration = libraryConfiguration.resolvedConfiguration.lenientConfiguration
        val resolvedPluginConfiguration = pluginConfiguration.resolvedConfiguration.lenientConfiguration
        val resolvedCurrentLibraryConfiguration = currentLibraryConfiguration.resolvedConfiguration.lenientConfiguration
        val resolvedCurrentPluginConfiguration = currentPluginConfiguration.resolvedConfiguration.lenientConfiguration

        val resolvedLibraries =
            acceptedVersions.toMap().filterNot { it.key.isPlugin } +
                resolvedLibraryConfiguration.firstLevelModuleDependencies.associate {
                    Dependency(it.module.id.module) to it.module.id.version
                }

        val unresolvedLibraries =
            resolvedLibraryConfiguration.unresolvedModuleDependencies
                .map {
                    Dependency(it.selector.module)
                }.filter {
                    acceptedVersions[it] == null
                }.associateWith { currentVersions[it] }
        val unresolvedCurrentLibraries =
            resolvedCurrentLibraryConfiguration.unresolvedModuleDependencies
                .map { Dependency(it.selector.module) }
                .filter {
                    currentAcceptedVersions[it] == null
                }.associateWith {
                    currentVersions[it]
                }
        val resolvedPlugins =
            acceptedVersions.filter { it.key.isPlugin } +
                resolvedPluginConfiguration.firstLevelModuleDependencies.associate {
                    Dependency(it) to it.module.id.version
                }
        val unresolvedPlugins =
            resolvedPluginConfiguration.unresolvedModuleDependencies
                .map {
                    Dependency(it) to it.selector.version
                }.filter {
                    acceptedVersions[it.first] == null
                }.toMap()

        val unresolvedCurrentPlugins =
            resolvedCurrentPluginConfiguration.unresolvedModuleDependencies
                .map {
                    Dependency(it.selector.module) to it.selector.version
                }.filter {
                    currentAcceptedVersions[it.first] == null
                }.toMap()

        if (selectorError != null) {
            throw GradleException("An error occurred when selecting versions", selectorError)
        }

        val catalog =
            VersionCatalog(
                versions = emptyMap(),
                libraries =
                    resolvedLibraries
                        .map {
                            val module = it.key
                            module.tomlKey to
                                Library(
                                    module.module,
                                    VersionDefinition.Simple(it.value),
                                )
                        }.toMap(),
                plugins =
                    resolvedPlugins
                        .map {
                            val module = it.key
                            module.group to Plugin(module.group, VersionDefinition.Simple(it.value))
                        }.toMap(),
                bundles = emptyMap(),
            )

        val unresolvedLibraryUpdates =
            unresolvedLibraries.mapNotNull { dependency ->
                resolvedCatalog.libraries.values.firstOrNull { it.module == dependency.key.module }
            }

        val unresolvedPluginUpdates =
            unresolvedPlugins.mapNotNull { dependency ->
                resolvedCatalog.plugins.values.firstOrNull { it.id == dependency.key.group }
            }

        val exceededLibraryUpdates =
            unresolvedCurrentLibraries.mapNotNull { dependency ->
                catalog.libraries.values.firstOrNull { library -> library.module == dependency.key.module }
            }

        val exceededPluginUpdates =
            unresolvedCurrentPlugins.mapNotNull { dependency ->
                catalog.plugins.values.firstOrNull { plugin -> plugin.id == dependency.key.group }
            }

        val richVersionLibraryUpdates =
            resolvedLibraries
                .mapNotNull { dependency ->
                    resolvedCatalog.libraries.values
                        .firstOrNull {
                            it.module == dependency.key.module
                        }?.let {
                            dependency to it
                        }
                }.filter {
                    val (dependency, library) = it
                    val version =
                        when (val version = library.version) {
                            is VersionDefinition.Simple -> version.version
                            else -> null
                        }
                    library.version.richVersion && dependency.value != version
                }.associate {
                    val (dependency, library) = it
                    library to Library(library.module, VersionDefinition.Simple(dependency.value))
                }

        val richVersionPluginUpdates =
            resolvedPlugins
                .mapNotNull { dependency ->
                    resolvedCatalog.plugins.values
                        .firstOrNull {
                            it.id == dependency.key.group
                        }?.let {
                            dependency to it
                        }
                }.filter {
                    val (dependency, plugin) = it
                    val version =
                        when (val version = plugin.version) {
                            is VersionDefinition.Simple -> version.version
                            else -> null
                        }
                    plugin.version.richVersion && dependency.value != version
                }.associate {
                    val (dependency, plugin) = it
                    plugin to Plugin(plugin.id, VersionDefinition.Simple(dependency.value))
                }

        return DependencyResolverResult(
            versionCatalog =
                catalog.copy(
                    libraries =
                        catalog.libraries.toMutableMap().apply {
                            values.removeAll(
                                values
                                    .filter { library -> richVersionLibraryUpdates.values.any { it.module == library.module } }
                                    .toSet(),
                            )
                            values.removeAll(
                                values
                                    .filter { library -> exceededLibraryUpdates.any { it.module == library.module } }
                                    .toSet(),
                            )
                        },
                    plugins =
                        catalog.plugins.toMutableMap().apply {
                            values.removeAll(
                                values
                                    .filter { plugin -> richVersionPluginUpdates.values.any { it.id == plugin.id } }
                                    .toSet(),
                            )
                            values.removeAll(
                                values
                                    .filter { plugin -> exceededPluginUpdates.any { it.id == plugin.id } }
                                    .toSet(),
                            )
                        },
                ),
            exceeded = DependencyResolverResult.DependencyCollection(exceededLibraryUpdates, exceededPluginUpdates),
            unresolved =
                DependencyResolverResult.DependencyCollection(
                    unresolvedLibraryUpdates,
                    unresolvedPluginUpdates,
                ),
            richVersions =
                DependencyResolverResult.DependencyCollection(
                    richVersionLibraryUpdates.values,
                    richVersionPluginUpdates.values,
                ),
        )
    }

    private fun configureConstraint(
        constraint: DependencyConstraint,
        richVersion: VersionDefinition.Condition,
    ) {
        val require = richVersion.definition["require"] as? String

        @Suppress("UNCHECKED_CAST")
        val reject = richVersion.definition["reject"] as? List<String>
        val strictly = richVersion.definition["strictly"] as? String
        val prefer = richVersion.definition["prefer"] as? String
        val rejectAll = (richVersion.definition["rejectAll"] as? String)?.toBoolean() ?: false
        constraint.version { version ->
            require?.let {
                version.require(it)
            }
            reject?.let {
                version.reject(*it.toTypedArray())
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
        componentSelection: (ComponentSelection) -> Unit,
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
        configuration.resolutionStrategy.cacheDynamicVersionsFor(60, TimeUnit.MINUTES)
    }
}

private data class Dependency(
    val group: String,
    val name: String,
) {
    constructor(moduleIdentifier: ModuleIdentifier) : this(moduleIdentifier.group, moduleIdentifier.name)
    constructor(moduleIdentifier: ModuleVersionIdentifier) : this(moduleIdentifier.group, moduleIdentifier.name)
    constructor(dependency: ResolvedDependency) : this(dependency.module.id.group, dependency.module.id.name)
    constructor(dependency: UnresolvedDependency) : this(dependency.selector.module.group, dependency.selector.module.name)

    val module: String
        get() = "$group:$name"

    val tomlKey: String
        get() = module.replace(".", "-")

    val isPlugin: Boolean
        get() = name.endsWith(".gradle.plugin")
}

private val VersionDefinition.richVersion: Boolean
    get() =
        when (this) {
            is VersionDefinition.Simple -> version.contains("[")
            is VersionDefinition.Condition -> true
            else -> false
        }
