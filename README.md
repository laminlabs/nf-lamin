# nf-lamin

[Nextflow](https://www.nextflow.io/) is a widely used workflow manager designed for scalable and reproducible data analysis in bioinformatics.

LaminDB integrates with Nextflow through:

1. The `nf-lamin` Nextflow plugin
2. A post-run Python script

## Plugin

The `nf-lamin` Nextflow plugin automatically tracks your Nextflow workflow executions, including parameters, code versions, and input/output files as structured metadata in LaminDB.
For detailed instructions, please see the [plugin documentation](https://docs.lamin.ai/nextflow-plugin).

### Usage

You first need to store your [Lamin API key](https://lamin.ai/settings) as a secret in your Nextflow configuration.
This allows the plugin to authenticate with your LaminDB instance:

```bash
nextflow secrets set LAMIN_API_KEY "your_lamin_api_key_here"
```

To use the plugin in a Nextflow workflow, add it to your `nextflow.config` file and configure it with your LaminDB instance and API key:

```groovy
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

## Post-run script

We generally recommend using the `nf-lamin` plugin.
However, if lower level LaminDB usage is required, it might be worthwhile writing a custom Python script.

### Usage

For an example, please see the [post-run documentation](https://docs.lamin.ai/nextflow-plugin).

Such a script could be deployed via:

1. A serverless environment trigger (e.g., AWS Lambda)
2. A [post-run script](https://docs.seqera.io/platform-cloud/launch/advanced#pre-and-post-run-scripts) on the Seqera Platform

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for more information about contributing to this repository.
