# Version catalog update plugin
[![gradlePluginPortal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/nl/littlerobots/version-catalog-update/nl.littlerobots.version-catalog-update.gradle.plugin/maven-metadata.xml.svg?label=gradlePluginPortal)](https://plugins.gradle.org/plugin/nl.littlerobots.version-catalog-update)
[![Maven Central](https://img.shields.io/maven-central/v/nl.littlerobots.vcu/plugin)](https://search.maven.org/search?q=g:nl.littlerobots.vcu%20a:plugin)

This plugin helps to keep the versions in a Gradle [version catalog toml file](https://docs.gradle.org/current/userguide/platforms.html) up to date.

# Getting started
This plugin requires Gradle 7.2 or up. [Version catalogs](https://docs.gradle.org/current/userguide/platforms.html) are a Gradle incubating feature for versions lower than 7.4, check out the documentation on how to enable this feature for those versions.

The version catalog plugin is hosted on the [Gradle Plugin portal](https://plugins.gradle.org/plugin/nl.littlerobots.version-catalog-update) and Maven Central. [Snapshots](#snapshot-versions) are only published to Maven Central. To use Maven Central, be sure to include
it in your plugin repositories, for example in `settings.gradle`:

```
pluginManagement {
    repositories {
      mavenCentral()
    }
}
```
In your `build.gradle[.kts]`:

<details open>
<summary>build.gradle</summary>

```groovy
plugins {
  id "nl.littlerobots.version-catalog-update" version "<latest version>"
}
```

</details>

<details>
<summary>build.gradle.kts</summary>

```kotlin
plugins {
  id("nl.littlerobots.version-catalog-update") version "<latest version>"
}
```

</details>

When using the plugins block, the classpath dependency is `nl.littlerobots.vcu:plugin:<version>`

## Upgrading
Earlier versions of this plugin used the  [versions plugin](https://github.com/ben-manes/gradle-versions-plugin) to resolve
dependencies. This plugin is no longer required and version selection is configured using the `versionSelector` option.

# Selecting dependency versions
By default, a stable version will be selected, based on the version name, unless the current version is already considered unstable and a newer version exists. A version is considered stable if it isn't a snapshot or alpha version.
See the [VersionSelectors](https://github.com/littlerobots/version-catalog-update-plugin/blob/main/plugin/src/main/kotlin/nl/littlerobots/vcu/plugin/resolver/VersionSelectors.kt#L20) class for the exact definition.

The selection can be customized by configuring a version selector:

<details open>
<summary>build.gradle</summary>

```groovy
versionCatalogUpdate {
    versionSelector {
        // here 'it' is a ModuleVersionCandidate that can be used to determine if the version
        // is allowed, returning true if it is.
        !(it.candidate.version.contains('SNAPSHOT') || it.candidate.version.contains('ALPHA'))
    }
}
```

</details>

<details>
<summary>build.gradle.kts</summary>

```kotlin
import nl.littlerobots.vcu.plugin.versionSelector

versionCatalogUpdate {
    versionSelector {
        // here 'it' is a ModuleVersionCandidate that can be used to determine if the version
        // is allowed, returning true if it is.
        !(it.candidate.version.contains("SNAPSHOT") || it.candidate.version.contains("ALPHA"))
    }
}
```
</details>

Or use one of the predefined selectors:

```kotlin
import nl.littlerobots.vcu.plugin.resolver.VersionSelectors

// LATEST to accept any latest version, STABLE to select only stable versions or
// PREFER_STABLE (default setting) to accept unstable versions only if the catalog already uses an unstable version
versionSelector(VersionSelectors.STABLE)
```

## Updating the `libs.versions.toml` file
To update the catalog file at any time run `./gradlew versionCatalogUpdate`. This takes care of the following:

* New available versions will be updated in the catalog, retaining existing version groups if they exist
* Keys used in `versions`, `libraries`, `bundles` and `plugins` will be sorted by name [configurable](#configuration)
* `bundles` will be updated so that they only contain valid keys. Keys in a bundle will be sorted.

Versions that are not used and not marked to be kept will be removed when the catalog is updated. This is [configurable](#configuration).

### Interactive mode
Updating all dependencies at once is without testing is generally not recommended. When using a version control system, changes to the version catalog can be rolled back
by comparing a diff, but for large updates this may be inconvenient. In these cases, interactive mode might help.

When running `./gradlew versionCatalogUpdate --interactive` the `libs.versions.toml` file will not be directly be updated, in stead
a `libs.versions.updates.toml` file will be created containing the entries that would be updated and any pinned entries
that can be updated. This file uses the short form dependency notation without any `version.ref`s. Pinned entries are commented
by default, all other entries are uncommented. To skip updating an entry in the TOML file it can be commented out or removed completely.
It's also possible to edit the entry if that's desired.

To apply the changes to the `libs.versions.toml` file, run `./gradlew versionCatalogApplyUpdates`. This will also
update `version.ref`s and `versions` in the same way as `versionCatalogUpdate` would.

Note that any comments and any other TOML tables than `[libraries]` and `[plugins]` will be ignored when applying the changes.

<details>
<summary>Example libs.versions.updates.toml</summary>

```
# Version catalog updates generated at 2022-08-19T16:00:29.757349
#
# Contents of this file will be applied to libs.versions.toml when running versionCatalogApplyUpdates.
#
# Comments will not be applied to the version catalog when updating.
# To prevent a version upgrade, comment out the entry or remove it.
#
[libraries]
# @pinned version 4.9.3 (antlr) --> 4.10.1
#antlr = "org.antlr:antlr4:4.10.1"
# From version 2.5.4 (asciidoctorj) --> 2.5.5
asciidoctorj = "org.asciidoctor:asciidoctorj:2.5.5"
# From version 2.1.2 (asciidoctorjPdf) --> 2.1.6
asciidoctorjPdf = "org.asciidoctor:asciidoctorj-pdf:2.1.6"

[plugins]
# Updated from version 1.20.0
detekt = "io.gitlab.arturbosch.detekt:1.21.0"
# Updated from version 1.7.0
dokka = "org.jetbrains.dokka:1.7.10"
```

</details>

### Formatting only
Running the `versionCatalogUpdate` task will also format the catalog. For formatting libraries and plugins, the plugin will use
the "short" syntax if possible (`com.group:module:version` for libraries). If a `version.ref` is applied, it will be kept.
By default, entries in the version catalog will be sorted alphabetically. Note that during formatting some whitespace might
be lost due to parsing the toml file and rewriting it.

To format the existing `libs.versions.toml` file without updating library versions, you can run `./gradlew versionCatalogFormat`.
This comes in handy when you added an entry to the version catalog, but aren't ready yet to update any dependencies.

## Informational output
In some cases the plugin will output some additional messages when checking for updates.

### Dependencies with invalid versions
The versions plugin will report dependencies with invalid versions, which are dependencies that don't match the declared version for some reason. This can happen when you specify a version that does not exist, but some other dependency is pulling in an existing version of the same dependency.
In that case, your build will not fail, but the version used isn't the version that was specified in the catalog file. The plugin will show a warning and update the catalog to the correct version in that case.

<details>
<summary>example warning</summary>

```
There are libraries with invalid versions that could be updated:
 - androidx.activity:activity-compose (androidx-activity-activity-compose) -> 1.9.2
```
</details>

### Rich versions
A version may be specified as a rich version in the toml file. In that case the plugin cannot determine if the dependency should be updated and will leave it alone.

<details>
<summary>example output</summary>

```
There are libraries using a version condition that could be updated:
 - androidx.appcompat:appcompat (androidx-appCompat ref:appCompat) -> 1.4.1
```
</details>

### Pinned versions (see below)
When an entry in the catalog is affected by a pin that you have configured, the plugin will leave that entry alone, but tell you that there's a possible update.

<details>
<summary>example output</summary>

```
There are updates available for pinned entries in the version catalog:
 - androidx.appcompat:appcompat (androidx-appcompat) 1.4.0 -> 1.4.1
```
</details>


## Configuration
The plugin can be configured using the `versionCatalogUpdate` block in the build file:

<details open>
<summary>build.gradle</summary>

```groovy
versionCatalogUpdate {
    // sort the catalog by key (default is true)
    sortByKey = true
    // Referenced that are pinned are not automatically updated.
    // They are also not automatically kept however (use keep for that).
    pin {
        // pins all libraries and plugins using the given versions
        versions = ["my-version-name", "other-version"]
        // pins specific libraries that are in the version catalog
        libraries = [libs.my.library.reference, libs.my.other.library.reference]
        // pins specific plugins that are in the version catalog
        plugins = [libs.plugins.my.plugin, libs.plugins.my.other.plugin]
        // pins all libraries (not plugins) for the given groups
        groups = ["com.somegroup", "com.someothergroup"]
    }
    keep {
        // keep has the same options as pin to keep specific entries
        // note that for versions it will ONLY keep the specified version, not all
        // entries that reference it.
        versions = ["my-version-name", "other-version"]
        // keep versions without any library or plugin reference
        keepUnusedVersions = true
    }

    // Return true in the version selector function to accept the updated version
    // For more details refer to the chapter earlier in this README
    versionSelector {
        ///
    }
}
```

</details>
<details>
<summary>build.gradle.kts</summary>

```kotlin
versionCatalogUpdate {
    // sort the catalog by key (default is true)
    sortByKey.set(true)
    // Referenced that are pinned are not automatically updated.
    // They are also not automatically kept however (use keep for that).
    pin {
        // pins all libraries and plugins using the given versions
        versions.add("my-version-name")
        versions.add("other-version")
        // pins specific libraries that are in the version catalog
        libraries.add(libs.my.library.reference)
        libraries.add(libs.my.other.library.reference)
        // pins specific plugins that are in the version catalog
        plugins.add(libs.plugins.my.plugin)
        plugins.add(libs.plugins.my.other.plugin)
        // pins all libraries (not plugins) for the given groups
        groups.add("com.somegroup")
        groups.add("com.someothergroup")
    }
    keep {
        // keep has the same options as pin to keep specific entries
        versions.add("my-version-name")
        versions.add("other-version")
        // keep versions without any library or plugin reference
        keepUnusedVersions.set(true)
    }

    // Return true in the version selector function to accept the updated version
    // For more details refer to the chapter earlier in this README
    versionSelector {
        ///
    }
}
```

</details>

### Keeping and pinning entries with a TOML comment
To keep an entry in the TOML file, or pin it to a specific version you can also use annotations in TOML comments.
This functions in the same way as specifying the `keep` and `pin` configuration in the build file.
For a `@keep` or `@pin` annotation to be recognised, the comment must start with a single `#`.

```toml
[versions]
# @keep this version, for example because it is not used in a dependency declaration
minSdk = "21"
# Pinning the version will keep every library using this version on 1.6.10
# @pin
kotlin = "1.6.10"

[libraries]
# @pin this library to version 1.0
my-library = "com.example.library:1.0"
```

## Managing additional version catalogs
The default tasks operate on the default version catalog file, `libs.versions.toml` in the `gradle` directory
of a project. Additional version catalogs can be configured within the `versionCatalogUpdate` extension:

<details open>
<summary>build.gradle</summary>

```groovy
versionCatalogUpdate {
    // These options will be set as default for all version catalogs
    sortByKey = true
    // Referenced that are pinned are not automatically updated.
    // They are also not automatically kept however (use keep for that).
    pin {
        ...
    }
    keep {
        ...
    }
    versionCatalogs {
        myOtherCatalog {
            catalogFile = file("catalogs/mycatalog.versions.toml")
            // not sorted
            sortByKey = false
        }
        special {
            catalogFile = file("catalogs/special.versions.toml")
            // overrides the options set above
            keep {
                keepUnusedVersions = true
            }
            versionSelector(VersionSelectors.LATEST)
        }
    }
}
```
</details>
<details>
<summary>build.gradle.kts</summary>

```kotlin
versionCatalogUpdate {
    // These options will be set as default for all version catalogs
    sortByKey.set(true)
    // Referenced that are pinned are not automatically updated.
    // They are also not automatically kept however (use keep for that).
    pin {
        ...
    }
    keep {
        ...
    }
    versionCatalogs {
        create("myOtherCatalog") {
            catalogFile.set(file("catalogs/mycatalog.versions.toml"))
            // not sorted
            sortByKey.set(false)
        }
        create("special") {
            catalogFile.set(file("catalogs/special.versions.toml"))
            // overrides the options set above
            keep {
                keepUnusedVersions.set(true)
            }
            versionSelector(VersionSelectors.LATEST)
        }
    }
}
```
</details>

By configuring additional version catalogs, new tasks in the form of `versionCatalogUpdate<Name>` will get added.
For example, when declaring a `myOtherCatalog` catalog, the tasks `versionCatalogUpdateMyOtherCatalog`, `versionCatalogFormatMyotherCatalog`
and `versionCatalogAppyUpdatesMyOtherCatalog` are configured. These work the same as the default tasks
and have the same available options. Each version catalog definition can specify configuration for
`sortByKey` and the `pin` and `keep` blocks. If not defined, the default options will be applied for those options.

### Changing the default version catalog
By the default the plugin uses `gradle/libs.versions.toml` as the primary version catalog file.
To change the default, configure it in the `versionCatalogUpdate` block:

<details open>
<summary>build.gradle</summary>

```groovy
versionCatalogUpdate {
    catalogFile = file("path/to/the/catalog.toml")
}
```
</details>
<details open>
<summary>build.gradle.kts</summary>

```kotlin
versionCatalogUpdate {
    catalogFile.set(file("path/to/the/catalog.toml"))
}
```
</details>

## Snapshot versions
For snapshots versions add the Sonatype snapshot repository `https://oss.sonatype.org/content/repositories/snapshots/`.

## Known issues and limitations
* The TOML file will be updated and formatted by this plugin; this is by design. If this is undesirable then the  [versions plugin](https://github.com/ben-manes/gradle-versions-plugin) is probably what you are looking for.
