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

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
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
            return
        }

        val extension = project.extensions.create(EXTENSION_NAME, VersionCatalogUpdateExtension::class.java)

        val reportJson = project.objects.fileProperty()

        val catalogUpdatesTask = project.tasks.register(UPDATE_TASK_NAME, VersionCatalogUpdateTask::class.java)
        val catalogFormatTask = project.tasks.register(FORMAT_TASK_NAME, VersionCatalogFormatTask::class.java)

        catalogUpdatesTask.configure { task ->
            task.reportJson.set(reportJson)
            task.pins.set(project.objects.newInstance(PinsConfigurationInput::class.java, extension.pins))
            task.keep.set(project.objects.newInstance(KeepConfigurationInput::class.java, extension.keep))
            task.sortByKey.set(extension.sortByKey)

            if (!task.catalogFile.isPresent) {
                task.catalogFile.set(project.rootProject.file("gradle/libs.versions.toml"))
            }
        }

        catalogFormatTask.configure { task ->
            task.sortByKey.set(extension.sortByKey)
            task.keep.set(project.objects.newInstance(KeepConfigurationInput::class.java, extension.keep))

            if (!task.catalogFile.isPresent) {
                task.catalogFile.set(project.rootProject.file("gradle/libs.versions.toml"))
            }
        }

        project.pluginManager.withPlugin(VERSIONS_PLUGIN_ID) {
            val dependencyUpdatesTask =
                project.tasks.named(DEPENDENCY_UPDATES_TASK_NAME, DependencyUpdatesTask::class.java) {
                    reportJson.set(File(project.file(it.outputDir), "${it.reportfileName}.json"))
                    it.outputFormatter = "json,xml,plain"
                    it.checkConstraints = true
                    it.checkBuildEnvironmentConstraints = true
                }
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
