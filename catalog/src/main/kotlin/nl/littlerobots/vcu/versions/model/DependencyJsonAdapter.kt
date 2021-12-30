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
package nl.littlerobots.vcu.versions.model

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson

class DependencyJsonAdapter() {
    @FromJson
    fun fromJson(dependencyJson: DependencyJson): Dependency {
        return if (dependencyJson.latest != null) {
            ExceededDependency(dependencyJson.group, dependencyJson.name, dependencyJson.version, dependencyJson.projectUrl, dependencyJson.latest)
        } else if (dependencyJson.available != null) {
            OutdatedDependency(dependencyJson.group, dependencyJson.name, dependencyJson.version, dependencyJson.projectUrl, dependencyJson.available)
        } else {
            // not always "current" but good enough for our purposes
            CurrentDependency(dependencyJson.group, dependencyJson.name, dependencyJson.version, dependencyJson.projectUrl)
        }
    }

    @ToJson
    @Suppress("UNUSED_PARAMETER")
    fun toJson(unused: Dependency): Map<String, Any?> {
        throw NotImplementedError()
    }
}

@JsonClass(generateAdapter = true)
data class DependencyJson(
    val group: String,
    val name: String,
    val version: String,
    val latest: String?,
    val projectUrl: String?,
    val available: Map<String, String?>? = null
)
