package nl.littlerobots.vcu.versions.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class VersionsReport(
    // parse defensively, allowing for missing input
    val current: Dependencies = Dependencies(emptyList()),
    val exceeded: Dependencies = Dependencies(emptyList()),
    val outdated: Dependencies = Dependencies(emptyList()),
    val unresolved: Dependencies = Dependencies(emptyList())
)