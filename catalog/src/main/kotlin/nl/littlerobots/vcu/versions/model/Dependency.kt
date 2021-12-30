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

sealed class Dependency(
    val group: String,
    val name: String,
    val projectUrl: String?
) {
    abstract val currentVersion: String
    abstract val latestVersion: String
}

class CurrentDependency(
    group: String,
    name: String,
    version: String,
    projectUrl: String?,
) : Dependency(group, name, projectUrl) {
    override val currentVersion: String = version
    override val latestVersion: String = version
}

class ExceededDependency(
    group: String,
    name: String,
    version: String,
    projectUrl: String?,
    latest: String
) : Dependency(group, name, projectUrl) {
    override val currentVersion: String = version
    override val latestVersion: String = latest
}

class OutdatedDependency(
    group: String,
    name: String,
    version: String,
    projectUrl: String?,
    available: Map<String, String?>
) : Dependency(group, name, projectUrl) {
    override val currentVersion: String = version
    override val latestVersion: String = available.values.filterNotNull().first()
}

val Dependency.module: String
    get() = "$group:$name"
