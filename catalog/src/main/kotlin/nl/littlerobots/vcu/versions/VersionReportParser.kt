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
import nl.littlerobots.vcu.model.resolveVersions
import nl.littlerobots.vcu.toml.toTomlKey
import nl.littlerobots.vcu.versions.model.Dependency
import nl.littlerobots.vcu.versions.model.DependencyJsonAdapter
import nl.littlerobots.vcu.versions.model.VersionsReport
import nl.littlerobots.vcu.versions.model.module
import okio.buffer
import okio.source
import java.io.InputStream

private const val GRADLE_PLUGIN_MODULE_POST_FIX = ".gradle.plugin"

class VersionReportParser {
    private val moshi = Moshi.Builder()
        .add(DependencyJsonAdapter())
        .addLast(KotlinJsonAdapterFactory()).build()

    /**
     * Generate a version Catalog file from a version report
     *
     * @param versionsJson the report json input stream
     * @param currentCatalog the version catalog to check "current" versions against
     * @param useLatestVersions whether to add the latest versions in the returned [VersionCatalog] or the current versions. Defaults to true.
     */
    fun generateCatalog(
        versionsJson: InputStream,
        currentCatalog: VersionCatalog,
        useLatestVersions: Boolean = true
    ): VersionReportResult {
        val adapter = moshi.adapter(VersionsReport::class.java)
        val report = versionsJson.use {
            adapter.fromJson(it.source().buffer())!!
        }

        val dependencies = (
            report.current.dependencies.filterUnusedTransitive(currentCatalog) +
                report.exceeded.dependencies +
                report.outdated.dependencies +
                report.unresolved.dependencies
            ).toSortedSet(
            compareBy({ it.group }, { it.name })
        )

        val shortNames = dependencies.groupBy {
            it.tomlKey
        }

        val libraries =
            dependencies.filterNot { it.name.endsWith(GRADLE_PLUGIN_MODULE_POST_FIX) }.associate { dependency ->
                val key = if (shortNames[dependency.tomlKey]!!.size == 1) {
                    dependency.tomlKey
                } else {
                    "${dependency.group}.${dependency.name}".toTomlKey()
                }
                key to Library(
                    group = dependency.group,
                    name = dependency.name,
                    version = VersionDefinition.Simple(if (useLatestVersions) dependency.latestVersion else dependency.currentVersion)
                )
            }

        val plugins = dependencies.filter { it.name.endsWith(GRADLE_PLUGIN_MODULE_POST_FIX) }.associate { dependency ->
            val pluginId = dependency.name.dropLast(GRADLE_PLUGIN_MODULE_POST_FIX.length)
            pluginId.toTomlKey() to Plugin(
                id = pluginId,
                version = VersionDefinition.Simple(if (useLatestVersions) dependency.latestVersion else dependency.currentVersion)
            )
        }

        return VersionReportResult(
            report.exceeded.dependencies.toSet(),
            report.outdated.dependencies.toSet(),
            VersionCatalog(emptyMap(), libraries, emptyMap(), plugins)
        )
    }

    /**
     * Transitive dependencies that are not directly used are reported as "current".
     * If any of these "current" versions do not match what's in the supplied catalog, it must be unused and we filter
     * that dependency.
     *
     * @param catalog the catalog to check the current version for
     **/
    private fun List<Dependency>.filterUnusedTransitive(catalog: VersionCatalog): List<Dependency> {
        val resolvedCatalog = catalog.resolveVersions()
        return filterNot { dependency ->
            // if we can find a definition for this module w/ an exact version that is different from
            // the dependency version, omit it.
            resolvedCatalog.libraries.values.any {
                it.module == dependency.module &&
                    it.version is VersionDefinition.Simple &&
                    it.version.version != dependency.currentVersion
            }
        }
    }
}

data class VersionReportResult(
    val exceeded: Set<Dependency>,
    val outdated: Set<Dependency>,
    val catalog: VersionCatalog
)

private val Dependency.tomlKey: String
    get() {
        val lastGroupElement = group.split('.').last()
        return if (lastGroupElement == name) {
            group
        } else {
            "$group.$name"
        }.toTomlKey()
    }
