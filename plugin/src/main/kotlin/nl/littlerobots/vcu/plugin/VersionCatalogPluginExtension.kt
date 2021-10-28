package nl.littlerobots.vcu.plugin

import org.gradle.api.provider.Property

interface VersionCatalogPluginExtension {
    val sortByKey: Property<Boolean>
    val addDependencies: Property<Boolean>
    val keepUnused: Property<Boolean>
}