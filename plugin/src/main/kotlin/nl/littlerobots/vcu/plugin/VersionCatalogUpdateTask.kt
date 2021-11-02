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
import nl.littlerobots.vcu.model.mapPlugins
import nl.littlerobots.vcu.model.sortKeys
import nl.littlerobots.vcu.model.updateFrom
import nl.littlerobots.vcu.versions.VersionReportParser
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import java.io.File
import java.util.jar.JarFile

private const val PROPERTIES_SUFFIX = ".properties"

abstract class VersionCatalogUpdateTask : DefaultTask() {
    @get:InputFile
    abstract val reportJson: RegularFileProperty

    @get:OutputFile
    @get:Optional
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
        val extension = project.extensions.getByType(VersionCatalogUpdateExtension::class.java)

        val addDependencies = addDependencies ?: extension.addDependencies
        val keepUnused = keepUnusedDependencies ?: extension.keepUnused

        val reportParser = VersionReportParser()

        val catalogFromDependencies = reportParser.generateCatalog(reportJson.get().asFile.inputStream())

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

        val updatedCatalog = currentCatalog.updateFrom(
            // TODO not sure if we can have multiple configurations for the buildscript or if it can even be no config
            project.buildscript.configurations.firstOrNull()?.let {
                resolvePluginIds(it, catalogFromDependencies)
            } ?: catalogFromDependencies,
            addNew = addDependencies || createCatalog,
            purge = !keepUnused
        ).let {
            if (extension.sortByKey) {
                it.sortKeys()
            } else {
                it
            }
        }

        val writer = VersionCatalogWriter()
        writer.write(updatedCatalog, catalogFile.get().writer())
    }

    private fun resolvePluginIds(configuration: Configuration, versionCatalog: VersionCatalog): VersionCatalog {
        val moduleIds = versionCatalog.libraries.values.map { it.module }
        val knownPluginModules = versionCatalog.plugins.values.map { "${it.id}.gradle.plugins" }

        val plugins = configuration.resolvedConfiguration.resolvedArtifacts.mapNotNull { resolvedArtifact ->
            val module = (resolvedArtifact.id as? ModuleComponentArtifactIdentifier)?.let {
                "${it.componentIdentifier.moduleIdentifier.group}:${it.componentIdentifier.moduleIdentifier.name}"
            }

            if (module != null && moduleIds.contains(module) && ! knownPluginModules.contains(module)) {
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
}
