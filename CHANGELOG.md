# Changelog
Version 0.6.0
-------------
* New feature: `--incremental` command line option to stage changes without updating the TOML file [(#63)](https://github.com/littlerobots/version-catalog-update-plugin/issues/63)
* Shadow (bundle) dependencies to prevent conflicts with other plugins [(#39)](https://github.com/littlerobots/version-catalog-update-plugin/issues/39)
* Fix an issue with unused kept pinned dependencies causing the update task to fail [(#61)](https://github.com/littlerobots/version-catalog-update-plugin/issues/61)
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
