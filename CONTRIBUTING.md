# Contributing to nf-lamin

## Plugin

This document provides guidelines for contributing to the `nf-lamin` Nextflow plugin.

See the [Nextflow documentation](https://nextflow.io/docs/latest/plugins.html) for more information about developing plugins.

### Structure

The nf-lamin plugin follows the modern Nextflow plugin architecture:

#### Core Files

- **`build.gradle`** - Main build configuration using the `io.nextflow.nextflow-plugin` plugin
- **`settings.gradle`** - Gradle project settings
- **`src/main/groovy/ai/lamin/plugin/`** - Plugin implementation sources
- **`src/test/groovy/ai/lamin/plugin/`** - Plugin unit tests
- **`nextflow_lamin/`** - Python package for documentation and testing
- **`docs/`** - Documentation as executable Jupyter notebooks

#### Key Plugin Classes

- **`LaminPlugin.groovy`** - Main plugin entry point extending `BasePlugin`
- **`LaminConfig.groovy`** - Configuration handling from `nextflow.config` lamin block
- **`LaminExtension.groovy`** - Custom channel factories, operators, and functions
- **`LaminFactory.groovy`** - Factory for creating plugin components
- **`LaminObserver.groovy`** - Implements `TraceObserver` to capture workflow events
- **`hub/`** and **`instance/`** - API clients for different Lamin environments

### Development Environment Setup

#### Prerequisites

1. **Java Development Kit (JDK)** - Full JDK installation (not just JRE)
2. **Git** - For version control
3. **Python 3.8+** - For documentation and testing (optional)

### Building and Testing

#### Building the Plugin

Build the plugin using the provided Makefile:

```bash
# Build the plugin
make assemble

# Install to local Nextflow plugins directory
make install
```

#### Unit Testing

Run the Groovy unit tests:

```bash
# Run all tests
./gradlew check

# Run tests with verbose output
./gradlew test --info
```

#### Integration Testing

Test the plugin with real Nextflow workflows:

```bash
# Test with a simple pipeline
nextflow run hello -plugins nf-lamin@0.2.3

# Test with nf-core pipeline
nextflow run nf-core/hello -plugins nf-lamin@0.2.3
```

#### Lamin Observer Integration Tests

The forthcoming LaminObserver integration suite requires real Lamin Hub connectivity. Configure the following environment variables before running those tests; each test will auto-skip if the required values are missing.

| Variable                | Purpose                                                                     |
| ----------------------- | --------------------------------------------------------------------------- |
| `LAMIN_API_KEY`         | Lamin production API key (`env = 'prod'`).                                  |
| `LAMIN_STAGING_API_KEY` | Lamin staging API key (`env = 'staging'`).                                  |
| `LAMIN_TEST_BUCKET`     | Remote bucket URI (`s3://…` or `gs://…`) used to register remote artifacts. |

The tests will also rely on stable resources that should already exist:

- Lamin instance owner/name (both environments): `laminlabs/lamindata`
- Production transform UID: `PhX5TXQhj3l6wowA`
- Production run UID: `G3LlrTKJxNFvlWaSktvD`
- Staging transform UID: `J49HdErpEFrs0000`
- Staging run UID: `Bw0tT39K7MaRX1UMTOvo`

> **Note:** Transform and run identifiers created during CI are intentionally left in place to avoid accidental deletion of production/staging data. Future clean-up helpers can be added once if desirable.

#### Documentation Testing

The documentation is tested using Python notebooks:

```bash
# Install Python dependencies (optional)
pip install -e .

# Run notebook tests
python -m pytest tests/test_notebooks.py
```

### Configuration for Development

#### Local Testing Configuration

Create a `nextflow.config` file for testing:

```groovy
plugins {
    id 'nf-lamin@0.2.3'
}

lamin {
    instance = "your-org/your-instance"
    api_key = secrets.LAMIN_API_KEY
    env = "staging"  // Use staging for development
}
```

#### Environment Variables

Useful environment variables for development:

```bash
# Enable development mode
export NXF_PLUGINS_MODE=dev
export NXF_PLUGINS_DEV=$PWD

# Use specific Nextflow version
export NXF_VER=25.04.6

# Enable debug logging
export NXF_DEBUG=2
```

## Getting Help

- Check the [full documentation](https://docs.lamin.ai/nextflow)
- Review existing [GitHub issues](https://github.com/laminlabs/nf-lamin/issues)
- Refer to the [Nextflow plugin documentation](https://nextflow.io/docs/latest/plugins.html)
