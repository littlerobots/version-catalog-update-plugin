package nl.littlerobots.vcu.versions.model

class Dependency(val group: String, val name: String, val version: String)

val Dependency.module: String
    get() = "${group}:${name}"