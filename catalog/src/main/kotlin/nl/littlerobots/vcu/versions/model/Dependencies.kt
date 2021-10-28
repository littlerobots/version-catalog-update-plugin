package nl.littlerobots.vcu.versions.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Dependencies(val dependencies: List<Dependency>)