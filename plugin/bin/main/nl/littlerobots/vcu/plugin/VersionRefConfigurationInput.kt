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

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.io.Serializable
import javax.inject.Inject

@Suppress("LeakingThis")
abstract class PinsConfigurationInput
    @Inject
    constructor(
        pinConfiguration: PinConfiguration,
    ) : Serializable {
        abstract val versions: ListProperty<String>
        abstract val libraries: ListProperty<ModuleIdentifier>
        abstract val plugins: ListProperty<String>
        abstract val groups: ListProperty<String>

        init {
            versions.set(pinConfiguration.versions)
            libraries.set(
                pinConfiguration.libraries.map { libraries ->
                    libraries.map {
                        it.get().module
                    }
                },
            )
            plugins.set(
                pinConfiguration.plugins.map { plugins ->
                    plugins.map {
                        it.get().pluginId
                    }
                },
            )
            groups.set(pinConfiguration.groups)
        }
    }

@Suppress("LeakingThis")
abstract class KeepConfigurationInput
    @Inject
    constructor(
        keepConfiguration: KeepConfiguration,
    ) {
        abstract val versions: ListProperty<String>
        abstract val keepUnusedVersions: Property<Boolean>

        init {
            keepUnusedVersions.set(keepConfiguration.keepUnusedVersions)
            versions.set(keepConfiguration.versions)
        }
    }

internal fun PinsConfigurationInput.getVersionCatalogRefs(): Set<VersionCatalogRef> =
    (
        versions
            .convention(emptySet())
            .get()
            .map { VersionRef(it) } +
            libraries
                .convention(emptySet())
                .get()
                .map {
                    LibraryRef(it)
                } +
            plugins
                .convention(emptySet())
                .get()
                .map {
                    PluginRef(it)
                } +
            groups
                .convention(emptySet())
                .get()
                .map {
                    GroupRef(it)
                }
    ).toSet()

internal fun KeepConfigurationInput.getVersionCatalogRefs(): Set<VersionCatalogRef> =
    (
        versions
            .convention(emptySet())
            .get()
            .map { VersionRef(it) }
    ).toSet()
