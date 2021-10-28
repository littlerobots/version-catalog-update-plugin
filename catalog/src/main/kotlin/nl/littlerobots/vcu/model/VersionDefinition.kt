package nl.littlerobots.vcu.model

sealed class VersionDefinition {
    data class Simple(val version: String) : VersionDefinition()
    data class Reference(val ref: String) : VersionDefinition()
    data class Condition(val definition: Map<String, String>) : VersionDefinition()
}