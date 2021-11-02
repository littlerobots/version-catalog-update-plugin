# Version catalog update plugin
[![Maven Central](https://img.shields.io/maven-central/v/nl.littlerobots.vcu/plugin)](https://search.maven.org/search?q=g:nl.littlerobots.vcu%20a:plugin)

This plugin helps to keep the versions in a Gradle [version catalog toml file](https://docs.gradle.org/current/userguide/platforms.html) up to date.
The version updates are determined by the [versions plugin](https://github.com/ben-manes/gradle-versions-plugin).

# Getting started
This plugin requires Gradle 7.2 or up. Currently [version catalogs](https://docs.gradle.org/current/userguide/platforms.html) are a Gradle incubating feature, check out the documentation on how to enable this feature.

The [versions plugin](https://github.com/ben-manes/gradle-versions-plugin) needs to be applied in the root `build.gradle` or `build.gradle.kts` build file.

The version catalog plugin (including its snapshots) is hosted on Maven Central. Be sure to add `mavenCentral()` to your plugin repositories, for example in `settings.gradle`:

```
pluginManagement {
 dependencyResolutionManagement {
    repositories {
      mavenCentral()
    }
  }
}
```

The version catalog update plugin then needs to be applied after the versions plugin.

```
plugins {
  id "com.github.ben-manes.versions" version "0.39.0"
  id "nl.littlerobots.version-catalog-update" version "<latest version>"
}
```

When using the plugins block, the classpath dependency is `nl.littlerobots.vcu:plugin:<version>`

## Creating a `libs.versions.toml` file

If you don't have a catalog file yet, one can be created by using the `--create` command line option:

```
./gradlew versionCatalogUpdate --create
```

Note that this will use the latest applicable versions as detected / configured by the versions plugin. After running the build with `--create` `gradle/libs.version.toml` can be edited needed.
You can rename and remove keys that are not applicable; the versions plugin may include dependencies that are internal to Gradle or added by other means, such as plugins adding a dependency.

The plugin will attempt to create versions for artifacts in the same group with a common version.

The catalog will be updated with the latest available version as determined by the versions plugin. [You can configure the versions plugin](https://github.com/ben-manes/gradle-versions-plugin#rejectversionsif-and-componentselection)
on what versions are acceptable. A common case is to reject unstable versions like alphas.

After you have created the `libs.versions.toml` file you can update your dependency references to use the catalog in stead of direct dependency declarations.

## Updating the `libs.versions.toml` file
To update the catalog file at any time run `./gradlew versionCatalogUpdate`. This takes care of the following:

* New available versions will be updated in the catalog, retaining existing version groups if they exist
* Keys used in `versions`, `libraries`, `bundles` and `plugins` will be sorted by name [configurable](#configuration)
* `bundles` will be updated so that they only contain valid keys. Keys in a bundle will be sorted.

By default no new entries will be added to the catalog, but unused entries will be removed. Any dependency that is not reported by the versions plugin, but still appears
in the version catalog file will be considered unused. This is [configurable](#configuration).

## Configuration
The plugin can be configured using the `versionCatalogUpdate` block in the build file:

```
versionCatalogUpdate {
  sortyByKey = true
  addDependencies = false
  keepUnused = false
}
```

In addition, you can override configured values using command line arguments. Use `./gradlew versionCatalogUpdate --add` to add dependencies or `./gradlew --keepUnused` (or both).

## Snapshot versions
For snapshots versions add the Sonatype snapshot repository `https://oss.sonatype.org/content/repositories/snapshots/`.

## Known issues and limitations
* When adding dependencies, "internal" dependencies or plugins might be added to the catalog. It is recommended to leave the default set to `false` for that reason. Usually after the initial version catalog has been setup, new dependencies should be added to the toml file manually anyway.
