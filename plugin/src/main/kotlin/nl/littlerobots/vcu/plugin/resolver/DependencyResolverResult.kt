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
package nl.littlerobots.vcu.plugin.resolver

import nl.littlerobots.vcu.model.Library
import nl.littlerobots.vcu.model.Plugin
import nl.littlerobots.vcu.model.VersionCatalog

internal data class DependencyResolverResult(
    /**
     * Updated version catalog
     */
    val versionCatalog: VersionCatalog,
    /**
     * Versions that cannot be resolved for updating
     */
    val unresolved: DependencyCollection,
    /**
     * Latest acceptable versions for entries with an incorrect / unresolvable version
     * in the version catalog.
     */
    val exceeded: DependencyCollection,
    /**
     * Versions that could be updated
     */
    val richVersions: DependencyCollection
) {
    internal data class DependencyCollection(
        val libraries: Collection<Library>,
        val plugins: Collection<Plugin>
    )
}
