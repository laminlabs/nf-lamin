# Reference

## Configuration

### Basic Configuration

Add the following block to your `nextflow.config`:

```groovy
plugins {
  id 'nf-lamin'
}

lamin {
  instance = "<your-lamin-org>/<your-lamin-instance>"
  api_key = secrets.LAMIN_API_KEY
  project = "your-lamin-project" // optional
}
```

**Settings:**

- `lamin.instance`: **(Required)** The LaminDB instance to connect to, in the format `organization/instance`.
- `lamin.api_key`: **(Required)** Your Lamin Hub API key. It is strongly recommended to set this using `nextflow secrets`.
- `lamin.project`: (Optional) The project name in LaminDB to associate runs with.

Alternatively, you can use environment variables, though this is less secure:

```bash
export LAMIN_CURRENT_INSTANCE="laminlabs/lamindata"
export LAMIN_API_KEY="your-lamin-api-key"
export LAMIN_CURRENT_PROJECT="your-lamin-project"
```

### Advanced Configuration

The plugin offers advanced settings for custom deployments or for tuning its behavior.

```groovy
lamin {
  // ... basic settings ...

  // The environment name in LaminDB (e.g. "prod" or "staging")
  env = "prod"
  // The Supabase API URL for the LaminDB instance (if env is set to "custom")
  supabase_api_url = "https://your-supabase-api-url.supabase.co"
  // The Supabase anon key for the LaminDB instance (if env is set to "custom")
  supabase_anon_key = secrets.SUPABASE_ANON_KEY
  // The number of retries for API requests
  max_retries = 3
  // The delay between retries in milliseconds
  retry_delay = 100
}
```

You can also set these using environment variables:

```bash
export LAMIN_ENV="prod"
export SUPABASE_API_URL="https://your-supabase-api-url.supabase.co"
export SUPABASE_ANON_KEY="your-supabase-anon-key"
export LAMIN_MAX_RETRIES=3
export LAMIN_RETRY_DELAY=100
```
