package nl.littlerobots.vcu.plugin

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

class VersionCatalogPluginTest {
    @Test
    fun `plugin requires versions plugin`() {
        val project: Project = ProjectBuilder.builder().build()
        try {
            project.pluginManager.apply("nl.littlerobots.version-catalog")
            fail("Should throw")
        } catch (ex: GradleException) {
            assertEquals("com.github.ben-manes.versions needs to be added before this plugin", ex.cause?.message)
        }
    }

    @Test
    fun `adds VersionCatalogUpdateTask and sets report path`() {
        val project: Project = ProjectBuilder.builder().build()
        project.pluginManager.apply("com.github.ben-manes.versions")
        project.pluginManager.apply("nl.littlerobots.version-catalog")
        // force creation and configuration of dependent task
        project.tasks.getByName("dependencyUpdates")

        val task = project.tasks.getByName(TASK_NAME) as VersionCatalogUpdateTask
        assertNotNull(task.reportJson.orNull)
    }
}