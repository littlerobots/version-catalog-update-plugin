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
import nl.littlerobots.vcu.model.VersionCatalog
import nl.littlerobots.vcu.model.sortKeys
import nl.littlerobots.vcu.model.updateFrom
import nl.littlerobots.vcu.versions.VersionReportParser
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import java.io.File

abstract class VersionCatalogUpdateTask : DefaultTask() {
    @get:Internal
    abstract val reportJson: Property<File>

    @get:Internal
    abstract val catalogFile: Property<File>

    @set:Option(option = "create", description = "Create libs.versions.toml based on current dependencies")
    @get:Internal
    abstract var createCatalog: Boolean

    private var keepUnusedDependencies: Boolean? = null
    private var addDependencies: Boolean? = null

    @Option(option = "keep-unused", description = "Keep unused dependencies in the toml file")
    fun setKeepUnusedDependenciesOption(keep: Boolean) {
        this.keepUnusedDependencies = keep
    }

    @Option(option = "add", description = "Add new dependencies in the toml file")
    fun setAddDependenciesOption(add: Boolean) {
        this.addDependencies = add
    }

    @TaskAction
    fun updateCatalog() {
        val extension = project.extensions.getByType(VersionCatalogPluginExtension::class.java)

        val addDependencies = addDependencies ?: extension.addDependencies.getOrElse(false)
        val keepUnused = keepUnusedDependencies ?: extension.keepUnused.getOrElse(false)

        val reportParser = VersionReportParser()
        val pluginModules = project.buildscript.configurations.flatMap { configuration ->
            configuration.dependencies.filterIsInstance<ExternalDependency>().map { "${it.group}:${it.name}" }.toList()
        }

        val catalogFromDependencies = reportParser.generateCatalog(reportJson.get().inputStream(), pluginModules)
        val currentCatalog = if (catalogFile.get().exists()) {
            if (createCatalog) {
                throw GradleException("${catalogFile.get()} already exists and cannot be created from scratch.")
            }
            val catalogParser = VersionCatalogParser()
            catalogParser.parse(catalogFile.get().reader())
        } else {
            if (createCatalog) {
                VersionCatalog(emptyMap(), emptyMap(), emptyMap(), emptyMap())
            } else {
                throw GradleException("${catalogFile.get()} does not exist. Did you mean to specify the --create option?")
            }
        }

        val updatedCatalog = currentCatalog.updateFrom(
            catalogFromDependencies,
            addNew = addDependencies || createCatalog,
            purge = !keepUnused
        ).let {
            if (extension.sortByKey.getOrElse(true)) {
                it.sortKeys()
            } else {
                it
            }
        }
        val writer = VersionCatalogWriter()
        writer.write(updatedCatalog, catalogFile.get().writer())
    }
}
