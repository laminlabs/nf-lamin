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
nextflow run <your-pipeline>
```

    N E X T F L O W  ~  version 24.10.5
    Launching `...`
    ✅ Connected to LaminDB instance 'your-organization/your-instance' as 'your-username'
    Transform XXXYYYZZZABC0001 (https://lamin.ai/your-organization/your-instance/transform/XXXYYYZZZABC0001)
    Run abcdefghijklmnopqrst (https://staging.laminhub.com/laminlabs/lamindata/transform/XXXYYYZZZABC0001/abcdefghijklmnopqrst)

## Building

To build the plugin:

```bash
make assemble
```

## Testing with Nextflow

The plugin can be tested without a local Nextflow installation:

1. Build and install the plugin to your local Nextflow installation: `make install`
2. Run a pipeline with the right version of the plugin: `nextflow run hello -plugins nf-lamin@0.2.0`

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for more information about contributing to this repository.
