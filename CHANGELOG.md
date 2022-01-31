# Changelog
Version 0.3.0-SNAPSHOT
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
