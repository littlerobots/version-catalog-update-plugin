# Usage Guide

## Local Development & Publishing

### Publish to Local Maven Repository

```bash
./gradlew clean :plugin:publishToMavenLocal -PskipSigning
```

This publishes version `1.0.2-SNAPSHOT` (see `gradle.properties`) to `~/.m2/repository`.

---

## Using the Plugin

### 1. Configure Your Consumer Project

**settings.gradle.kts**:
```kotlin
pluginManagement {
    repositories {
        mavenLocal()  // Required for local snapshots
        gradlePluginPortal()
    }
}
```

**build.gradle.kts** (root project):
```kotlin
plugins {
    id("nl.littlerobots.version-catalog-update") version "1.0.2-SNAPSHOT"
}
```

### 2. Run Plugin Tasks

| Task | Description |
|------|-------------|
| `./gradlew versionCatalogUpdate` | Updates versions in `gradle/libs.versions.toml` |
| `./gradlew versionCatalogFormat` | Formats/sorts catalog without updating versions |
| `./gradlew versionCatalogUpdate --interactive` | Stages changes for review |
| `./gradlew versionCatalogApplyUpdates` | Applies staged changes |

### 3. Optional Configuration

> ⚠️ **Warning**: By default `sortByKey` is `true`, which will **alphabetically reorder** your entire toml file, destroying any custom groupings or section organization. Set `sortByKey.set(false)` to preserve your original ordering.

```kotlin
versionCatalogUpdate {
    sortByKey.set(false) // IMPORTANT: Preserve original order
    
    pin {
        versions.add("kotlin")        // Don't auto-update these versions
        groups.add("com.example")     // Pin all libraries in a group
    }
    
    keep {
        keepUnusedVersions.set(true)  // Retain versions not referenced by libraries
    }
}
```

---

## Output Messages

| Message Type | Meaning |
|--------------|---------|
| **Unresolved libraries** | Dependency couldn't be found (missing repo or private artifact) |
| **Invalid versions** | Declared version doesn't exist; Gradle resolved a different version transitively |
| **Rich versions** | Uses version ranges/constraints; plugin won't auto-update |
| **Pinned entries** | Updates available but blocked by your `pin` configuration |

---

## Requirements

- **Gradle**: 9.0+ (this build is configured for Gradle 9)
- **JDK**: 17+
