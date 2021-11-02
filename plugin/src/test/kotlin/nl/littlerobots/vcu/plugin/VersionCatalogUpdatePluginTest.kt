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

import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertNotNull
import org.junit.Test

class VersionCatalogUpdatePluginTest {
    @Test(expected = UnknownTaskException::class)
    fun `plugin requires versions plugin`() {
        val project: Project = ProjectBuilder.builder().build()
        project.pluginManager.apply("nl.littlerobots.version-catalog-update")
        project.tasks.getByName(TASK_NAME)
    }

    @Test
    fun `adds VersionCatalogUpdateTask and sets report path`() {
        val project: Project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.github.ben-manes.versions")
        project.pluginManager.apply("nl.littlerobots.version-catalog-update")
        // force creation and configuration of dependent task
        project.tasks.getByName("dependencyUpdates")

        val task = project.tasks.getByName(TASK_NAME) as VersionCatalogUpdateTask
        assertNotNull(task.reportJson.orNull)
    }
}
