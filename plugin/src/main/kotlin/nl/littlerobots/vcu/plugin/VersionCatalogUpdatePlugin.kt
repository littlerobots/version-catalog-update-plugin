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

import nl.littlerobots.vcu.plugin.resolver.VersionSelectors
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.util.GradleVersion

internal const val UPDATE_TASK_NAME = "versionCatalogUpdate"
internal const val FORMAT_TASK_NAME = "versionCatalogFormat"
internal const val EXTENSION_NAME = "versionCatalogUpdate"

class VersionCatalogUpdatePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (GradleVersion.current() < GradleVersion.version("7.2")) {
            throw GradleException("Gradle 7.2 or greater is required for this plugin")
        }

        if (project != project.rootProject) {
            throw IllegalStateException("Should be applied to the root project only")
        }

        val extension = project.extensions.create(EXTENSION_NAME, VersionCatalogUpdateExtension::class.java)

        val defaultVersionCatalog = project.objects.newInstance(VersionCatalogConfig::class.java, "")
            .applyDefaultSettings(extension)

        defaultVersionCatalog.catalogFile.set(
            extension.catalogFile.convention(project.layout.projectDirectory.file("gradle/libs.versions.toml"))
        )

        configureTasks(project, defaultVersionCatalog)

        extension.versionCatalogs.all {
            configureTasks(project, it.applyDefaultSettings(extension))
        }
    }

    private fun configureTasks(
        project: Project,
        versionCatalogConfig: VersionCatalogConfig,
    ) {
        configureExperimentalUpdateTask(project, versionCatalogConfig)
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
                VersionCatalogUpdateTask::class.java
            )
        catalogUpdatesTask.configure { task ->
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
            val versionSelector = versionCatalogConfig.versionSelector.orElse(extension.versionSelector)
                .orElse(VersionSelectors.PREFER_STABLE)
            task.versionSelector.set(versionSelector)
        }
    }
}

private fun VersionCatalogConfig.applyDefaultSettings(extension: VersionCatalogUpdateExtension): VersionCatalogConfig {
    sortByKey.convention(extension.sortByKey)
    pins.applyDefaultSettings(extension.pins)
    keep.applyDefaultSettings(extension.keep)
    keep.keepUnusedVersions.convention(extension.keep.keepUnusedVersions)
    return this
}

private fun PinConfiguration.applyDefaultSettings(source: PinConfiguration) {
    libraries.convention(source.libraries)
    versions.convention(source.versions)
    plugins.convention(source.plugins)
    groups.convention(source.groups)
}

private fun KeepConfiguration.applyDefaultSettings(source: KeepConfiguration) {
    versions.convention(source.versions)
}
