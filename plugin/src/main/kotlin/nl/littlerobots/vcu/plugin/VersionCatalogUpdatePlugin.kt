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

import nl.littlerobots.vcu.plugin.model.createBuildScriptArtifactProperty
import nl.littlerobots.vcu.plugin.resolver.VersionSelectors
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.util.GradleVersion
import java.io.File

internal const val UPDATE_TASK_NAME = "versionCatalogUpdate"
internal const val FORMAT_TASK_NAME = "versionCatalogFormat"
internal const val EXTENSION_NAME = "versionCatalogUpdate"
private const val DEPENDENCY_UPDATES_TASK_NAME = "dependencyUpdates"
private const val VERSIONS_PLUGIN_ID = "com.github.ben-manes.versions"

class VersionCatalogUpdatePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (GradleVersion.current() < GradleVersion.version("7.2")) {
            throw GradleException("Gradle 7.2 or greater is required for this plugin")
        }

        if (project != project.rootProject) {
            throw IllegalStateException("Should be applied to the root project only")
        }

        val extension = project.extensions.create(EXTENSION_NAME, VersionCatalogUpdateExtension::class.java)
        val reportJson = project.objects.fileProperty()

        val defaultVersionCatalog = project.objects.newInstance(VersionCatalogConfig::class.java, "")
            .applyDefaultSettings(extension)

        defaultVersionCatalog.catalogFile.set(
            extension.catalogFile.convention(project.layout.projectDirectory.file("gradle/libs.versions.toml"))
        )

        configureTasks(project, defaultVersionCatalog, reportJson)

        extension.versionCatalogs.all {
            configureTasks(project, it.applyDefaultSettings(extension), reportJson)
        }

        project.pluginManager.withPlugin(VERSIONS_PLUGIN_ID) {
            project.tasks.named(DEPENDENCY_UPDATES_TASK_NAME) {
                val outputDir = it.property("outputDir") as String
                val reportFileName = it.property("reportfileName") as String
                reportJson.set(File(project.file(outputDir), "$reportFileName.json"))
                it.setProperty("outputFormatter", "json,xml,plain")
                it.setProperty("checkConstraints", true)
                it.setProperty("checkBuildEnvironmentConstraints", true)
            }
        }
    }

    private fun configureTasks(
        project: Project,
        versionCatalogConfig: VersionCatalogConfig,
        reportJson: RegularFileProperty
    ) {
        if (project.findProperty("nl.littlerobots.vcu.resolver") == "true") {
            configureExperimentalUpdateTask(project, versionCatalogConfig)
        } else {
            configureUpdateTask(project, versionCatalogConfig, reportJson)
        }
        configureFormatTask(project, versionCatalogConfig)
        configureApplyTask(project, versionCatalogConfig)
    }

    private fun configureApplyTask(project: Project, versionCatalogConfig: VersionCatalogConfig) {
        project.tasks.register(
            "${VersionCatalogApplyUpdatesTask.TASK_NAME}${versionCatalogConfig.name.capitalize()}",
            VersionCatalogApplyUpdatesTask::class.java
        ) { task ->
            task.sortByKey.set(versionCatalogConfig.sortByKey)
            task.keep.set(
                project.provider {
                    project.objects.newInstance(
                        KeepConfigurationInput::class.java,
                        versionCatalogConfig.keep
                    )
                }
            )
            task.catalogFile.set(versionCatalogConfig.catalogFile)
        }
    }

    private fun configureFormatTask(project: Project, versionCatalogConfig: VersionCatalogConfig) {
        project.tasks.register(
            "$FORMAT_TASK_NAME${versionCatalogConfig.name.capitalize()}",
            VersionCatalogFormatTask::class.java
        ) { task ->
            task.sortByKey.set(versionCatalogConfig.sortByKey)
            task.keep.set(
                project.provider {
                    project.objects.newInstance(
                        KeepConfigurationInput::class.java,
                        versionCatalogConfig.keep
                    )
                }
            )
            task.catalogFile.set(versionCatalogConfig.catalogFile)
        }
    }

    private fun configureExperimentalUpdateTask(project: Project, versionCatalogConfig: VersionCatalogConfig) {
        val extension = project.extensions.getByType(VersionCatalogUpdateExtension::class.java)
        val catalogUpdatesTask =
            project.tasks.register(
                "${UPDATE_TASK_NAME}${versionCatalogConfig.name.capitalize()}",
                ExperimentalVersionCatalogUpdateTask::class.java
            )
        catalogUpdatesTask.configure {
            task ->
            task.pins.set(
                project.provider {
                    project.objects.newInstance(
                        PinsConfigurationInput::class.java,
                        versionCatalogConfig.pins
                    )
                }
            )
            task.keep.set(
                project.provider {
                    project.objects.newInstance(
                        KeepConfigurationInput::class.java,
                        versionCatalogConfig.keep
                    )
                }
            )
            task.sortByKey.set(versionCatalogConfig.sortByKey)
            task.catalogFile.set(versionCatalogConfig.catalogFile.asFile)
            task.notCompatibleWithConfigurationCache("Uses project")
            task.outputs.upToDateWhen { false }
            val versionSelector = versionCatalogConfig.versionSelector.orElse(extension.versionSelector).orElse(VersionSelectors.PREFER_STABLE)
            task.versionSelector.set(versionSelector)
        }
    }

    private fun configureUpdateTask(
        project: Project,
        versionCatalogConfig: VersionCatalogConfig,
        reportJson: RegularFileProperty
    ) {
        val catalogUpdatesTask =
            project.tasks.register(
                "${UPDATE_TASK_NAME}${versionCatalogConfig.name.capitalize()}",
                VersionCatalogUpdateTask::class.java
            )

        catalogUpdatesTask.configure { task ->
            task.reportJson.set(reportJson)
            task.pins.set(
                project.provider {
                    project.objects.newInstance(
                        PinsConfigurationInput::class.java,
                        versionCatalogConfig.pins
                    )
                }
            )
            task.keep.set(
                project.provider {
                    project.objects.newInstance(
                        KeepConfigurationInput::class.java,
                        versionCatalogConfig.keep
                    )
                }
            )
            task.sortByKey.set(versionCatalogConfig.sortByKey)
            task.catalogFile.set(versionCatalogConfig.catalogFile.asFile)
            task.buildScriptArtifacts.set(createBuildScriptArtifactProperty(project))
            task.doLast {
                it.logger.warn("\nA new experimental resolver for dependencies is available, see https://github.com/littlerobots/version-catalog-update-plugin/pull/125 for more details")
                it.logger.warn("Please try it out on your project and report any issues you encounter at https://github.com/littlerobots/version-catalog-update-plugin/issues")
            }
        }

        project.pluginManager.withPlugin(VERSIONS_PLUGIN_ID) {
            val dependencyUpdatesTask =
                project.tasks.named(DEPENDENCY_UPDATES_TASK_NAME)
            catalogUpdatesTask.configure {
                it.dependsOn(dependencyUpdatesTask)
            }
        }

        project.afterEvaluate {
            catalogUpdatesTask.configure {
                if (it.enabled && !it.reportJson.isPresent) {
                    if (!project.pluginManager.hasPlugin(VERSIONS_PLUGIN_ID)) {
                        throw IllegalStateException("com.github.ben-manes.versions needs to be applied as a plugin")
                    }
                }
            }
        }
    }
}

private fun VersionCatalogConfig.applyDefaultSettings(extension: VersionCatalogUpdateExtension): VersionCatalogConfig {
    sortByKey.convention(extension.sortByKey)
    pins.applyDefaultSettings(extension.pins)
    keep.applyDefaultSettings(extension.keep)
    keep.keepUnusedLibraries.convention(extension.keep.keepUnusedLibraries)
    keep.keepUnusedVersions.convention(extension.keep.keepUnusedVersions)
    keep.keepUnusedPlugins.convention(extension.keep.keepUnusedPlugins)
    return this
}

private fun VersionRefConfiguration.applyDefaultSettings(source: VersionRefConfiguration) {
    libraries.convention(source.libraries)
    versions.convention(source.versions)
    plugins.convention(source.plugins)
    groups.convention(source.groups)
}
