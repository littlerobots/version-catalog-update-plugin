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

internal const val TASK_NAME = "versionCatalogUpdate"
internal const val EXTENSION_NAME = "versionCatalogUpdate"
private const val DEPENDENCY_UPDATES_TASK_NAME = "dependencyUpdates"

class VersionCatalogPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (GradleVersion.current() < GradleVersion.version("7.2")) {
            throw GradleException("Gradle 7.2 or greater is required for this plugin")
        }
        if (!project.pluginManager.hasPlugin("com.github.ben-manes.versions")) {
            throw IllegalStateException("com.github.ben-manes.versions needs to be added before this plugin")
        }

        if (project != project.rootProject) {
            throw IllegalStateException("Should be applied to the roort project only")
        }

        val extension = project.extensions.create(EXTENSION_NAME, VersionCatalogPluginExtension::class.java)

        // TODO see if we can configure the output format in other ways or if this is fine
        System.setProperty("outputFormatter", "json,xml,plain")

        val reportJson = project.objects.property(File::class.java)

        val dependencyUpdatesTask =
            project.tasks.named(DEPENDENCY_UPDATES_TASK_NAME, DependencyUpdatesTask::class.java) {
                reportJson.set(File(project.file(it.outputDir), "${it.reportfileName}.json"))
            }

        project.tasks.register(TASK_NAME, VersionCatalogUpdateTask::class.java) {
            it.reportJson.set(reportJson)
            if (!it.catalogFile.isPresent) {
                it.catalogFile.set(project.rootProject.file("gradle/libs.versions.toml"))
            }
            it.dependsOn(dependencyUpdatesTask)
        }
    }
}
