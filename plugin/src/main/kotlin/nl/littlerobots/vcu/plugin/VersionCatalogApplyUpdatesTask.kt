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
import nl.littlerobots.vcu.model.resolveVersions
import nl.littlerobots.vcu.model.sortKeys
import nl.littlerobots.vcu.model.updateFrom
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class VersionCatalogApplyUpdatesTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val sortByKey: Property<Boolean>

    @get:Internal
    abstract val keep: Property<KeepConfigurationInput>

    private val keepRefs by lazy {
        keep.orNull?.getVersionCatalogRefs() ?: emptySet()
    }

    @TaskAction
    fun applyUpdates() {
        val updatesFile = catalogFile.asFile.get().updatesFile
        val versionCatalogParser = VersionCatalogParser()
        if (updatesFile.exists()) {
            val catalog = catalogFile.asFile.get().inputStream().use {
                versionCatalogParser.parse(it)
            }
            val updates = updatesFile.inputStream().use {
                versionCatalogParser.parse(it)
            }
            if (updates.libraries.isEmpty() && updates.plugins.isEmpty()) {
                updatesFile.delete()
                return
            }

            // reconstruct an update from the current catalog + the updates, as if manually edited
            val fullUpdate = catalog.resolveVersions()
                .copy(versions = emptyMap(), bundles = emptyMap())
                .updateFrom(updates, purge = false)
                // undo any version grouping
                .resolveVersions()
                .copy(versions = emptyMap())

            val updatedCatalog = catalog.updateFrom(fullUpdate, addNew = false, purge = false)
                .withKeepUnusedVersions(catalog, keep.orNull?.keepUnusedVersions?.getOrElse(false) ?: false)
                .withKeptVersions(catalog, keepRefs)
                .let {
                    if (sortByKey.getOrElse(true)) {
                        it.sortKeys()
                    } else {
                        it
                    }
                }
            VersionCatalogWriter().write(updatedCatalog, catalogFile.get().asFile.writer())
            updatesFile.delete()
        }
    }

    companion object {
        const val TASK_NAME = "versionCatalogApplyUpdates"
    }
}
