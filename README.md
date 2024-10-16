# Version catalog update plugin
[![gradlePluginPortal](https://img.shields.io/maven-metadata/v/https/plugins.gradle.org/m2/nl/littlerobots/version-catalog-update/nl.littlerobots.version-catalog-update.gradle.plugin/maven-metadata.xml.svg?label=gradlePluginPortal)](https://plugins.gradle.org/plugin/nl.littlerobots.version-catalog-update)
[![Maven Central](https://img.shields.io/maven-central/v/nl.littlerobots.vcu/plugin)](https://search.maven.org/search?q=g:nl.littlerobots.vcu%20a:plugin)

This plugin helps to keep the versions in a Gradle [version catalog toml file](https://docs.gradle.org/current/userguide/platforms.html) up to date.
The version updates are determined by the [versions plugin](https://github.com/ben-manes/gradle-versions-plugin).

# Getting started
This plugin requires Gradle 7.2 or up. [Version catalogs](https://docs.gradle.org/current/userguide/platforms.html) are a Gradle incubating feature for versions lower than 7.4, check out the documentation on how to enable this feature for those versions.

The [versions plugin](https://github.com/ben-manes/gradle-versions-plugin) needs to be applied in the root `build.gradle` or `build.gradle.kts` build file.

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
  id "com.github.ben-manes.versions" version "0.41.0"
  id "nl.littlerobots.version-catalog-update" version "<latest version>"
}
```

</details>

<details>
<summary>build.gradle.kts</summary>

```kotlin
plugins {
  id("com.github.ben-manes.versions") version "0.41.0"
  id("nl.littlerobots.version-catalog-update") version "<latest version>"
}
```

</details>


When using the plugins block, the classpath dependency is `nl.littlerobots.vcu:plugin:<version>`

## Creating a `libs.versions.toml` file

If you don't have a catalog file yet, one can be created by using the `--create` command line option:

```
./gradlew versionCatalogUpdate --create
```

This will use the current versions used as detected the versions plugin. After running the build with `--create` `gradle/libs.versions.toml` can be edited needed.
You can rename and remove keys that are not applicable; the versions plugin may include dependencies that are internal to Gradle or added by other means, such as plugins adding a dependency.

The plugin will attempt to create versions for artifacts in the same group with a common version.

The catalog will be updated with the latest available version as determined by the versions plugin.
__[You should probably configure the versions plugin on what versions are acceptable](https://github.com/ben-manes/gradle-versions-plugin#rejectversionsif-and-componentselection)__
A common case is to reject unstable versions like alphas, [please refer to these examples](https://github.com/ben-manes/gradle-versions-plugin#rejectversionsif-and-componentselection).

When you configure the dependency versions plugin, make sure that you don't disable the JSON report, as this report
is used as an input to this plugin. Disabling the json report will result in errors or stale updates.

After you have created the `libs.versions.toml` file you can update your dependency references to use the catalog instead of direct dependency declarations.

## Updating the `libs.versions.toml` file
To update the catalog file at any time run `./gradlew versionCatalogUpdate`. This takes care of the following:

* New available versions will be updated in the catalog, retaining existing version groups if they exist
* Keys used in `versions`, `libraries`, `bundles` and `plugins` will be sorted by name [configurable](#configuration)
* `bundles` will be updated so that they only contain valid keys. Keys in a bundle will be sorted.

No new entries will be added to the catalog, but unused entries will be removed. Any dependency that is not reported by the versions plugin, but still appears
in the version catalog file will be considered unused. This is [configurable](#configuration).

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
To format the existing `libs.versions.toml` file without updating library versions, you can run `./gradlew versionCatalogFormat`.
This will format the version catalog and create new version references, just like the `versionCatalogUpdate` task would do.
This comes in handy when you added an entry to the version catalog, but aren't ready yet to update any dependencies.

## Informational output
In some cases the plugin will output some additional messages when checking for updates.

### "Exceeded" dependencies
The versions plugin will report "exceeded" dependencies, which are dependencies that don't match the declared version for some reason. This can happen when you specify a version that does not exist, but some other dependency is pulling in an existing version of the same dependency.
In that case, your build will not fail, but the version used isn't the version that was specified in the catalog file. The plugin will show a warning and update the catalog to the correct version in that case.

<details>
<summary>example warning</summary>

```
Some libraries declared in the version catalog did not match the resolved version used this project.
This mismatch can occur when a version is declared that does not exist, or when a dependency is referenced by a transitive dependency that requires a different version.
The version in the version catalog has been updated to the actual version. If this is not what you want, consider using a strict version definition.


The affected libraries are:
 - androidx.test:core (libs.androidx.test.core)
     requested: 1.4.1 (androidxTest), resolved: 1.4.0
 - androidx.test:rules (libs.androidx.test.rules)
     requested: 1.4.1 (androidxTest), resolved: 1.4.0

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
        libraries = [libs.my.library.reference, libs.my.other.library.reference]
        plugins = [libs.plugins.my.plugin, libs.plugins.my.other.plugin]
        groups = ["com.somegroup", "com.someothergroup"]

        // keep versions without any library or plugin reference
        keepUnusedVersions = true
        // keep all libraries that aren't used in the project
        keepUnusedLibraries = true
        // keep all plugins that aren't used in the project
        keepUnusedPlugins = true
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
        libraries.add(libs.my.library.reference)
        libraries.add(libs.my.other.library.reference)
        plugins.add(libs.plugins.my.plugin)
        plugins.add(libs.plugins.my.other.plugin)
        groups.add("com.somegroup")
        groups.add("com.someothergroup")

        // keep versions without any library or plugin reference
        keepUnusedVersions.set(true)
        // keep all libraries that aren't used in the project
        keepUnusedLibraries.set(true)
        // keep all plugins that aren't used in the project
        keepUnusedPlugins.set(true)
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
* When creating the catalog from existing dependencies, "internal" dependencies or plugins might be added to the catalog.
* The TOML file will be updated and formatted by this plugin; this is by design. If this is undesirable then the  [versions plugin](https://github.com/ben-manes/gradle-versions-plugin) is probably what you are looking for.
