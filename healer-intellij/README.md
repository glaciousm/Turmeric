# Intent Healer IntelliJ Plugin

This module provides IntelliJ IDEA integration for Intent Healer.

## Building the Plugin

This module requires the IntelliJ Platform SDK and is best built using Gradle rather than Maven.

### Option 1: Build with Gradle (Recommended)

```bash
cd healer-intellij
./gradlew buildPlugin
```

The plugin ZIP will be created in `build/distributions/`.

### Option 2: Build with Maven

To build with Maven, you need to activate the `build-intellij-plugin` profile:

```bash
mvn clean install -Pbuild-intellij-plugin
```

**Note:** Maven build requires IntelliJ Platform SDK dependencies which are downloaded from JetBrains repositories.

### Installing the Plugin

1. Open IntelliJ IDEA
2. Go to `Settings` → `Plugins` → `⚙️` → `Install Plugin from Disk...`
3. Select the built plugin ZIP file
4. Restart IntelliJ IDEA

## Development Setup

For development, it's recommended to use Gradle with the IntelliJ Gradle Plugin:

```bash
./gradlew runIde
```

This will start a sandboxed IntelliJ IDEA instance with the plugin installed.

## Features

- **Heal History Viewer** - Browse and manage heal history
- **Trust Dashboard** - Monitor trust levels and healing statistics
- **Quick Actions** - Accept, reject, or blacklist heals from the IDE
- **Locator Suggestions** - Get suggestions for more stable locators
- **Intent Annotations** - Line markers for @Intent annotations

## Configuration

Configure the plugin at: `Settings` → `Tools` → `Intent Healer`

## Module Structure

```
healer-intellij/
├── src/main/java/
│   └── io/github/glaciousm/intellij/
│       ├── actions/          # IDE actions
│       ├── services/         # Project services
│       ├── settings/         # Plugin settings
│       └── ui/              # UI components
├── src/main/resources/
│   └── META-INF/
│       └── plugin.xml       # Plugin descriptor
├── build.gradle.kts         # Gradle build (recommended)
└── pom.xml                  # Maven build (alternative)
```
