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
package nl.littlerobots.vcu.versions

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import nl.littlerobots.vcu.model.Library
import nl.littlerobots.vcu.model.Plugin
import nl.littlerobots.vcu.model.VersionCatalog
import nl.littlerobots.vcu.model.VersionDefinition
import nl.littlerobots.vcu.toml.toTomlKey
import nl.littlerobots.vcu.versions.model.Dependency
import nl.littlerobots.vcu.versions.model.DependencyJsonAdapter
import nl.littlerobots.vcu.versions.model.VersionsReport
import okio.buffer
import okio.source
import java.io.InputStream

private const val GRADLE_PLUGIN_MODULE_POST_FIX = ".gradle.plugin"

class VersionReportParser {
    private val moshi = Moshi.Builder()
        .add(DependencyJsonAdapter())
        .addLast(KotlinJsonAdapterFactory()).build()

    fun generateCatalog(versionsJson: InputStream): VersionCatalog {
        val adapter = moshi.adapter(VersionsReport::class.java)
        val report = versionsJson.use {
            adapter.fromJson(it.source().buffer())!!
        }

        val dependencies = (
            report.current.dependencies +
                report.exceeded.dependencies +
                report.outdated.dependencies +
                report.unresolved.dependencies
            ).toSortedSet(
            compareBy({ it.group }, { it.name })
        )

        val shortNames = dependencies.groupBy {
            it.tomlKey
        }

        val libraries = dependencies.filterNot { it.name.endsWith(GRADLE_PLUGIN_MODULE_POST_FIX) }.associate { dependency ->
            val key = if (shortNames[dependency.tomlKey]!!.size == 1) {
                dependency.tomlKey
            } else {
                "${dependency.group}.${dependency.name}".toTomlKey()
            }
            key to Library(
                group = dependency.group,
                name = dependency.name,
                version = VersionDefinition.Simple(dependency.latestVersion)
            )
        }

        val plugins = dependencies.filter { it.name.endsWith(GRADLE_PLUGIN_MODULE_POST_FIX) }.associate {
            dependency ->
            val pluginId = dependency.name.dropLast(GRADLE_PLUGIN_MODULE_POST_FIX.length)
            pluginId.toTomlKey() to Plugin(id = pluginId, version = VersionDefinition.Simple(dependency.latestVersion))
        }

        return VersionCatalog(emptyMap(), libraries, emptyMap(), plugins)
    }
}

private val Dependency.tomlKey: String
    get() {
        val lastGroupElement = group.split('.').last()
        return if (lastGroupElement == name) {
            group
        } else {
            "$group.$name"
        }.toTomlKey()
    }
