package nl.littlerobots.vcu.versions.model

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonClass
import com.squareup.moshi.ToJson

class DependencyJsonAdapter() {
    @FromJson
    fun fromJson(dependencyJson: DependencyJson): Dependency {
        val version = dependencyJson.available?.values?.filterNotNull()?.first() ?: dependencyJson.version
        return Dependency(dependencyJson.group, dependencyJson.name, version)
    }

    @ToJson
    fun toJson(dependency: Dependency): Map<String, Any?> {
        throw NotImplementedError()
    }
}

@JsonClass(generateAdapter = true)
data class DependencyJson(
    val group: String,
    val name: String,
    val version: String,
    val available: Map<String, String?>? = null
)