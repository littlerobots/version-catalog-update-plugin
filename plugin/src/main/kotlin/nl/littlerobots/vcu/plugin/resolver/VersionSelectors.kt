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

class VersionSelectors {
    companion object {
        private fun isStable(version: String): Boolean {
            val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { version.toUpperCase().contains(it) }
            val regex = "^[0-9,.v-]+(-r)?$".toRegex()
            val isStable = stableKeyword || regex.matches(version)
            return isStable
        }

        val LATEST = object : ModuleVersionSelector {
            override fun select(candidate: ModuleVersionCandidate): Boolean {
                return true
            }
        }
        val STABLE = object : ModuleVersionSelector {
            override fun select(candidate: ModuleVersionCandidate): Boolean {
                return isStable(candidate.candidate.version)
            }
        }
        val DEFAULT = object : ModuleVersionSelector {
            override fun select(candidate: ModuleVersionCandidate): Boolean {
                return (isStable(candidate.candidate.version) && isStable(candidate.currentVersion)) || (
                    !isStable(
                        candidate.candidate.version
                    ) && !isStable(candidate.currentVersion)
                    )
            }
        }
    }
}
