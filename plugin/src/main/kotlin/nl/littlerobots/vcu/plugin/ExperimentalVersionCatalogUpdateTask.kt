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

import nl.littlerobots.vcu.model.VersionCatalog
import nl.littlerobots.vcu.plugin.resolver.DependencyResolver
import org.gradle.api.Action
import org.gradle.api.artifacts.ComponentSelection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class ExperimentalVersionCatalogUpdateTask @Inject constructor(private val objectFactory: ObjectFactory) : BaseVersionCatalogUpdateTask() {
    private var componentSelectorAction: Action<in ComponentSelection>? = null

    fun componentSelector(action: Action<in ComponentSelection>) {
        this.componentSelectorAction = action
    }

    override fun onVersionCatalogUpdated(updatedCatalog: VersionCatalog, currentCatalog: VersionCatalog) {
    }

    override fun createCatalogWithLatestVersions(currentCatalog: VersionCatalog): VersionCatalog {
        val dependencyResolver = DependencyResolver()
        val libraryConfiguration = project.configurations.detachedConfiguration()
        val pluginConfiguration = project.buildscript.configurations.detachedConfiguration()

        val catalog = dependencyResolver.resolveFromCatalog(
            libraryConfiguration,
            pluginConfiguration,
            project.dependencies,
            currentCatalog
        ) {
            componentSelectorAction?.execute(it)
        }
        return catalog
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
