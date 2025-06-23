# Contributing

See the [Nextflow documentation](https://nextflow.io/docs/latest/plugins.html) for more information about developing plugins.

## Plugin structure

- `settings.gradle`

  Gradle project settings.

- `plugins/nf-lamin`

  The plugin implementation base directory.

- `plugins/nf-lamin/build.gradle`

  Plugin Gradle build file. Project dependencies should be added here.

- `plugins/nf-lamin/src/resources/META-INF/MANIFEST.MF`

  Manifest file defining the plugin attributes e.g. name, version, etc. The attribute `Plugin-Class` declares the plugin main class. This class should extend the base class `nextflow.plugin.BasePlugin` e.g. `lamin.LaminPlugin`.

- `plugins/nf-lamin/src/resources/META-INF/extensions.idx`

  This file declares one or more extension classes provided by the plugin. Each line should contain the fully qualified name of a Java class that implements the `org.pf4j.ExtensionPoint` interface (or a sub-interface).

- `plugins/nf-lamin/src/main`

  The plugin implementation sources.

- `plugins/nf-lamin/src/test`

  The plugin unit tests.

## Plugin classes

- `LaminConfig`: shows how to handle options from the Nextflow configuration

- `LaminExtension`: shows how to create custom channel factories, operators, and fuctions that can be included into pipeline scripts

- `LaminObserverFactoryTest` and `LaminObserver`: shows how to react to workflow events with custom behavior

- `LaminPlugin`: the plugin entry point

## Unit testing

To run your unit tests, run the following command in the project root directory (ie. where the file `settings.gradle` is located):

```bash
./gradlew check
```

## Testing and debugging

To build and test the plugin during development, use the following commands:

1. Rebuild the plugin using the `make install` command. Take note of the version number of the plugin.

2. Run Nextflow with the plugin by adding the option `-plugins nf-lamin@0.1.0` to load the plugin:

   ```bash
   nextflow run nf-core/hello -plugins nf-lamin@0.1.0
   ```

   Note: replace the version number with the actual version of the plugin.

## Package, upload, and publish

Follow these steps to package, upload and publish the plugin:

1. Create a file named `gradle.properties` in the project root containing the following attributes (this file should not be committed to Git):

   - `github_organization`: the GitHub organisation where the plugin repository is hosted.
   - `github_username`: The GitHub username granting access to the plugin repository.
   - `github_access_token`: The GitHub access token required to upload and commit changes to the plugin repository.
   - `github_commit_email`: The email address associated with your GitHub account.

2. Use the following command to package and create a release for your plugin on GitHub:

   ```bash
   ./gradlew :plugins:nf-lamin:upload
   ```

3. Create a pull request against [nextflow-io/plugins](https://github.com/nextflow-io/plugins/blob/main/plugins.json) to make the plugin accessible to Nextflow.
