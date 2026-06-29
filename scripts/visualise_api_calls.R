#!/usr/bin/env Rscript
#
# Visualise nf-lamin API calls recorded in a Nextflow log (`-trace ai.lamin`).
#
# Parses the ai.lamin TRACE/DEBUG lines, pairs each request with its response,
# and writes a parquet table, the list of artifact S3 paths, a summary table,
# and three plots (response-time beeswarm, response-time-over-time, and
# response-count histogram) to the output directory.
#
# Usage:
#   scripts/visualise_api_calls.R <nextflow.log> <output_dir>
#
# Example:
#   scripts/visualise_api_calls.R .nextflow.log out/stress_run1/

suppressPackageStartupMessages(library(tidyverse))

# ---- arguments --------------------------------------------------------------
args <- commandArgs(trailingOnly = TRUE)
if (length(args) < 2) {
  stop(
    "Usage: scripts/visualise_api_calls.R <nextflow.log> <output_dir>",
    call. = FALSE
  )
}
log_file <- args[[1]]
outdir <- args[[2]]

if (!file.exists(log_file)) {
  stop("Log file not found: ", log_file, call. = FALSE)
}
dir.create(outdir, recursive = TRUE, showWarnings = FALSE)

# Nextflow log timestamps omit the year; assume the log is from the current year.
log_year <- format(Sys.Date(), "%Y")

# ---- parse ------------------------------------------------------------------
lines <- read_lines(log_file)

api_lines <-
  tibble(line = lines) |>
  # filter ai.lamin api calls with request id in square brackets
  filter(str_detect(line, "ai\\.lamin.*\\[[0-9a-f]{8}\\]")) |>
  mutate(
    timestamp = str_extract(line, "^\\S+ \\S+") |>
      paste(log_year) |>
      parse_datetime(format = "%b-%d %H:%M:%OS %Y"),
    log_level = str_extract(line, "(?<=\\] )\\w+(?= ai\\.lamin)"),
    req_id = str_extract(line, "(?<=\\[)[0-9a-f]{8}(?=\\])"),
    method = str_extract(line, "(?<=\\] )(GET|POST|PUT|PATCH|DELETE)"),
    endpoint = str_extract(line, "(?<=(GET|POST|PUT|PATCH|DELETE) )\\w+"),
    is_response = str_detect(line, "response: |- status \\d+ -"),
    status_code = case_when(
      str_detect(line, "response: ") ~ str_extract(line, "(?<=statusCode:)\\d+(?=\\.0)") |> as.integer(),
      str_detect(line, "- status \\d+ -") ~ str_extract(line, "(?<=- status )\\d+") |> as.integer(),
      TRUE ~ NA_integer_
    ),
    # request payload: everything after "METHOD endpoint: " (NA for endpoints without a body, e.g. getAccount)
    request_data = if_else(
      is_response,
      NA_character_,
      str_match(line, "\\] (?:GET|POST|PUT|PATCH|DELETE) \\w+(?:: (.*))$")[, 2]
    ),
    # response body: everything after "response: " (TRACE) or "Response: " (DEBUG/WARN status lines)
    response_data = if_else(
      is_response,
      str_match(line, "[Rr]esponse: (.*)$")[, 2],
      NA_character_
    )
  )

# Pair each request with its response. A request is retried with the SAME req_id,
# so a single req_id yields an alternating request/response/request/... sequence.
# Number requests and responses independently per req_id (in log order) and join
# on that index: the k-th request matches the k-th response. This is robust to a
# trailing request whose response was not (yet) logged -- it stays unmatched
# (NA response) instead of stealing an earlier response and producing a negative
# duration, which is what a fixed "2 lines per attempt" assumption did.
lines_join <- full_join(
  api_lines |> filter(!is_response) |>
    group_by(req_id) |> mutate(retry_num = row_number()) |> ungroup() |>
    select(req_id, timestamp_start = timestamp, method, endpoint,
           log_level_start = log_level, retry_num, line_start = line, request_data),
  api_lines |> filter(is_response) |>
    group_by(req_id) |> mutate(retry_num = row_number()) |> ungroup() |>
    select(req_id, timestamp_end = timestamp, status_code,
           log_level_end = log_level, retry_num, line_end = line, response_data),
  by = c("req_id", "retry_num")
) |>
  mutate(
    # A matched success line ("response: ...") carries no statusCode, so default
    # those to 200. But an UNMATCHED request (no response line at all) must stay
    # NA -- otherwise it would be mislabelled a successful 200 with no duration.
    status_code = if_else(is.na(timestamp_end), NA_integer_,
                          replace_na(status_code, 200L)),
    status_lbl = if_else(is.na(status_code), "no response",
                         as.character(status_code)),
    diff_sec = as.numeric(difftime(timestamp_end, timestamp_start, units = "secs")),
    label = paste0(method, " ", endpoint)
  )

message("Parsed ", nrow(lines_join), " API calls from ", log_file)

n_noresp <- sum(is.na(lines_join$timestamp_end))
if (n_noresp > 0) {
  message(n_noresp, " request(s) had no matching response line ",
          "(shown as 'no response').")
}

# Any remaining negative durations are mispairings (e.g. req_id collisions across
# concurrent requests); drop them from timing so they don't break log scales.
n_neg <- sum(lines_join$diff_sec < 0, na.rm = TRUE)
if (n_neg > 0) {
  warning(n_neg, " call(s) had a negative duration (likely req_id collisions); ",
          "setting their duration to NA.")
  lines_join$diff_sec[!is.na(lines_join$diff_sec) & lines_join$diff_sec < 0] <- NA_real_
}

if (nrow(lines_join) == 0) {
  # No request/response trace lines => the run made no API calls. Surface the
  # most likely cause: a Lamin init failure (e.g. missing instance/api_key
  # because the lamin config was not passed at launch).
  init_err <- lines[str_detect(lines, "Could not initialize Lamin run")]
  hint <- if (length(init_err)) {
    paste0("\n  Lamin init error in log: ", str_trim(str_extract(init_err[[1]], "(?<= - ).*")))
  } else {
    ""
  }
  stop(
    "No ai.lamin API calls found in ", log_file, ".\n",
    "  The pipeline did not make any API calls (did the Lamin run initialise? ",
    "was the lamin config / instance + api_key passed at launch?).", hint,
    call. = FALSE
  )
}

# ---- run start/end markers --------------------------------------------------
parse_log_time <- function(pattern) {
  hit <- lines[str_detect(lines, pattern)]
  if (length(hit) == 0) return(as.POSIXct(NA))
  str_extract(hit[[1]], "^\\S+ \\S+") |>
    paste(log_year) |>
    parse_datetime(format = "%b-%d %H:%M:%OS %Y")
}
run_start <- parse_log_time("nextflow\\.Session - Session start")
tasks_done <- parse_log_time("Session await > all processes finished")
run_end <- parse_log_time("Workflow completed")

# Note: tasks usually finish well before run end; output-artifact registration
# happens during the publish phase in between, so the gap is the API tail.
run_markers <- c(
  `run start` = run_start,
  `tasks finished` = tasks_done,
  `run end` = run_end
)
run_markers <- run_markers[!is.na(run_markers)]
message(
  "Run start: ", if (is.na(run_start)) "not found" else format(run_start),
  " | tasks finished: ", if (is.na(tasks_done)) "not found" else format(tasks_done),
  " | run end: ", if (is.na(run_end)) "not found" else format(run_end)
)

# ggplot layers marking the run window (NULL if no marker was found).
# Use an explicit data frame + inherit.aes = FALSE so it works across facets.
# NB: column is `marker` (not `label`) to avoid clashing with the `~ label`
# facet variable, which would otherwise spawn extra facet panels.
marker_df <- tibble(
  x = unname(run_markers),
  marker = names(run_markers),
  y = Inf
)
run_vlines <- if (nrow(marker_df)) {
  geom_vline(data = marker_df, aes(xintercept = x),
             linetype = "dashed", colour = "grey30", inherit.aes = FALSE)
} else NULL
run_labels <- if (nrow(marker_df)) {
  geom_text(data = marker_df, aes(x = x, y = y, label = marker),
            vjust = 1.3, hjust = 0.05, size = 3, colour = "grey30",
            inherit.aes = FALSE, angle = -45)
} else NULL

# ---- run info: copy log + record versions -----------------------------------
# Keep the source log next to the analysis so a run is self-contained.
invisible(file.copy(log_file, file.path(outdir, basename(log_file)),
                    overwrite = TRUE))

or_unknown <- function(x) if (length(x) == 0 || is.na(x[1])) "unknown" else x[1]
first_capture <- function(pattern, group = 1) {
  hit <- str_subset(lines, pattern)
  if (length(hit) == 0) return(NA_character_)
  str_match(hit[[1]], pattern)[1, group + 1]
}

# Version appears as "N E X T F L O W ~ version 25.10.5" and "Version: 25.10.5
# build 12056" (the build number only on the latter); match either, case-insensitively.
nf_version <- first_capture("(?i)version:? ([0-9][0-9A-Za-z.\\-]*)")
nf_build   <- first_capture("(?i)build ([0-9]+)")
os_info    <- first_capture("System: (.*)$")
nf_command <- first_capture("\\$> (nextflow .*)$")
# All loaded plugins with versions, e.g. "Plugin 'nf-lamin@0.8.2'".
plugins <- str_match(lines, "Plugin '(nf-[a-z]+@[0-9][0-9A-Za-z.\\-]*)")[, 2]
plugins <- sort(unique(plugins[!is.na(plugins)]))

fmt_ts <- function(x) if (is.na(x)) "unknown" else format(x, "%Y-%m-%d %H:%M:%S")
run_info <- c(
  paste("log_file:        ", normalizePath(log_file)),
  paste("analysed_at:     ", format(Sys.time(), "%Y-%m-%d %H:%M:%S")),
  paste("nextflow_version:", or_unknown(nf_version)),
  paste("nextflow_build:  ", or_unknown(nf_build)),
  paste("system:          ", or_unknown(os_info)),
  "plugins:",
  if (length(plugins)) paste0("  - ", plugins) else "  (none found)",
  paste("api_calls:       ", nrow(lines_join)),
  paste("no_response:     ", n_noresp),
  paste("run_start:       ", fmt_ts(run_start)),
  paste("tasks_finished:  ", fmt_ts(tasks_done)),
  paste("run_end:         ", fmt_ts(run_end)),
  paste("command:         ", or_unknown(nf_command))
)
write_lines(run_info, file.path(outdir, "run_info.txt"))

# ---- outputs ----------------------------------------------------------------
# save as parquet for further analysis
nanoparquet::write_parquet(lines_join, file.path(outdir, "apicalls.parquet"))

# extract all artifact s3 paths in request_data
lines_join |>
  filter(!is.na(request_data)) |>
  mutate(
    artifact_s3_path = str_extract(request_data, "s3://[^\"\\s]+")
  ) |>
  filter(!is.na(artifact_s3_path)) |>
  pull(artifact_s3_path) |>
  sort() |>
  unique() |>
  write_lines(file.path(outdir, "artifact_s3_paths.txt"))

if (any(is.na(lines_join$timestamp_start))) warning("Some requests have no matching start line.")

# Aggregate only the non-NA durations so all-"no response" groups yield NA
# (not Inf / NaN with warnings, as min()/quantile() do on empty input).
agg <- function(x, f, ...) {
  x <- x[!is.na(x)]
  if (length(x) == 0) NA_real_ else f(x, ...)
}

# write summary statistics table of status codes by method and endpoint
summary_tbl <- lines_join |>
  group_by(method, endpoint, status = status_lbl) |>
  summarise(
    count = n(),
    min_time_sec = agg(diff_sec, min),
    quartile_25_time_sec = agg(diff_sec, quantile, 0.25),
    mean_time_sec = agg(diff_sec, mean),
    median_time_sec = agg(diff_sec, median),
    quartile_75_time_sec = agg(diff_sec, quantile, 0.75),
    max_time_sec = agg(diff_sec, max),
    .groups = "drop"
  ) |>
  arrange(method, endpoint, status)

summary_tbl |>
  knitr::kable(
    caption = "Summary Statistics of API Response Times by Method, Endpoint, and Status Code",
    col.names = c("Method", "Endpoint", "Status", "Count", "Min Time (s)", "25th Percentile Time (s)", "Mean Time (s)", "Median Time (s)", "75th Percentile Time (s)", "Max Time (s)"),
    digits = 2
  ) |>
  write_lines(file.path(outdir, "api_summary.md"))

status_colours <- c("200" = "darkgreen", "502" = "red", "404" = "blue",
                    "500" = "darkred", "429" = "pink", "no response" = "grey50")

# beeswarm of response times by status code, faceted by method and endpoint
lines_join |>
  filter(!is.na(diff_sec)) |>
  sample_n(n()) |>
  ggplot(aes(x = diff_sec, y = factor(label), colour = factor(status_lbl))) +
  ggbeeswarm::geom_beeswarm(size = 1, alpha = 0.2, orientation = "y") +
  labs(y = "Status Code", x = "Response Time (seconds)", title = "API Response Times by Status Code", colour = "Status") +
  theme_bw() +
  scale_colour_manual(values = status_colours) +
  scale_x_log10(labels = scales::comma) +
  facet_wrap(~ label, scales = "free_y", ncol = 1)

ggsave(file.path(outdir, "api_response_times.png"), width = 10, height = 10)

# dotplot of response time over time
ggplot(lines_join, aes(timestamp_start, diff_sec, colour = factor(status_lbl))) +
  geom_point() +
  run_vlines +
  run_labels +
  labs(x = "Request Timestamp", y = "Response Time (seconds)", title = "API Response Times Over Time", colour = "Status") +
  theme_bw() +
  scale_colour_manual(values = status_colours) +
  scale_y_log10(labels = scales::comma) +
  facet_wrap(~ label, scales = "free_y", ncol = 1)

ggsave(file.path(outdir, "api_response_times_over_time.png"), width = 10, height = 10)

# count of status codes per 60 second intervals, faceted by method and endpoint
lines_join |>
  ggplot(aes(x = timestamp_start, fill = factor(status_lbl))) +
  geom_histogram(binwidth = 60) +
  run_vlines +
  run_labels +
  labs(x = "Request Timestamp", y = "Count", title = "API Request Counts Over Time", fill = "Status") +
  theme_bw() +
  scale_fill_manual(values = status_colours) +
  facet_wrap(~ label, scales = "free_y", ncol = 1) +
  scale_x_datetime(labels = scales::date_format("%H:%M:%S"))

ggsave(file.path(outdir, "api_response_counts.png"), width = 10, height = 10)

message("Wrote outputs to ", normalizePath(outdir))
