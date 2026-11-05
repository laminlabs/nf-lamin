# validation/console

Checks what the plugin prints on the console, since `-trace` hides problems with this: it makes plugin logs show regardless of the logger's name.

Exercises:

- an invalid API key: one warning with the reason, no errors, and a pipeline that succeeds
- a valid API key: the connection and the link to the run

## Run

```bash
make validate-console
```

The check with a valid API key needs `LAMIN_API_KEY` and `LAMIN_CURRENT_INSTANCE`, and is skipped without them.
