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
@file:Suppress("UnstableApiUsage")

package nl.littlerobots.vcu.plugin

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.plugin.use.PluginDependency
import java.io.Serializable
import javax.inject.Inject

abstract class VersionCatalogUpdateExtension {
    @get:Optional
    abstract val sortByKey: Property<Boolean>

    @get:Optional
    abstract val catalogFile: RegularFileProperty

    @get:Nested
    abstract val pins: PinConfiguration

    @get:Nested
    abstract val keep: KeepConfiguration

    @get:Nested
    abstract val versionCatalogs: NamedDomainObjectContainer<VersionCatalogConfig>

    fun pin(action: Action<PinConfiguration>) {
        action.execute(pins)
    }

    fun keep(action: Action<KeepConfiguration>) {
        action.execute(keep)
    }

    fun versionCatalogs(action: Action<NamedDomainObjectContainer<VersionCatalogConfig>>) {
        action.execute(versionCatalogs)
    }
}

abstract class VersionRefConfiguration : Serializable {
    abstract val versions: SetProperty<String>
    abstract val libraries: SetProperty<Provider<MinimalExternalModuleDependency>>
    abstract val plugins: SetProperty<Provider<PluginDependency>>
    abstract val groups: SetProperty<String>
}

abstract class VersionCatalogConfig @Inject constructor(val name: String) {
    abstract val catalogFile: RegularFileProperty
    @get:Optional
    abstract val sortByKey: Property<Boolean>

    @get:Nested
    abstract val pins: PinConfiguration

    @get:Nested
    abstract val keep: KeepConfiguration
}

abstract class PinConfiguration : VersionRefConfiguration()
abstract class KeepConfiguration : VersionRefConfiguration() {
    abstract val keepUnusedVersions: Property<Boolean>
    abstract val keepUnusedLibraries: Property<Boolean>
    abstract val keepUnusedPlugins: Property<Boolean>
}

internal sealed class VersionCatalogRef
internal data class VersionRef(val versionName: String) : VersionCatalogRef()
internal data class LibraryRef(val dependency: ModuleIdentifier) : VersionCatalogRef()
internal data class PluginRef(val pluginId: String) : VersionCatalogRef()
internal data class GroupRef(val group: String) : VersionCatalogRef()

fun Project.versionCatalogUpdate(block: VersionCatalogUpdateExtension.() -> Unit) {
    extensions.configure(VersionCatalogUpdateExtension::class.java, block)
}
