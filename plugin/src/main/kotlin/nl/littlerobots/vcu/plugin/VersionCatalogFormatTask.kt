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
import javax.inject.Inject

abstract class VersionCatalogFormatTask @Inject constructor() : DefaultTask() {
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

    init {
        description = "Formats the libs.versions.toml file."
        group = "Version catalog update"
    }

    @TaskAction
    fun formatCatalogFile() {
        val catalog = VersionCatalogParser().parse(catalogFile.get().asFile.inputStream())
        val keepRefs = this.keepRefs + getKeepRefsFromComments(catalog)

        // run an "update" to group versions
        val updated = catalog.updateFrom(catalog)
            .withKeepUnusedVersions(catalog, keep.orNull?.keepUnusedVersions?.getOrElse(false) ?: false)
            .withKeptVersions(catalog, keepRefs)
            .let {
                if (sortByKey.getOrElse(true)) {
                    it.sortKeys()
                } else {
                    it
                }
            }
        VersionCatalogWriter().write(updated, catalogFile.get().asFile.writer())
    }
}
