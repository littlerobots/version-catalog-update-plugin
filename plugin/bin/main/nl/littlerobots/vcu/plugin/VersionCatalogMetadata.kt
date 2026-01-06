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

import nl.littlerobots.vcu.model.Comments
import nl.littlerobots.vcu.model.VersionCatalog
import org.gradle.api.artifacts.ModuleIdentifier

private val PIN_REGEX = Regex("#\\s?(?:@pin|@pinned)(?:\$|\\s.*)")
private val KEEP_REGEX = Regex("#\\s?@keep(?:\$|\\s.*)")

internal fun Comments.getPinnedKeys(): Set<String> = getMatchingKeys(PIN_REGEX)

internal fun Comments.getKeptKeys(): Set<String> = getMatchingKeys(KEEP_REGEX)

internal fun getPinnedRefsFromComments(currentCatalog: VersionCatalog): Set<VersionCatalogRef> =
    getRefsFromComments(currentCatalog) {
        it.getPinnedKeys()
    }

internal fun getKeepRefsFromComments(currentCatalog: VersionCatalog): Set<VersionCatalogRef> =
    getRefsFromComments(currentCatalog) {
        it.getKeptKeys()
    }

private fun getRefsFromComments(
    currentCatalog: VersionCatalog,
    getRefs: (Comments) -> Set<String>,
): Set<VersionCatalogRef> =
    getRefs(currentCatalog.versionComments)
        .map {
            VersionRef(it)
        }.toSet() +
        getRefs(currentCatalog.libraryComments)
            .mapNotNull {
                currentCatalog.libraries[it]
            }.map {
                LibraryRef(
                    object : ModuleIdentifier {
                        override fun getGroup(): String = it.group

                        override fun getName(): String = it.name
                    },
                )
            }.toSet() +
        getRefs(currentCatalog.pluginComments)
            .mapNotNull {
                currentCatalog.plugins[it]
            }.map {
                PluginRef(it.id)
            }.toSet()

private fun Comments.getMatchingKeys(regex: Regex): Set<String> =
    entryComments
        .filter {
            it.value.any { comment ->
                comment.matches(regex)
            }
        }.map {
            it.key
        }.toSet()
