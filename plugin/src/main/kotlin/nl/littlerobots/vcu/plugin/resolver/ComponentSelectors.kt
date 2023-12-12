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

import org.gradle.api.Action

class ComponentSelectors {
    companion object {
        private fun isNonStable(version: String): Boolean {
            val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
            val regex = "^[0-9,.v-]+(-r)?$".toRegex()
            val isStable = stableKeyword || regex.matches(version)
            return !isStable
        }

        val LATEST = Action<ComponentSelectionWithCurrent> { /* nothing */ }
        val STABLE = Action<ComponentSelectionWithCurrent> {
            if (isNonStable(it.candidate.version)) {
                it.reject("${it.candidate.version} is not a stable version")
            }
        }
        val DEFAULT = Action<ComponentSelectionWithCurrent> {
            if (isNonStable(it.candidate.version) && !isNonStable(it.currentVersion)) {
                it.reject("Current version ${it.currentVersion} is stable, update ${it.candidate.version} is non-stable")
            }
        }
    }
}
