# Contributing to nf-lamin

This document provides guidelines for contributing to the nf-lamin Nextflow plugin.

See the [Nextflow documentation](https://nextflow.io/docs/latest/plugins.html) for more information about developing plugins.

## Project Structure

The nf-lamin plugin follows the modern Nextflow plugin architecture:

### Core Files

- **`build.gradle`** - Main build configuration using the `io.nextflow.nextflow-plugin` plugin
- **`settings.gradle`** - Gradle project settings
- **`src/main/groovy/ai/lamin/plugin/`** - Plugin implementation sources
- **`src/test/groovy/ai/lamin/plugin/`** - Plugin unit tests
- **`nextflow_lamin/`** - Python package for documentation and testing
- **`docs/`** - Documentation as executable Jupyter notebooks

### Key Plugin Classes

- **`LaminPlugin.groovy`** - Main plugin entry point extending `BasePlugin`
- **`LaminConfig.groovy`** - Configuration handling from `nextflow.config` lamin block
- **`LaminExtension.groovy`** - Custom channel factories, operators, and functions
- **`LaminFactory.groovy`** - Factory for creating plugin components
- **`LaminObserver.groovy`** - Implements `TraceObserver` to capture workflow events
- **`hub/`** and **`instance/`** - API clients for different Lamin environments

## Development Environment Setup

### Prerequisites

1. **Java Development Kit (JDK)** - Full JDK installation (not just JRE)
2. **Git** - For version control
3. **Python 3.8+** - For documentation and testing (optional)

## Building and Testing

### Building the Plugin

Build the plugin using the provided Makefile:

```bash
# Build the plugin
make assemble

# Install to local Nextflow plugins directory
make install
```

### Unit Testing

Run the Groovy unit tests:

```bash
# Run all tests
./gradlew check

# Run tests with verbose output
./gradlew test --info
```

### Integration Testing

Test the plugin with real Nextflow workflows:

```bash
# Test with a simple pipeline
nextflow run hello -plugins nf-lamin@0.1.1

# Test with nf-core pipeline
nextflow run nf-core/hello -plugins nf-lamin@0.1.1
```

### Documentation Testing

The documentation is tested using Python notebooks:

```bash
# Install Python dependencies (optional)
pip install -e .

# Run notebook tests
python -m pytest tests/test_notebooks.py
```

## Configuration for Development

### Local Testing Configuration

Create a `nextflow.config` file for testing:

```groovy
plugins {
    id 'nf-lamin@0.1.1'
}

lamin {
    instance = "your-org/your-instance"
    api_key = secrets.LAMIN_API_KEY
    env = "staging"  // Use staging for development
}
```

### Environment Variables

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
- Join the discussion in GitHub Discussions
- Refer to [Nextflow plugin documentation](https://nextflow.io/docs/latest/plugins.html)
