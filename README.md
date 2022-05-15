# Version catalog update plugin
[![Maven Central](https://img.shields.io/maven-central/v/nl.littlerobots.vcu/plugin)](https://search.maven.org/search?q=g:nl.littlerobots.vcu%20a:plugin)

This plugin helps to keep the versions in a Gradle [version catalog toml file](https://docs.gradle.org/current/userguide/platforms.html) up to date.
The version updates are determined by the [versions plugin](https://github.com/ben-manes/gradle-versions-plugin).

# Getting started
This plugin requires Gradle 7.2 or up. [Version catalogs](https://docs.gradle.org/current/userguide/platforms.html) are a Gradle incubating feature for versions lower than 7.4, check out the documentation on how to enable this feature for those versions.

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

In your `build.gradle[.kts]`:
```
plugins {
  id "com.github.ben-manes.versions" version "0.41.0"
  id "nl.littlerobots.version-catalog-update" version "<latest version>"
}
```

When using the plugins block, the classpath dependency is `nl.littlerobots.vcu:plugin:<version>`

## Creating a `libs.versions.toml` file

If you don't have a catalog file yet, one can be created by using the `--create` command line option:

```
./gradlew versionCatalogUpdate --create
```

This will use the current versions used as detected the versions plugin. After running the build with `--create` `gradle/libs.version.toml` can be edited needed.
You can rename and remove keys that are not applicable; the versions plugin may include dependencies that are internal to Gradle or added by other means, such as plugins adding a dependency.

The plugin will attempt to create versions for artifacts in the same group with a common version.

The catalog will be updated with the latest available version as determined by the versions plugin. [You can configure the versions plugin](https://github.com/ben-manes/gradle-versions-plugin#rejectversionsif-and-componentselection)
on what versions are acceptable. A common case is to reject unstable versions like alphas.

After you have created the `libs.versions.toml` file you can update your dependency references to use the catalog instead of direct dependency declarations.

## Updating the `libs.versions.toml` file
To update the catalog file at any time run `./gradlew versionCatalogUpdate`. This takes care of the following:

* New available versions will be updated in the catalog, retaining existing version groups if they exist
* Keys used in `versions`, `libraries`, `bundles` and `plugins` will be sorted by name [configurable](#configuration)
* `bundles` will be updated so that they only contain valid keys. Keys in a bundle will be sorted.

No new entries will be added to the catalog, but unused entries will be removed. Any dependency that is not reported by the versions plugin, but still appears
in the version catalog file will be considered unused. This is [configurable](#configuration).

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

In addition, you can add new dependencies using `./gradlew versionCatalogUpdate --add`. Note that this will probably also add dependencies that
are not directly declared in your build files, such as internal dependencies or dependencies added by plugins, so generally it is adviced to use this option with care.

## Snapshot versions
For snapshots versions add the Sonatype snapshot repository `https://oss.sonatype.org/content/repositories/snapshots/`.

## Known issues and limitations
* When adding dependencies, "internal" dependencies or plugins might be added to the catalog. It is recommended to leave the default set to `false` for that reason. Usually after the initial version catalog has been setup, new dependencies should be added to the toml file manually anyway.
