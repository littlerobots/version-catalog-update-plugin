package nl.littlerobots.vcu.model

data class Library(
    val module: String,
    val version: VersionDefinition
) {
    constructor(
        group: String,
        name: String,
        version: VersionDefinition
    ) : this("${group}:${name}", version)

    val group: String by lazy {
        module.split(':').dropLast(1).joinToString()
    }
    val name: String by lazy {
        module.split(':').last()
    }
}