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
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.attributes.java.TargetJvmVersion
import java.util.concurrent.TimeUnit

internal class DependencyResolver {
    internal fun resolveFromCatalog(
        libraryConfiguration: Configuration,
        pluginConfiguration: Configuration,
        dependencyHandler: DependencyHandler,
        versionCatalog: VersionCatalog,
        componentSelection: (ComponentSelection) -> Unit
    ): VersionCatalog {
        val resolvedCatalog = versionCatalog.resolveVersions()

        configureConfiguration(libraryConfiguration, componentSelection)
        configureConfiguration(pluginConfiguration, componentSelection)

        resolvedCatalog.libraries.values.forEach {
            when (it.version) {
                is VersionDefinition.Simple -> {
                    libraryConfiguration.dependencies.add(dependencyHandler.create("${it.module}:+"))
                }

                else -> {
                    // either no version specified or rich version which is already enforced by Gradle
                }
            }
        }
        resolvedCatalog.plugins.values.forEach {
            when (it.version) {
                is VersionDefinition.Simple -> {
                    pluginConfiguration.dependencies.add(dependencyHandler.create("${it.id}:${it.id}.gradle.plugin:+"))
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

        return catalog
    }

    private fun configureConfiguration(
        configuration: Configuration,
        componentSelection: (ComponentSelection) -> Unit
    ) {
        configuration.isTransitive = false
        configuration.isCanBeResolved = true
        configuration.isVisible = false
        configuration.isCanBeConsumed = false
        configuration.attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.MAX_VALUE)
        configuration.resolutionStrategy.componentSelection { rules ->
            rules.all {
                componentSelection(it)
            }
        }
        configuration.resolutionStrategy.cacheDynamicVersionsFor(60, TimeUnit.SECONDS)
    }
}
