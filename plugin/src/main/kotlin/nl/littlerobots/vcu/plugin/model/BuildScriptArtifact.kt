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
package nl.littlerobots.vcu.plugin.model

import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.provider.SetProperty
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier
import java.io.File
import java.io.Serializable

internal data class BuildScriptArtifact(val module: String, val file: File) : Serializable

internal fun createBuildScriptArtifactProperty(project: Project): SetProperty<BuildScriptArtifact> {
    val property = project.objects.setProperty(BuildScriptArtifact::class.java)
    property.value(
        project.provider {
            getResolvedBuildScriptArtifacts(project).mapNotNull { resolved ->
                (resolved.id as? ModuleComponentArtifactIdentifier)?.let {
                    BuildScriptArtifact(
                        "${it.componentIdentifier.moduleIdentifier.group}:${it.componentIdentifier.moduleIdentifier.name}",
                        resolved.file
                    )
                }
            }.toSet()
        }
    )
    return property
}

/**
 * Get the resolved build script dependencies for the given project and any subprojects
 *
 * @param project project to get the dependencies for
 * @return a set of [ResolvedArtifact], may be empty
 */
private fun getResolvedBuildScriptArtifacts(project: Project): Set<ResolvedArtifact> {
    val projectResolvedArtifacts =
        project.buildscript.configurations.firstOrNull()?.resolvedConfiguration?.resolvedArtifacts?.filterNotNull()
            ?.toSet()
            ?: (emptySet())
    return if (project.subprojects.isNotEmpty()) {
        project.subprojects.map { getResolvedBuildScriptArtifacts(it) }.flatten().toSet() + projectResolvedArtifacts
    } else {
        projectResolvedArtifacts
    }
}
