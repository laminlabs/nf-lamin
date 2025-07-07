# nf-lamin plugin

A Nextflow plugin that integrates [LaminDB](https://github.com/laminlabs/lamindb) data provenance into Nextflow workflows.

This plugin automatically tracks your Nextflow workflow executions, including parameters, code versions, and input/output files as structured metadata in LaminDB.

For detailed instructions, please see the [**full documentation**](https://docs.lamin.ai/nextflow).

## Usage

You first need to store your [Lamin API key](https://lamin.ai/settings) as a secret in your Nextflow configuration.
This allows the plugin to authenticate with your LaminDB instance.
You can do this by running the following command:

```bash
nextflow secrets set LAMIN_API_KEY "your_lamin_api_key_here"
```

To use the plugin in a Nextflow workflow, add the plugin to your `nextflow.config` and configure it with your LaminDB instance and API key:

```groovy
// filepath: nextflow.config
plugins {
  id 'nf-lamin'
}

lamin {
  instance = "your-organization/your-instance"
  api_key = secrets.LAMIN_API_KEY
}
```

Now, simply run your Nextflow pipeline with the plugin enabled.
The plugin will automatically connect to your LaminDB instance and record the run.

```bash
nextflow run <your-pipeline> -plugins nf-lamin
```

    N E X T F L O W  ~  version 24.10.5
    Launching `...`
    ✅ Connected to LaminDB instance 'your-organization/your-instance' as 'your-username'
    Transform XXXYYYZZZABC0001 (https://lamin.ai/your-organization/your-instance/transform/XXXYYYZZZABC0001)
    Run abcdefghijklmnopqrst (https://staging.laminhub.com/laminlabs/lamindata/transform/XXXYYYZZZABC0001/abcdefghijklmnopqrst)

## Testing locally

### Prerequisites

1. Install full JDK (not just JRE)

### Setup

1. Set path variables:

```bash
export NXF_PLUGINS_MODE=dev
export NXF_PLUGINS_DEV=$(pwd)
```

2. Get a local copy of Nextflow:

```bash
git clone --depth 1 https://github.com/nextflow-io/nextflow ../nextflow
```

3. Configure composite build:

```bash
echo "includeBuild('../nextflow')" >> settings.gradle
```

4. Create launch classpath (from Nextflow directory):

```bash
cd ../nextflow
./gradlew exportClasspath
cd ../nf-lamin
```

5. Build the plugin:
```bash
make assemble
```

### Running the plugin

```bash
./launch.sh run nextflow-io/hello -plugins nf-lamin
```

## Publishing the plugin

1. Create a file named gradle.properties in the project root containing the following attributes (this file should not be committed to Git):

```
github_organization: the GitHub organisation where the plugin repository is hosted.
github_username: The GitHub username granting access to the plugin repository.
github_access_token: The GitHub access token required to upload and commit changes to the plugin repository.
github_commit_email: The email address associated with your GitHub account.
```

2. Use the following command to package and create a release for your plugin on GitHub:

```bash
./gradlew :plugins:nf-hello:upload
```

3. Create a pull request against [nextflow-io/plugins](https://github.com/nextflow-io/plugins/blob/main/plugins.json) to make the plugin accessible to Nextflow.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for more information about contributing to this repository.
