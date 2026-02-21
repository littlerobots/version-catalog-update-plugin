# Changelog
Version 1.1.0
-------------
* Fix adding dependencies that are contributed through build scripts ([#179](https://github.com/littlerobots/version-catalog-update-plugin/issues/179))
* Add a [new option](https://github.com/littlerobots/version-catalog-update-plugin?tab=readme-ov-file#checking-for-updates) to the `versionCatalogUpdate` task that will fail the build if there are updates for any dependency or specific dependencies. ([#168](https://github.com/littlerobots/version-catalog-update-plugin/issues/168))

Version 1.0.1
-------------
* Fix handling of version conditions using `reject` ([#177](https://github.com/littlerobots/version-catalog-update-plugin/issues/177))

Version 1.0.0
-------------
This version no longer uses the dependency versions plugin for resolving versions, but
is using Gradle APIs to resolve the version catalog dependencies. This option has been
available behind a flag for a while and is now the default.

This is quite a big change which has a few behavioral changes, but will also result in a more predictable output.

If you do not need the functionality that the dependency versions plugin is providing,
you can remove the plugin from your build.gradle, as it is no longer required for this plugin.

Some configuration options have changed, please refer to the README for details.

## Resolving dependencies
Previously, only dependencies that where part of a `dependencies` block would be detected by the
plugin. This would lead to situations where sometimes entries in the version catalog where not
updated because these entries weren't used.
Now _all_ dependencies from the version catalog will be resolved and checked for updates, whether
they are used or not in the project.

This also means most of the old options to `keep` dependencies are now no longer applicable;
since the plugin is no longer "checking" what dependencies are used, it can also no longer determine
which dependencies are not. See the README for updated configuration.

Because the plugin no longer requires dependencies to be used in your project,
it's also very easy now to have "catalog only" projects. For an example see
https://github.com/jamesward/kotlin-universe-catalog

## Selecting versions
Previously the dependency versions plugin and its configuration would determine
acceptable versions. As of this version the `versionSelector` configuration is used
to configure this. The default configuration will prefer stable versions, unless the entry
in the version catalog is already using an unstable version (like alpha, beta, etc.).

## Creating a version catalog
Because the source of truth is now the version catalog file, there's no way
anymore to create a new version catalog based on existing dependencies.

## Improvement to version constraints
When a version constraint is applied to an entry in the version catalog, the plugin
will now suggest suitable updates if they are available.

Version 0.8.5
-------------
* Fix setting version selector for kts build files ([#149](https://github.com/littlerobots/version-catalog-update-plugin/issues/149))
* Fixes for new resolver behaviour that had regressed vs the old resolver

Version 0.8.4
-------------
* Fail the build when an incompatible XML parser is on the build classpath with Gradle 8.4+ and 7.6.3 ([#139](https://github.com/littlerobots/version-catalog-update-plugin/issues/139))
* Add kts extension for version selector (new resolver only)

Version 0.8.3
-------------
Fixes and enhancements for the internal resolver:

* Handle variants / redirection correctly ([#129](https://github.com/littlerobots/version-catalog-update-plugin/issues/129))
* Handle unresolved libraries due to capability conflicts ([#131](https://github.com/littlerobots/version-catalog-update-plugin/issues/131))
* Per catalog version selectors ([#130](https://github.com/littlerobots/version-catalog-update-plugin/issues/131))

Also fixes an issue where versions weren't correctly grouped to a regression in `0.8.2`

Version 0.8.2
-------------
🚨 This version introduces a new way to resolve dependencies, no longer depending on the Gradle versions plugin 🚨
This functionality is *not* enabled by default yet, but can be enabled by setting a property. Please try it out and report any issues you might find!

See [the PR](https://github.com/littlerobots/version-catalog-update-plugin/pull/125) on how to enable the new resolver,
what the behavioural changes are and some of the rationale behind the change.

* Versions grouping picks the most common version [(#127)](https://github.com/littlerobots/version-catalog-update-plugin/issues/127)
* Resolve dependencies from the plugin itself [(#123)](https://github.com/littlerobots/version-catalog-update-plugin/issues/123)

Version 0.8.1
-------------
* Order of sections in the version catalog is now maintained [(#113)](https://github.com/littlerobots/version-catalog-update-plugin/issues/113)

Version 0.8.0
-------------
* Added an option to specify the toml file to use for the default tasks [(#92)](https://github.com/littlerobots/version-catalog-update-plugin/issues/92)
* Added a warning for plugin dependencies with a version condition that can be upgraded, similar to library dependencies. [(#95)](https://github.com/littlerobots/version-catalog-update-plugin/issues/95)
* Removed the compile-time dependency on the versions plugin. This should fix class path issues when the versions plugin is applied from an init script for example. [(#97)](https://github.com/littlerobots/version-catalog-update-plugin/issues/97)
* Fixed warnings when configuration cache is enabled [(#103)](https://github.com/littlerobots/version-catalog-update-plugin/issues/103)

Version 0.7.0
-------------
* Group tasks [PR #86](https://github.com/littlerobots/version-catalog-update-plugin/pull/86)
* Support multiple TOML files [(#52)](https://github.com/littlerobots/version-catalog-update-plugin/issues/52)

Version 0.6.1
---------------------
* Fixed an issue with grouping versions [(#82)](https://github.com/littlerobots/version-catalog-update-plugin/issues/82)
* Fix: retain order of kept versions [(#78)](https://github.com/littlerobots/version-catalog-update-plugin/issues/78)
* Now also available from the Gradle Plugin portal

Version 0.6.0
-------------
* New feature: `--interactive` command line option to stage changes without updating the TOML file [(#63)](https://github.com/littlerobots/version-catalog-update-plugin/issues/63)
* Shadow (bundle) dependencies to prevent conflicts with other plugins [(#39)](https://github.com/littlerobots/version-catalog-update-plugin/issues/39)
* Fix an issue with unused transitive dependencies causing unwanted changes to the TOML file [(#71)](https://github.com/littlerobots/version-catalog-update-plugin/issues/71)
* Removed `--add` option. [(#69)](https://github.com/littlerobots/version-catalog-update-plugin/issues/69)

Version 0.5.3
-------------
* Fix an issue with unused kept pinned dependencies causing the update task to fail [(#61)](https://github.com/littlerobots/version-catalog-update-plugin/issues/61)

Version 0.5.2
-------------
~~* Fix an issue with unused kept pinned dependencies causing the update task to fail [(#61)](https://github.com/littlerobots/version-catalog-update-plugin/issues/61)~~

Version 0.5.1
-------------
* Fix dropping unused versions when formatting (respecting keep settings) [(#54)](https://github.com/littlerobots/version-catalog-update-plugin/issues/54)
* Show message when pinned plugins can be updated [(#53)](https://github.com/littlerobots/version-catalog-update-plugin/issues/53)

Version 0.5.0
-------------
* Added `versionCatalogFormat` task for formatting only, without updating dependencies. [(#48)](https://github.com/littlerobots/version-catalog-update-plugin/issues/48)
* The [versions plugin](https://github.com/ben-manes/gradle-versions-plugin) is configured to [check constraints](https://github.com/ben-manes/gradle-versions-plugin#constraints) by default now. At least version `0.4.0` of the versions plugin is required.
* Bundles are now formatted with one entry per line and a trailing comma for easier diffing. [(#44)](https://github.com/littlerobots/version-catalog-update-plugin/issues/44)

Version 0.4.0
-------------
* Retain comments in TOML file (with some limitations) [(#18)](https://github.com/littlerobots/version-catalog-update-plugin/issues/18)
* Use `@pin` or `@keep` in a comment to pin/keep specific entries
* Fixed: Adding dependencies to a version could lead to invalid definitions or update version references in unexpected ways [(#36)](https://github.com/littlerobots/version-catalog-update-plugin/issues/36)

Version 0.3.1
-------------
* Support plugin declarations without a version [(#22)](https://github.com/littlerobots/version-catalog-update-plugin/issues/22)
* Change `sortByKey` to a property so it matches the docs ;)

Version 0.3.0
-------------
* Create option now uses the current version used in the project vs the latest version [(#13)](https://github.com/littlerobots/version-catalog-update-plugin/issues/13)
* Emit a warning when version declared in the catalog does not match the version used in the project [(#8)](https://github.com/littlerobots/version-catalog-update-plugin/issues/8)
* Version conditions (rich versions) in the version block are now handled correctly. If updates are available for a version with a condition, no updates are done automatically, in stead a message will be logged to the console. [(#17)](https://github.com/littlerobots/version-catalog-update-plugin/issues/17)
* The `keepUnused` option has been removed from the command line and the plugin configuration and is replaced by the new `keep` option in the `versionCatalogUpdate` configuration block.
* A new `pin` option has been added to the `versionCatalogUpdate` block that allows pinning of entries that should not be automatically updated. This can work in addition with rich version definitions.

Version 0.2.2
-------------
* Fixes an issue where plugin dependencies were incorrectly classified as libraries when using a `resolutionStrategy` mapping plugin ids. [(#11)](https://github.com/littlerobots/version-catalog-update-plugin/issues/11)

Version 0.2.1
--------------
* Use the preferred version for `exceeded` dependencies in the toml file. [(#7)](https://github.com/littlerobots/version-catalog-update-plugin/issues/7)

Version 0.2.0
-------------
* `keepUnused = true` keeps unused version declarations too now
* Plugins are no longer moved from the libraries section so that a library declaration for a plugin can still be updated.

Version 0.1.0
-------------
* Hello, world!
